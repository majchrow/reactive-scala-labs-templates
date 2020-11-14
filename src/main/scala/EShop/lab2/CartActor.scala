package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command

  case class AddItem(item: Any) extends Command

  case class RemoveItem(item: Any) extends Command

  case object ExpireCart extends Command

  case object StartCheckout extends Command

  case object ConfirmCheckoutCancelled extends Command

  case object ConfirmCheckoutClosed extends Command

  case object GetItems extends Command // command made to make testing easier

  sealed trait Event

  case class CheckoutStarted(checkoutRef: ActorRef, cart: Cart) extends Event

  case class ItemAdded(itemId: Any, cart: Cart) extends Event

  case class ItemRemoved(itemId: Any, cart: Cart) extends Event

  case object CartEmptied extends Event

  case object CartExpired extends Event

  case object CheckoutClosed extends Event

  case class CheckoutCancelled(cart: Cart) extends Event


  def props() = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5 seconds
  private val scheduler = context.system.scheduler


  // https://doc.akka.io/docs/akka/current/scheduler.html
  private def scheduleTimer: Cancellable = scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      log.info(s"Item $item added to empty cart")
      context become nonEmpty(Cart(Seq(item)), scheduleTimer)
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      log.info(s"Item $item added to cart nonempty $cart")
      context become nonEmpty(cart.addItem(item), scheduleTimer)
    case RemoveItem(item) =>
      if (cart.contains(item)) {
        log.info(s"Removing item $item from cart $cart")
        if (cart.size > 1) {
          context become nonEmpty(cart.removeItem(item), scheduleTimer)
        } else {
          timer.cancel()
          context become empty
        }
      } else {
        log.info(s"Item $item not in the cart $cart")
      }
    case ExpireCart =>
      log.info(s"Cart $cart expired")
      context become empty
    case StartCheckout =>
      if (cart.size > 0) {
        log.info(s"Checkout with cart $cart started")
        timer.cancel()
        context become inCheckout(cart)
      } else {
        log.info(s"No item in cart to proceed checkout")
      }

  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      log.info(s"Checkout with cart $cart cancelled")
      if (cart.size > 0) context become nonEmpty(cart, scheduleTimer)
      else context become empty

    case ConfirmCheckoutClosed =>
      log.info(s"Checkout with cart $cart closed")
      context become empty

  }

}
