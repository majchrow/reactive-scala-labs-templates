package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.Cancellable
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{FiniteDuration, _}

class TypedCartTest
  extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActorTest._

  //use GetItems command which was added to make test easier
  // Synchronous - https://doc.akka.io/docs/akka/current/typed/testing-sync.html
  it should "add item properly" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()
    testKit.run(TypedCartActor.GetItems(inbox.ref))
    inbox.expectMessage(Cart(Seq()))
    testKit.run(TypedCartActor.AddItem("Hamlet"))
    testKit.run(TypedCartActor.GetItems(inbox.ref))
    inbox.expectMessage(Cart(Seq("Hamlet")))
  }

  it should "be empty after adding and removing the same item" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()
    testKit.run(TypedCartActor.GetItems(inbox.ref))
    testKit.run(TypedCartActor.AddItem("Hamlet"))
    testKit.run(TypedCartActor.RemoveItem("Hamlet"))
    inbox.expectMessage(Cart(Seq()))

  }

  it should "start checkout" in {
    val probe = testKit.createTestProbe[Any]()
    val cart = cartActorWithCartSizeResponseOnStateChange(testKit, probe.ref)
    val tom = testKit.spawn(new TypedOrderManager().start, "tom")

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)

    cart ! TypedCartActor.AddItem("Hamlet")

    cart ! TypedCartActor.StartCheckout(tom.ref)

    probe.expectMessage(inCheckoutMsg)


  }
}

object TypedCartActorTest {
  val emptyMsg = "empty"
  val nonEmptyMsg = "nonEmpty"
  val inCheckoutMsg = "inCheckout"

  def cartActorWithCartSizeResponseOnStateChange(
                                                  testKit: ActorTestKit,
                                                  probe: ActorRef[Any]
                                                ): ActorRef[TypedCartActor.Command] =
    testKit.spawn {
      val cartActor = new TypedCartActor {
        override val cartTimerDuration: FiniteDuration = 1.seconds

        override def empty: Behavior[TypedCartActor.Command] =
          Behaviors.setup(_ => {
            probe ! emptyMsg
            probe ! 0
            super.empty
          })

        override def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] =
          Behaviors.setup(_ => {
            probe ! inCheckoutMsg
            super.inCheckout(cart)
          })

      }
      cartActor.start
    }

}