/**
 * Copyright 2011-2013 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.http.ahc

import java.util.concurrent.atomic.AtomicBoolean

import com.ning.http.client.{ AsyncHandlerExtensions, HttpResponseBodyPart, HttpResponseHeaders, HttpResponseStatus, ProgressAsyncHandler }
import com.ning.http.client.AsyncHandler.STATE.CONTINUE
import com.typesafe.scalalogging.slf4j.Logging

/**
 * This class is the AsyncHandler that AsyncHttpClient needs to process a request's response
 *
 * It is part of the HttpRequestAction
 *
 * @constructor constructs a GatlingAsyncHandler
 * @param tx the data about the request to be sent and processed
 * @param responseBuilder the builder for the response
 */
class AsyncHandler(tx: HttpTx) extends ProgressAsyncHandler[Unit] with AsyncHandlerExtensions with Logging {

	val responseBuilder = tx.responseBuilderFactory(tx.request)
	private val done = new AtomicBoolean(false)

	def onRequestSent {
		responseBuilder.updateFirstByteSent
	}

	def onRetry {
		responseBuilder.reset
	}

	def onHeaderWriteCompleted = {
		if (!done.get) responseBuilder.updateLastByteSent
		CONTINUE
	}

	def onContentWriteCompleted = {
		if (!done.get) responseBuilder.updateLastByteSent
		CONTINUE
	}

	def onContentWriteProgress(amount: Long, current: Long, total: Long) = CONTINUE

	def onStatusReceived(status: HttpResponseStatus) = {
		if (!done.get) responseBuilder.accumulate(status)
		CONTINUE
	}

	def onHeadersReceived(headers: HttpResponseHeaders) = {
		if (!done.get) responseBuilder.accumulate(headers)
		CONTINUE
	}

	def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
		if (!done.get) responseBuilder.accumulate(bodyPart)
		CONTINUE
	}

	def onCompleted {
		if (!done.getAndSet(true)) {
			try {
				val response = responseBuilder.build
				AsyncHandlerActor.instance ! OnCompleted(tx, response)
			} catch {
				case e: Exception => sendOnThrowable(e)
			}
		}
	}

	def onThrowable(throwable: Throwable) {
		if (!done.getAndSet(true)) {
			responseBuilder.updateLastByteReceived
			sendOnThrowable(throwable)
		}
	}

	def sendOnThrowable(throwable: Throwable) {
		val errorMessage = Option(throwable.getMessage) match {
			case Some(m) => m
			case _ => throwable.getClass.getName
		}

		if (logger.underlying.isInfoEnabled)
			logger.warn(s"Request '${tx.requestName}' failed for user ${tx.session.userId}", throwable)
		else
			logger.warn(s"Request '${tx.requestName}' failed for user ${tx.session.userId}: $errorMessage")

		AsyncHandlerActor.instance ! OnThrowable(tx, responseBuilder.build, errorMessage)
	}
}
