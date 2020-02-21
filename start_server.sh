java -jar server/target/scala-2.13/server.jar -p 45678 &
sleep 1
java -jar server/target/scala-2.13/server.jar -p 45679 -s localhost:45678 &
java -jar server/target/scala-2.13/server.jar -p 45680 -s localhost:45678 &
wait
jobs -p | xargs -I{} kill -- -{}
