package com.songo.scalar

import org.slf4j.LoggerFactory
import akka.actor.Actor
import com.typesafe.scalalogging._

class DeadLetterListener extends Actor with SubscriberActor {
  private var logger = Logger(LoggerFactory.getLogger(this.getClass))
  def subscribedClasses = List(classOf[akka.actor.DeadLetter])
  def receive = {
    case d: akka.actor.DeadLetter => logger.warn(d.toString)
  }
}