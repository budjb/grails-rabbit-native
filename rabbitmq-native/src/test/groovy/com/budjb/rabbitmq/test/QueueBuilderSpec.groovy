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
package com.budjb.rabbitmq.test

import com.budjb.rabbitmq.connection.ConnectionContext
import com.budjb.rabbitmq.connection.ConnectionManager
import com.budjb.rabbitmq.exception.ContextNotFoundException
import com.budjb.rabbitmq.exception.InvalidConfigurationException
import com.budjb.rabbitmq.queuebuilder.QueueBuilder
import com.budjb.rabbitmq.queuebuilder.QueueBuilderImpl
import com.rabbitmq.client.Channel
import grails.config.Config
import grails.core.GrailsApplication
import org.grails.config.CodeGenConfig
import org.grails.config.PropertySourcesConfig
import spock.lang.Specification

class QueueBuilderSpec extends Specification {
    QueueBuilder queueBuilder
    GrailsApplication grailsApplication
    ConnectionManager connectionManager

    def setup() {
        grailsApplication = Mock(GrailsApplication)

        connectionManager = Mock(ConnectionManager)

        queueBuilder = new QueueBuilderImpl()
        queueBuilder.grailsApplication = grailsApplication
        queueBuilder.connectionManager = connectionManager
    }

    def 'If a queue is missing a name, an exception is thrown'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    queue(durable: true)
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getConnection(_) >> connectionContext

        when:
        queueBuilder.configure()

        then:
        RuntimeException e = thrown()
        e.message == 'queue name is required'
    }

    def 'If a queue\'s named connection is not found, an exception is thrown'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    queue(name: 'test-queue', connection: 'test-connection', durable: true)
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        connectionManager.getContext('test-connection') >> {
            throw new ContextNotFoundException("no connection context with name 'test-connection' is configured")
        }

        when:
        queueBuilder.configure()

        then:
        ContextNotFoundException e = thrown()
        e.message == "no connection context with name 'test-connection' is configured".toString()
    }

    def 'If the default connection is not found, an exception is thrown'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    queue(name: 'test-queue', durable: true)
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        connectionManager.getContext() >> {
            throw new ContextNotFoundException("no default connection context is configured")
        }

        when:
        queueBuilder.configure()

        then:
        ContextNotFoundException e = thrown()
        e.message == 'no default connection context is configured'
    }

    def 'Ensure setConnectionManager(ConnectionManager) sets the property correctly'() {
        setup:
        ConnectionManager connectionManager = Mock(ConnectionManager)

        when:
        queueBuilder.setConnectionManager(connectionManager)

        then:
        queueBuilder.connectionManager == connectionManager
    }

    def 'Ensure setGrailsApplication(GrailsApplication) sets the property correctly'() {
        setup:
        GrailsApplication grailsApplication = Mock(GrailsApplication)

        when:
        queueBuilder.setGrailsApplication(grailsApplication)

        then:
        queueBuilder.grailsApplication == grailsApplication
    }

    def 'Test queue creation on a single connection'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    queue(name: 'test-queue-1', durable: true)
                    exchange(name: 'test-exchange-1', type: 'topic', durable: true) {
                        queue(name: 'test-queue-2', durable: true, binding: 'test.binding.#')
                    }
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext() >> connectionContext

        when:
        queueBuilder.configure()

        then:
        1 * channel.queueDeclare('test-queue-1', true, false, false, [:])
        1 * channel.exchangeDeclare('test-exchange-1', 'topic', true, false, [:])
        1 * channel.queueDeclare('test-queue-2', true, false, false, [:])
        1 * channel.queueBind('test-queue-2', 'test-exchange-1', 'test.binding.#')
    }

    def 'Test configuration containing a connection'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    connection('secondaryConnection') {
                        exchange(name: 'test-exchange', type: 'topic', durable: true) {
                            queue(name: 'test-queue', durable: true, binding: 'test.binding.#')
                        }
                    }
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel1 = Mock(Channel)
        channel1.isOpen() >> true
        Channel channel2 = Mock(Channel)
        channel2.isOpen() >> true
        ConnectionContext connectionContext1 = Mock(ConnectionContext)
        connectionContext1.createChannel() >> channel1
        ConnectionContext connectionContext2 = Mock(ConnectionContext)
        connectionContext2.createChannel() >> channel2
        connectionManager.getContext() >> connectionContext1
        connectionManager.getContext('secondaryConnection') >> connectionContext2

        when:
        queueBuilder.configure()

        then:
        1 * channel2.exchangeDeclare('test-exchange', 'topic', true, false, [:])
        1 * channel2.queueDeclare('test-queue', true, false, false, [:])
        1 * channel2.queueBind('test-queue', 'test-exchange', 'test.binding.#')

        0 * connectionContext1.createChannel() >> channel1
        0 * channel1.close()
        3 * channel2.close()
    }

    def 'Validate all queue options are respected'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    queue(
                        'name': 'test-queue',
                        'exchange': 'test-exchange',
                        'autoDelete': true,
                        'exclusive': true,
                        'durable': true,
                        'connection': 'test-connection',
                        'binding': 'test-binding',
                        'arguments': ['foo': 'bar']
                    )
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext('test-connection') >> connectionContext

        when:
        queueBuilder.configure()

        then:
        2 * connectionContext.createChannel() >> channel
        1 * channel.queueDeclare('test-queue', true, true, true, ['foo': 'bar'])
        1 * channel.queueBind('test-queue', 'test-exchange', 'test-binding')
        2 * channel.close()
    }

    def 'If a queue binding is empty or null, test the proper binding is defined'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    queue(
                        'name': 'test-queue',
                        'exchange': 'test-exchange',
                        'autoDelete': true,
                        'exclusive': true,
                        'durable': true,
                        'connection': 'test-connection',
                    )
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext('test-connection') >> connectionContext

        when:
        queueBuilder.configure()

        then:
        1 * channel.queueBind('test-queue', 'test-exchange', '')
    }

    def 'If a queue binding is a Map, test the header type binding'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    queue(
                        'name': 'test-queue',
                        'exchange': 'test-exchange',
                        'autoDelete': true,
                        'exclusive': true,
                        'durable': true,
                        'connection': 'test-connection',
                        'binding': ['foo': 'bar'],
                        'match': 'all',
                        'arguments': ['foo': 'bar']
                    )
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext('test-connection') >> connectionContext

        when:
        queueBuilder.configure()

        then:
        2 * connectionContext.createChannel() >> channel
        1 * channel.queueDeclare('test-queue', true, true, true, ['foo': 'bar'])
        1 * channel.queueBind('test-queue', 'test-exchange', '', ['foo': 'bar', 'x-match': 'all'])
        2 * channel.close()
    }

    def 'If a queue binding is a Map but the match is incorrect, the queue binding is not created'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    queue(
                        'name': 'test-queue',
                        'exchange': 'test-exchange',
                        'binding': ['foo': 'bar'],
                        'match': 'foobar'
                    )
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext() >> connectionContext

        when:
        queueBuilder.configure()

        then:
        thrown InvalidConfigurationException
        0 * channel.queueBind(*_)
    }

    def 'Validate all exchange options are respected'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    exchange(
                        'name': 'test-exchange',
                        'type': 'direct',
                        'autoDelete': true,
                        'durable': true,
                        'connection': 'test-connection',
                        'arguments': ['foo': 'bar']
                    )
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext('test-connection') >> connectionContext

        when:
        queueBuilder.configure()

        then:
        1 * connectionContext.createChannel() >> channel
        1 * channel.exchangeDeclare('test-exchange', 'direct', true, true, ['foo': 'bar'])
        1 * channel.close()
    }

    def 'If a queue declaration fails, the channel should close and not be closed'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    queue(name: 'test-queue-1', durable: true)
                    exchange(name: 'test-exchange-1', type: 'topic', durable: true) {
                        queue(name: 'test-queue-2', durable: true, binding: 'test.binding.#')
                    }
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> false
        channel.queueDeclare(*_) >> { throw new IOException() }
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext() >> connectionContext

        when:
        queueBuilder.configure()

        then:
        thrown IOException
        0 * channel.close()
    }

    def 'If an exchange is declared inside an exchange block, a RuntimeException is thrown'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    exchange(name: 'test-exchange-1', type: 'topic', durable: true) {
                        exchange(name: 'test-exchange-2', type: 'topic', durable: true)
                    }
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext(_) >> connectionContext

        when:
        queueBuilder.configure()

        then:
        Throwable e = thrown()
        e.message == 'cannot declare an exchange within another exchange'
    }

    def 'If an exchange is declared without a name, a RuntimeException is thrown'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    exchange(type: 'topic', durable: true)
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext(_) >> connectionContext

        when:
        queueBuilder.configure()

        then:
        InvalidConfigurationException e = thrown()
        e.message == 'exchange name is required'
    }

    def 'If an exchange is declared without a type, a RuntimeException is thrown'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    exchange(name: 'test-exchange', durable: true)
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext(_) >> connectionContext

        when:
        queueBuilder.configure()

        then:
        RuntimeException e = thrown()
        e.message == 'exchange type is required'
    }

    def 'If an exchange\'s named connection is not found, a RunTime exception is thrown'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    exchange(name: 'test-exchange', type: 'topic', connection: 'test-connection', durable: true)
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        connectionManager.getContext(_) >> {
            throw new ContextNotFoundException("no connection context with name 'test-connection' is configured")
        }

        when:
        queueBuilder.configure()

        then:
        ContextNotFoundException e = thrown()
        e.message == "no connection context with name 'test-connection' is configured"
    }

    def 'If an exchange\'s default connection is not found, a RunTime exception is thrown'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    exchange(name: 'test-exchange', type: 'topic', durable: true)
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        connectionManager.getContext() >> {
            throw new ContextNotFoundException("no default connection context is configured")
        }

        when:
        queueBuilder.configure()

        then:
        Throwable e = thrown()
        e.message == "no default connection context is configured"
    }

    def 'If an exchange declaration fails, the channel should already be closed'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    exchange(name: 'test-exchange-1', type: 'topic', durable: true)
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> false
        channel.exchangeDeclare(*_) >> { throw new IOException() }
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext() >> connectionContext

        when:
        queueBuilder.configure()

        then:
        thrown IOException
        0 * channel.close()
    }

    def 'If a connection is started inside a connection, a RuntimeException is thrown'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    connection('test-connection') {
                        connection('test-connection') {
                            queue(name: 'test-queue')
                        }
                    }
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext(_) >> connectionContext

        when:
        queueBuilder.configure()

        then:
        Throwable e = thrown()
        e.message == 'unexpected connection in the queue configuration; the connection test-connection is already open'
    }

    def 'Ensure the type aliases work correctly'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': {
                    exchange(name: 'test-exchange', type: direct)
                    exchange(name: 'test-exchange-2', type: headers, match: any)
                    exchange(name: 'test-exchange-3', type: topic)
                    exchange(name: 'test-exchange-4', type: fanout)
                    exchange(name: 'test-exchange-5', type: headers, match: all)
                }
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext() >> connectionContext

        when:
        queueBuilder.configure()

        then:
        notThrown Throwable
    }

    def 'When a YAML configuration is used and the queues section is not a list, an exception is thrown'() {
        setup:
        def input = new ByteArrayInputStream('''
rabbitmq:
    queues:
        testqueue:
        '''.getBytes())
        CodeGenConfig config = new CodeGenConfig()
        config.loadYml(input)

        grailsApplication.getConfig() >> new PropertySourcesConfig(config)

        when:
        queueBuilder.configure()

        then:
        thrown IllegalArgumentException
    }

    def 'When a YAML configuration is used and the exchanges section is not a list, an exception is thrown'() {
        setup:
        def input = new ByteArrayInputStream('''
rabbitmq:
    exchanges:
        testexchange:
        '''.getBytes())
        CodeGenConfig config = new CodeGenConfig()
        config.loadYml(input)

        grailsApplication.getConfig() >> new PropertySourcesConfig(config)

        when:
        queueBuilder.configure()

        then:
        thrown IllegalArgumentException
    }

    def 'When a YAML configuration is used, queues are created properly'() {
        setup:
        def input = new ByteArrayInputStream('''
rabbitmq:
    queues:
      - name: test.queue
        durable: true

      - name: test.queue.2
        exclusive: true
        '''.getBytes())
        CodeGenConfig config = new CodeGenConfig()
        config.loadYml(input)

        grailsApplication.getConfig() >> new PropertySourcesConfig(config)
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext() >> connectionContext

        when:
        queueBuilder.configure()

        then:
        1 * channel.queueDeclare('test.queue', true, false, false, [:])
        1 * channel.queueDeclare('test.queue.2', false, true, false, [:])
    }

    def 'When a YAML configuration is used, exchanges are created properly'() {
        setup:
        def input = new ByteArrayInputStream('''
rabbitmq:
    exchanges:
      - name: test.exchange
        type: topic
        durable: true

      - name: test.exchange.2
        type: direct
        autoDelete: true
        '''.getBytes())
        CodeGenConfig config = new CodeGenConfig()
        config.loadYml(input)

        grailsApplication.getConfig() >> new PropertySourcesConfig(config)
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext() >> connectionContext

        when:
        queueBuilder.configure()

        then:
        1 * channel.exchangeDeclare('test.exchange', 'topic', true, false, [:])
        1 * channel.exchangeDeclare('test.exchange.2', 'direct', false, true, [:])
    }

    def 'When a YAML configuration is used, queues and exchanges are created and bound properly'() {
        setup:
        def input = new ByteArrayInputStream('''
rabbitmq:
    queues:
      - name: test.queue
        durable: true
        exchange: test.exchange
        binding: '#'

      - name: test.queue.2
        exclusive: true
        exchange: test.exchange.2
        binding: route

    exchanges:
      - name: test.exchange
        type: topic
        durable: true

      - name: test.exchange.2
        type: direct
        autoDelete: true
        '''.getBytes())
        CodeGenConfig config = new CodeGenConfig()
        config.loadYml(input)

        grailsApplication.getConfig() >> new PropertySourcesConfig(config)
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext() >> connectionContext

        when:
        queueBuilder.configure()

        then:
        1 * channel.exchangeDeclare('test.exchange', 'topic', true, false, [:])
        1 * channel.exchangeDeclare('test.exchange.2', 'direct', false, true, [:])
        1 * channel.queueDeclare('test.queue', true, false, false, [:])
        1 * channel.queueDeclare('test.queue.2', false, true, false, [:])
        1 * channel.queueBind('test.queue', 'test.exchange', '#')
        1 * channel.queueBind('test.queue.2', 'test.exchange.2', 'route')
    }

    def 'When a groovy configuration is used, queues and exchanges are created and bound properly'() {
        setup:
        Config configuration = new PropertySourcesConfig()
        configuration.putAll([
            'rabbitmq': [
                'queues': [
                    [
                        name: 'test.queue',
                        durable: true,
                        exchange: 'test.exchange',
                        binding: '#'
                    ],
                    [
                        name: 'test.queue.2',
                        exclusive: true,
                        exchange: 'test.exchange.2',
                        binding: 'route'
                    ]
                ],
                'exchanges': [
                    [
                        name: 'test.exchange',
                        type: 'topic',
                        durable: true
                    ],
                    [
                        name: 'test.exchange.2',
                        type: 'direct',
                        autoDelete: true
                    ]
                ]
            ]
        ])
        grailsApplication.getConfig() >> configuration
        Channel channel = Mock(Channel)
        channel.isOpen() >> true
        ConnectionContext connectionContext = Mock(ConnectionContext)
        connectionContext.createChannel() >> channel
        connectionManager.getContext() >> connectionContext

        when:
        queueBuilder.configure()

        then:
        1 * channel.exchangeDeclare('test.exchange', 'topic', true, false, [:])
        1 * channel.exchangeDeclare('test.exchange.2', 'direct', false, true, [:])
        1 * channel.queueDeclare('test.queue', true, false, false, [:])
        1 * channel.queueDeclare('test.queue.2', false, true, false, [:])
        1 * channel.queueBind('test.queue', 'test.exchange', '#')
        1 * channel.queueBind('test.queue.2', 'test.exchange.2', 'route')
    }
}
