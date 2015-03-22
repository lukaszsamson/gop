package com.songo.scalar

/**
 * Created by lukaszsamson on 07.03.15.
 */

import java.nio.charset.Charset

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.persistence.{serialization, PersistentRepr}
import akka.serialization.{ SerializationExtension, Serialization }
import org.json4s.ext.JodaTimeSerializers
import scala.concurrent.Future
import scala.util.control.NonFatal
import eventstore._

trait EventStorePlugin extends ActorLogging { self: Actor =>
  val connection: EsConnection = EventStoreExtension(context.system).connection
  //val serialization: Serialization = SerializationExtension(context.system)
  import context.dispatcher

  import org.json4s._
  import org.json4s.jackson.Serialization
  import org.json4s.jackson.Serialization.{read, write}

  def deserialize(event: Event): PersistentRepr = {
    val clazz = Class.forName(event.data.eventType)
    implicit val formats = Serialization.formats(FullTypeHints(List(clazz))) ++ JodaTimeSerializers.all

    val str = event.data.data.value.decodeString("UTF-8")
    val res = read[AnyRef](str)

    PersistentRepr(res, event.number.value)
  }

  def serialize(data: PersistentRepr): EventData = {
    val clazz = data.payload.getClass
    implicit val formats = Serialization.formats(FullTypeHints(List(clazz))) ++ JodaTimeSerializers.all

    val s = write(data.payload.asInstanceOf[AnyRef])

    EventData(
        eventType = data.payload.getClass.getName,
        data = Content.Json(s)
        )
  }

  case class Metadata(sequenceNr: Long)

  def asyncUnit(x: => Future[_]): Future[Unit] = async(x).map(_ => Unit)

  def async[T](x: => Future[T]): Future[T] = try x catch {
    case NonFatal(f) => Future.failed(f)
  }

  def asyncSeq[A](x: => Iterable[Future[A]]): Future[Unit] = asyncUnit(Future.sequence(x))


}
