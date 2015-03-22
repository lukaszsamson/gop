package com.songo.scalar

import akka.actor.{ReceiveTimeout, ActorLogging, Actor}
import eventstore.{Event, LiveProcessingStarted, IndexedEvent}

import scala.concurrent.duration._

object PickDb {
  case class GetPick()
  class PickDb extends Actor with ActorLogging {

    //context.setReceiveTimeout(10.second)

    def receive = count(Map[String, List[String]]())

    def count(picks: Map[String, List[String]]): Receive = {
      case x: IndexedEvent  if x.event.data.eventType == classOf[Pick.Event.PickCreated].getName      => {
        val e = Utils.deserializeEvent(x.event).asInstanceOf[Pick.Event.PickCreated]
        context become count(picks + (e.id -> e.files))
      }
      case LiveProcessingStarted => log.info("live processing started")
      case GetPick() => {
        log.info("getpick {}", picks)
        val res = picks.find { x => true}
        sender ! res
      }
    }
  }
}
