package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCartActor {

  sealed trait Command

  case class AddItem(item: Any) extends Command

  case class RemoveItem(item: Any) extends Command

  case object ExpireCart extends Command

  case object StartCheckout extends Command

  case object ConfirmCheckoutCancelled extends Command

  case object ConfirmCheckoutClosed extends Command

  sealed trait Event

  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event

}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case AddItem(item) => nonEmpty(Cart(Seq(item)), scheduleTimer(context))
        case _ => Behaviors.same
      }
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case AddItem(item) => nonEmpty(Cart(Seq(item)), scheduleTimer(context))
        case RemoveItem(item) =>
          if (cart.contains(item)) {
            if (cart.size > 1) {
              nonEmpty(cart.removeItem(item), timer)
            } else {
              empty
            }
          } else {
            Behaviors.same
          }
        case ExpireCart => empty
        case StartCheckout => if (cart.size > 0) inCheckout(cart) else Behaviors.same
        case _ => Behaviors.same
      }
  }

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case ConfirmCheckoutCancelled => if (cart.size > 0) nonEmpty(cart, scheduleTimer(context)) else empty
        case ConfirmCheckoutClosed => empty
        case _ => Behaviors.same
      }
  }


}
