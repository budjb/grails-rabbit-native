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
package com.budjb.rabbitmq.test.publisher

import com.budjb.rabbitmq.publisher.RabbitMessageProperties
import com.rabbitmq.client.BasicProperties
import com.rabbitmq.client.Channel
import spock.lang.Specification

import java.time.OffsetDateTime

class RabbitMessagePropertiesSpec extends Specification {
    RabbitMessageProperties rabbitMessageProperties

    def setup() {
        rabbitMessageProperties = new RabbitMessageProperties()
    }

    def 'When a properties object is construction, validate the default properties aer still present'() {
        when:
        rabbitMessageProperties = new RabbitMessageProperties()

        then:
        rabbitMessageProperties.appId == null
        rabbitMessageProperties.autoConvert
        rabbitMessageProperties.body == null
        rabbitMessageProperties.channel == null
        rabbitMessageProperties.connection == null
        rabbitMessageProperties.contentEncoding == null
        rabbitMessageProperties.contentType == null
        rabbitMessageProperties.correlationId == null
        rabbitMessageProperties.deliveryMode == 0
        rabbitMessageProperties.exchange == ''
        rabbitMessageProperties.expiration == null
        rabbitMessageProperties.headers.isEmpty()
        rabbitMessageProperties.messageId == null
        rabbitMessageProperties.priority == 0
        rabbitMessageProperties.replyTo == null
        rabbitMessageProperties.routingKey == ''
        rabbitMessageProperties.timeout == 5000
        rabbitMessageProperties.timestamp == null
        rabbitMessageProperties.type == null
        rabbitMessageProperties.userId == null

    }

    def 'When a properties object is built with an empty closure, validate the default properties are still present'() {
        when:
        rabbitMessageProperties.build {}

        then:
        rabbitMessageProperties.appId == null
        rabbitMessageProperties.autoConvert
        rabbitMessageProperties.body == null
        rabbitMessageProperties.channel == null
        rabbitMessageProperties.connection == null
        rabbitMessageProperties.contentEncoding == null
        rabbitMessageProperties.contentType == null
        rabbitMessageProperties.correlationId == null
        rabbitMessageProperties.deliveryMode == 0
        rabbitMessageProperties.exchange == ''
        rabbitMessageProperties.expiration == null
        rabbitMessageProperties.headers.isEmpty()
        rabbitMessageProperties.messageId == null
        rabbitMessageProperties.priority == 0
        rabbitMessageProperties.replyTo == null
        rabbitMessageProperties.routingKey == ''
        rabbitMessageProperties.timeout == 5000
        rabbitMessageProperties.timestamp == null
        rabbitMessageProperties.type == null
        rabbitMessageProperties.userId == null
    }

    def 'Ensure properties are set correctly when overridden'() {
        setup:
        Channel channel = Mock(Channel)
        OffsetDateTime offsetDateTime = OffsetDateTime.now()

        when:
        rabbitMessageProperties.build {
            appId = 'test-appId'
            autoConvert = false
            body = 'test-body'
            delegate.channel = channel
            connection = 'test-connection'
            contentEncoding = 'test-encoding'
            contentType = 'text/plain'
            correlationId = 'test-correlationId'
            deliveryMode = 1
            exchange = 'test-exchange'
            expiration = 'test-expiration'
            headers = ['foo': 'bar']
            messageId = 'test-messageId'
            priority = 2
            replyTo = 'test-replyTo'
            routingKey = 'test-routingKey'
            timeout = 10000
            timestamp = offsetDateTime
            type = 'test-type'
            userId = 'test-userId'
        }

        then:
        rabbitMessageProperties.appId == 'test-appId'
        !rabbitMessageProperties.autoConvert
        rabbitMessageProperties.body == 'test-body'
        rabbitMessageProperties.channel == channel
        rabbitMessageProperties.connection == 'test-connection'
        rabbitMessageProperties.contentEncoding == 'test-encoding'
        rabbitMessageProperties.contentType == 'text/plain'
        rabbitMessageProperties.correlationId == 'test-correlationId'
        rabbitMessageProperties.deliveryMode == 1
        rabbitMessageProperties.exchange == 'test-exchange'
        rabbitMessageProperties.expiration == 'test-expiration'
        rabbitMessageProperties.headers.foo == 'bar'
        rabbitMessageProperties.messageId == 'test-messageId'
        rabbitMessageProperties.priority == 2
        rabbitMessageProperties.replyTo == 'test-replyTo'
        rabbitMessageProperties.routingKey == 'test-routingKey'
        rabbitMessageProperties.timeout == 10000
        rabbitMessageProperties.timestamp == offsetDateTime
        rabbitMessageProperties.type == 'test-type'
        rabbitMessageProperties.userId == 'test-userId'
    }

    def 'Ensure a basic properties object reflects the correct values when a default message properties object is used'() {
        when:
        BasicProperties properties = rabbitMessageProperties.toBasicProperties()

        then:
        properties.getAppId() == null
        properties.getContentEncoding() == null
        properties.getContentType() == null
        properties.getCorrelationId() == null
        properties.getDeliveryMode() == null
        properties.getExpiration() == null
        properties.getHeaders() == [:]
        properties.getMessageId() == null
        properties.getPriority() == null
        properties.getReplyTo() == null
        properties.getTimestamp() == null
        properties.getType() == null
        properties.getUserId() == null
    }

    def 'Ensure a basic properties object reflects the correct values when an overridden message properties object is used'() {
        setup:
        rabbitMessageProperties.build {
            appId = 'test-appId'
            autoConvert = false
            body = 'test-body'
            delegate.channel = channel
            connection = 'test-connection'
            contentEncoding = 'test-encoding'
            contentType = 'text/plain'
            correlationId = 'test-correlationId'
            deliveryMode = 1
            exchange = 'test-exchange'
            expiration = 'test-expiration'
            headers = ['foo': 'bar']
            messageId = 'test-messageId'
            priority = 2
            replyTo = 'test-replyTo'
            routingKey = 'test-routingKey'
            timeout = 10000
            type = 'test-type'
            userId = 'test-userId'
        }

        when:
        BasicProperties properties = rabbitMessageProperties.toBasicProperties()

        then:
        properties.getAppId() == 'test-appId'
        properties.getContentEncoding() == 'test-encoding'
        properties.getContentType() == 'text/plain'
        properties.getCorrelationId() == 'test-correlationId'
        properties.getDeliveryMode() == 1
        properties.getExpiration() == 'test-expiration'
        properties.getHeaders() == ['foo': 'bar']
        properties.getMessageId() == 'test-messageId'
        properties.getPriority() == 2
        properties.getReplyTo() == 'test-replyTo'
        properties.getType() == 'test-type'
        properties.getUserId() == 'test-userId'
    }
}
