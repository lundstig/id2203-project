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
package se.kth.id2203.kvstore;

import se.kth.id2203.networking._;
import se.kth.id2203.overlay.Routing;
import se.kth.id2203.leader_election._;
import se.kth.id2203.paxos._;
import se.sics.kompics.sl._;
import se.sics.kompics.network.Network;
import scala.collection.mutable._;
import java.util.UUID;

class KVService extends ComponentDefinition {

  //******* Ports ******
  val net = requires[Network];
  val route = requires(Routing);
  val sc = requires[SConsensus];

  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val store: HashMap[String, String] = HashMap(("1" -> "abc"), ("2" -> "def"), ("5" -> "opq"));
  val leader: Option[NetAddress] = None;
  val opPending = Map.empty[UUID, NetAddress];
  val opLog = ListBuffer.empty[Operation]


  //******* Handlers ******
  net uponEvent {
    case NetMessage(header, op: Operation) => {
      log.info("Got operation {}!", op);
      opPending += (op.id -> header.src);
      trigger(SC_Propose(op) -> sc);
    }
  }

  sc uponEvent {
    case SC_Decide(op: Operation) => {
      log.info(s"Decide: $op");
      val src = opPending.getOrElse(op.id, self);
      op match {
        case Get(_, _) => {
          log.info("Sequence Consensus: GET from KVStore");
          val value = store.get(op.key);
          if (value.isDefined) {
            trigger(NetMessage(self, src, GetResponse(op.id, OpCode.Ok, value.get)) -> net);
          }
          else {
            trigger(NetMessage(self, src, GetResponse(op.id, OpCode.NotFound, "")) -> net);
          }
        }

        case Put(key, value, _) => {
          log.info("Sequence Consensus: PUT to KVStore");
          store += (key -> value);
          trigger(NetMessage(self, src, PutResponse(op.id, OpCode.Ok)) -> net);
        }

        case Cas(key, compare, value, _) => {
          log.info("Sequence Consensus: CAS to KVStore");
          if (!store.contains(key)){
            log.info(s"KEY $key: Does not exists in store!");
            trigger(NetMessage(self, src, CasResponse(op.id, OpCode.NotFound, ""))-> net)
          }else{
            val currentValue = store.get(key).get;
            if(currentValue != compare) {
              log.info(s"COMPARE $compare: Does not match the current value $currentValue");
              trigger(NetMessage(self, src, CasResponse(op.id, OpCode.Ok, currentValue)) -> net);
            }else {
              store += (key -> value);
              log.info("CAS Completed");
              trigger(NetMessage(self, src, CasResponse(op.id, OpCode.Ok, currentValue)) -> net)
            }
          }
        }
      }
    }
  }
}
