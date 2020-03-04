package se.kth.id2203.leader_election

import scala.collection.mutable;
import se.kth.id2203.networking.{NetMessage,NetAddress}
import se.sics.kompics.{KompicsEvent, Start}
import se.sics.kompics.network.Network
import se.sics.kompics.sl._
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}

class BLElection extends Port {
  indication[BLE_Leader];
  request[Overview];
}

case class Overview(nodes: List[NetAddress]) extends KompicsEvent

case class BLE_Leader(leader: NetAddress, ballot: Long) extends KompicsEvent;

case class CheckTimeout(timeout: ScheduleTimeout) extends Timeout(timeout);

case class HB_Request(round: Long, highestBallot: Long) extends KompicsEvent;

case class HB_Response(round: Long, ballot: Long) extends KompicsEvent;

class GossipLeaderElection extends ComponentDefinition {

    val ble = provides[BLElection];
    val net = requires[Network];
    val timer = requires[Timer];

    val self = cfg.getValue[NetAddress]("id2203.project.address");
    val topology = mutable.ListBuffer.empty[NetAddress];
    val delta = cfg.getValue[Long]("id2203.project.keepAlivePeriod");
    var majority = (topology.size / 2) + 1;

    private var period = delta
    private val ballots = mutable.Map.empty[NetAddress, Long];

    
    private var round = 0L;
    private var ballot = ballotFromNAddress(0, self);

    private var leader: Option[(NetAddress, Long)] = None;
    private var highestBallot: Long = ballot;

    private val ballotOne = 0x0100000000L;

    def ballotFromNAddress(n: Int, adr: NetAddress): Long = {
        val nBytes = com.google.common.primitives.Ints.toByteArray(n);
        val addrBytes = com.google.common.primitives.Ints.toByteArray(adr.hashCode());
        val bytes = nBytes ++ addrBytes;
        val r = com.google.common.primitives.Longs.fromByteArray(bytes);
        assert(r > 0); // should not produce negative numbers!
        r
    }

    def incrementBallotBy(ballot: Long, inc: Int): Long = {
        ballot + inc.toLong * ballotOne
    }

    private def incrementBallot(ballot: Long): Long = {
        ballot + ballotOne
    }

    private def startTimer(delay: Long): Unit = {
        val scheduledTimeout = new ScheduleTimeout(period);
        scheduledTimeout.setTimeoutEvent(CheckTimeout(scheduledTimeout));
        trigger(scheduledTimeout -> timer);
    }

    private def checkLeader(): Unit = {
        ballots += ((self, ballot))
        val top = ballots.maxBy(_._2)
        val ( topProcess, topBallot ) = top
        if ( topBallot < highestBallot ) {
            while ( ballot <= highestBallot ) {
                ballot = incrementBallotBy(ballot, 1)
            }
            leader = None
        } 
        else {
            if( leader.isDefined ) {
                if ( top !=  leader.get ) {
                highestBallot = topBallot
                leader = Some(top)
                trigger( BLE_Leader( topProcess, topBallot ) -> ble )
                }
            } 
            else {
                highestBallot = topBallot
                leader = Some(top)
                trigger( BLE_Leader( topProcess, topBallot ) -> ble )
            }
        }
    }

    ble uponEvent {
        case Overview(nodes) => {
            for(n <- nodes) {
                topology += n;
            }
            majority = (topology.size / 2) + 1;
            startTimer(period);
            log.info(s"System overview $topology $majority")
        }
    }


    timer uponEvent {
        case CheckTimeout(_) => {
            if ( ballots.size + 1 >= majority ) {
                checkLeader();
            }

            ballots.clear
            round += 1
            for (p <- topology ) {
                if ( p != self ) {
                    trigger(NetMessage(self, p, HB_Request(round, highestBallot)) -> net);
                }
            }
            startTimer(period);
        }
    }

    net uponEvent {
        case NetMessage(src, HB_Request(r, hb)) => {
            if ( hb > highestBallot ) {
                highestBallot = hb
            }
            trigger(NetMessage(src, HB_Response(r, ballot)) -> net);
        }
        case NetMessage(src, HB_Response(r, b)) => {
            if (r == round) {
                ballots += ((src.getSource(), b))
            } 
            else {
                period += delta
            }
        }
    }

}
