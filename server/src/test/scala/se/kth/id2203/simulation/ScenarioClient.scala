/*
 * The MIT License
 *
 * Copyright 2017 Lars Kroll <lkroll@kth.se>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package se.kth.id2203.simulation;

import java.util.UUID;
import se.kth.id2203.kvstore._;
import se.kth.id2203.networking._;
import se.kth.id2203.overlay.RouteMsg;
import se.sics.kompics.sl._
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.sl.simulator.SimulationResult;
import collection.mutable;

class ScenarioClient extends ComponentDefinition {

  //******* Ports ******
  val net = requires[Network];
  val timer = requires[Timer];
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val server = cfg.getValue[NetAddress]("id2203.project.bootstrap-address");
  private val pending = mutable.Map.empty[UUID, String];
  //******* Handlers ******
  
  val operations = mutable.Queue.empty[Operation]
  var opCounter = 0;
  var oP : Cas = new Cas("","","");
  var opID = oP.id
  ctrl uponEvent {
    case _: Start => {
      val messages = SimulationResult[Int]("messages")
      for (i <- 0 to messages) {
        val put = new Put(s"test$i",s"$i");
        val msg1 = RouteMsg(put.key, put);
        trigger(NetMessage(self, server, val) -> net)
        operations.enqueue(put)
        pending += (put.id -> put.key)
        SimulationResult += (put.key -> "Ok")

        val get = new Get(s"test$i")
        val msg2 = RouteMsg(get.key, get) 
        trigger(NetMessage(self, server, msg2) -> net)
        operations.enqueue(get)
        pending += (get.id -> get.key)
        SimulationResult += (get.key -> "Ok")
      }
      for(i <- 0 to messages/2) {
        val newValue = i + 1;
        val cas = new Cas(s"test$i",s"$i",s"$newValue");
        opID = cas.id
        val msg = RouteMsg(cas.key, cas)
        trigger(NetMessage(self, server, msg) -> net)
      }
    }
  }

  net uponEvent {
    case NetMessage(header, or @ OpResponse(id, status)) => {
      var correctOp = true
      if(id.equals(opID)){
        val tempOps = operations.clone()
        for(i <- 0 to SimulationResult[Int]("messages")*2){
          val opr = tempOps.dequeue()
          while(!opr.id.equals(operations.dequeue().id)){
            if(operations.isEmpty){
              correctOp = false
              log.info("Not Linearizable")
            }
            else{
                log.info("Is Linearizable")
              }
          }
        }
        opCounter = opCounter + 1
        SimulationResult += (opCounter.toString + self.toString -> correctOp)
      }
    }
  }
}
