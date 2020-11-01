package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data

  case object Uninitialized extends Data

  case class SelectingDeliveryStarted(timer: Cancellable) extends Data

  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command

  case object StartCheckout extends Command

  case class SelectDeliveryMethod(method: String) extends Command

  case object CancelCheckout extends Command

  case object ExpireCheckout extends Command

  case class SelectPayment(payment: String) extends Command

  case object ExpirePayment extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Event

  case object CheckOutClosed extends Event

  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout())
}

class Checkout extends Actor {

  import Checkout._

  private val scheduler = context.system.scheduler
  private val log = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration = 1 seconds

  private def scheduleCheckoutTimer: Cancellable = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)

  private def paymentCheckoutTimer: Cancellable = scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)


  def receive: Receive = LoggingReceive {
    case StartCheckout => {
      log.info("Checkout started")
      context become selectingDelivery(scheduleCheckoutTimer)
    }
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout => {
      timer.cancel()
      log.info("Cancelling checkout")
      context become cancelled
    }
    case ExpireCheckout => {
      timer.cancel()
      log.info("Checkout expired")
      context become cancelled
    }
    case SelectDeliveryMethod(method: String) => {
      log.info(s"Delivery method $method selected")
      context become selectingPaymentMethod(scheduleCheckoutTimer)
    }
  }


  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout =>
      log.info("Cancelling checkout")
      context become cancelled

    case ExpireCheckout => {
      timer.cancel()
      log.info("Checkout expired")
      context become cancelled
    }
    case SelectPayment(payment: String) => {
      timer.cancel()
      log.info(s"Payment $payment selected")
      context become processingPayment(paymentCheckoutTimer)
    }
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout => {
      timer.cancel()
      log.info("Cancelling checkout")
      context become cancelled
    }
    case ExpirePayment => {
      timer.cancel()
      log.info("Checkout expired")
      context become cancelled
    }
    case ConfirmPaymentReceived => {
      log.info("Payment received")
      context become closed
    }
  }


  def cancelled: Receive = LoggingReceive {
    case StartCheckout => {
      log.info("Checkout started from cancelled state")
      context become selectingDelivery(scheduleCheckoutTimer)
    }
  }

  def closed: Receive = LoggingReceive {
    case StartCheckout => {
      log.info("Checkout started from closed state")
      context become selectingDelivery(scheduleCheckoutTimer)
    }
  }


}
