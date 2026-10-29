package com.bankmanagement.common.audit;

import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.common.web.ClientIp;
import com.bankmanagement.common.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes audit events. Inside a transaction the event is sent only after commit, so rolled-back
 * work is never audited as done.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditPublisher {

    private static final int MAX_DETAIL = 500;

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name}")
    private String serviceName;

    /** Audits an action by the current authenticated user, in the tenant the request targets. */
    public void record(String module, String action, String detail) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        publish(SecurityUtils.currentTenant(), user.userId().toString(), user.name(), user.role(),
                user.impersonatorId() == null ? null : user.impersonatorId().toString(), module, action, detail);
    }

    /** Audits an action whose actor is not (yet) authenticated, e.g. a failed sign-in. */
    public void publish(String tenantCode, String actorId, String actorName, String actorRole,
                        String impersonatorId, String module, String action, String detail) {
        String ip = SecurityUtils.currentRequest().map(ClientIp::resolve).orElse(null);
        AuditEvent event = new AuditEvent(UUID.randomUUID(), tenantCode, actorId, actorName, actorRole,
                impersonatorId, module, action, truncate(detail), ip, MDC.get(RequestIdFilter.MDC_KEY),
                serviceName, Instant.now());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(AuditEvent event) {
        try {
            rabbitTemplate.convertAndSend(AmqpConfig.AUDIT_EXCHANGE, AmqpConfig.AUDIT_ROUTING_KEY, event);
        } catch (AmqpException ex) {
            // Never fail the business operation because the broker is down; the event is logged instead.
            log.error("AUDIT_EVENT_NOT_DELIVERED {}", event, ex);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_DETAIL ? value.substring(0, MAX_DETAIL) : value;
    }
}
