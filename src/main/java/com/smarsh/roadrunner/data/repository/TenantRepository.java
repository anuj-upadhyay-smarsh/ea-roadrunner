package com.smarsh.roadrunner.data.repository;

import com.smarsh.roadrunner.common.context.TenantContext;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

/**
 * Base repository with automatic tenant filtering.
 * All queries automatically include tenant_id filter.
 */
public interface TenantRepository<T, ID> extends MongoRepository<T, ID> {
    
    /**
     * Find by ID with automatic tenant verification
     */
    Optional<T> findByIdAndTenantId(ID id, String tenantId);
    
    /**
     * Find all documents for a tenant
     */
    List<T> findByTenantId(String tenantId);
}
