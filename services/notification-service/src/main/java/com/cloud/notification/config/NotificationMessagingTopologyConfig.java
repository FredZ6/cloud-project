package com.cloud.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationMessagingTopologyConfig {

    @Bean
    public TopicExchange eventsExchange(@Value("${app.messaging.exchange:ecom.events}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue paymentResultQueue(@Value("${app.messaging.queues.payment-result:q.notification.payment-result}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding paymentResultBinding(
            @Qualifier("paymentResultQueue") Queue paymentResultQueue,
            TopicExchange eventsExchange,
            @Value("${app.messaging.routing-keys.payment-result:payment.*}") String routingKey) {
        return BindingBuilder.bind(paymentResultQueue).to(eventsExchange).with(routingKey);
    }
}
