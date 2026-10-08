package com.smarsh.roadrunner.engine.generic;

import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GenericReconEngineTest {
    
    private final GenericReconEngine engine = new GenericReconEngine();
    
    @Test
    public void testEngineType() {
        assertEquals("generic", engine.getEngineType());
    }
    
    @Test
    public void testEngineDescription() {
        assertNotNull(engine.getDescription());
        assertTrue(engine.getDescription().length() > 0);
    }
    
    @Test
    public void testIsHealthy() {
        assertTrue(engine.isHealthy());
    }
    
    @Test
    public void testExecuteRecon() throws Exception {
        TenantContext context = new TenantContext("tenant-123", "user-456", "user@example.com", 
                                                 Set.of("ROLE_USER"), "production");
        Map<String, Object> parameters = new HashMap<>();
        
        ReconResult result = engine.execute(context, parameters);
        
        assertNotNull(result);
        assertEquals("tenant-123", result.getTenantId());
        assertEquals("generic", result.getEngineType());
        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals(1000, result.getProcessedCount());
        assertEquals(980, result.getMatchedCount());
        assertTrue(result.getSuccessRate() > 0);
    }
}
