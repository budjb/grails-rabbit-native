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
package com.budjb.rabbitmq.consumer

import com.budjb.rabbitmq.converter.ByteToObjectInput
import com.budjb.rabbitmq.converter.MessageConverterManager
import com.budjb.rabbitmq.exception.DuplicateHandlerException
import com.budjb.rabbitmq.exception.NoConverterFoundException
import com.budjb.rabbitmq.exception.MissingConfigurationException
import com.budjb.rabbitmq.exception.NoMessageHandlersDefinedException
import grails.core.GrailsApplication
import grails.util.GrailsClassUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.util.ClassUtils

class GrailsMessageConsumer extends AbstractMessageConsumer implements InitializingBean {
    /**
     * Name of the method that should handle incoming messages.
     */
    protected static final String MESSAGE_HANDLER_NAME = 'handleMessage'

    /**
     * Name of the configuration variable a consumer is expected to define.
     */
    protected static final String RABBIT_CONFIG_NAME = 'rabbitConfig'

    /**
     * Grails application bean.
     */
    @Autowired
    GrailsApplication grailsApplication

    /**
     * Message converter manager bean.
     */
    @Autowired
    MessageConverterManager messageConverterManager

    /**
     * {@inheritDoc}
     */
    ConsumerConfiguration configuration

    /**
     * List of classes the handlers of this consumer supports.
     */
    protected Map<Class<?>, MetaMethod> handlers = [:]

    /**
     * Logger.
     */
    Logger log = LoggerFactory.getLogger(getClass())

    /**
     * {@inheritDoc}
     */
    Object process(MessageContext messageContext) {
        MetaMethod handler = null
        Object body = null

        if (messageContext.getBody() != null) {
            try {
                body = messageConverterManager.convert(new ByteToObjectInput(
                    messageContext.getBody(),
                    (String) messageContext.getProperties().getContentType(),
                    getConfiguration().getConvert(),
                    handlers.keySet().toList())
                ).getResult()
            }
            catch (NoConverterFoundException ignored) {
                // There was no message converter that could convert the body.
                // This is OK. The consumer may have a byte[] or MessageContext handler.
            }
            catch (Throwable e) {
                throw new RuntimeException("unexpected exception ${e.getClass()} encountered converting incoming request with handler ${getId()}", e)
            }
        }

        if (body != null) {
            handler = findHandler(body)
        }

        if (!handler && messageContext.getBody() != null) {
            handler = findHandler(messageContext.getBody())
        }

        if (!handler) {
            handler = findHandler(messageContext)
        }

        if (!handler) {
            throw new IllegalArgumentException("could not find a message converter and message handler combination to process an incoming message")
        }

        if (handler.nativeParameterTypes.size() == 1) {
            if (MessageContext.isAssignableFrom(handler.nativeParameterTypes[0])) {
                return handler.invoke(getActualConsumer(), [messageContext] as Object[])
            }
            else {
                return handler.invoke(getActualConsumer(), [body] as Object[])
            }
        }
        else {
            return handler.invoke(getActualConsumer(), body, messageContext)
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void init() throws RuntimeException {
        loadConfiguration()
        loadHandlers()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void afterPropertiesSet() throws Exception {
        init()
    }

    /**
     * Loads the consumer's configuration.
     */
    protected void loadConfiguration() {
        if (getConfiguration() != null) {
            return
        }

        def configuration = grailsApplication.config.rabbitmq.consumers."${getName()}"

        if (configuration && Map.isInstance(configuration)) {
            this.configuration = new ConsumerConfigurationImpl((Map) configuration)
            return
        }

        configuration = GrailsClassUtils.getStaticPropertyValue(getActualConsumer().getClass(), RABBIT_CONFIG_NAME)

        if (configuration && Map.isInstance(configuration)) {
            this.configuration = new ConsumerConfigurationImpl((Map) configuration)
            return
        }

        throw new MissingConfigurationException("consumer has no configuration defined either within either its class or the application configuration")
    }

    /**
     * Returns a list of all potential handler methods.
     */
    protected void loadHandlers() {
        Object actualConsumer = getActualConsumer()

        for (MetaMethod method : actualConsumer.getMetaClass().getMethods()) {
            if (method.getName() == MESSAGE_HANDLER_NAME && method.isPublic()) {
                Class[] types = method.getNativeParameterTypes()

                if (types.size() == 1 || types.size() == 2) {
                    Class<?> type = types[0]

                    if (handlers.containsKey(type)) {
                        throw new DuplicateHandlerException(type)
                    }

                    handlers.put(type, method)
                }
            }
        }

        if (!handlers.size()) {
            throw new NoMessageHandlersDefinedException((Class<MessageConsumer>) actualConsumer.getClass())
        }
    }

    /**
     * Finds a message handler method that will accept an incoming message.
     *
     * This method will prioritize method signatures that do not include the
     * Object type, since any class will match that signature if present.
     *
     * @param object Object to find a handler for.
     * @return
     */
    protected MetaMethod findHandler(Object object) {
        MetaMethod match = handlers.find { owner.isHandlerMatch(it.key, object.getClass()) }?.value

        if (!match) {
            match = handlers.find { owner.isHandlerMatch(it.key, Object) }?.value
        }

        return match
    }

    /**
     * Determines if the given method is a match for the given message body type.
     *
     * @param method Method to check.
     * @param clazz Class of the converted message body type.
     * @return
     */
    protected boolean isHandlerMatch(Class<?> handler, Class<?> clazz) {
        if (handler == Object.class && clazz != Object.class) {
            return false
        }

        if (!ClassUtils.isAssignable(handler, clazz)) {
            return false
        }

        return true
    }
}
