package janrain.jedi.load

import akka.actor._
import scala.concurrent.{Await, Future}, Future._
import spray.can.Http
import akka.io.IO
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import concurrent.duration._
import spray.http.{HttpResponse, FormData}
import akka.routing.RoundRobinRouter
import spray.can.Http.HostConnectorInfo
import akka.event.LoggingReceive

object LoadJedi extends App {
  implicit val actorSystem = ActorSystem()
  implicit val timeout = new Timeout(10.second)

  import actorSystem.dispatcher

  object JediRequestActor {
    case class Event(kind: String, payload: String, captureAppId: Option[String])
  }
  class JediRequestActor extends Actor with ActorLogging with Stash {
    import JediRequestActor._
    import spray.client.pipelining._

    override def preStart() {
      IO(Http) ! Http.HostConnectorSetup("localhost", 9990)
    }

    def connected(connector: ActorRef): Receive = {
      LoggingReceive {
        case Event(kind, payload, captureAppId) ⇒
          val http = sendReceive(connector)
          var params = Map(
            "type" → kind,
            "payload" → payload
          )
          captureAppId foreach { captureAppId ⇒
            params += "captureAppId" → captureAppId
          }
          val request = Post("/event", FormData(params))
          http(request) pipeTo sender
      }
    }

    def receive = LoggingReceive {
      case HostConnectorInfo(connector, _) ⇒
        context become connected(connector)
        unstashAll()

      case _ ⇒
        stash()
    }
  }

  val requester = actorSystem.actorOf(Props[JediRequestActor].withRouter(RoundRobinRouter(nrOfInstances = 8)))

  // program arguments
  val n = args(0).toInt
  val captureAppId = if (args.length >= 2) Some(args(1)) else None
  val payload = if (args.length >= 3) args(2) else "{}"
  val kind = if (args.length >= 4) args(3) else "entity_update"

  val work = sequence(for {
    _ ← 1 to n
  } yield {
    (requester ? JediRequestActor.Event(kind, payload, captureAppId)).mapTo[HttpResponse]
  })

  val result = Await.ready(work, 30.seconds)

  actorSystem.shutdown()
}
