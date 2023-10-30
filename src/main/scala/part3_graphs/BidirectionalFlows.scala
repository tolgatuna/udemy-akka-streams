package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{BidiShape, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

object BidirectionalFlows extends App {
  implicit val system: ActorSystem = ActorSystem("BidirectionalFlows")

  /*
    Example: Cryptography
   */
  def encrypt(n: Int)(string: String): String = string.map(c => (c + n).toChar)
  def decrypt(n: Int)(string: String): String = string.map(c => (c - n).toChar)

  // bidirectional flow
  private val bidirectionalCrypto = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val encryptFlowShape = builder.add(Flow[String].map(encrypt(3)))
    val decryptFlowShape = builder.add(Flow[String].map(decrypt(3)))

    //BidiShape(encryptFlowShape.in, encryptFlowShape.out, decryptFlowShape.in, decryptFlowShape.out)
    BidiShape.fromFlows(encryptFlowShape, decryptFlowShape)
  }

  private val unencryptedStrings = List("akka", "is", "awesome", "testing", "bidirectional", "flows")
  private val unencryptedStringSource = Source(unencryptedStrings)
  private val encryptedStringSource = Source(unencryptedStrings.map(encrypt(3)))

  private val cryptoBidiGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val unencryptedStringSourceShape = builder.add(unencryptedStringSource)
      val encryptedStringSourceShape = builder.add(encryptedStringSource)
      val bidi = builder.add(bidirectionalCrypto)
      val encryptedSinkShape = builder.add(Sink.foreach[String](string => println(s"Encrypted: $string")))
      val decryptedSinkShape = builder.add(Sink.foreach[String](string => println(s"Decrypted: $string")))

      unencryptedStringSourceShape ~> bidi.in1  ; bidi.out1 ~> encryptedSinkShape
      decryptedSinkShape           <~ bidi.out2 ; bidi.in2  <~ encryptedStringSourceShape

      ClosedShape
    }
  )
  cryptoBidiGraph.run()

}
