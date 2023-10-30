package part2_primer

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration._
import scala.language.postfixOps

object BackpressureBasics extends App {
  implicit val system: ActorSystem = ActorSystem("BackpressureBasics")

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    // simulating a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  // fastSource.to(slowSink).run() // fusing?

//  fastSource.async.to(slowSink).run() // backpressure in place!

  val simpleFlow = Flow[Int].map {x =>
    println(s"Incoming: $x")
    x + 1
  }

//  fastSource.async
//    .via(simpleFlow).async
//    .to(slowSink)
//    .run() //backpressure in real life

  /*
    reactions to backpressure (in order):
      - try to slow down if possible
      - buffer elements until there's more demand
      - drop down elements from the buffer if it overflows
      - tear down/kill the whole stream (failure)
   */

  /*
    1 - 16: nobody is backpressured
    17 -26: flow will buffer, flow will start dropping at the next element
    26-1000: flow will always drop the oldest element
      => 991 - 1000 => 992 - 1001 => sink
   */
  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
//  fastSource.async
//    .via(bufferedFlow).async
//    .to(slowSink)
//    .run() //backpressure in real life

  /*
    Overflow strategies:
      - dropHead = oldest
      - dropTail = newest
      - dropNew = exact element to be added = keeps the buffer as same
      - drop the entire buffer
      - backpressure signal
      - fail!
   */

  // throttling
  fastSource.throttle(10, 2 second).runWith(Sink.foreach(println))




}
