package se.kth.id2203.broadcast.beb

import se.kth.id2203.networking.{NetMessage,NetAddress}
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort}

class BEB extends ComponentDefinition {

  private val beb: NegativePort[BEBPort] = provides[BEBPort];
  private val net: PositivePort[Network] = requires[Network];
  private val self = cfg.getValue[NetAddress]("id2203.project.address");

    beb uponEvent {
      case broadcast: BEBBroadcast => {
        for(node <- broadcast.nodes) {
          trigger(NetMessage(self, node, BEBBroadcast(broadcast.payload, broadcast.nodes)) -> net);
        }
      }
    }

    net uponEvent {
      case NetMessage(src, BEBBroadcast(payload, nodes)) => {
        trigger(BEBDeliver(src.getSource(), payload) -> beb);
      }
    }
}
