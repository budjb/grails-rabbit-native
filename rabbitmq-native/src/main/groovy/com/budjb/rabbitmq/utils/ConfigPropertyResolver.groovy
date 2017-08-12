/*
 * Copyright 2017 Bud Byrd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.budjb.rabbitmq.utils

import grails.core.GrailsApplication

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @since 02/12/2016
 */
trait ConfigPropertyResolver {

    abstract GrailsApplication getGrailsApplication()

    /**
     * Temporary fix to handle property resolution from YAML processed lists of maps.
     * And values of the spring property format '${lookup}' will be resolved against the
     * grails configuration. This should be fixed dependant on Issue 10340 in Grails-Core.
     * See https://github.com/grails/grails-core/issues/10340.
     *
     * @param map the map to fix
     * @param config Grails Application config
     * @return a map with all values remapped where necessary
     */
    Map fixPropertyResolution(Map map) {
        map.collectEntries { k, v ->
            def val = v
            if (val instanceof String) {
                Matcher m = Pattern.compile(/\$\{(.+?)}/).matcher(val)
                if (m.matches()) {
                    val = grailsApplication.config.get(m.group(1))
                }
            }
            [k, val]
        }
    }

}
