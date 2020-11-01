package EShop.lab2

import EShop.lab3.TypedOrderManager
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

  case class StartCheckout(orderManagerRef: ActorRef[TypedOrderManager.Command]) extends Command

  case object ConfirmCheckoutCancelled extends Command

  case object ConfirmCheckoutClosed extends Command

  case class GetItems(sender: ActorRef[Cart]) extends Command

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
        case AddItem(item) =>
          print("Add Item - CartActor\n")
          nonEmpty(Cart(Seq(item)), scheduleTimer(context))
        case GetItems(sender: ActorRef[Cart]) =>
          sender ! Cart(Seq())
          Behaviors.same
        case _ =>
          Behaviors.same
      }
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case AddItem(item) =>
          print("Add Item - CartActor\n")
          nonEmpty(Cart(Seq(item)), scheduleTimer(context))
        case RemoveItem(item) =>
          print("Remove Item - CartActor\n")
          if (cart.contains(item)) {
            if (cart.size > 1) {
              nonEmpty(cart.removeItem(item), timer)
            } else {
              empty
            }
          } else {
            Behaviors.same
          }
        case ExpireCart =>
          empty
        case StartCheckout(orderManagerRef: ActorRef[TypedOrderManager]) =>
          print("Start Checkout - Cart Actor\n")
          if (cart.size > 0) {
            val spawned = context.spawn((new TypedCheckout(context.self)).start, "spawnedCheckout")
            spawned ! TypedCheckout.StartCheckout
            orderManagerRef ! TypedOrderManager.ConfirmCheckoutStarted(spawned)
            inCheckout(cart)
          }
          else
            Behaviors.same
        case GetItems(sender: ActorRef[Cart]) =>
          sender ! cart
          Behaviors.same
        case _ =>
          Behaviors.same
      }
  }

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case ConfirmCheckoutCancelled => if (cart.size > 0) nonEmpty(cart, scheduleTimer(context)) else empty
        case ConfirmCheckoutClosed =>
          print("ConfirmPaymentReceived - CartActor\n")
          empty
        case _ => Behaviors.same
      }
  }


}
