package se.kth.id2203.broadcast.rb

import se.sics.kompics.KompicsEvent
import se.kth.id2203.networking.NetAddress

case class RBBroadcast(event: KompicsEvent, nodes: List[NetAddress]) extends KompicsEvent with Serializable