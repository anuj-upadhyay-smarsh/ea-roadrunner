package com.smarsh.roadrunner.infra.audit;

import com.smarsh.roadrunner.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final TenantContext tenantContext;

    @Before("@annotation(com.smarsh.roadrunner.infra.audit.Audited)")
    public void auditMutation(JoinPoint joinPoint) {
        String method = joinPoint.getSignature().getName();
        LocalDateTime timestamp = LocalDateTime.now();

        String tenantId = "unknown";
        String userId = "unknown";
        try {
            // tenantContext is a request-scoped proxy; resolution fails outside a request
            // (e.g. async/scheduled threads). Audit logging must not break the call.
            tenantId = tenantContext.getTenantId();
            userId = tenantContext.getUserId();
        } catch (RuntimeException ignored) {
            // no request-bound tenant context available
        }

        log.info("AUDIT: tenant={} user={} action={} timestamp={}",
                tenantId, userId, method, timestamp);
    }
}
