package com.songo.scalar

import org.slf4j.LoggerFactory

import com.typesafe.scalalogging.Logger
import akka.actor.Status
import akka.persistence.PersistentActor
import akka.persistence.RecoveryCompleted
import akka.persistence.SnapshotOffer
object Pick {


  object Event {
    sealed trait Event
    case class PickVoted(file: String) extends Event
    case class PickCreated(id: String, files: List[String]) extends Event
  }

  object Command {
    sealed trait Command
    case class Create(id: String, files: List[String]) extends Command
    case class Vote(file: String) extends Command
  }
  
  object Query {
    sealed trait Query
    case class GetResults()
  }

  case class PickState(Votes: Map[String, Int]) {
    import Event._

    def updated(evt: Event): PickState =
      copy(
        evt match {
          case PickCreated(id, files) => {
            files.map(f => (f, 0)).toMap
          }
          case PickVoted(file) => {
            Votes + (file -> (Votes(file) + 1))
          }
          case x => throw new Exception(s"No match for $x")
        })
    private var logger = Logger(LoggerFactory.getLogger(this.getClass))
    override def toString: String = Votes.toString
  }

  class PickActor(pickId: String) extends PersistentActor {
    import Command._
    import Event._
    import Query._
    override def persistenceId = pickId
    var state = PickState(Map[String, Int]())
    def updateStateAndAck(event: Event): Unit = {
      updateState(event)
      sender ! Ack
      context.system.eventStream.publish(event)
    }
    def updateState(event: Event): Unit = {
      logger.debug(s"Processing event: $event")
      logger.debug(s"Old state: $state")
      state = state.updated(event)
      logger.debug(s"New state: $state")
      
    }

    val receiveRecover: Receive = {
      case SnapshotOffer(_, snapshot: PickState) => state = snapshot
      case RecoveryCompleted                     =>
      case x: Event                              => updateState(x)
    }

    val receiveCommand: Receive = {
      case Vote(data) => {
        logger.debug(s"Vote recieved $state $data")
        if (state.Votes.isEmpty)
          sender ! Status.Failure(new Exception("Not created"))
        else if (!state.Votes.contains(data))
          sender ! Status.Failure(new Exception("Cannot vote for not existing file"))
        else {
          logger.debug(s"emiting Voted")
          persist(PickVoted(data))(updateStateAndAck)
        }
      }
      case Create(id, data) => {
        logger.debug(s"Create recieved $state")
        if (data.size < 2)
          sender ! Status.Failure(new Exception("At least 2 files needed"))
        else if (!state.Votes.isEmpty)
          sender ! Status.Failure(new Exception("Already created"))
        else {
          logger.debug(s"emiting Created")
          persist(PickCreated(id, data))(updateStateAndAck)
          
        }
      }
      case GetResults() =>
        logger.debug(s"Get results recieved")
        sender ! state.Votes
    }
    private var logger = Logger(LoggerFactory.getLogger(this.getClass))
  }
}