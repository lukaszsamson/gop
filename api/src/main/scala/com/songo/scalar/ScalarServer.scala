package com.songo.scalar

import _root_.eventstore.{StreamSubscriptionActor, EventStream, EventStoreExtension, SubscriptionActor}
import _root_.eventstore.tcp.ConnectionActor
import akka.actor.{ Props, Actor, ActorSystem, ActorRef, ActorNotFound }
import akka.pattern.ask
import akka.util.Timeout
import com.songo.scalar.PickDb.{GetPick, PickDb}
import scala.util._
import scala.concurrent.duration._
import com.typesafe.scalalogging._
import org.slf4j.LoggerFactory
import scala.concurrent.Future
import akka.persistence._
import java.util.{ Date, Calendar }
import akka.pattern.{ after, ask, pipe }
import spray.httpx._
import spray.http._
import spray.http.MediaTypes._
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.io.InputStream


object ScalarServer extends App with spray.routing.SimpleRoutingApp {
  // setup
  implicit val actorSystem = ActorSystem()
  implicit val timeout = Timeout(10.second)
  import actorSystem.dispatcher
  import spray.routing._
  import spray.json._
  // an actor which holds a map of counters which can be queried and updated
  //val uploadsActor = actorSystem.actorOf(Props(new Upload.UploadsActor()), "upload")
  val fileUserActor = actorSystem.actorOf(Props(new PickFileUser()), "pickFileUser")
    val db = actorSystem.actorOf(Props[PickDb], "pickDb")


  val connection = EventStoreExtension(actorSystem).actor

  actorSystem.actorOf(SubscriptionActor.props(connection, db), "subscription")


  //private val config =  ConfigFactory.load()

  private var logger = Logger(LoggerFactory.getLogger("ScalarServer"))

  def getActor[T<:PersistentActor](clazz: Class[T], id: String): Future[ActorRef] = {
    val className = clazz.getName
    actorSystem.actorSelection(s"/user/$className-$id").resolveOne.recover {
      case ActorNotFound(_) => {
        //logger.debug(s"ActorNotFound: /user/$className-$id")
        actorSystem.actorOf(Props(clazz, id), s"$className-$id")
      }
    }
  }

  case class User(accessToken: String, expiresIn: Int, signedRequest: String, userID: String)
  object UserJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val PortofolioFormats = jsonFormat4(User)
  }

  case class CreatePickRequest(uploadsIds: List[String])
  object CreatePickRequestJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val PortofolioFormats = jsonFormat1(CreatePickRequest)
  }

  startServer(interface = "0.0.0.0", port = 3000) {
    println(classOf[Pick.Event.PickCreated].getName)

    def saveAttachment(fileName: String, content: InputStream): Unit = {
      saveAttachment_[InputStream](fileName, content,
        { (is, os) =>
          val buffer = new Array[Byte](16384)
          Iterator
            .continually(is.read(buffer))
            .takeWhile(-1 !=)
            .foreach(read => os.write(buffer, 0, read))
        })
    }

    def saveAttachment_[T](fileName: String, content: T, writeFile: (T, OutputStream) => Unit): Unit = {
        val fos = new java.io.FileOutputStream(fileName)
        writeFile(content, fos)
        fos.close()
    }

    import UserJsonSupport._
    path("login") {

      entity(as[User]) { person =>
        complete(s"Person: ${person.userID}")
      }
    } ~
      path("picks") {
        get {
          val f = for {
            a <- getActor(classOf[Upload.UploadsActor], "user1")
            r <- a ? Upload.Query.GetFiles()
          } yield r

          onComplete(f) {
            case Success(l: List[String]) => respondWithMediaType(`application/json`) {
              complete { l }
            }
            case Failure(th) => failWith(th)
          }
        } ~
        post {
          entity(as[MultipartFormData]) { formData =>
            val id = Utils.newId()
            val bodyPart = formData.fields.find(x => true).get

            //val content = entity.buffer
            val content = new ByteArrayInputStream(bodyPart.entity.data.toByteArray)
            //val contentType = bodyPart.headers.find(h => h.is("content-type")).get.value
            val fileName = bodyPart.headers.find(h => h.is("content-disposition")).get.value.split("filename=").last
            saveAttachment(s"/uploads/image_$id", content)

            val f = for {
              a <- getActor(classOf[Upload.UploadsActor], "user1")
              r <- a ? Upload.Command.Upload(id, fileName)
            } yield r

            onComplete(f) {
              case Success(_) => respondWithMediaType(`application/json`) {
                complete { Map[String, String]("pickId" -> id) }
              }
              case Failure(th) => failWith(th)
            }
          }
        }
      } ~
      path("next") {
        get {
          respondWithMediaType(`application/json`) {
                    complete { 
                      (db ? GetPick()).mapTo[Option[(String, List[String])]]
                    }
        }
        }
      } ~
      pathPrefix("questions") {
        pathEnd {
          post {
            import CreatePickRequestJsonSupport._
            entity(as[CreatePickRequest]) { request =>
              {
                onComplete(fileUserActor ? CreatePick("user1", request.uploadsIds)) {
                  case Success(id: String) => respondWithMediaType(`application/json`) {
                    complete { Map[String, String]("questionId" -> id) }
                  }
                  case Failure(th) => failWith(th)
                }

              }
            }
          }
        } ~
          pathPrefix(Segment) { pickId =>
            pathEnd {
              get {
                val f = for {
                  a <- getActor(classOf[Pick.PickActor], pickId)
                  r <- a ? Pick.Query.GetResults()
                } yield { r }
                onComplete(f) {
                  case Success(results: List[(String, Int)]) => respondWithMediaType(`application/json`) {
                    complete {
                      results.map(m => Map[String, String]("id" -> m._1, "votes" -> m._2.toString))
                    }
                  }
                  case Failure(th)                        => failWith(th)
                }
              }
            } ~
              path("pick" / Segment / "vote") { fileId =>
                post {
                  val f = for {
                    a <- getActor(classOf[Pick.PickActor], pickId)
                    r <- a ? Pick.Command.Vote(fileId)
                  } yield { r }
                  onComplete(f) {
                    case Success(_)  => complete { "Ok" }
                    case Failure(th) => failWith(th)
                  }
                }
              }
          }
      }
  }

  case class CreatePick(userId: String, files: List[String])
  case class UploadFile(userId: String, files: List[String])

  class PickFileUser extends Actor with SubscriberActor {
    private val logger = Logger(LoggerFactory.getLogger(getClass))
    def subscribedClasses = List(classOf[Pick.Event.PickCreated])
    def receive = {
      case e: Pick.Event.PickCreated =>
        getActor(classOf[Upload.UploadsActor], e.userId).map(a =>
          a ! Upload.Command.UseFiles(e.files, e.id))
      case c: CreatePick => {
        logger.debug("CreatePick")
        val pickId = Utils.newId()
        val f = for {
          _ <- Future(logger.debug(s"Asking uploads"))
          a <- getActor(classOf[Upload.UploadsActor], c.userId)
          canUse <- (a ? Upload.Query.CanUseFiles(c.files)).mapTo[Boolean]
          _ <- Future(logger.debug(s"canUse = $canUse"))
          if canUse

          a <- getActor(classOf[Pick.PickActor], pickId)
          _ <- Future(logger.debug(s"Asking pick"))
          r <- a ? Pick.Command.Create(pickId, c.files, c.userId)
          _ <- Future(logger.debug(s"Pick response = $r"))
        } yield {
          pickId
        }
        f pipeTo sender
      }
    }
  }


  
  
}

