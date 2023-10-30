package part4_techniques

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.util.{Success, Failure}
import scala.concurrent.duration._
import scala.language.postfixOps

class TestingStreamSpec extends TestKit(ActorSystem("TestingStreamSpec"))
  with AnyWordSpecLike
  with BeforeAndAfterAll {
  implicit val actorSystem: ActorSystem = system

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A simple stream" should {
    "satisfy basic assertions" in {
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)

      val sumFuture = simpleSource.runWith(simpleSink)
      val sum = Await.result(sumFuture, 2 seconds)

      assert(sum == 55)
    }

    "integrate with test actors via materialized values" in {
      import akka.pattern.pipe
      import system.dispatcher

      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)

      val probe = TestProbe()

      simpleSource.runWith(simpleSink).pipeTo(probe.ref)
      probe.expectMsg(55)
    }

    "integrate with a test-actor-based-sink" in {
      val simpleSource = Source(1 to 5)
      val flow = Flow[Int].scan[Int](0)(_ + _) // 0, 1, 3, 6, 10 ,15

      val streamUnderTest = simpleSource.via(flow)

      val probe = TestProbe()
      val probeSink = Sink.actorRef(probe.ref, "completionMessage")

      streamUnderTest.to(probeSink).run()
      probe.expectMsgAllOf(0, 1, 3, 6, 10 ,15)
    }

    "integrate with Streams Testkit Sink" in {
      val sourceUnderTest = Source(1 to 5).map(_ * 2)

      val testSink = TestSink[Int]
      val materializedTestValue = sourceUnderTest.runWith(testSink)

      materializedTestValue
        .request(5) // request 5 elements
        .expectNext(2, 4, 6, 8, 10)
        .expectComplete()
    }

    "integrate with Streams Testkit Source" in {
      import system.dispatcher
      val sinkUnderTest = Sink.foreach[Int] {
        case 13 => throw new RuntimeException("bad luck!")
        case _ =>
      }

      val testSource = TestSource[Int]
      val materialized = testSource.toMat(sinkUnderTest)(Keep.both).run()
      val (testPublisher, resultFuture) = materialized
      testPublisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(13)
        .sendComplete()

      resultFuture.onComplete {
        case Success(_) => fail("the sink under the test should have thrown an exception for 13")
        case Failure(_) => // ok
      }
    }

    "test flows with a TestKit source and a TestKit sink" in {
      import system.dispatcher
      val flowUnderTest = Flow[Int].map(_ * 2)

      val testSource = TestSource[Int]
      val testSink = TestSink[Int]

      val materialized = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()
      val (publisher, subscriber) = materialized
      publisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(42)
        .sendNext(99)
        .sendComplete()

      subscriber
        .request(4)
        .expectNext(2, 10, 84, 198)
        .expectComplete()

    }
  }
}
