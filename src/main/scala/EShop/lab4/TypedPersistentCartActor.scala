package EShop.lab4

import EShop.lab2.TypedCheckout
import EShop.lab3.TypedOrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class TypedPersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      Empty,
      commandHandler(context),
      eventHandler(context)
    )
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))
          case ExpireCart =>
            Effect.persist(CartExpired)
          case _ => Effect.none
        }
      case NonEmpty(cart, _) =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))
          case RemoveItem(item) =>
            if (state.cart.contains(item)) {
              val cart = state.cart.removeItem(item)
              if (cart.size == 0)
                Effect.persist(CartEmptied)
              else {
                Effect.persist(ItemRemoved(item))
              }
            } else {
              Effect.none
            }
          case ExpireCart =>
            Effect.persist(CartExpired)
          case StartCheckout(orderManagerRef) =>
            val spawned = context.spawn((new TypedCheckout(context.self)).start, "spawnedCheckout")
            spawned ! TypedCheckout.StartCheckout
            orderManagerRef ! TypedOrderManager.ConfirmCheckoutStarted(spawned)
            Effect.persist(CheckoutStarted(spawned))
          case _ => Effect.none
        }

      case InCheckout(_) =>
        command match {
          case ExpireCart =>
            Effect.persist(CartExpired)
          case ConfirmCheckoutClosed =>
            Effect.persist(CheckoutClosed)
          case ConfirmCheckoutCancelled =>
            Effect.persist(CheckoutCancelled)
          case _ => Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    val timer = scheduleTimer(context)
    event match {
      case CheckoutStarted(_) =>
        InCheckout(state.cart)
      case ItemAdded(item) =>
        NonEmpty(state.cart.addItem(item), timer)
      case ItemRemoved(item) =>
        NonEmpty(state.cart.removeItem(item), timer)
      case CartEmptied | CartExpired =>
        timer.cancel()
        Empty
      case CheckoutClosed =>
        timer.cancel()
        Empty
      case CheckoutCancelled =>
        timer.cancel()
        NonEmpty(state.cart, timer)

    }
  }

}
