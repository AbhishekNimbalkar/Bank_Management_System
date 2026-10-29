package com.bankmanagement.audit.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Durable audit queue with a dead-letter queue for events that repeatedly fail to persist. */
@Configuration
public class AuditQueueConfig {

    public static final String QUEUE = "bankmanagement.audit.events";
    public static final String DLX = "bankmanagement.audit.dlx";
    public static final String DLQ = "bankmanagement.audit.events.dlq";

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
    }

    @Bean
    public Binding auditBinding(Queue auditQueue, TopicExchange auditExchange) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with("audit.#");
    }

    @Bean
    public DirectExchange auditDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding auditDeadLetterBinding(Queue auditDeadLetterQueue, DirectExchange auditDeadLetterExchange) {
        return BindingBuilder.bind(auditDeadLetterQueue).to(auditDeadLetterExchange).with(DLQ);
    }
}
