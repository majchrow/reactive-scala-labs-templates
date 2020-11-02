package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object TypedPayment {

  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object PaymentReceived extends Event
}

class TypedPayment(
  method: String,
  orderManager: ActorRef[Any],
  checkout: ActorRef[TypedCheckout.Command]
) {

  import TypedPayment._

  def start: Behavior[TypedPayment.Command] = {
    Behaviors.receive(
      (context, msg) =>
        msg match {
          case DoPayment =>
            print("DoPayment - Payment\n")
            orderManager ! PaymentReceived
            checkout ! TypedCheckout.ConfirmPaymentReceived
            Behaviors.same
          case _ => Behaviors.same
        }
    )
  }

}
