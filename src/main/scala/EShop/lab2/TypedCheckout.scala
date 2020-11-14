package EShop.lab2

import EShop.lab3.{TypedOrderManager, TypedPayment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cats.implicits.catsSyntaxOptionId

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCheckout {

  sealed trait Data

  case object Uninitialized extends Data

  case class SelectingDeliveryStarted(timer: Cancellable) extends Data

  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command


  case object StartCheckout extends Command

  case class SelectDeliveryMethod(method: String) extends Command

  case object CancelCheckout extends Command

  case object ExpireCheckout extends Command

  case class SelectPayment(payment: String, orderManagerRef: ActorRef[TypedOrderManager.Command]) extends Command

  case object ExpirePayment extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Event

  case object CheckOutClosed extends Event

  case class PaymentStarted(payment: ActorRef[TypedPayment.Command]) extends Event

  case object CheckoutStarted extends Event

  case object CheckoutCancelled extends Event

  case class DeliveryMethodSelected(method: String) extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])

  case object WaitingForStart extends State(None)

  case class SelectingDelivery(timer: Cancellable) extends State(timer.some)

  case class SelectingPaymentMethod(timer: Cancellable) extends State(timer.some)

  case object Closed extends State(None)

  case object Cancelled extends State(None)

  case class ProcessingPayment(timer: Cancellable) extends State(timer.some)

}

class TypedCheckout(
                     cartActor: ActorRef[TypedCartActor.Command]
                   ) {

  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration = 1 seconds

  private def scheduleTimerCheckout(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)

  private def scheduleTimerPayment(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)


  def start: Behavior[TypedCheckout.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case StartCheckout => selectingDelivery(scheduleTimerCheckout(context))
        case _ => Behaviors.same
      }
  }

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case CancelCheckout | ExpireCheckout => cancelled
        case SelectDeliveryMethod(method: String) =>
          print("Select DeliveryMethod - Checkout\n")
          selectingPaymentMethod(timer)
        case SelectPayment(payment: String, orderManagerRef: TypedOrderManager.Command) =>
          context.self ! SelectPayment(payment, orderManagerRef)
          Behaviors.same
        case _ => Behaviors.same
      }
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case CancelCheckout | ExpireCheckout => cancelled
        case SelectPayment(payment: String, orderManagerRef: ActorRef[TypedOrderManager]) =>
          print("Select Payment - Checkout\n")
          val spawned = context.spawn((new TypedPayment(payment, orderManagerRef, context.self)).start, "spawnedPayment")
          orderManagerRef ! TypedOrderManager.ConfirmPaymentStarted(spawned)
          timer.cancel()
          processingPayment(scheduleTimerPayment(context))
        case _ =>
          Behaviors.same
      }
  }

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case CancelCheckout | ExpirePayment => cancelled
        case ConfirmPaymentReceived =>
          print("ConfirmPaymentReceived - Checkout\n")
          cartActor ! TypedCartActor.ConfirmCheckoutClosed
          timer.cancel()
          closed
        case _ => Behaviors.same
      }
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped
}