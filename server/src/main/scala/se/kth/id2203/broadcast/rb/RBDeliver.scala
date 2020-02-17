package se.kth.id2203.broadcast.rb

import se.sics.kompics.KompicsEvent
import se.kth.id2203.networking.NetAddress

case class RBDeliver(source: NetAddress, event: KompicsEvent) extends KompicsEvent with Serializable
