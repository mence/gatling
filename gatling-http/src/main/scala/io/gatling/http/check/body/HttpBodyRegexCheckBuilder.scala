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
package io.gatling.http.check.body

import io.gatling.core.check.extractor.regex.{ CountRegexExtractor, GroupExtractor, MultipleRegexExtractor, SingleRegexExtractor }
import io.gatling.core.session.Expression
import io.gatling.http.check.{ HttpCheckBuilders, HttpMultipleCheckBuilder }

object HttpBodyRegexCheckBuilder {

	def regex[X](expression: Expression[String])(implicit groupExtractor: GroupExtractor[X]) =
		new HttpMultipleCheckBuilder[CharSequence, X](HttpCheckBuilders.bodyCheckFactory, HttpCheckBuilders.charsResponsePreparer) {
			def findExtractor(occurrence: Int) = session => expression(session).map(new SingleRegexExtractor(_, occurrence))
			def findAllExtractor = session => expression(session).map(new MultipleRegexExtractor(_))
			def countExtractor = session => expression(session).map(new CountRegexExtractor(_))
		}
}
