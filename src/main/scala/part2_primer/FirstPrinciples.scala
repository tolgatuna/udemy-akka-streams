package part2_primer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {
  implicit val system: ActorSystem = ActorSystem("FirstPrinciples")

  // sources
  private val source = Source(1 to 10)

  // sinks
  private val sink = Sink.foreach[Int](println)

  private val graph = source.to(sink)
  //  graph.run()

  // flows transform element
  private val flow = Flow[Int].map(x => x + 1)
  private val sourceWithFlow: Source[Int, NotUsed] = source.via(flow)
  //  sourceWithFlow.to(sink).run()

  // nulls are NOT ALLOWED - use options instead!
  //  val illegalSource = Source.single[String](null) // -> Null pointer exception - NULL IS NOT ALLOWED even without run!

  // various kinds of sources
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(Stream.from(1)) // do not confuse an Akka streams with a "collection" stream

  import scala.concurrent.ExecutionContext.Implicits.global

  val futureSource = Source.future(Future(42))

  // sinks
  val theMostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves head and then closes the stream
  val foldSing = Sink.fold[Int, Int](0)(_ + _)

  // flows - usually mapped to collection operators
  val mapFlow = Flow[Int].map(x => x * 2)
  val takeFlow = Flow[Int].take(5)
  // drop, filter
  // !NOT HAVE flatMap

  // source -> flow -> flow -> .... -> sink
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)

  // syntactic sugar
  private val mapSource = Source(1 to 10).map(_ * 2) // == Source(1 to 10).via(Flow[Int].map(_ * 2))

  // run streams directly
  mapSource.runForeach(println) // == mapSource.to(Sink.foreach[Int](println)).run()

  // OPERATORS == components

  /**
   * Exercise: Create a stream that takes the names of persons,
   * then you will keep the first 2 names with length > 5 characters
   * and then print them into the console
   */
  case class Person(name: String)

  private val sourcePerson: Source[Person, NotUsed] = Source(List(
    Person("Ali"),
    Person("Tolga"),
    Person("Suleyman"),
    Person("Nejdet"),
    Person("Abdullah"),
    Person("Necati"),
    Person("Demet")
  ))
  sourcePerson.filter(p => p.name.length > 5).take(2).runForeach(println)


}
