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
package io.gatling.recorder.scenario.template

import com.dongxiguo.fastring.Fastring.Implicits._

import io.gatling.recorder.scenario.{ ProtocolElement, ScenarioElement, TagElement }

object SimulationTemplate {

	def render(packageName: String,
		simulationClassName: String,
		protocol: ProtocolElement,
		headers: Map[Int, List[(String, String)]],
		scenarioName: String,
		scenarioElements: Either[Seq[ScenarioElement], Seq[Seq[ScenarioElement]]]): String = {

		def renderPackage = if (!packageName.isEmpty) fast"package $packageName\n" else ""

		def renderHeaders = {

			def printHeaders(headers: List[(String, String)]) = {
				if (headers.size > 1) {
					val mapContent = headers.map { case (name, value) => fast"		$tripleQuotes$name$tripleQuotes -> $tripleQuotes$value$tripleQuotes" }.mkFastring(",\n")
					fast"""Map(
$mapContent)"""
				} else {
					val (name, value) = headers(0)
					fast"Map($tripleQuotes$name$tripleQuotes -> $tripleQuotes$value$tripleQuotes)"
				}
			}

			headers
				.map { case (headersBlockIndex, headers) => fast"""	val ${RequestTemplate.headersBlockName(headersBlockIndex)} = ${printHeaders(headers)}""" }
				.mkFastring("\n\n")
		}

		def renderScenario = {
			scenarioElements match {
				case Left(elements) =>
					val scenarioElements = elements.map { element =>
						val prefix = element match {
							case _: TagElement => ""
							case _ => "."
						}
						fast"$prefix$element"
					}.mkFastring("\n\t\t")

					fast"""val scn = scenario("$scenarioName")
		$scenarioElements"""

				case Right(chains) =>
					val chainElements = chains.zipWithIndex.map {
						case (chain, i) =>
							var firstNonTagElement = true
							val chainContent = chain.map { element =>
								val prefix = element match {
									case _: TagElement => ""
									case _ => if (firstNonTagElement) { firstNonTagElement = false; "" } else "."
								}
								fast"$prefix$element"
							}.mkFastring("\n\t\t")
							fast"val chain_$i = $chainContent"
					}.mkFastring("\n\n")

					val chainsList = (for (i <- 0 until chains.size) yield fast"chain_$i").mkFastring(", ")

					fast"""$chainElements
					
	val scn = scenario("$scenarioName").exec(
		$chainsList)"""
			}

		}

		fast"""$renderPackage
import scala.concurrent.duration._

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class $simulationClassName extends Simulation {

	val httpProtocol = http$protocol

$renderHeaders

	$renderScenario

	setUp(scn.inject(atOnceUsers(1))).protocols(httpProtocol)
}""".toString
	}
}
