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

class Paxos extends ComponentDefinition {
   
    val sc = provides[SConsensus];
    val ble = requires[BLElection];
    val net = requires[Network];

    val self = cfg.getValue[NetAddress]("id2203.project.address");
    var pi = Set[NetAddress]();
    var others = Set[NetAddress]();
    var majority = 0;
    
    var state = (Role.FOLLOWER, State.UNKOWN);
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

     def setLeaderState = {
        state = (Role.LEADER, State.PREPARE);
        propCmds = List.empty[Operation];
        las.clear;
        lds.clear;
        lc = 0;
        acks.clear;
    }
    //---------------------------------

    def suffix(s: List[Operation], l: Int): List[Operation] = {
        s.drop(l)
    }

    def prefix(s: List[Operation], l: Int): List[Operation] = {
        s.take(l)
    }
    
    ble uponEvent {
        case BLE_Leader(l, n) => {
            log.info(s"BLE_Leader:  $l ")
            if (n > nL) {
                leader = Some(l);
                nL = n;
                if (self == l && nL > nProm) {
                    setLeaderState;
                    for (p <- others) {
                        log.info(s"LEADER($self): sending Prepare($nL,$ld,$na) TO: $p")
                        trigger(NetMessage(self, p, Prepare(nL,ld,na)) -> net);
                    }
                    acks += ((l, (na, suffix(va, ld))));
                    lds += ((self, ld));
                    nProm = nL;
                } 
                else {
                    state = (Role.FOLLOWER, state._2);
                }
                log.info(s"SELF: $self LEADER: $leader BALLOT: $nL MY STATE: $state");
            }
        }
    }

    net uponEvent {
        case NetMessage(src, Prepare(np, ldp, n)) => {
            log.info(s"$self: Prepare($np / $ldp / $n) FROM: $src");
            if (nProm < np) {
                nProm = np;
                state = (Role.FOLLOWER, State.PREPARE);
                var sfx = List[Operation]();
                if (na >= n) {
                    sfx ++= suffix(va,ld);
                }
                trigger(NetMessage(self, src.getSource(), Promise(np, na, sfx, ld)) -> net);
            }
        }

        case NetMessage(src, Promise(n, nA, sfxa, lda)) => {
            log.info(s"$self: Promise(n: $n / nA: $nA / $lda) FROM: $src");
            if ((n == nL) && (state == (Role.LEADER, State.PREPARE))) {
                acks += ((src.getSource(), (nA, sfxa)));
                lds += ((src.getSource(), lda))
                var P = Set[NetAddress]();
                var tempAcks = mutable.Map.empty[NetAddress, (Long, List[Operation])];
                for (p <- pi) if (acks.get(p) != None) {
                    P += p;
                    tempAcks += ((p, acks.get(p).get));
                }

                if (P.size == majority) {
                    val sfx = tempAcks.maxBy(x => (x._2._1, x._2._2.length) )._2._2;
                    var vasize1 = va.size;
                    va = prefix(va, ld) ++ sfx ++ propCmds;
                    var vasize2 = va.size;
                    log.info(s"$self: Promise (vasize (before: $vasize1 / after: $vasize2))");
                    las += ((self, va.size))
                    propCmds = List.empty[Operation];
                    state = (Role.LEADER, State.ACCEPT);
                    log.info(s"$self LEADER ACCEPT STATE. Others = $others\nlds=$lds");
                
                    for (p <- others) if (lds.get(p) != None) {
                        var tempp = lds.get(p).get;
                        var sfxp = suffix(va, lds.get(p).get);
                        var sfxpsize = sfxp.sizeIs;
                        log.info(s"$self: AcceptSync(nL: $nL / sfx: $sfxpsize / lds: p $tempp) TO: $p");
                        trigger(NetMessage(self, p, AcceptSync(nL, sfxp, tempp)) -> net);
                    }
                }
            }
            else if ((n == nL) && (state == (Role.LEADER, State.ACCEPT))) {
                lds += ((src.getSource(), lda));
                val sfx = suffix(va, lda);
                var sfxsize = sfx.size;
                log.info(s"$self: AcceptSync(lda: $lda / sfx: $sfxsize / nL: $nL / lc: $lc) TO: $src");
                trigger(NetMessage(self, src.getSource(), AcceptSync(nL, sfx, lda)) -> net);
                if (lc != 0) {
                    trigger(NetMessage(self, src.getSource(), Decide(lc, nL)) -> net);
                }
            }
        }

        case NetMessage(src, AcceptSync(n, sfx, ldp)) => {
            var p = src.getSource();
            log.info(s"$self: AcceptSync(state: $state / nProm: $nProm / n: $n)  FROM: $p");
            if ((nProm == n) && (state == (Role.FOLLOWER, State.PREPARE))) {
                na = n;
                var vasize1 = va.size;
                va = prefix(va, ldp) ++ sfx;
                var vasize2 = va.size;
                log.info(s"$self: AcceptSync(n: $n / ldp: $ldp / vasize(before: $vasize1 / after: $vasize2))");
                state = (Role.FOLLOWER, State.ACCEPT);
                trigger(NetMessage(self, p, Accepted(n, va.size)) -> net)
            }
        }

        case NetMessage(src, Accept(n, c)) => {
            if ((nProm == n) && (state == (Role.FOLLOWER, State.ACCEPT))) {
                var vasize1 = va.size
                va = va ++ List(c);
                var vasize2 = va.size;
                log.info(s"$self: Accept(n: $n / c: $c / vasize(before: $vasize1 / after: $vasize2))");
                trigger(NetMessage(self, src.getSource(), Accepted(n, va.size)) -> net);
            }
        }

        case NetMessage(src, Decide(l, n)) => {
            var p = src.getSource();
            var vasize = va.size;
            log.info(s"$self: Decide(l: $l / n: $n / vasize: $vasize / ld: $ld) FROM: $p");
            if (nProm == n) {
                while (ld < l){
                    trigger(SC_Decide(va(ld)) -> sc);
                    ld += 1;
                }
            }
        }

        case NetMessage(src, Accepted(n, m)) => {
            if ((n == nL) && (state == (Role.LEADER, State.ACCEPT))) {
                las += ((src.getSource(), m));
                var lasfilter = las.filter{x => x._2>=m}.size;
                log.info(s"$self: Accepted(n: $n / m: $m / lc: $lc / majority: $majority) \n Filter: $lasfilter / $las");
                if ((lc < m) && (las.filter{x => x._2>=m}.size >= majority)) {
                    lc = m;
                    var ldssize = lds.size
                    log.info(s"$self: Decide TO: $ldssize")
                    for (p <- pi) {
                        if (lds.get(p) != None) {
                            trigger(NetMessage(self, p, Decide(lc,nL)) -> net);
                        }
                    }
                }
            }
        }
    }

    sc uponEvent {
        case SC_Propose(c) => {
            log.info(s"$self SC_Propose $c")
            if (state == (Role.LEADER, State.PREPARE)) {
                propCmds = propCmds++List(c);
                log.info(s"$self SC_Propose $propCmds")
            }
            else if (state == (Role.LEADER, State.ACCEPT)) {
                var vasize1 = va.size
                va = va ++ List(c);
                var vasize2 = va.size
                las += ((self, las.get(self).get+1));
                log.info(s"$self SC_Propose $c -> vasize (before: $vasize1 / after: $vasize2)")
                for (p <- others) if (lds.get(p) != None) {
                    trigger(NetMessage(self, p, Accept(nL, c)) -> net)
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
