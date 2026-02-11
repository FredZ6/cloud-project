package com.cloud.payment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentMessagingTopologyConfig {

    @Bean
    public TopicExchange eventsExchange(@Value("${app.messaging.exchange:ecom.events}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange retryExchange(@Value("${app.messaging.retry-exchange:payment.retry.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange dlqExchange(@Value("${app.messaging.dlq-exchange:payment.dlq.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue inventoryReservedQueue(
            @Value("${app.messaging.queues.inventory-reserved:q.payment.inventory-reserved}") String queueName,
            @Value("${app.messaging.retry-exchange:payment.retry.exchange}") String retryExchange,
            @Value("${app.messaging.routing-keys.inventory-reserved-retry:q.payment.inventory-reserved.retry}") String retryRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", retryExchange)
                .withArgument("x-dead-letter-routing-key", retryRoutingKey)
                .build();
    }

    @Bean
    public Queue inventoryReservedRetryQueue(
            @Value("${app.messaging.queues.inventory-reserved-retry:q.payment.inventory-reserved.retry}") String queueName,
            @Value("${app.messaging.retry-ttl-ms:10000}") long ttl,
            @Value("${app.messaging.exchange:ecom.events}") String eventsExchange,
            @Value("${app.messaging.routing-keys.inventory-reserved:inventory.reserved}") String inventoryReservedRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-message-ttl", ttl)
                .withArgument("x-dead-letter-exchange", eventsExchange)
                .withArgument("x-dead-letter-routing-key", inventoryReservedRoutingKey)
                .build();
    }

    @Bean
    public Queue inventoryReservedDlqQueue(@Value("${app.messaging.queues.inventory-reserved-dlq:q.payment.inventory-reserved.dlq}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding inventoryReservedBinding(
            @Qualifier("inventoryReservedQueue") Queue inventoryReservedQueue,
            TopicExchange eventsExchange,
            @Value("${app.messaging.routing-keys.inventory-reserved:inventory.reserved}") String routingKey) {
        return BindingBuilder.bind(inventoryReservedQueue).to(eventsExchange).with(routingKey);
    }

    @Bean
    public Binding inventoryReservedRetryBinding(
            @Qualifier("inventoryReservedRetryQueue") Queue inventoryReservedRetryQueue,
            @Qualifier("retryExchange") DirectExchange retryExchange,
            @Value("${app.messaging.routing-keys.inventory-reserved-retry:q.payment.inventory-reserved.retry}") String retryRoutingKey) {
        return BindingBuilder.bind(inventoryReservedRetryQueue).to(retryExchange).with(retryRoutingKey);
    }

    @Bean
    public Binding inventoryReservedDlqBinding(
            @Qualifier("inventoryReservedDlqQueue") Queue inventoryReservedDlqQueue,
            @Qualifier("dlqExchange") DirectExchange dlqExchange,
            @Value("${app.messaging.routing-keys.inventory-reserved-dlq:q.payment.inventory-reserved.dlq}") String dlqRoutingKey) {
        return BindingBuilder.bind(inventoryReservedDlqQueue).to(dlqExchange).with(dlqRoutingKey);
    }
}
