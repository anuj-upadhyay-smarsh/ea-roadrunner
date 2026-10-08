package com.smarsh.roadrunner.engine.hybrid;

import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HybridReconEngineTest {

    private HybridReconEngine engine;
    private TenantContext context;

    @BeforeEach
    void setUp() {
        engine  = new HybridReconEngine();
        context = new TenantContext("tenant-hybrid", "user-01", "user@example.com",
                Set.of("ROLE_USER"), "test");
    }

    @Test
    void testEngineType() {
        assertEquals("hybrid", engine.getEngineType());
    }

    @Test
    void testDescription() {
        String desc = engine.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isBlank());
    }

    @Test
    void testIsHealthy() {
        assertTrue(engine.isHealthy());
    }

    @Test
    void testExecuteWithDefaults() throws Exception {
        ReconResult result = engine.execute(context, new HashMap<>());

        assertNotNull(result);
        assertEquals("tenant-hybrid", result.getTenantId());
        assertEquals("hybrid", result.getEngineType());
        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals(5_000L, result.getProcessedCount());
        assertTrue(result.getMatchedCount() > 0);
        assertTrue(result.getMatchedCount() <= result.getProcessedCount());
        assertTrue(result.getSuccessRate() > 90.0);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().containsKey("exactMatched"));
        assertTrue(result.getMetadata().containsKey("fuzzyMatched"));
        assertTrue(result.getMetadata().containsKey("thresholdMatched"));
    }

    @Test
    void testExecuteWithCustomParams() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("recordCount", 10_000L);
        params.put("fuzzyTolerance", 0.90);
        params.put("thresholdPct", 0.01);

        ReconResult result = engine.execute(context, params);

        assertEquals(10_000L, result.getProcessedCount());
        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertTrue(result.getMatchedCount() > 0);
    }

    @Test
    void testMatchedNeverExceedsProcessed() throws Exception {
        ReconResult result = engine.execute(context, new HashMap<>());
        assertTrue(result.getMatchedCount() + result.getUnreconciled() <= result.getProcessedCount());
    }

    @Test
    void testReconIdIsUnique() throws Exception {
        ReconResult r1 = engine.execute(context, new HashMap<>());
        ReconResult r2 = engine.execute(context, new HashMap<>());
        assertNotEquals(r1.getReconId(), r2.getReconId());
    }
}
