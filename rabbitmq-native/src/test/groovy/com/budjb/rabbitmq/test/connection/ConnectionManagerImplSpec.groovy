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
package com.budjb.rabbitmq.test.connection

import com.budjb.rabbitmq.connection.ConnectionBuilder
import com.budjb.rabbitmq.connection.ConnectionContext
import com.budjb.rabbitmq.connection.ConnectionManagerImpl
import com.budjb.rabbitmq.event.ConnectionManagerStartedEvent
import com.budjb.rabbitmq.event.ConnectionManagerStartingEvent
import com.budjb.rabbitmq.event.ConnectionManagerStoppedEvent
import com.budjb.rabbitmq.event.ConnectionManagerStoppingEvent
import com.budjb.rabbitmq.exception.ContextNotFoundException
import com.budjb.rabbitmq.exception.InvalidConfigurationException
import com.budjb.rabbitmq.exception.MissingConfigurationException
import grails.config.Config
import grails.core.GrailsApplication
import org.grails.config.PropertySourcesConfig
import org.slf4j.Logger
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

class ConnectionManagerImplSpec extends Specification {
    GrailsApplication grailsApplication
    ConnectionBuilder connectionBuilder
    ConnectionManagerImpl connectionManager
    ApplicationEventPublisher applicationEventPublisher

    def setup() {
        grailsApplication = Mock(GrailsApplication)
        connectionBuilder = Mock(ConnectionBuilder)
        applicationEventPublisher = Mock(ApplicationEventPublisher)

        connectionManager = new ConnectionManagerImpl()
        connectionManager.grailsApplication = grailsApplication
        connectionManager.connectionBuilder = connectionBuilder
        connectionManager.applicationEventPublisher = applicationEventPublisher
    }

    def 'If no connection configuration is missing, a MissingConfigurationException should be thrown'() {
        setup:
        grailsApplication.getConfig() >> new PropertySourcesConfig()

        when:
        connectionManager.load()

        then:
        thrown MissingConfigurationException
    }

    def 'If the connection stanza is not a map or closure, an InvalidConfigurationException should be thrown'() {
        setup:
        Config config = new PropertySourcesConfig()
        config.putAll([
            'rabbitmq': [
                'connection': 'foobar'
            ]
        ])
        grailsApplication.getConfig() >> config

        when:
        connectionManager.load()

        then:
        thrown InvalidConfigurationException
    }

    def 'Legacy map configuration test'() {
        setup:
        Config config = new PropertySourcesConfig()
        config.putAll([
            'rabbitmq': [
                'connection': [
                    'name'    : 'myConnection',
                    'host'    : 'test.budjb.com',
                    'username': 'test-user',
                    'password': 'test-password'
                ]
            ]
        ])
        grailsApplication.getConfig() >> config

        when:
        connectionManager.load()

        then:
        connectionManager.getContexts().size() == 1
        connectionManager.getContext('myConnection') != null
    }

    def 'Legacy closure configuration test'() {
        setup:
        Config config = new PropertySourcesConfig()
        config.putAll([
            'rabbitmq': [
                'connection': {
                    connection(
                        'name': 'primaryConnection',
                        'isDefault': true,
                        'host': 'test.budjb.com',
                        'username': 'test-user',
                        'password': 'test-password'
                    )
                    connection(
                        'name': 'secondaryConnection',
                        'host': 'foo.budjb.com',
                        'username': 'test-user',
                        'password': 'test-password'
                    )
                }
            ]
        ])
        grailsApplication.getConfig() >> config

        when:
        connectionManager.load()

        then:
        1 * connectionBuilder.loadConnectionContexts((Closure) _)
    }

    def 'If a connection is registered as a default connection while another default connection is already registered, an InvalidConfigurationException is thrown'() {
        setup:
        ConnectionContext context1 = Mock(ConnectionContext)
        context1.getId() >> "connection1"
        context1.getIsDefault() >> true

        ConnectionContext context2 = Mock(ConnectionContext)
        context2.getId() >> "connection2"
        context2.getIsDefault() >> true

        connectionManager.register(context1)

        when:
        connectionManager.register(context2)

        then:
        thrown InvalidConfigurationException
    }

    def 'If no contexts are registered and the manager is started, a warning should be logged'() {
        setup:
        Logger log = Mock(Logger)
        connectionManager.log = log

        when:
        connectionManager.start()

        then:
        1 * log.warn("not starting connections because no RabbitMQ connections were configured")
        0 * _
    }

    def 'If only one context is registered, it should be forced to be the default'() {
        setup:
        ConnectionContext context = Mock(ConnectionContext)
        context.getIsDefault() >> false

        connectionManager.register(context)

        when:
        connectionManager.start()

        then:
        1 * context.setIsDefault(true)
    }

    def 'If a context is registered with the same name as another context, the old context should be stopped'() {
        setup:
        ConnectionContext context1 = Mock(ConnectionContext)
        context1.getId() >> "test-connection"

        ConnectionContext context2 = Mock(ConnectionContext)
        context2.getId() >> "test-connection"

        connectionManager.register(context1)

        when:
        connectionManager.register(context2)

        then:
        1 * context1.stop()
    }

    def 'Validate that starting a connection by its name starts the correct connection'() {
        setup:
        ConnectionContext context1 = Mock(ConnectionContext)
        ConnectionContext context2 = Mock(ConnectionContext)
        ConnectionContext context3 = Mock(ConnectionContext)

        context1.getId() >> "connection1"
        context2.getId() >> "connection2"
        context3.getId() >> "connection3"

        connectionManager.register(context1)
        connectionManager.register(context2)
        connectionManager.register(context3)

        when:
        connectionManager.start("connection2")

        then:
        0 * context1.start()
        1 * context2.start()
        0 * context3.start()
    }

    def 'Validate all contexts are started when start() is called'() {
        setup:
        ConnectionContext context1 = Mock(ConnectionContext)
        ConnectionContext context2 = Mock(ConnectionContext)
        ConnectionContext context3 = Mock(ConnectionContext)

        context1.getId() >> "connection1"
        context2.getId() >> "connection2"
        context3.getId() >> "connection3"

        connectionManager.register(context1)
        connectionManager.register(context2)
        connectionManager.register(context3)

        when:
        connectionManager.start()

        then:
        1 * context1.start()
        1 * context2.start()
        1 * context3.start()
    }

    def 'Validate that stopping a connection by its name stops the correct connection'() {
        setup:
        ConnectionContext context1 = Mock(ConnectionContext)
        ConnectionContext context2 = Mock(ConnectionContext)
        ConnectionContext context3 = Mock(ConnectionContext)

        context1.getId() >> "connection1"
        context2.getId() >> "connection2"
        context3.getId() >> "connection3"

        connectionManager.register(context1)
        connectionManager.register(context2)
        connectionManager.register(context3)

        when:
        connectionManager.stop("connection2")

        then:
        0 * context1.stop()
        1 * context2.stop()
        0 * context3.stop()
    }

    def 'Validate all contexts are stopped when stop() is called'() {
        setup:
        ConnectionContext context1 = Mock(ConnectionContext)
        ConnectionContext context2 = Mock(ConnectionContext)
        ConnectionContext context3 = Mock(ConnectionContext)

        context1.getId() >> "connection1"
        context2.getId() >> "connection2"
        context3.getId() >> "connection3"

        connectionManager.register(context1)
        connectionManager.register(context2)
        connectionManager.register(context3)

        when:
        connectionManager.stop()

        then:
        1 * context1.stop()
        1 * context2.stop()
        1 * context3.stop()
    }

    def 'Verify that all connections are stopped and removed when reset() is called'() {
        setup:
        ConnectionContext context1 = Mock(ConnectionContext)
        ConnectionContext context2 = Mock(ConnectionContext)
        ConnectionContext context3 = Mock(ConnectionContext)

        context1.getId() >> "connection1"
        context2.getId() >> "connection2"
        context3.getId() >> "connection3"

        connectionManager.register(context1)
        connectionManager.register(context2)
        connectionManager.register(context3)

        when:
        connectionManager.reset()

        then:
        1 * context1.stop()
        1 * context2.stop()
        1 * context3.stop()

        connectionManager.getContexts().size() == 0
    }

    def 'If a connection is unregistered, validate that it is first stopped'() {
        setup:
        ConnectionContext context = Mock(ConnectionContext)

        connectionManager.register(context)

        when:
        connectionManager.unregister(context)

        then:
        1 * context.stop()
        connectionManager.getContexts().size() == 0
    }

    def 'If a null connection name is passed to getConnection(String), the default connection should be returned'() {
        setup:
        ConnectionContext context1 = Mock(ConnectionContext)
        ConnectionContext context2 = Mock(ConnectionContext)

        context1.getIsDefault() >> false
        context2.getIsDefault() >> true

        connectionManager.register(context1)
        connectionManager.register(context2)

        when:
        ConnectionContext connection = connectionManager.getContext(null)

        then:
        connection == context2
    }

    def 'If a creating a channel is attempted but its connection is closed, an IllegalStateException should be thrown'() {
        setup:
        ConnectionContext context = Mock(ConnectionContext)
        context.createChannel() >> { throw new IllegalStateException() }
        context.getIsDefault() >> true
        context.getId() >> "test-connection"

        connectionManager.register(context)

        when:
        connectionManager.createChannel()

        then:
        thrown IllegalStateException

        when:
        connectionManager.createChannel("test-connection")

        then:
        thrown IllegalStateException
    }

    def 'If no default connection is registered, getContext() should throw a ContextNotFoundException'() {
        setup:
        ConnectionContext context = Mock(ConnectionContext)
        connectionManager.register(context)

        when:
        connectionManager.getContext()

        then:
        thrown ContextNotFoundException
    }

    def 'If a connection is requested by name but it is not registered, a ContextNotFoundException should be thrown'() {
        setup:
        ConnectionContext context = Mock(ConnectionContext)
        connectionManager.register(context)

        when:
        connectionManager.getContext("test-connection")

        then:
        thrown ContextNotFoundException
    }

    def 'If all connections are started while some of those connections are already started, the IllegalStateException should be swallowed'() {
        setup:
        ConnectionContext connection1 = Mock(ConnectionContext)
        ConnectionContext connection2 = Mock(ConnectionContext)

        connectionManager.register(connection1)
        connectionManager.register(connection2)

        when:
        connectionManager.start(connection1)

        then:
        1 * connection1.start()
        0 * connection2.start()

        when:
        connectionManager.start()

        then:
        notThrown IllegalStateException
        1 * connection1.start() >> { throw new IllegalStateException('already started bro') }
        1 * connection2.start()
    }

    def 'If multiple connections have the same name, only one is registered'() {
        setup:
        ConnectionContext context1 = Mock(ConnectionContext)
        ConnectionContext context2 = Mock(ConnectionContext)
        ConnectionContext context3 = Mock(ConnectionContext)

        context1.getId() >> 'foo'
        context2.getId() >> 'bar'
        context3.getId() >> 'bar'

        connectionManager.register(context1)
        connectionManager.register(context2)

        when:
        connectionManager.register(context3)

        then:
        connectionManager.getContexts().size() == 2
        1 * context2.stop()
    }

    def 'Ensure that connection manager start events are published in the correct order'() {
        setup:
        ConnectionContext context = Mock(ConnectionContext)
        context.getIsDefault() >> false

        connectionManager.register(context)

        when:
        connectionManager.start()

        then:
        1 * applicationEventPublisher.publishEvent({ it instanceof ConnectionManagerStartingEvent })
        0 * applicationEventPublisher.publishEvent({ it instanceof ConnectionManagerStartedEvent })
        0 * context.start()

        then:
        0 * applicationEventPublisher.publishEvent({ it instanceof ConnectionManagerStartedEvent })
        1 * context.start()

        then:
        1 * applicationEventPublisher.publishEvent({ it instanceof ConnectionManagerStartedEvent })
    }

    def 'Ensure that connection manager stop events are published in the correct order'() {
        setup:
        ConnectionContext context = Mock(ConnectionContext)
        context.getIsDefault() >> false

        connectionManager.register(context)

        when:
        connectionManager.stop()

        then:
        1 * applicationEventPublisher.publishEvent({ it instanceof ConnectionManagerStoppingEvent })
        0 * applicationEventPublisher.publishEvent({ it instanceof ConnectionManagerStoppedEvent })
        0 * context.stop()

        then:
        0 * applicationEventPublisher.publishEvent({ it instanceof ConnectionManagerStoppedEvent })
        1 * context.stop()

        then:
        1 * applicationEventPublisher.publishEvent({ it instanceof ConnectionManagerStoppedEvent })
    }
}
