/**
 * Copyright 2011-2012 eBusiness Information, Groupe Excilys (www.excilys.com)
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
package com.excilys.ebi.gatling.recorder.config

import com.excilys.ebi.gatling.recorder.ui.enumeration.PatternType._
import scala.reflect.BeanProperty

class Pattern(@BeanProperty val patternType: PatternType, @BeanProperty val pattern: String) {
	override def toString = patternType + " | " + pattern

	override def hashCode: Int = {
		val prime = 31
		var result = 1
		result = prime * result + (if(pattern == null) 0 else pattern.hashCode)
		result = prime * result + (if(patternType == null) 0 else patternType.hashCode)
		result
	}

	override def equals(obj: Any): Boolean = {
		obj match {
      case p: Pattern =>
        p.pattern == pattern && p.patternType == patternType
      case _ => false
    }
	}

}