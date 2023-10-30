package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.language.postfixOps

object GraphBasics extends App {
  implicit val system = ActorSystem("GraphBasics")

  private val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(x => x + 1) // hard computation
  val multiplier = Flow[Int].map(x => x * 10) // hard computation
  val output = Sink.foreach[(Int, Int)](println)

  // step 1 - setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._ // brings some nice operators into scope

      // step 2 - add the necessary components of this
      val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator
      val zip = builder.add(Zip[Int, Int]) // fan in operator

      // step 3 - tying up the components
      input ~> broadcast
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> output

      // step 4 - return a closed shape
      ClosedShape // FREEZE the builder's shape
      // shape
    } // static graph
  ) // runnable graph

//  graph.run() // run the graph and materialize it

  /**
   * Exercise 1: feed a source into 2 sinks at the same time (hint use a broadcast)
   */
  val sink1 = Sink.foreach[Int](x => println(s"Sink 1: ${x}"))
  val sink2 = Sink.foreach[Int](x => println(s"Sink 2: ${x}"))
  val graphEx1 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._ // brings some nice operators into scope

      // step 2 - add the necessary components of this
      val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator

      // step 3 - tying up the components
//      input ~> broadcast
//      broadcast.out(0) ~> sink1
//      broadcast.out(1) ~> sink2

      // Easy way
      input ~> broadcast ~> sink1
      broadcast ~> sink2

      // step 4 - return a closed shape
      ClosedShape // FREEZE the builder's shape
      // shape
    } // static graph
  ) // runnable graph

//  graphEx1.run() // run the graph and materialize it

  /**
   * Exercise 2: Implement following graph
   *
   * fast source ~> |-------|        |---------| ~> sink 1
   *                | Merge |   ~>   | Balance |
   * slow source ~> |-------|        |---------| ~> sink 2
   */

  import scala.concurrent.duration._

  private val fastSource = input.throttle(5, 1 second)
  private val slowSource = input.throttle(1, 1 second).map(x => x + 1000)
  private val graphEx2 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[Int](2)) // merge operator
      val balance = builder.add(Balance[Int](2)) // merge operator

      fastSource ~> merge ~> balance ~> sink1
      slowSource ~> merge;
      balance ~> sink2

      ClosedShape
    }
  )
  graphEx2.run()


}
