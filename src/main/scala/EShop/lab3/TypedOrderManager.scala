package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object TypedOrderManager {

  sealed trait Command

  case class AddItem(id: String, sender: ActorRef[Ack]) extends Command

  case class RemoveItem(id: String, sender: ActorRef[Ack]) extends Command

  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command

  case class Buy(sender: ActorRef[Ack]) extends Command

  case class Pay(sender: ActorRef[Ack]) extends Command

  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Command

  case class ConfirmPaymentStarted(paymentRef: ActorRef[TypedPayment.Command]) extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Ack


  case object Done extends Ack //trivial ACK
}

class TypedOrderManager {

  import TypedOrderManager._


  def start: Behavior[TypedOrderManager.Command] = uninitialized

  def uninitialized: Behavior[TypedOrderManager.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case AddItem(id: String, sender: ActorRef[Ack]) =>
          val spawned = context.spawn((new TypedCartActor).empty, "spawnedCartActor")
          print("Add Item - OrderManager\n")
          spawned ! TypedCartActor.AddItem(id)
          sender ! Done
          open(spawned)
        case _ => Behaviors.same
      }
  }

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[TypedOrderManager.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case AddItem(id: String, sender: ActorRef[Ack]) =>
          print("Add Item - OrderManager\n")
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          Behaviors.same
        case RemoveItem(id: String, sender: ActorRef[Ack]) =>
          print("Remove Item - OrderManager\n")
          cartActor ! TypedCartActor.RemoveItem(id)
          sender ! Done
          Behaviors.same
        case Buy(sender: ActorRef[Ack]) =>
          print("Buy - OrderManager\n")
          cartActor ! TypedCartActor.StartCheckout(context.self)
          sender ! Done
          inCheckout(cartActor, sender)
        case _ => Behaviors.same
      }
  }

  def inCheckout(
                  cartActorRef: ActorRef[TypedCartActor.Command],
                  senderRef: ActorRef[Ack]
                ): Behavior[TypedOrderManager.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) =>
          print("Confirm Checkout Started - OrderManager\n")
          senderRef ! Done
          inCheckout(checkoutRef)
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          context.self ! SelectDeliveryAndPaymentMethod(delivery, payment, sender)
          senderRef ! Done
          Behaviors.same
        case _ => Behaviors.same
      }
  }

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[TypedOrderManager.Command] = {
    Behaviors.receive((context, msg) =>
      msg match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          print("Select DeliveryAndPayment Method - OrderManager\n")
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
          sender ! Done
          inPayment(sender)
        case _ =>
          Behaviors.same
      }
    )
  }

  def inPayment(senderRef: ActorRef[Ack]): Behavior[TypedOrderManager.Command] = {
    Behaviors.receive((context, msg) =>
      msg match {
        case ConfirmPaymentStarted(paymentRef) =>
          print("Confirm payment started - OrderManager\n")
          inPayment(paymentRef, senderRef)
        case Pay(sender: ActorRef[Ack]) =>
          context.self ! Pay(sender)
          sender ! Done
          Behaviors.same
        case _ =>
          Behaviors.same
      }
    )
  }


  def inPayment(
                 paymentActorRef: ActorRef[TypedPayment.Command],
                 senderRef: ActorRef[Ack]
               ): Behavior[TypedOrderManager.Command] = {
    Behaviors.receive((context, msg) =>
      msg match {
        case Pay(sender: ActorRef[Ack]) =>
          print("Pay - OrderManager\n")
          paymentActorRef ! TypedPayment.DoPayment
          sender ! Done
          Behaviors.same
        case ConfirmPaymentReceived =>
          print("ConfirmPaymentReceived - OrderManager\n")
          senderRef ! Done
          finished
        case _ =>
          Behaviors.same
      }
    )
  }

  def finished: Behavior[TypedOrderManager.Command] = Behaviors.same
}
