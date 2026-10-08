package com.smarsh.roadrunner.engine.statecorrector;

import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StateCorrectorEngineTest {

    private StateCorrectorEngine engine;
    private TenantContext context;

    @BeforeEach
    void setUp() {
        engine  = new StateCorrectorEngine();
        context = new TenantContext("tenant-sc", "user-03", "user3@example.com",
                Set.of("ROLE_ADMIN"), "test");
    }

    @Test
    void testEngineType() {
        assertEquals("state-corrector", engine.getEngineType());
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
        assertEquals("tenant-sc", result.getTenantId());
        assertEquals("state-corrector", result.getEngineType());
        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals(10_000L, result.getProcessedCount());
        assertTrue(result.getMatchedCount() > 0);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().containsKey("staleDetected"));
        assertTrue(result.getMetadata().containsKey("corrected"));
        assertTrue(result.getMetadata().containsKey("correctionEnabled"));
    }

    @Test
    void testDryRunSkipsCorrection() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("dryRun", true);
        params.put("recordCount", 1_000L);
        params.put("staleRatio", 0.10);

        ReconResult result = engine.execute(context, params);

        assertEquals(1_000L, result.getProcessedCount());
        assertEquals(Boolean.FALSE, result.getMetadata().get("correctionEnabled"));
    }

    @Test
    void testCorrectionEnabledByDefault() throws Exception {
        ReconResult result = engine.execute(context, new HashMap<>());
        assertEquals(Boolean.TRUE, result.getMetadata().get("correctionEnabled"));
    }

    @Test
    void testHighStaleRatioReducesMatchRate() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("recordCount", 1_000L);
        params.put("staleRatio", 0.50);

        ReconResult result = engine.execute(context, params);

        assertTrue(result.getSuccessRate() < 100.0);
    }

    @Test
    void testMatchedPlusUnreconciledEqualProcessed() throws Exception {
        ReconResult result = engine.execute(context, new HashMap<>());
        assertEquals(result.getProcessedCount(),
                result.getMatchedCount() + result.getUnreconciled() + result.getErrors());
    }
}
