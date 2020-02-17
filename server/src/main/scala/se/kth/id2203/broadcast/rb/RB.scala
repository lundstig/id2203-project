package se.kth.id2203.broadcast.rb

import se.sics.kompics.KompicsEvent
import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort}
import se.kth.id2203.networking.NetAddress
import se.kth.id2203.broadcast.beb.{BEBPort, BEBBroadcast, BEBDeliver}

class RB extends ComponentDefinition {
    private val beb: PositivePort[BEBPort] = requires[BEBPort];
    private val rb: NegativePort[RBPort] = provides[RBPort];
    private val self = cfg.getValue[NetAddress]("id2203.project.address");
    private val delivered = collection.mutable.Set[KompicsEvent]();

    rb uponEvent {
        case broadcast: RBBroadcast => {
            trigger(BEBBroadcast(RBBroadcast(broadcast.event, broadcast.nodes), broadcast.nodes) -> beb);
        }
    }

    beb uponEvent {
        case BEBDeliver(source, RBBroadcast(event, nodes)) => {
            if(!delivered.contains(event)) {
                delivered.add(event);
                trigger(RBDeliver(source, event) -> rb);
                trigger(BEBBroadcast(event, nodes) -> beb);
            }
        }
    }

}