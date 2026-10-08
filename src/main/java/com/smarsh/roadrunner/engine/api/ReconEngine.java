package com.smarsh.roadrunner.engine.api;

import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import java.util.Map;

/**
 * Interface for all recon engine implementations.
 * Each engine handles a specific reconciliation type.
 */
public interface ReconEngine {
    
    /**
     * Get the engine type identifier
     */
    String getEngineType();
    
    /**
     * Get human-readable description
     */
    String getDescription();
    
    /**
     * Execute reconciliation with given parameters
     */
    ReconResult execute(TenantContext tenantContext, Map<String, Object> parameters) throws Exception;
    
    /**
     * Check if engine is available/healthy
     */
    boolean isHealthy();
}
