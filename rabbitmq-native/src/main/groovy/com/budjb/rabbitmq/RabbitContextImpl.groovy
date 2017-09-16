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
package com.budjb.rabbitmq

import com.budjb.rabbitmq.connection.ConnectionConfiguration
import com.budjb.rabbitmq.connection.ConnectionManager
import com.budjb.rabbitmq.consumer.ConsumerManager
import com.budjb.rabbitmq.converter.MessageConverter
import com.budjb.rabbitmq.converter.MessageConverterManager
import com.budjb.rabbitmq.event.RabbitContextStartedEvent
import com.budjb.rabbitmq.event.RabbitContextStartingEvent
import com.budjb.rabbitmq.queuebuilder.QueueBuilder
import com.budjb.rabbitmq.report.ConnectionReport
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher

class RabbitContextImpl implements RabbitContext {
    /**
     * Spring application event publisher.
     */
    @Autowired
    ApplicationEventPublisher applicationEventPublisher

    /**
     * Message converter manager.
     */
    @Autowired
    MessageConverterManager messageConverterManager

    /**
     * Connection manager.
     */
    @Autowired
    ConnectionManager connectionManager

    /**
     * Consumer manager.
     */
    @Autowired
    ConsumerManager consumerManager

    /**
     * Queue builder.
     */
    @Autowired
    QueueBuilder queueBuilder

    /**
     * Loads the configuration and registers any consumers or converters.
     */
    @Override
    void load() {
        messageConverterManager.load()
        connectionManager.load()
        consumerManager.load()
    }

    /**
     * Starts all connections, creates exchanges and queues, and starts all consumers.
     */
    @Override
    void start() {
        applicationEventPublisher.publishEvent(new RabbitContextStartingEvent(this))

        startConnections()
        createExchangesAndQueues()
        startConsumers()

        applicationEventPublisher.publishEvent(new RabbitContextStartedEvent(this))
    }

    /**
     * Stops all consumers and connections.
     */
    @Override
    void stop() {
        stopConnections() // this will also stop consumers
    }

    /**
     * Stops all consumers and connections, reloads the application configurations,
     * and finally starts all connections and consumers.
     *
     * Calling this method will retain any manually registered consumers and connections
     * unless they are overridden by the configuration.
     */
    @Override
    void reload() {
        stop()
        load()
        start()
    }

    /**
     * Stops all consumers and connections, and removes any registered contexts.
     */
    @Override
    void reset() {
        stop()
        messageConverterManager.reset()
        consumerManager.reset()
        connectionManager.reset()
    }

    /**
     * Starts all consumers.
     */
    @Override
    void startConsumers() {
        consumerManager.start()
    }

    /**
     * Starts all consumers associated with the given connection name.
     */
    @Override
    void startConsumers(String connectionName) {
        consumerManager.start(connectionManager.getContext(connectionName))
    }

    /**
     * Starts a consumer based on its name.
     */
    @Override
    void startConsumer(String name) {
        consumerManager.start(name)
    }

    /**
     * Stops all consumers.
     */
    @Override
    void stopConsumers() {
        consumerManager.stop()
    }

    /**
     * Stops all consumers associated with the given connection name.
     */
    @Override
    void stopConsumers(String connectionName) {
        consumerManager.stop(connectionManager.getContext(connectionName))
    }

    /**
     * Stops a consumer based on its name.
     *
     * @param name
     */
    @Override
    void stopConsumer(String name) {
        consumerManager.stop(name)
    }

    /**
     * Registers a consumer.
     *
     * @param candidate
     */
    @Override
    void registerConsumer(Object consumer) {
        consumerManager.register(consumerManager.createContext(consumer))
    }

    /**
     * Registers a message converter.
     *
     * @param converter
     */
    @Override
    void registerMessageConverter(MessageConverter converter) {
        messageConverterManager.register(converter)
    }

    /**
     * Starts all connections.
     */
    @Override
    void startConnections() {
        connectionManager.start()
    }

    /**
     * Starts the connection with the given name.
     *
     * @param name
     */
    @Override
    void startConnection(String name) {
        connectionManager.start(name)
    }

    /**
     * Stops all connections.
     *
     * This will also stop all consumers.
     */
    @Override
    void stopConnections() {
        stopConsumers()
        connectionManager.stop()
    }

    /**
     * Stops the connection with the given name.
     *
     * This will also stop all consumers using the connection.
     *
     * @param name
     */
    @Override
    void stopConnection(String name) {
        stopConsumers(name)
        connectionManager.stop(name)
    }

    /**
     * Registers a new connection.
     *
     * @param configuration
     */
    @Override
    void registerConnection(ConnectionConfiguration configuration) {
        connectionManager.register(connectionManager.createContext(configuration))
    }

    /**
     * Creates a RabbitMQ Channel with the default connection.
     *
     * @return
     */
    @Override
    Channel createChannel() {
        return connectionManager.createChannel()
    }

    /**
     * Creates a RabbitMQ Channel with the specified connection.
     *
     * @return
     */
    @Override
    Channel createChannel(String connectionName) {
        return connectionManager.createChannel(connectionName)
    }

    /**
     * Returns the RabbitMQ Connection associated with the default connection.
     *
     * @return
     */
    @Override
    Connection getConnection() {
        return connectionManager.getConnection()
    }

    /**
     * Returns the RabbitMQ Connection with the specified connection name.
     *
     * @param name
     * @return
     */
    @Override
    Connection getConnection(String name) {
        return connectionManager.getConnection(name)
    }

    /**
     * Creates any configured exchanges and queues.
     */
    @Override
    void createExchangesAndQueues() {
        queueBuilder.configure()
    }

    /**
     * Get the overall state of consumers and connections.
     *
     * @return
     */
    @Override
    RunningState getRunningState() {
        RunningState connectionState = connectionManager.getRunningState()
        RunningState consumerState = consumerManager.getRunningState()

        if (connectionState == RunningState.STOPPED) {
            if (consumerState != RunningState.STOPPED) {
                consumerManager.stop()
            }
            return RunningState.STOPPED
        }

        if (consumerState == RunningState.SHUTTING_DOWN) {
            return RunningState.SHUTTING_DOWN
        }

        return RunningState.RUNNING
    }

    /**
     * Perform a graceful shutdown of consumers and then disconnects.
     *
     * This method blocks until the full shutdown is complete.
     */
    @Override
    void shutdown() {
        consumerManager.shutdown()
        connectionManager.stop()
    }

    /**
     * Generates a report about all connections and consumers.
     *
     * @return
     */
    @Override
    List<ConnectionReport> getStatusReport() {
        return connectionManager.getContexts().collect {
            ConnectionReport report = it.getStatusReport()

            report.consumers = consumerManager.getContexts(it)*.getStatusReport()

            return report
        }
    }
}
