package se.kth.id2203.broadcast.rb

import se.sics.kompics.PortType

class RBPort extends PortType {
    indication(classOf[RBDeliver]);
    request(classOf[RBBroadcast]);
}
