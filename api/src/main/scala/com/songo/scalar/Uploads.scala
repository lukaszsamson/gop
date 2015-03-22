package com.songo.scalar

import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.Logger
import akka.persistence.PersistentActor
import akka.persistence.RecoveryCompleted
import akka.persistence.SnapshotOffer
import akka.actor.Status

object Upload {

  object Event {
    sealed trait Event extends EventBase
    case class FileUploaded(id: String, fileName: String, uploadDate: DateTime) extends Event
    case class PickCreated(pickId:String, fileIds: List[String]) extends Event
    case class FileDeleted(id: String) extends Event
  }

  object Command {
    sealed trait Command
    case class Upload(id: String, fileName: String) extends Command
    case class CreatePick(pickId: String, fileIds: List[String]) extends Command
    case class UseFiles(ids: List[String], pickId: String) extends Command
  }
  
  object Query {
    sealed trait Query
    case class CanUseFiles(ids: List[String]) extends Query
    case class GetFiles() extends Query
  }

  case class File(fileName: String, uploadDate: DateTime, usedInPicks: Set[String], isDeleted: Boolean)

  case class UploadsState(Files: Map[String, File], PickIds: List[String]) {
    import Event._

    def updated(evt: Event): UploadsState =
      
        evt match {
          case FileUploaded(id, fileName, uploadDate) => {
            copy(Files + (id -> File(fileName, uploadDate, Set[String](), false)))
          }
          case PickCreated(pickId, fileIds) => {
            copy(PickIds = pickId :: PickIds)
          }
          case x => throw new Exception(s"No match for $x")
        }
    private var logger = Logger(LoggerFactory.getLogger(this.getClass))
    override def toString: String = Files.toString
  }

  // implementation of the actor
  class UploadsActor(userId: String) extends PersistentActor {
    import Query._
    import Command._
    import Event._
    private var state = UploadsState(Map[String, File](), List[String]())
    override def persistenceId = userId

    private val logger = Logger(LoggerFactory.getLogger(this.getClass))
    def updateStateAndAck(event: Event): Unit = {
      updateState(event)
      sender ! Ack
    }
    def updateState(event: Event): Unit = {
      logger.debug(s"Processing event: $event")
      logger.debug(s"Old state: $state")
      state = state.updated(event)
      logger.debug(s"New state: $state")
    }
    val receiveCommand: Receive = {
      case Command.Upload(id, fileName) => {
        persist(FileUploaded(id, fileName, DateTime.now()))(updateStateAndAck)
      }
      case CreatePick(pickId, fileIds) => {
        if (state.PickIds.contains(pickId))
          sender ! Status.Failure(new Exception("Already created"))
        else if (fileIds.isEmpty || fileIds.size < 2)
          sender ! Status.Failure(new Exception("At least two files required"))
        else if (fileIds.exists { !state.Files.contains(_) })
          sender ! Status.Failure(new Exception("File not exists"))
        else
          persist(PickCreated(pickId, fileIds))(updateStateAndAck)
      }
        
      case CanUseFiles(ids) =>
        sender ! ids.forall(state.Files.get(_) match {
          case None => false
          case Some(x) => !x.isDeleted
        })
      case GetFiles() =>
        sender ! state.Files.map(f => f._1)
    }
    val receiveRecover: Receive = {
      case SnapshotOffer(_, snapshot: UploadsState) => state = snapshot
      case RecoveryCompleted                        =>
      case x: Event                                 => updateState(x)
    }
  }
}