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
package se.kth.id2203.kvstore

import com.larskroll.common.repl._
import com.typesafe.scalalogging.StrictLogging;
import concurrent.Await
import concurrent.duration._
import concurrent.Future
import fastparse._, NoWhitespace._
import org.apache.log4j.Layout
import util.log4j.ColoredPatternLayout;

object ClientConsole {
  def lowercase[_: P] = P(CharIn("a-z"))
  def uppercase[_: P] = P(CharIn("A-Z"))
  def digit[_: P] = P(CharIn("0-9"))
  def simpleStr[_: P] = P(lowercase | uppercase | digit)
  val colouredLayout = new ColoredPatternLayout("%d{[HH:mm:ss,SSS]} %-5p {%c{1}} %m%n");
}

class ClientConsole(val service: ClientService) extends CommandConsole with ParsedCommands with StrictLogging {
  import ClientConsole._;

  override def layout: Layout = colouredLayout;
  override def onInterrupt(): Unit = exit();

  val opParser = new ParsingObject[String] {
    override def parseOperation[_: P]: P[String] = P("op" ~ " " ~ simpleStr.!);
  }

  val getParser = new ParsingObject[String] {
    override def parseOperation[_: P]: P[String] = P("get" ~ " " ~ simpleStr.!);
  }

  val putParser = new ParsingObject[(String, String)] {
    override def parseOperation[_: P]: P[(String, String)] = P("put" ~ " " ~ simpleStr.! ~ " " ~ simpleStr.!);
  }

  val casParser = new ParsingObject[(String, String, String)] {
    override def parseOperation[_: P]: P[(String, String, String)] 
      = P("cas" ~ " " ~ simpleStr.! ~ " " ~ simpleStr.! ~ " " ~ simpleStr.!);
  }

  def getResponse(fr: Future[OperationResponse]): Option[OperationResponse] = {
    try {
      val r = Await.result(fr, 5.seconds);
      out.println("Operation complete! Response was: " + r.status);
      return Some(r);
    } catch {
      case e: Throwable => logger.error("Error during op.", e);
      return None
    }
  }

  val opCommand = parsed(opParser, usage = "op <key>", descr = "Executes an op for <key>.") {
    key =>
    getResponse(service.op(key));
  };

  val getCommand = parsed(getParser, usage = "get <key>", descr = "Reads the value of <key>") {
    key =>
    val or: Option[OperationResponse] = getResponse(service.get(key));
    if (or.isDefined) {
      val r = or.get.asInstanceOf[GetResponse];
      println(s"Value is ${r.value}"); 
    }
  }

  val putCommand = parsed(putParser, usage = "put <key> <value>", descr = "Writes <value> to <key>") {
    case (key, value) =>
    getResponse(service.put(key, value));
  }

  val casCommand = parsed(casParser, usage = "cas <key> <compare> <value>", descr = "Writes <value> to <key> if previous value is <compare>, returns previous value") {
    case (key, compare, value) =>
    getResponse(service.cas(key, compare, value));
  }
}
