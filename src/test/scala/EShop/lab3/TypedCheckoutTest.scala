package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {


  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val probe = testKit.createTestProbe[Any]()
    val probeRef = probe.ref
    val cart = Cart(Seq("item"))
    val tom = testKit.spawn(new TypedOrderManager().start, "tom")
    val cartActor = testKit.spawn(
      {
        val ca = new TypedCartActor {
          override def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] =
            Behaviors.receive { (context, msg) => {
              if (msg.eq(TypedCartActor.ConfirmCheckoutClosed)) {
                probeRef ! "confirmed"
              }
              super.inCheckout(cart)
            }
            }

        }
        ca.inCheckout(cart)
      }
    )

    val checkoutActor = testKit.spawn(
      {
        val checkout = new TypedCheckout(cartActor)

        checkout.start
      })

    checkoutActor ! TypedCheckout.StartCheckout
    checkoutActor ! TypedCheckout.SelectDeliveryMethod("method")
    checkoutActor ! TypedCheckout.SelectPayment("payment", tom)
    checkoutActor ! TypedCheckout.ConfirmPaymentReceived
    probe.expectMessage("confirmed")

  }

}
