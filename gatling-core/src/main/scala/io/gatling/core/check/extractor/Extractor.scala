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
package io.gatling.core.check.extractor

import io.gatling.core.session.{ Expression, Session }
import io.gatling.core.validation.Validation

trait Extractor[P, X] {
	def name: String
	def apply(session: Session, prepared: P): Validation[Option[X]]
}

abstract class CriterionExtractor[P, T, X] extends Extractor[P, X] {
	def criterion: Expression[T]
	def extract(prepared: P, criterion: T): Validation[Option[X]]
	def criterionName: String
	def name = s"$criterionName($criterion)"
	def apply(session: Session, prepared: P): Validation[Option[X]] =
		for {
			criterionValue <- criterion(session).mapError(message => s"could not resolve extractor criterion: $message")
			extracted <- extract(prepared, criterionValue).mapError(message => s"($criterionValue) could not extract : $message")
		} yield extracted
}
