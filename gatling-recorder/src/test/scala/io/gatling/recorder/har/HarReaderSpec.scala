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

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import io.gatling.core.util.IOHelper
import io.gatling.recorder.config.RecorderConfiguration
import io.gatling.recorder.scenario.{ PauseElement, RequestElement }
import io.gatling.recorder.util.Json.parseJson
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HarReaderSpec extends Specification {

	def resourceAsStream(p: String) = getClass.getClassLoader.getResourceAsStream(p)
	val harEmptyJson = IOHelper.withCloseable(resourceAsStream("har/empty.har"))(parseJson(_))
	val harKernelJson = IOHelper.withCloseable(resourceAsStream("har/www.kernel.org.har"))(parseJson(_))

	RecorderConfiguration.initialSetup(mutable.HashMap.empty, None)

	"HarReader" should {

		"work with empty JSON" in {
			HarReader(harEmptyJson) must beEmpty
		}

		val elts = HarReader(harKernelJson)
		val pauseElts = elts.collect { case PauseElement(duration) => duration }

		"return the correct number of Pause elements" in {
			pauseElts.size must beLessThan(elts.size / 2)
		}

		"return an appropriate pause duration" in {
			val pauseDuration = pauseElts.foldLeft(0 milliseconds)(_ + _)

			// The total duration of the HAR record is of 6454ms
			(pauseDuration must beLessThanOrEqualTo(88389 milliseconds)) and
				(pauseDuration must beGreaterThan(80000 milliseconds))
		}

		"return the right request elements" in {
			val (googleFontUris, uris) = elts
				.collect { case RequestElement(uri, _, _, _, _, _) => uri }
				.partition(_.contains("google"))

			(uris must contain(startingWith("https://www.kernel.org")).forall) and
				(uris.size must beEqualTo(41)) and
				(googleFontUris.size must beEqualTo(16)) and
				(uris.head must beEqualTo("https://www.kernel.org/"))
		}
	}

	// Deactivate Specs2 implicit to be able to use the ones provided in scala.concurrent.duration
	override def intToRichLong(v: Int) = super.intToRichLong(v)
}