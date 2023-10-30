package part2_primer

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializingStreams extends App {
  implicit val system: ActorSystem = ActorSystem("FirstPrinciples")
  import system.dispatcher

  private val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
  val simpleMaterializedValue = simpleGraph.run()

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int](_ + _)
  private val sumFuture: Future[Int] = source.runWith(sink)
  sumFuture.onComplete {
    case Success(value) => println(s"The sum of all elements is $value")
    case Failure(exception) => println(s"The sum of all elements could not be computed: $exception")
  }

  // choosing materialized valued
  private val simpleSource = Source(1 to 10)
  private val simpleFlow = Flow[Int].map(_ + 1)
  private val simpleSink = Sink.foreach[Int](println)

  private val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right) //(sourceMat, flowMat) => flowMat
  graph.run().onComplete {
    case Success(_) => println(s"Stream processing finished")
    case Failure(exception) => println(s"Stream processing failed with $exception")
  }

  private val eventualInt = Source(1 to 20).runWith(Sink.reduce[Int](_ + _)) // === source.to(Sink.Reduce)(Keep.right)
  private val eventualIntV2 = Source(1 to 20).runReduce(_ + _) // much shorter
  eventualInt.onComplete {
    case Success(value) => println(s"The sum of all elements v1 is $value")
    case Failure(exception) => println(s"The sum of all elements v1 could not be computed: $exception")
  }

  eventualIntV2.onComplete {
    case Success(value) => println(s"The sum of all elements v2 is $value")
    case Failure(exception) => println(s"The sum of all elements v2 could not be computed: $exception")
  }

  //backwards
  Sink.foreach[Int](println).runWith(Source.single(42)) // === source(..).to(sink...).run()

  // both ways
  Flow[Int].map(x => x * 2).runWith(simpleSource, simpleSink)

  /**
   * - return the last element out of a source (use Sink.last)
   * - compute the total word count of a stream of sentences
   *    - use combinations map, fold, reduce
   */
  private val finalValueV1: Future[Int] = Source(1 to 100).runWith(Sink.last)
  private val finalValueV2: Future[Int] = Source(1 to 100).runReduce((a, b) => b)
  private val finalValueV3: Future[Int] = Source(1 to 100).toMat(Sink.last)(Keep.right).run()

  private val totalWordCount: Future[Int] = Source(List("A Sentence with some words", "This sentence is better", "I love akka"))
    .via(Flow[String].map(a => a.split(" ").length))
    .reduce(_ + _)
    .runWith(Sink.last)

  totalWordCount.onComplete {
    case Success(value) => println(s"Word count is $value")
    case Failure(exception) => println(s"Word count could not be computed: $exception")
  }
}
