package part3_graphs

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source}

import scala.concurrent.Future

object OpenGraphs extends App {
  implicit val system: ActorSystem = ActorSystem("OpenGraphs")

  /*
    A composite source that concatenates 2 sources
      - emits ALL the elements from the first source
      - then ALL the elements from the second

      source 1 ~> |--------|
                  | concat | ~>
      source 2 ~> |--------|

      * CONCAT is taking all elements from its first input
        and then all the elements from its second input
        and pushes them out so on so forth.
   */
  private val firstSource = Source(1 to 10)
  private val secondSource = Source(42 to 60)

  // step 1
  private val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2: declaring components
      val concat = builder.add(Concat[Int](2))

      // step 3: tying up them together
      firstSource ~> concat
      secondSource ~> concat

      // step 4- return the shape
      SourceShape(concat.out)
    }
  )

//  sourceGraph.to(Sink.foreach(println)).run()

  /*
    Complex sink
   */
  private val sink1 = Sink.foreach[Int](x => println(s"Sink 1 $x"))
  private val sink2 = Sink.foreach[Int](x => println(s"Sink 2 $x"))

  // step 1
  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2 - add a broadcast
      val broadcast = builder.add(Broadcast[Int](2))

      // step 3 - return the shape
      broadcast ~> sink1
      broadcast ~> sink2

      // step 4- return the shape
      SinkShape(broadcast.in)
    }
  )

  sourceGraph.to(sinkGraph).run()

  /**
   * Exercise 1
   * Write your own flow that's composed of two other flows
   *  - one that adds 1 to a number
   *  - one that multiples a number with 10
   */
  private val flow1 = Flow[Int].map(_ + 1)
  private val flow2 = Flow[Int].map(_ * 10)
  // step 1
  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2 - define auxiliary components
      val flow1Shape = builder.add(flow1)
      val flow2Shape = builder.add(flow2)

      // step 3 - connect the components
      flow1Shape ~> flow2Shape

      // step 4
      FlowShape(flow1Shape.in, flow2Shape.out) // SHAPE
    }
  )

  sourceGraph.via(flowGraph).to(sinkGraph).run()

  /**
   * Exercise 2
   * Create a flow from a sink and a source
   * ~> Sink  Source ~>
   */

  // step 1
  val flowGraph2 = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2 - define auxiliary components
      val sinkGraphShape = builder.add(sinkGraph)
      val sourceGraphShape = builder.add(sourceGraph)
      // step 3 - connect the components

      // step 4
      FlowShape(sinkGraphShape.in, sourceGraphShape.out) // Not much meaningful
    }
  )





}
