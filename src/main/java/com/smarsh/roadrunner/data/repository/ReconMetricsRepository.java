package com.smarsh.roadrunner.data.repository;

import com.smarsh.roadrunner.data.entity.ReconMetrics;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReconMetricsRepository extends MongoRepository<ReconMetrics, String> {
    
    /**
     * Find by ID and tenant_id (prevents cross-tenant access)
     */
    Optional<ReconMetrics> findByIdAndTenantId(String id, String tenantId);
    
    /**
     * Find all records for a tenant
     */
    List<ReconMetrics> findByTenantId(String tenantId);
    
    /**
     * Find records for a tenant by engine type
     */
    List<ReconMetrics> findByTenantIdAndEngineType(String tenantId, String engineType);
    
    /**
     * Find recent records for a tenant
     */
    @Query("{ 'tenantId': ?0, 'createdAt': { $gte: ?1 } }")
    List<ReconMetrics> findRecentForTenant(String tenantId, LocalDateTime since);
    
    /**
     * Count records by tenant and status
     */
    long countByTenantIdAndStatus(String tenantId, String status);
}
