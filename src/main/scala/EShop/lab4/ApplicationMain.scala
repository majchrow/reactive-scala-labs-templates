package EShop.lab4

import EShop.lab2.TypedCartActor
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.persistence.typed.PersistenceId

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object TypedMain {

  trait Command

  case object Init extends Command

  case object Test extends Command

  case object Done extends Command

  def apply(): Behavior[TypedMain.Command] = Behaviors.setup { context =>
    val pco = context.spawn(new TypedPersistentCartActor().apply(PersistenceId.ofUniqueId("pco")), "pco").ref
    test_pco(pco)
  }

  def test_pco(pco: ActorRef[TypedCartActor.Command]): Behavior[TypedMain.Command] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case Done => Behaviors.stopped
      case Test =>

        pco ! TypedCartActor.AddItem("item")
        pco ! TypedCartActor.AddItem("hamlet")
        pco ! TypedCartActor.RemoveItem("item")
        pco ! TypedCartActor.AddItem("item")
        print(pco)
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
