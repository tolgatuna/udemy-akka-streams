package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FanOutShape2, UniformFanInShape, UniformFanOutShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}

import java.util.Date

object MoreOpenGraphs extends App {
  implicit val system: ActorSystem = ActorSystem("MoreOpenGraphs")

  // --------------- UniformFanInShape --------------- //
  /*
    Example: Max3 Operator (It will be NOT runnable)
      - 3 inputs of type int
      - the maximum of the 3

                             Max3 Component
                  |-----------------------------------|
                  |      zipWith                      |
      Source 1 ~> |  ~~> |-------|       zipWith      |
                  |      |       |  ~~> |-------|     |
      Source 2 ~> |  ~~> |-------|      |       | ~~> | ~>
                  |                     |       |     |
      Source 3 ~> |  ~~~~~~~~~~~~~~~~>  |-------|     |
                  |-----------------------------------|
   */

  // step 1
  private val max3StaticGraph = GraphDSL.create() {implicit builder =>
    import GraphDSL.Implicits._

    // step 2 - define aux Shapes
    val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))

    // step 3 - combine
    max1.out ~> max2.in0

    // step 4
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  private val source1 = Source(1 to 10)
  private val source2 = Source((1 to 10).map(_ => 5))
  private val source3 = Source((1 to 10).reverse)

  private val maxSinkPrinter = Sink.foreach[Int](x => println(s"Max is $x"))

  /*
        If we want to use that component now

                      Max3 Component
                    |----------------|
        Source 1 ~> |                |       Print Sink
                    |                |     |------------|
        Source 2 ~> |                |  ~> |            | ~>
                    |                |     |------------|
        Source 3 ~> |                |
                    |----------------|
  */
  // step 1
  private val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2 - declare SHAPES
      val max3StaticGraphShape = builder.add(max3StaticGraph)

      // step 3 - tie
      source1 ~> max3StaticGraphShape ~> maxSinkPrinter
      source2 ~> max3StaticGraphShape
      source3 ~> max3StaticGraphShape

      // step 4 - close
      ClosedShape
    }
  )

  max3RunnableGraph.run()

  // --------------- UniformFanOutShape --------------- //
  /*
      Example: Processing bank transactions
        Transaction suspicious if amount > 10000

        Streams component for Transactions
          - output 1: let the transaction go through
          - output 2: suspicious transaction ids

                                Suspicious Txn Graph
                              |----------------------|
                              |                      | ~> Process
        Transaction Source ~> |                      |
                              |                      | ~> Suspicious txn id
                              |----------------------|
   */

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)
  val transactionSource = Source(List(
    Transaction("9871237", "Paul", "Jim", 100, new Date),
    Transaction("2271439", "Daniel", "Ricardo", 98000, new Date),
    Transaction("6723412", "Robert", "Alex", 7000, new Date)
  ))

  val bankProcessorService = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](id => println(s"Suspicious transaction Id: $id"))

  // step 1
  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    // step 2 - define SHAPES
    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(txn => txn.amount > 10000))
    val txnIdExtractor = builder.add(Flow[Transaction].map[String](txn => txn.id))

    // step 3 - tie Shapes together
    broadcast.out(1) ~> suspiciousTxnFilter ~> txnIdExtractor

    // step 4
    new FanOutShape2(broadcast.in, broadcast.out(0), txnIdExtractor.out)
  }

  /*
    Lets use it
  */
  // step 1
  private val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2
      val suspiciousTxnStaticGraphShape = builder.add(suspiciousTxnStaticGraph)

      // step 3
      transactionSource ~> suspiciousTxnStaticGraphShape.in
      suspiciousTxnStaticGraphShape.out0  ~> bankProcessorService
      suspiciousTxnStaticGraphShape.out1  ~> suspiciousAnalysisService

      // step 4
      ClosedShape
    }
  )

  suspiciousTxnRunnableGraph.run()
}
