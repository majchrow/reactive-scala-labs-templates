package EShop.lab3

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, _}

object TypedMain {

  trait Command

  case object Init extends Command

  case object Test extends Command

  case object Done extends Command

  def apply(): Behavior[TypedMain.Command] = Behaviors.setup { context =>
    val tom = context.spawn(new TypedOrderManager().start, "tom")
    test(tom)
  }

  def test(tom: ActorRef[TypedOrderManager.Command]): Behavior[TypedMain.Command] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case Done => Behaviors.stopped
      case Test =>
        import akka.actor.typed.scaladsl.AskPattern.Askable
        implicit val timeout: Timeout = 10 seconds
        implicit val scheduler: Scheduler = context.system.scheduler
        tom.ask[Any](TypedOrderManager.AddItem("item", _))
        tom.ask[Any](TypedOrderManager.RemoveItem("item", _))
        tom.ask[Any](TypedOrderManager.AddItem("hamlet", _))
        tom.ask[Any](TypedOrderManager.AddItem("item", _))
        tom.ask[Any](TypedOrderManager.Buy)
        tom.ask[Any](TypedOrderManager.SelectDeliveryAndPaymentMethod("paypal", "inpost", _))

        Behaviors.same
      case _ => Behaviors.same
    }
  }
}


object ApplicationMain extends App {
  val system = ActorSystem(TypedMain(), "testSystem")

  system ! TypedMain.Test
  Await.result(system.whenTerminated, Duration.Inf)
}
