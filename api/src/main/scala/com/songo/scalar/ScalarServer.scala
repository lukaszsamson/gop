package com.songo.scalar

import akka.actor.{ Props, Actor, ActorSystem, ActorRef, ActorNotFound }
import akka.pattern.ask
import akka.util.Timeout
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
object Utils {
  def newId(): String = java.util.UUID.randomUUID.toString
}

object ScalarServer extends App with spray.routing.SimpleRoutingApp {
  // setup
  implicit val actorSystem = ActorSystem()
  implicit val timeout = Timeout(1.second)
  import actorSystem.dispatcher
  import spray.routing._
  import spray.json._
  val listener = actorSystem.actorOf(Props(classOf[DeadLetterListener]))
  // an actor which holds a map of counters which can be queried and updated
  val uploadsActor = actorSystem.actorOf(Props(new Upload.UploadsActor()), "upload")
  val fileUserActor = actorSystem.actorOf(Props(new PickFileUser()), "pickFileUser")
    val db = actorSystem.actorOf(Props(new PickDb()), "pickDb")
  //private val config =  ConfigFactory.load()

  private var logger = Logger(LoggerFactory.getLogger("ScalarServer"))

  def getActor[T<:PersistentActor](clazz: Class[T], id: String): Future[ActorRef] = {
    var className = clazz.getName
    actorSystem.actorSelection(s"/user/$className-$id").resolveOne.recover {
      case ActorNotFound(_) => {
        logger.debug(s"ActorNotFound: /user/$className-$id")
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

    def saveAttachment(fileName: String, content: InputStream): Boolean = {
      saveAttachment_[InputStream](fileName, content,
        { (is, os) =>
          val buffer = new Array[Byte](16384)
          Iterator
            .continually(is.read(buffer))
            .takeWhile(-1 !=)
            .foreach(read => os.write(buffer, 0, read))
        })
    }

    def saveAttachment_[T](fileName: String, content: T, writeFile: (T, OutputStream) => Unit): Boolean = {
      try {
        val fos = new java.io.FileOutputStream(fileName)
        writeFile(content, fos)
        fos.close()
        true
      } catch {
        case _ => false
      }
    }

    System.setProperty("akka.persistence.journal.leveldb.dir", "/uploads")
    // definition of how the server should behave when a request comes in

    import UserJsonSupport._
    path("login") {

      entity(as[User]) { person =>
        complete(s"Person: ${person.userID}")
      }
    } ~
      path("picks") {
        post {
          entity(as[MultipartFormData]) { formData =>
            val id = Utils.newId()
            val bodyPart = formData.fields.find(x => true).get

            //val content = entity.buffer
            val content = new ByteArrayInputStream(bodyPart.entity.data.toByteArray)
            //val contentType = bodyPart.headers.find(h => h.is("content-type")).get.value
            val fileName = bodyPart.headers.find(h => h.is("content-disposition")).get.value.split("filename=").last
            val result = saveAttachment(s"/uploads/image_$id", content)

            onComplete(uploadsActor ? Upload.Command.Upload(id, fileName)) {
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
                      (db ? GetPick()).mapTo[(String, List[String])]
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
                onComplete(fileUserActor ? CreatePick(request.uploadsIds)) {
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
                  case Success(results: Map[String, Int]) => respondWithMediaType(`application/json`) { complete { results } }
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

  case class CreatePick(files: List[String])

  class PickFileUser extends Actor with SubscriberActor {
    private var logger = Logger(LoggerFactory.getLogger(getClass))
    def subscribedClasses = List(classOf[Pick.Event.PickCreated])
    def receive = {
      case Pick.Event.PickCreated(id, files) =>
        uploadsActor ! Upload.Command.UseFiles(files, id)
      case CreatePick(files) => {
        logger.debug("CreatePick")
        val id = Utils.newId()
        val f = for {
          _ <- Future(logger.debug(s"Asking uploads"))
          canUse <- (uploadsActor ? Upload.Query.CanUseFiles(files)).mapTo[Boolean]
          _ <- Future(logger.debug(s"canUse = $canUse"))
          if canUse
          a <- getActor(classOf[Pick.PickActor], id)
          _ <- Future(logger.debug(s"Asking pick"))
          r <- a ? Pick.Command.Create(id, files)
          _ <- Future(logger.debug(s"Pick response = $r"))
        } yield {
          id
        }
        f pipeTo sender
      }
    }
  }

  case class GetPick()
  class PickDb extends Actor with SubscriberActor {
    private var picks = Map[String, List[String]]()
    def subscribedClasses = List(classOf[Pick.Event.PickCreated])
    def receive = {
    case Pick.Event.PickCreated(id, files) =>
      picks = picks + (id->files)
    case GetPick() =>
      sender ! picks.find { x => true }
    }
  }
  
  
}
