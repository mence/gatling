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
package io.gatling.core.check

import com.typesafe.scalalogging.slf4j.StrictLogging

import io.gatling.core.check.extractor.Extractor
import io.gatling.core.session.{ Expression, ExpressionWrapper, RichExpression }
import io.gatling.core.validation.{ FailureWrapper, SuccessWrapper, Validation }

trait ExtractorCheckBuilder[C <: Check[R], R, P, X] {

	def find: ValidatorCheckBuilder[C, R, P, X]
}

trait MultipleExtractorCheckBuilder[C <: Check[R], R, P, T, X] extends ExtractorCheckBuilder[C, R, P, X] {

	def find(occurrence: Int): ValidatorCheckBuilder[C, R, P, X]

	def findAll: ValidatorCheckBuilder[C, R, P, X]

	def count: ValidatorCheckBuilder[C, R, P, X]
}

case class ValidatorCheckBuilder[C <: Check[R], R, P, X](
	checkFactory: CheckFactory[C, R],
	preparer: Preparer[R, P],
	extractor: Expression[Extractor[P, X]]) extends StrictLogging {

	def transform[X2](transformation: Option[X] => Option[X2]): ValidatorCheckBuilder[C, R, P, X2] = copy(extractor = extractor.map { extractor =>
		new Extractor[P, X2] {
			def name = extractor.name + " transformed"
			def apply(prepared: P): Validation[Option[X2]] = extractor(prepared).flatMap { extracted =>
				try {
					transformation(extracted).success
				} catch {
					case e: Exception => s"transform crashed with a exception: ${e.getMessage}".failure
				}
			}
		}
	})

	def validate(validator: Expression[Validator[X]]) = new CheckBuilder(this, validator) with SaveAs[C, R, P, X]

	def is(expected: Expression[X]) = validate(expected.map(new IsMatcher(_)))
	def not(expected: Expression[X]) = validate(expected.map(new NotMatcher(_)))
	def in(expected: Expression[Seq[X]]) = validate(expected.map(new InMatcher(_)))
	def exists = validate(new ExistsValidator().expression)
	def notExists = validate(new NotExistsValidator().expression)
	def dontValidate = validate(new NoopValidator().expression)
	def lessThan(expected: Expression[X])(implicit ordering: Ordering[X]) = validate(expected.map(new CompareMatcher("lessThan", "less than", implicitly[Ordering[X]].lt, _)))
	def lessThanOrEqual(expected: Expression[X])(implicit ordering: Ordering[X]) = validate(expected.map(new CompareMatcher("lessThanOrEqual", "less than or equal to", implicitly[Ordering[X]].lteq, _)))
	def greaterThan(expected: Expression[X])(implicit ordering: Ordering[X]) = validate(expected.map(new CompareMatcher("greaterThan", "greater than", implicitly[Ordering[X]].gt, _)))
	def greaterThanOrEqual(expected: Expression[X])(implicit ordering: Ordering[X]) = validate(expected.map(new CompareMatcher("greaterThanOrEqual", "greater than or equal to", implicitly[Ordering[X]].gteq, _)))
}

case class CheckBuilder[C <: Check[R], R, P, X](
	validatorCheckBuilder: ValidatorCheckBuilder[C, R, P, X],
	validator: Expression[Validator[X]],
	saveAs: Option[String] = None) {

	def build: C = {
		val base = CheckBase(validatorCheckBuilder.preparer, validatorCheckBuilder.extractor, validator, saveAs)
		validatorCheckBuilder.checkFactory(base)
	}
}

trait SaveAs[C <: Check[R], R, P, X] { this: CheckBuilder[C, R, P, X] =>
	def saveAs(key: String): CheckBuilder[C, R, P, X] = copy(saveAs = Some(key))
}
