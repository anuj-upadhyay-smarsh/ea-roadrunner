package com.smarsh.roadrunner.common.context;

import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class TenantContext {
    private final String tenantId;
    private final String userId;
    private final String email;
    private final Set<String> roles;
    private final String environment;

    public TenantContext(String tenantId, String userId, String email, Set<String> roles, String environment) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.email = email;
        this.roles = roles;
        this.environment = environment;
    }

    public static TenantContext from(String tenantId, Authentication authentication, String environment) {
        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        return new TenantContext(
            tenantId,
            authentication.getName(),
            (String) authentication.getPrincipal(),
            roles,
            environment
        );
    }

    public boolean hasRole(String role) {
        return roles.contains("ROLE_" + role);
    }

    public boolean hasAnyRole(String... checkRoles) {
        return java.util.Arrays.stream(checkRoles).anyMatch(this::hasRole);
    }

    @Override
    public String toString() {
        return "TenantContext{" +
            "tenantId='" + tenantId + '\'' +
            ", userId='" + userId + '\'' +
            ", roles=" + roles +
            '}';
    }
}
