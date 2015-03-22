package com.songo.scalar

import eventstore.Event
import org.json4s.FullTypeHints
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._

/**
 * Created by lukaszsamson on 07.03.15.
 */
object Utils {
  def newId(): String = java.util.UUID.randomUUID.toString

  def deserializeEvent(event: Event): AnyRef = {
    val clazz = Class.forName(event.data.eventType)
    implicit val formats = Serialization.formats(FullTypeHints(List(clazz))) ++ JodaTimeSerializers.all

    val str = event.data.data.value.decodeString("UTF-8")
    val res = read[AnyRef](str)
    res
  }
}
