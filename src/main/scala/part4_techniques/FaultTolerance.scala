package part4_techniques

import akka.actor.ActorSystem
import akka.stream.ActorAttributes
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.scaladsl.{Sink, Source}

import scala.language.postfixOps

object FaultTolerance extends App {
  implicit val system: ActorSystem = ActorSystem("FaultTolerance")

  // 1 - logging
  private val faultySource = Source(1 to 10).map(e => if (e == 6) throw new RuntimeException() else e)
  //  faultySource.log("trackingElements").to(Sink.ignore).run() // will crash the app

  // 2 - gracefully terminating a stream
  faultySource.recover {
    case _: RuntimeException => Int.MinValue
  }.log("gracefulSource")
    .to(Sink.ignore)
//    .run()

  // 3 - recover with another stream
  faultySource.recoverWithRetries(3, {
    case _: RuntimeException => Source(90 to 99)
  }).log("recoverWithRetries")
    .to(Sink.ignore)
//    .run()

  // 4 - backoff supervision

  //  import scala.concurrent.duration._
  //
  //  val restartSource = RestartSource.onFailuresWithBackoff(
  //    minBackoff = 1 second,
  //    maxBackoff = 30 seconds,
  //    randomFactor = 0.2,
  //  )(() => {
  //    val randomNumber = new Random().nextInt(20)
  //    Source(1 to 10).map(elem => if (elem == randomNumber) throw new RuntimeException() else elem)
  //  })
  //  restartSource.log("restartSourceBackoff")
  //    .to(Sink.ignore)
  //    .run

  // 5 - supervision strategy
  private val numbers = Source(1 to 20).map(e => if (e == 13) throw new RuntimeException("bad luck") else e).log("supervision")
  numbers.withAttributes(ActorAttributes.supervisionStrategy {
    /*
      Resume = Skip faulty element
      Stop = Stop the stream
      Restart = resume + clears internal state
     */
    case _: RuntimeException => println("Resumed"); Resume
    case _ => println("Stopped"); Stop
  }).to(Sink.ignore).run()


}
