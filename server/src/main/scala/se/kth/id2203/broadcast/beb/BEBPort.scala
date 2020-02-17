package se.kth.id2203.broadcast.beb

import se.sics.kompics.PortType
import se.kth.id2203.broadcast.beb.{BEBDeliver,BEBBroadcast}

class BEBPort extends PortType {
    indication(classOf[BEBDeliver]);
    request(classOf[BEBBroadcast]);  
}