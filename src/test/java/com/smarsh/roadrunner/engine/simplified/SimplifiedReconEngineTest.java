package com.smarsh.roadrunner.engine.simplified;

import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SimplifiedReconEngineTest {

    private SimplifiedReconEngine engine;
    private TenantContext context;

    @BeforeEach
    void setUp() {
        engine  = new SimplifiedReconEngine();
        context = new TenantContext("tenant-simplified", "user-02", "user2@example.com",
                Set.of("ROLE_USER"), "test");
    }

    @Test
    void testEngineType() {
        assertEquals("simplified", engine.getEngineType());
    }

    @Test
    void testDescription() {
        assertNotNull(engine.getDescription());
        assertFalse(engine.getDescription().isBlank());
    }

    @Test
    void testIsHealthy() {
        assertTrue(engine.isHealthy());
    }

    @Test
    void testExecuteWithDefaults() throws Exception {
        ReconResult result = engine.execute(context, new HashMap<>());

        assertNotNull(result);
        assertEquals("tenant-simplified", result.getTenantId());
        assertEquals("simplified", result.getEngineType());
        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals(50_000L, result.getProcessedCount());
        assertTrue(result.getMatchedCount() > 0);
        assertTrue(result.getSuccessRate() > 95.0);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().containsKey("batchCount"));
        assertTrue(result.getMetadata().containsKey("batchSize"));
    }

    @Test
    void testBatchingProducesCorrectBatchCount() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("recordCount", 5_000L);
        params.put("batchSize", 1_000L);

        ReconResult result = engine.execute(context, params);

        assertEquals(5_000L, result.getProcessedCount());
        assertEquals(5, result.getMetadata().get("batchCount"));
    }

    @Test
    void testCustomMatchRatio() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("recordCount", 1_000L);
        params.put("matchRatio", 0.80);

        ReconResult result = engine.execute(context, params);

        assertEquals(1_000L, result.getProcessedCount());
        assertTrue(result.getMatchedCount() <= 1_000L);
    }

    @Test
    void testReconIdIsUnique() throws Exception {
        ReconResult r1 = engine.execute(context, new HashMap<>());
        ReconResult r2 = engine.execute(context, new HashMap<>());
        assertNotEquals(r1.getReconId(), r2.getReconId());
    }
}
