package com.songo.scalar

import akka.persistence.PersistentActor

class UserActor  extends PersistentActor {
  override def receiveRecover: Receive = ???

  override def receiveCommand: Receive = ???

  override def persistenceId: String = ???
}