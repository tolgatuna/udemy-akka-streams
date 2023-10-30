package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{FlowShape, SinkShape}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {
  implicit val system = ActorSystem("GraphMaterializedValues")

  val wordSource = Source(List("Akka", "is", "awesome", "rock", "the", "jvm"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count + 1)

  /*
    A composite component (sink)
      - print out all strings which are lowercase
      - COUNTS the strings that are short (less than 5 character)
   */
  // step 1
  val complexWordSink = Sink.fromGraph(
    GraphDSL.createGraph(counter) { implicit builder => counterShape =>
      import GraphDSL.Implicits._

      // step 2 - Shapes
      val broadcast = builder.add(Broadcast[String](2))
      val lowercaseFilter = builder.add(Flow[String].filter(word => word == word.toLowerCase))
      val shortStringsFilter = builder.add(Flow[String].filter(_.length < 5))

      // step 3 - connections
      broadcast ~> lowercaseFilter  ~> printer
      broadcast ~> shortStringsFilter ~> counterShape

      // step 4 - the shape
      SinkShape(broadcast.in)
    }
  )

  private val result: Future[Int] = wordSource.runWith(complexWordSink)
  import system.dispatcher
  result.onComplete {
    case Success(count) => println(s"The short strings count is $count")
    case Failure(exception) => println(s"Error: $exception")
  }

  /**
   * Exercise
   * Hint: use a broadcast and Sink.fold
   */
  private def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink = Sink.fold[Int, B](0)((count, _) => count + 1)
    Flow.fromGraph(
      GraphDSL.createGraph(counterSink) { implicit builder => counterSinkShape =>
        import GraphDSL.Implicits._

        // step 2
        val broadcast = builder.add(Broadcast[B](2))
        val originalFlowShape = builder.add(flow)

        // Step 3
        originalFlowShape ~> broadcast
        broadcast ~> counterSinkShape

        // Step 4
        FlowShape(originalFlowShape.in, broadcast.out(1))
      }
    )
  }

  val simpleSource = Source(1 to 42)
  val simpleFlow = Flow[Int].map(x => x)
  val simpleSink = Sink.foreach(println)

  private val resultOfSink: Future[Int] = simpleSource.viaMat(enhanceFlow(simpleFlow))(Keep.right).toMat(simpleSink)(Keep.left).run()
  resultOfSink.onComplete {
    case Success(value) => println(s"Flow went through $value times")
    case Failure(exception) => println(s"Exception: $exception")
  }



}
