package EShop.lab2

import akka.actor.{ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ApplicationMain extends App {


  private val system = ActorSystem("Reactive1")
  private val cartActor = system.actorOf(Props[CartActor], "cartActor")

  cartActor ! CartActor.AddItem("Hamlet")
  cartActor ! CartActor.AddItem("Hamlet2")
  cartActor ! CartActor.RemoveItem("Hamlet")
  cartActor ! CartActor.AddItem("Hamlet3")
  cartActor ! CartActor.StartCheckout
  cartActor ! CartActor.ConfirmCheckoutClosed

  private val checkoutActor = system.actorOf(Checkout.props(cartActor), "checkoutActor")

  checkoutActor ! Checkout.StartCheckout
  checkoutActor ! Checkout.CancelCheckout
  checkoutActor ! Checkout.StartCheckout
  checkoutActor ! Checkout.SelectDeliveryMethod("Inpost")
  checkoutActor ! Checkout.SelectPayment("Payplal")
  checkoutActor ! Checkout.ConfirmPaymentReceived

  Await.result(system.whenTerminated, Duration.Inf)
}
