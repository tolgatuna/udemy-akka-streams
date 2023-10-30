package part4_techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future
import scala.language.postfixOps

object IntegrationWithExternalServices extends App {
  implicit val system = ActorSystem("IntegrationWithExternalServices")
  // import system.dispatcher // not recommended!
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  // example: simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)
  val eventSource = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
    PagerEvent("FastDataPipeline", "Illegal elements in the data pipeline", new Date),
    PagerEvent("AkkaInfra", "A service stopped responding", new Date),
    PagerEvent("SuperFrontend", "A button failed", new Date),
  ))

  object PagerService {
    val engineers = List("Daniel", "John", "Lady Gaga")
    val emails = Map(
      "Daniel" -> "daniel@rockthejvm.com",
      "John" -> "john@rockthejvm.com",
      "Lady Gaga" -> "lady.gaga@gmail.com"
    )

    def processEvent(pagerEvent: PagerEvent) = Future {
      val engineerIndex = pagerEvent.date.toInstant.getEpochSecond / (24 * 3600) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"Sending engineer $engineer a high priority email $pagerEvent")
      Thread.sleep(1000)
      engineerEmail
    }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  val pageEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => PagerService.processEvent(event))
  // guarantees the relative order of elements
  val pagesEmailsSink = Sink.foreach[String](email => println(s"Successfully sent notification to $email"))
  pageEngineerEmails.to(pagesEmailsSink).run()

  class PagerServiceActor extends Actor with ActorLogging{
    val engineers = List("Daniel", "John", "Lady Gaga")
    val emails = Map(
      "Daniel" -> "daniel@rockthejvm.com",
      "John" -> "john@rockthejvm.com",
      "Lady Gaga" -> "lady.gaga@gmail.com"
    )

    private def processEvent(pagerEvent: PagerEvent) = {
      val engineerIndex = pagerEvent.date.toInstant.getEpochSecond / (24 * 3600) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"Sending engineer $engineer a high priority email $pagerEvent")
      Thread.sleep(1000)
      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  import akka.pattern.ask
  import scala.concurrent.duration._
  implicit val timeout = Timeout(4 seconds)
  val pagerActor = system.actorOf(Props[PagerServiceActor], "pagerActor")
  private val alternativePagedEngineersEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
  alternativePagedEngineersEmails.to(pagesEmailsSink).run()

  // do not confused mapAsync with async (ASYNC boundary - It will make a component or a chain run on a separate actor!)
}
