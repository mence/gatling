/**
 * Copyright 2011-2013 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.jms

import io.gatling.core.Predef._
import io.gatling.core.action.Chainable
import akka.actor.{ ActorRef, Actor, Props }
import io.gatling.core.result.writer.DataWriter
import io.gatling.core.util.TimeHelper.nowMillis
import io.gatling.core.result.writer.RequestMessage
import io.gatling.core.result.message.OK
import java.util.{ Hashtable => JHashtable }
import javax.naming._
import javax.jms._

/**
 * Core JMS Action to handle Request-Reply semantics
 * <p>
 * This handles the core "send"ing of messages. Gatling calls the execute method to trigger a send.
 * This implementation then forwards it on to a tracking actor.
 * @author jasonk@bluedevel.com
 */
class JmsReqReplyAction(val next: ActorRef, val attributes: JmsAttributes,
	val protocol: JmsProtocol, val tracker: ActorRef) extends Chainable {

	// Create a client to refer to
	// this assumes the protocol has been validated by the builder
	val client = new SimpleJmsClient(protocol.connectionFactoryName.get,
		attributes.queueName, protocol.jmsUrl.get,
		protocol.username, protocol.password,
		protocol.contextFactory.get,
		protocol.deliveryMode)

	// start the requested number of listener threads
	for (i <- 1 to protocol.listenerCount) {
		startConsumerThread
	}

	/**
	 * Creates a consumer thread to pull from the client
	 */
	private def startConsumerThread = {

		val thread = new Thread(new Runnable {
			def run = {
				val replyConsumer = client.createReplyConsumer
				while (true) {
					val m = replyConsumer.receive()
					m match {
						case msg: Message => tracker ! MessageReceived(msg.getJMSCorrelationID, nowMillis, msg)
						case _ => {
							println("Blocking receive returned null. Possibly the consumer was closed.")
							throw new Exception("Blocking receive returned null. Possibly the consumer was closed.")
						}
					}
				}
			}
		})
		thread.start

	}

	/**
	 * Framework calls the execute() method to send a single request
	 * <p>
	 * Note this does not catch any exceptions (even JMSException) as generally these indicate a
	 * configuration failure that is unlikely to be addressed by retrying with another message
	 */
	def execute(session: io.gatling.core.Predef.Session) {
		import JmsMessageClass._

		// send the message
		val start = nowMillis
		val msgid = attributes.messageType match {
			case BytesJmsMessage => client.sendBytesMessage(attributes.bytesMessage, attributes.messageProperties)
			case MapJmsMessage => client.sendMapMessage(attributes.mapMessage, attributes.messageProperties)
			case ObjectJmsMessage => client.sendObjectMessage(attributes.objectMessage, attributes.messageProperties)
			case TextJmsMessage => client.sendTextMessage(attributes.textMessage, attributes.messageProperties)
		}

		// notify the tracker that a message was sent
		tracker ! MessageSent(msgid, start, nowMillis, attributes.checks, session, next, attributes.requestName)

	}
}
