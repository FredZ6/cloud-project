package com.cloud.inventory.config;

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
public class InventoryMessagingTopologyConfig {

    @Bean
    public TopicExchange eventsExchange(@Value("${app.messaging.exchange:ecom.events}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange retryExchange(@Value("${app.messaging.retry-exchange:inventory.retry.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange dlqExchange(@Value("${app.messaging.dlq-exchange:inventory.dlq.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue orderCreatedQueue(
            @Value("${app.messaging.queues.order-created:q.inventory.order-created}") String queueName,
            @Value("${app.messaging.retry-exchange:inventory.retry.exchange}") String retryExchange,
            @Value("${app.messaging.routing-keys.order-created-retry:q.inventory.order-created.retry}") String retryRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", retryExchange)
                .withArgument("x-dead-letter-routing-key", retryRoutingKey)
                .build();
    }

    @Bean
    public Queue orderCreatedRetryQueue(
            @Value("${app.messaging.queues.order-created-retry:q.inventory.order-created.retry}") String queueName,
            @Value("${app.messaging.retry-ttl-ms:10000}") long ttl,
            @Value("${app.messaging.exchange:ecom.events}") String eventsExchange,
            @Value("${app.messaging.routing-keys.order-created:order.created}") String orderCreatedRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-message-ttl", ttl)
                .withArgument("x-dead-letter-exchange", eventsExchange)
                .withArgument("x-dead-letter-routing-key", orderCreatedRoutingKey)
                .build();
    }

    @Bean
    public Queue orderCreatedDlqQueue(@Value("${app.messaging.queues.order-created-dlq:q.inventory.order-created.dlq}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Queue releaseRequestedQueue(
            @Value("${app.messaging.queues.release-requested:q.inventory.release-requested}") String queueName,
            @Value("${app.messaging.retry-exchange:inventory.retry.exchange}") String retryExchange,
            @Value("${app.messaging.routing-keys.release-requested-retry:q.inventory.release-requested.retry}") String retryRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", retryExchange)
                .withArgument("x-dead-letter-routing-key", retryRoutingKey)
                .build();
    }

    @Bean
    public Queue releaseRequestedRetryQueue(
            @Value("${app.messaging.queues.release-requested-retry:q.inventory.release-requested.retry}") String queueName,
            @Value("${app.messaging.retry-ttl-ms:10000}") long ttl,
            @Value("${app.messaging.exchange:ecom.events}") String eventsExchange,
            @Value("${app.messaging.routing-keys.release-requested:inventory.release.requested}") String releaseRequestedRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-message-ttl", ttl)
                .withArgument("x-dead-letter-exchange", eventsExchange)
                .withArgument("x-dead-letter-routing-key", releaseRequestedRoutingKey)
                .build();
    }

    @Bean
    public Queue releaseRequestedDlqQueue(@Value("${app.messaging.queues.release-requested-dlq:q.inventory.release-requested.dlq}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding orderCreatedBinding(
            @Qualifier("orderCreatedQueue") Queue orderCreatedQueue,
            TopicExchange eventsExchange,
            @Value("${app.messaging.routing-keys.order-created:order.created}") String orderCreatedRoutingKey) {
        return BindingBuilder.bind(orderCreatedQueue).to(eventsExchange).with(orderCreatedRoutingKey);
    }

    @Bean
    public Binding orderCreatedRetryBinding(
            @Qualifier("orderCreatedRetryQueue") Queue orderCreatedRetryQueue,
            @Qualifier("retryExchange") DirectExchange retryExchange,
            @Value("${app.messaging.routing-keys.order-created-retry:q.inventory.order-created.retry}") String retryRoutingKey) {
        return BindingBuilder.bind(orderCreatedRetryQueue).to(retryExchange).with(retryRoutingKey);
    }

    @Bean
    public Binding orderCreatedDlqBinding(
            @Qualifier("orderCreatedDlqQueue") Queue orderCreatedDlqQueue,
            @Qualifier("dlqExchange") DirectExchange dlqExchange,
            @Value("${app.messaging.routing-keys.order-created-dlq:q.inventory.order-created.dlq}") String dlqRoutingKey) {
        return BindingBuilder.bind(orderCreatedDlqQueue).to(dlqExchange).with(dlqRoutingKey);
    }

    @Bean
    public Binding releaseRequestedBinding(
            @Qualifier("releaseRequestedQueue") Queue releaseRequestedQueue,
            TopicExchange eventsExchange,
            @Value("${app.messaging.routing-keys.release-requested:inventory.release.requested}") String releaseRequestedRoutingKey) {
        return BindingBuilder.bind(releaseRequestedQueue).to(eventsExchange).with(releaseRequestedRoutingKey);
    }

    @Bean
    public Binding releaseRequestedRetryBinding(
            @Qualifier("releaseRequestedRetryQueue") Queue releaseRequestedRetryQueue,
            @Qualifier("retryExchange") DirectExchange retryExchange,
            @Value("${app.messaging.routing-keys.release-requested-retry:q.inventory.release-requested.retry}") String retryRoutingKey) {
        return BindingBuilder.bind(releaseRequestedRetryQueue).to(retryExchange).with(retryRoutingKey);
    }

    @Bean
    public Binding releaseRequestedDlqBinding(
            @Qualifier("releaseRequestedDlqQueue") Queue releaseRequestedDlqQueue,
            @Qualifier("dlqExchange") DirectExchange dlqExchange,
            @Value("${app.messaging.routing-keys.release-requested-dlq:q.inventory.release-requested.dlq}") String dlqRoutingKey) {
        return BindingBuilder.bind(releaseRequestedDlqQueue).to(dlqExchange).with(dlqRoutingKey);
    }
}
