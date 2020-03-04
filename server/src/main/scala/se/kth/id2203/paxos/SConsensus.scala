package se.kth.id2203.paxos

import se.kth.id2203.kvstore._
import se.kth.id2203.networking.{NetAddress}
import se.sics.kompics.sl.Port
import se.sics.kompics.KompicsEvent


case class SC_Decide(value: Operation) extends KompicsEvent
case class SC_Propose(value: Operation) extends KompicsEvent
case class SetTopology(topology: Set[NetAddress]) extends KompicsEvent

class SConsensus extends Port {
    indication[SC_Decide];
    request[SC_Propose];
    request[SetTopology];
}
