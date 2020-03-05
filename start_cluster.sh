#!/bin/bash
# Credit to https://github.com/Limmen/Distributed-KV-store/blob/master/cluster.sh 
#       and https://github.com/Max-Meldrum/id2203-project/blob/master/cluster_setup.sh

# Way to kill all nodes we have started through this script..
intexit() {
    kill -HUP -$$
}

hupexit() {
    echo
    echo "Killing cluster"
    exit
}

trap hupexit HUP
trap intexit INT

FIRSTPORT=45000

echo "Starting cluster of $1 servers"
java -jar server/target/scala-2.13/server.jar -p $FIRSTPORT &
{
sleep 2
BOOTCLIENTS=$(($1-1))
for i in `seq 1 $BOOTCLIENTS`;
       do
                 sleep 1
                 echo $PORT
                 PORT=$(($FIRSTPORT+$i))
                java -jar server/target/scala-2.13/server.jar -s localhost:$FIRSTPORT -p $PORT &
        done
} #&> /dev/null
wait
