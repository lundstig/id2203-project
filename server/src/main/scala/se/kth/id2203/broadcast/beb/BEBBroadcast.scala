package se.kth.id2203.broadcast.beb

import se.sics.kompics.KompicsEvent
import se.kth.id2203.networking.NetAddress

case class BEBBroadcast(payload: KompicsEvent, nodes: List[NetAddress]) extends KompicsEvent with Serializable