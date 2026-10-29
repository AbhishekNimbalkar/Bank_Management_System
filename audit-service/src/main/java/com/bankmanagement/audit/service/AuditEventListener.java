package com.bankmanagement.audit.service;

import com.bankmanagement.audit.config.AuditQueueConfig;
import com.bankmanagement.common.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Consumes audit events; failures are retried and finally dead-lettered (see application.yml). */
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditChainService chainService;

    @RabbitListener(queues = AuditQueueConfig.QUEUE)
    public void onEvent(AuditEvent event) {
        chainService.append(event);
    }
}
