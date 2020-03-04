package se.kth.id2203.broadcast

import se.kth.id2203.networking.{NetMessage,NetAddress}
import se.sics.kompics.KompicsEvent
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, Port}

case class BEB_Deliver(source: NetAddress, event: KompicsEvent) extends KompicsEvent;
case class BEB_Broadcast(payload: KompicsEvent, nodes: List[NetAddress]) extends KompicsEvent;

class BEBroadcast extends Port {
    indication[BEB_Deliver];
    request[BEB_Broadcast];  
}

class Broadcast extends ComponentDefinition {

  val beb = provides[BEBroadcast];
  val net = requires[Network];
  val self = cfg.getValue[NetAddress]("id2203.project.address");

    beb uponEvent {
      case broadcast: BEB_Broadcast => {
        for(node <- broadcast.nodes) {
          trigger(NetMessage(self, node, BEB_Broadcast(broadcast.payload, broadcast.nodes)) -> net);
        }
      }
    }

    net uponEvent {
      case NetMessage(src, BEB_Broadcast(payload, nodes)) => {
        trigger(BEB_Deliver(src.getSource(), payload) -> beb);
      }
    }
}
