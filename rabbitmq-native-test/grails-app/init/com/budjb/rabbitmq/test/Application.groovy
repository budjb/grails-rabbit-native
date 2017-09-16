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
package com.budjb.rabbitmq.test

import com.budjb.rabbitmq.consumer.ConsumerManager
import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import org.springframework.context.annotation.Bean

class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }

    @Bean
    ConsumerManagerCheckListener consumerManagerCheckListener(ConsumerManager consumerManager) {
        ConsumerManagerCheckListener consumerManagerCheckListener = new ConsumerManagerCheckListener()
        consumerManagerCheckListener.consumerManager = consumerManager
        return consumerManagerCheckListener
    }

    @Bean
    RabbitEventListener rabbitEventListener(ConsumerManager consumerManager) {
        return new RabbitEventListener()
    }
}
