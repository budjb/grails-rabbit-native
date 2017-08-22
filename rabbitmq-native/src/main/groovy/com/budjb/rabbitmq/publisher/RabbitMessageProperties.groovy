/*
 * Copyright 2013-2017 Bud Byrd
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
package com.budjb.rabbitmq.publisher

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import groovy.transform.CompileStatic

import java.time.OffsetDateTime

@CompileStatic
class RabbitMessageProperties {
    /**
     * Default timeout for RPC calls (5 seconds).
     */
    static final Integer DEFAULT_TIMEOUT = 5000

    /**
     * Routing key to send the message to.
     */
    String routingKey = ''

    /**
     * Exchange to send the message to.
     */
    String exchange = ''

    /**
     * RPC timeout, in milliseconds.
     */
    Integer timeout = DEFAULT_TIMEOUT

    /**
     * Message body.
     */
    Object body

    /**
     * Message headers.
     */
    Map headers = [:]

    /**
     * Content type.
     */
    String contentType

    /**
     * Content encoding.
     */
    String contentEncoding

    /**
     * Delivery mode (1 == non-persistent, 2 == persistent)
     */
    Integer deliveryMode = 0

    /**
     * Priority.
     */
    Integer priority = 0

    /**
     * Correlation id.
     */
    String correlationId

    /**
     * Queue to reply to.
     */
    String replyTo

    /**
     * Message TTL.
     */
    String expiration

    /**
     * Message ID.
     */
    String messageId

    /**
     * Message timestamp.
     */
    OffsetDateTime timestamp

    /**
     * Message type name.
     */
    String type

    /**
     * User ID.
     */
    String userId

    /**
     * Application ID.
     */
    String appId

    /**
     * Whether to auto-convertToBytes the reply payload.
     */
    Boolean autoConvert = true

    /**
     * Connection name.
     */
    String connection

    /**
     * Channel to publish messages through.
     */
    Channel channel

    /**
     * Builds the properties class from a provided closure and returns itself.
     *
     * @param closure
     * @return This object.
     */
    RabbitMessageProperties build(@DelegatesTo(RabbitMessageProperties) Closure closure) {
        run(closure)
        return this
    }

    /**
     * Runs the provided closure.
     *
     * @param closure
     */
    protected void run(@DelegatesTo(RabbitMessageProperties) Closure closure) {
        closure.delegate = this
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure()
    }

    /**
     * Creates an AMQP basic properties object suitable for use in publishing messages.
     *
     * @return
     */
    BasicProperties toBasicProperties() {
        // Create message properties
        BasicProperties.Builder builder = new BasicProperties.Builder()

        // Set any headers
        builder.headers(headers)

        // Content type
        if (contentType) {
            builder.contentType(contentType.toString())
        }

        // Content encoding
        if (contentEncoding) {
            builder.contentEncoding(contentEncoding)
        }

        // Delivery mode
        if (deliveryMode in [1, 2]) {
            builder.deliveryMode(deliveryMode)
        }

        // Set priority
        if (priority) {
            builder.priority(priority)
        }

        // Set correlation id
        if (correlationId) {
            builder.correlationId(correlationId)
        }

        // Reply-to
        if (replyTo) {
            builder.replyTo(replyTo)
        }

        // Expiration
        if (expiration) {
            builder.expiration(expiration)
        }

        // Message ID
        if (messageId) {
            builder.messageId(messageId)
        }

        // Timestamp
        if (timestamp) {
            builder.timestamp(Date.from(timestamp.toInstant()))
        }

        // Type
        if (type) {
            builder.type(type)
        }

        // User ID
        if (userId) {
            builder.userId(userId)
        }

        // Application ID
        if (appId) {
            builder.appId(appId)
        }

        return builder.build()
    }

    /**
     * Sets the message TTL.
     *
     * @param expiration
     */
    void setExpiration(String expiration) {
        this.expiration = expiration
    }

    /**
     * Sets the message TTL.
     *
     * @param expiration
     */
    void setExpiration(Number expiration) {
        this.expiration = expiration.toString()
    }
}
