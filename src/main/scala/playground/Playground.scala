package playground

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

object Playground extends App {
  implicit val aktorSystem = ActorSystem("Playground")
  implicit val materializer = ActorMaterializer()

  Source.single("Hello, Streams!").to(Sink.foreach(println)).run()

}
