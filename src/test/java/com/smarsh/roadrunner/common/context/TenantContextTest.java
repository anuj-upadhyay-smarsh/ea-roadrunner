package com.smarsh.roadrunner.common.context;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TenantContextTest {
    
    @Test
    public void testTenantContextCreation() {
        Set<String> roles = Set.of("ROLE_ADMIN", "ROLE_USER");
        TenantContext context = new TenantContext("tenant-123", "user-456", "user@example.com", roles, "production");
        
        assertEquals("tenant-123", context.getTenantId());
        assertEquals("user-456", context.getUserId());
        assertEquals("user@example.com", context.getEmail());
        assertEquals(2, context.getRoles().size());
        assertEquals("production", context.getEnvironment());
    }
    
    @Test
    public void testHasRole() {
        Set<String> roles = Set.of("ROLE_ADMIN", "ROLE_USER");
        TenantContext context = new TenantContext("tenant-123", "user-456", "user@example.com", roles, "production");
        
        assertTrue(context.hasRole("ADMIN"));
        assertTrue(context.hasRole("USER"));
        assertFalse(context.hasRole("SUPERADMIN"));
    }
    
    @Test
    public void testHasAnyRole() {
        Set<String> roles = Set.of("ROLE_ANALYST");
        TenantContext context = new TenantContext("tenant-123", "user-456", "user@example.com", roles, "production");
        
        assertTrue(context.hasAnyRole("ADMIN", "ANALYST"));
        assertFalse(context.hasAnyRole("SUPERADMIN", "VIEWER"));
    }
    
    @Test
    public void testToString() {
        Set<String> roles = Set.of("ROLE_USER");
        TenantContext context = new TenantContext("tenant-123", "user-456", "user@example.com", roles, "production");
        
        String str = context.toString();
        assertTrue(str.contains("tenant-123"));
        assertTrue(str.contains("user-456"));
    }
}
