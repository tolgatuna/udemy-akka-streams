package part4_techniques

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps

object IntegratingWithActors extends App {
  implicit val system = ActorSystem("IntegratingWithActors")

  /*
   Actor as a flow
   */
  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"String message received: $s")
        sender() ! s"Processed:$s"
      case n: Int =>
        log.info(s"Integer message received: $n")
        sender() ! (2 * n)
      case _ =>
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  val numberSource = Source(1 to 10)
  implicit val timeout: Timeout = Timeout(2 seconds)
  private val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)
//  numberSource.via(actorBasedFlow).to(Sink.foreach[Int](println)).run()
  // Same:
//  numberSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.foreach[Int](println)).run()

  /*
    Actor as a source
   */
  val actorPoweredSource = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
  private val materializedActorRef: ActorRef = actorPoweredSource.to(Sink.foreach[Int](number => println(s"Actor powered flow got number: $number"))).run()
  materializedActorRef ! 10

  // terminate the stream
  materializedActorRef ! akka.actor.Status.Success("complete")

  /*
    Actor as a destination/sink
      - an init message
      - an ack message to confirm the reception
      - a complete message
      - a function to generate a message in case the stream throws an exception
   */
  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream completed")
        context.stop(self)
      case StreamFail =>
        log.warning("Stream failed")
      case message =>
        log.info(s"Message get as Sink $message")
        sender() ! StreamAck
    }
  }

  private val destinationActor: ActorRef = system.actorOf(Props[DestinationActor])
  private val actorPoweredSink = Sink.actorRefWithAck[Int](
    destinationActor,
    ackMessage = StreamAck,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete,
    onFailureMessage = throwable => StreamFail(throwable) //Optional
  )

  Source(1 to 10).to(actorPoweredSink).run()

  // Sink.actorRef // don't recommended!




}
