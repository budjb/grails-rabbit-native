/*
 * Copyright 2013-2016 Bud Byrd
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
package com.budjb.rabbitmq.converter

import groovy.transform.CompileStatic
import org.springframework.util.MimeType

/**
 * Contains the result of a conversion from some object to a byte array.
 */
@CompileStatic
class ObjectToByteResult {
    /**
     * Result of the conversion.
     */
    final byte[] result

    /**
     * Mime type of the converted object.
     */
    final MimeType mimeType

    /**
     * Constructor.
     *
     * @param result
     * @param mimeType
     */
    ObjectToByteResult(byte[] result, MimeType mimeType) {
        this.result = result
        this.mimeType = mimeType
    }
}
