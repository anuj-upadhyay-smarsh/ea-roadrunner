package com.smarsh.roadrunner.api.config;

import com.smarsh.roadrunner.common.context.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

@Configuration
public class TenantContextConfig {

    @Value("${app.environment:production}")
    private String environment;

    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public TenantContext tenantContext() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("Request context not available");
        }

        String tenantId = attrs.getRequest().getHeader("X-Tenant-ID");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Missing X-Tenant-ID header");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return TenantContext.from(tenantId, auth, environment);
        }

        // No authenticated principal. Allowed only when security is disabled (e.g. dev profile).
        if (securityEnabled) {
            throw new IllegalStateException("User not authenticated");
        }
        return new TenantContext(tenantId, "anonymous", "anonymous@local", Set.of(), environment);
    }
}
