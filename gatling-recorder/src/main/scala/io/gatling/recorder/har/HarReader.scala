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
package io.gatling.recorder.har

import java.io.{ FileInputStream, InputStream }
import java.net.URL
import scala.concurrent.duration.DurationLong
import scala.util.Try
import io.gatling.core.util.IOHelper
import io.gatling.core.util.StringHelper.RichString
import io.gatling.http.HeaderNames.CONTENT_TYPE
import io.gatling.recorder.config.RecorderConfiguration.configuration
import io.gatling.recorder.scenario.{ PauseElement, RequestBodyBytes, RequestBodyParams, RequestElement, ScenarioElement }
import io.gatling.recorder.util.Json
import io.gatling.recorder.util.RedirectHelper
import io.gatling.recorder.scenario.Scenario

object HarReader {

	def apply(path: String): List[ScenarioElement] =
		IOHelper.withCloseable(new FileInputStream(path))(apply(_))

	def apply(jsonStream: InputStream): List[ScenarioElement] =
		apply(Json.parseJson(jsonStream))

	def apply(json: Json): Scenario = {
		val HttpArchive(Log(entries)) = HarMapping.jsonToHttpArchive(json)
		val elements = entries.iterator
			.filter(e => isValidURL(e.request.url))
			// TODO NICO : can't we move this in Scenario as well ?
			.filter(e => configuration.filters.filters.map(_.accept(e.request.url)).getOrElse(true))
			.map(createRequestWithArrivalTime)
			.toVector

		Scenario(elements, Nil)
	}

	private def createRequestWithArrivalTime(entry: Entry) = {
		def buildContent(postParams: Seq[PostParam]) =
			RequestBodyParams(postParams.map(postParam => (postParam.name, postParam.value)).toList)

		val uri = entry.request.url
		val method = entry.request.method
		val headers = buildHeaders(entry)

		// NetExport doesn't copy post params to text field
		val body = entry.request.postData.map { postData =>
			postData.text.trimToOption match {
				// TODO NICO : shouldn't the encoding be taken from the Content-Type header ?
				case Some(string) => RequestBodyBytes(string.getBytes(configuration.core.encoding))
				case None => buildContent(postData.params)
			}
		}

		(entry.arrivalTime, RequestElement(uri, method, headers, body, entry.response.status))
	}

	private def buildHeaders(entry: Entry): Map[String, String] = {
		val headers = entry.request.headers.map(h => (h.name, h.value)).toMap
		// NetExport doesn't add Content-Type to headers when POSTing, but both Chrome Dev Tools and NetExport set mimeType
		entry.request.postData.map(postData => headers.updated(CONTENT_TYPE, postData.mimeType)).getOrElse(headers)
	}

	private def isValidURL(url: String): Boolean = Try(new URL(url)).isSuccess
}
