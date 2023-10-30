package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Graph, OverflowStrategy, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip}

object GraphCycles extends App {
  implicit val system: ActorSystem = ActorSystem("GraphCycles")

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map(x => {
      println(s"Accelerating: $x");
      x + 1
    }))

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape

    ClosedShape
  }

  //  RunnableGraph.fromGraph(accelerator).run()
  // graph cycle deadlock!

  /*
    Solution 1:
    MergePreferred
   */
  val acceleratorWithMergePreferred = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergePreferredShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map(x => {
      println(s"Accelerating: $x");
      x + 1
    }))

    sourceShape ~> mergePreferredShape ~> incrementerShape
    mergePreferredShape <~ incrementerShape

    ClosedShape
  }
//  RunnableGraph.fromGraph(acceleratorWithMergePreferred).run()

  /*
      Solution 2:
      buffers
  */
  val acceleratorWithBufferedAcc = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map(x => {
      println(s"Accelerating: $x");
      Thread.sleep(100)
      x
    }))

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape

    ClosedShape
  }
  RunnableGraph.fromGraph(acceleratorWithBufferedAcc).run()

  /*
    cycles risk deadlocking
      - add bounds to the number of elements in the cycle

     boundedness vs liveness
   */

  /**
   * Challenge: create a fan-in shape
   *  - two inputs will be fed the EXACTLY ONE number (1 and 1)
   *  - output will emit an INFINITE FIBONACCI SEQUENCE of those 2 numbers
   *    1, 2, 3, 5, 8 ....
   *
   *  Hint: Use Zip and cycles, mergePreferred
   */
  private val fibonacciGenerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val zipShape = builder.add(Zip[BigInt, BigInt])
    val mergePreferredShape = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val fibonacciLogic = builder.add(Flow[(BigInt, BigInt)].map { pair =>
      val last = pair._1
      val previous = pair._2

      Thread.sleep(100)

      (last + previous, last)
    })
    val broadcast = builder.add(Broadcast[(BigInt, BigInt)]((2)))
    val extractLast = builder.add(Flow[(BigInt, BigInt)].map(pair => pair._1))

    zipShape.out ~> mergePreferredShape ~> fibonacciLogic ~> broadcast ~> extractLast
                    mergePreferredShape.preferred         <~ broadcast

    UniformFanInShape(extractLast.out, zipShape.in0, zipShape.in1)
  }

  val fiboGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val source1 = builder.add(Source.single[BigInt](1))
      val source2 = builder.add(Source.single[BigInt](1))
      val fiboShape = builder.add(fibonacciGenerator)
      val printSink = builder.add(Sink.foreach[BigInt](println))

      source1 ~> fiboShape ~> printSink
      source2 ~> fiboShape

      ClosedShape
    }
  )

  fiboGraph.run()
}
