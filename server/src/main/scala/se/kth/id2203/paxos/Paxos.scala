package se.kth.id2203.paxos

import se.kth.id2203.kvstore._
import se.kth.id2203.leader_election._
import se.kth.id2203.networking.{NetMessage,NetAddress}
import se.sics.kompics.sl._
import se.sics.kompics.KompicsEvent
import se.sics.kompics.network.Network

import scala.collection.mutable;

case class Prepare(nL: Long, ld: Int, na: Long) extends KompicsEvent
case class Promise(nL: Long, na: Long, suffix: List[Operation], ld: Int) extends KompicsEvent
case class AcceptSync(nL: Long, suffix: List[Operation], ld: Int) extends KompicsEvent
case class Accept(nL: Long, c: Operation) extends KompicsEvent
case class Accepted(nL: Long, m: Int) extends KompicsEvent
case class Decide(ld: Int, nL: Long) extends KompicsEvent

object State extends Enumeration {
    type State = Value;
    val PREPARE, ACCEPT, UNKOWN = Value;
}

object Role extends Enumeration {
    type Role = Value;
    val LEADER, FOLLOWER = Value;
}

import State._;
import Role._;

class Paxos extends ComponentDefinition {
   
    val sc = provides[SConsensus];
    val ble = requires[BLElection];
    val net = requires[Network];

    val self = cfg.getValue[NetAddress]("id2203.project.address");
    var pi = Set[NetAddress]();
    var others = Set[NetAddress]();
    var majority = 0;
    
    var state = (FOLLOWER, UNKOWN);
    var nL = 0L;
    var nProm = 0L;
    var leader: Option[NetAddress] = None;
    var na = 0L;
    var va = List.empty[Operation];
    var ld = 0;
    var started = false

    //------Leader State--------------
    var propCmds = List.empty[Operation];
    val las = mutable.Map.empty[NetAddress, Int];
    val lds = mutable.Map.empty[NetAddress, Int];
    var lc = 0;
    val acks = mutable.Map.empty[NetAddress, (Long, List[Operation])];

  ble uponEvent {
    case BLE_Leader(l, n) => {
      log.info(s"$self: BLE_Leader($l, $n)");
      if (n > nL) {
        leader = Some(l);
        log.info(s"$self: New leader is $leader");
        nL = n;
        if (self == l && nL > nProm) {
          // We are the leader!
          log.info(s"$self: I am now leader for term $nL!");
          state = (LEADER, PREPARE);
          propCmds = List.empty;
          las.clear();
          lds.clear();
          acks.clear();
          lc = 0;
          // Request promises from all nodes
          for (p <- pi) {
            if (p != self) {
              trigger(NetMessage(self, p, Prepare(nL, ld, na)) -> net);
            }
          }
          acks(self) = (na, va.takeRight(ld));
          lds(self) = ld;
          nProm = nL;
        } else {
          //We are not the leader, make sure our state reflects that
          if (state._1 != FOLLOWER)
            log.info("$self: No longer leader :(");
          state = (FOLLOWER, state._2);
        }
      }
    }
   }
   
   def bestSeq(acks: mutable.Map[NetAddress, (Long, List[Operation])]): (Long, List[Operation]) = {
     var bestK: Long = -1
     var bestSfx = List.empty[Operation];
     for ((_, (k, sfx)) <- acks) {
        if (k > bestK || (k == bestK && sfx.length > bestSfx.length)) {
            bestK = k;
            bestSfx = sfx;
        }
     }
     return (bestK, bestSfx);
   }
   
  net uponEvent {
    case NetMessage(header, Prepare(nLp, ldp, nap)) => {
      val p = header.src;
      if (nProm < nLp) {
        // This overrides our old promise
        nProm = nLp;
        state = (FOLLOWER, PREPARE);
        log.info(s"$self: Promising $nProm")
        // If we have accepted more than the leader, send it our sequence
        val sfx = if (na >= nap) va.takeRight(va.length - ld) else List.empty;
        trigger(NetMessage(self, p, Promise(nLp, na, sfx, ld)) -> net);
      } else {
        log.info(s"$self: NOT promising, since $nProm >= $nLp")
      }
    }
    case NetMessage(header, Promise(n, na, sfxa, lda)) => {
      val a = header.src;
      if ((n == nL) && (state == (LEADER, PREPARE))) {
        // We're getting a promise
        log.info(s"$self: Got promise from $a")
        acks(a) = (na, sfxa);
        lds(a) = lda;
        // If a majority have promised, switch to accept state
        if (acks.size == majority) {
          // Adopt the latest adopted sequence, netus the proposed cmds
          val (k, sfx) = bestSeq(acks);
          va = va.take(ld) ++ sfx ++ propCmds;
          las(self) = va.length;
          propCmds = List.empty;
          state = (LEADER, ACCEPT);
          log.info(s"$self: PREPARE done, va has length ${las(self)}")
          // Sync up everyone who've promised
          for (p <- lds.keys) {
            if (p != self) {
              val sfxp = va.takeRight(va.length - lds(p));
              trigger(NetMessage(self, p, AcceptSync(nL, sfxp, lds(p))) -> net);
            }
          }
        }
      } else if ((n == nL) && (state == (LEADER, ACCEPT))) {
        // Someone promising late, sync them up
        lds(a) = lda;
        val sfx = va.takeRight(va.length - lds(a));
        trigger(NetMessage(self, a, AcceptSync(nL, sfx, lds(a))) -> net);
        // Also sync what has been decided on since
        if (lc != 0) {
          trigger(NetMessage(self, a, Decide(ld, nL)) -> net);
        }
      }
    }
    case NetMessage(header, AcceptSync(nL, sfx, ldp)) => {
        val p = header.src;
      if ((nProm == nL) && (state == (FOLLOWER, PREPARE))) {
         // We're being synced up
         na = nL;
         va = va.take(ld) ++ sfx;
         trigger(NetMessage(self, p, Accepted(nL, va.length)) -> net);
         state = (FOLLOWER, ACCEPT);
         log.info(s"$self: Synced up! Now at ${va.length}")
      }
    }
    case NetMessage(header, Accept(nL, c)) => {
        val p = header.src;
      if ((nProm == nL) && (state == (FOLLOWER, ACCEPT))) {
         // New thing to accept
         va = va :+ c;
         log.info(s"$self: Accepting ${va.length}")
         trigger(NetMessage(self, p, Accepted(nL, va.length)) -> net);
      }
    }
    case NetMessage(_, Decide(l, nL)) => {
      if (nProm == nL) {
        // Report everything decided as decided :D
        while (ld < l) {
          log.info(s"$self: Deciding on ${ld+1}")
          trigger(SC_Decide(va(ld)) -> sc);
          ld += 1;
        }
      }
    }
    case NetMessage(header, Accepted(n, m)) => {
      val a = header.src;
      if ((n == nL) && (state == (LEADER, ACCEPT))) {
        //Someone accepted
        log.info(s"$self: Got Accepted $m from $a")
        las(a) = m;
        if (lc < m && las.values.count(_ >= m) >= majority) {
          lc = m;
          // Tell everyone it is decided, including us
          // Everyone recieving the decide must already have accepted it (FIFO links)
          log.info(s"$self: Triggering DECIDE on item $lc")
          for (p <- lds.keys) {
            trigger(NetMessage(self, p, Decide(lc, nL)) -> net);
          }
        }
      }
    }
  }    

  sc uponEvent {
    case SC_Propose(c) => {
      if (state == (LEADER, PREPARE)) {
         propCmds = propCmds :+ c;
         log.info(s"$self: Adding $c to propCmds")
      } 
      else if (state == (LEADER, ACCEPT)) {
         va = va :+ c;
         log.info(s"$self: Triggering accept ${las(self) + 1} $c")
         las(self) += 1;
         for (p <- lds.keys) {
            if (p != self) {
              trigger(NetMessage(self, p, Accept(nL, c)) -> net);
            }
         }
     } else {
         if (leader.isDefined) {
             log.info(s"$self: NOT LEADER -> Forwarding: $c To: $leader");
             trigger(NetMessage(self, leader.get, c) -> net);
         } else {
             log.info(s"$self: There is no leader, can't accept Propose");
         }
     }
    }
    case SetTopology(topology) => {
        log.info(s"$self SetTopology"); 
        pi = topology;
        others = pi - self;
        majority = (pi.size / 2) + 1;
        trigger(Overview(pi.toList) -> ble);
        started = true;
    }
  }
}
