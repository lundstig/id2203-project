package se.kth.id2203.broadcast.beb

import se.sics.kompics.KompicsEvent
import se.kth.id2203.networking.NetAddress

case class BEBDeliver(source: NetAddress, event: KompicsEvent) extends KompicsEvent with Serializable