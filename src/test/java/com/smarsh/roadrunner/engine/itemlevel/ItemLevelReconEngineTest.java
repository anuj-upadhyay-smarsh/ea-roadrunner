package com.smarsh.roadrunner.engine.itemlevel;

import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ItemLevelReconEngineTest {

    private ItemLevelReconEngine engine;
    private TenantContext context;

    @BeforeEach
    void setUp() {
        engine  = new ItemLevelReconEngine();
        context = new TenantContext("tenant-il", "user-04", "user4@example.com",
                Set.of("ROLE_USER"), "test");
    }

    @Test
    void testEngineType() {
        assertEquals("item-level", engine.getEngineType());
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
        assertEquals("tenant-il", result.getTenantId());
        assertEquals("item-level", result.getEngineType());
        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals(100_000L, result.getProcessedCount());
        assertTrue(result.getMatchedCount() > 0);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().containsKey("missingItems"));
        assertTrue(result.getMetadata().containsKey("extraItems"));
        assertTrue(result.getMetadata().containsKey("mismatchItems"));
        assertTrue(result.getMetadata().containsKey("totalFieldsCompared"));
    }

    @Test
    void testFieldsComparedIsItemsTimesFieldsPerItem() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("itemCount", 1_000L);
        params.put("fieldsPerItem", 10L);

        ReconResult result = engine.execute(context, params);

        assertEquals(10_000L, result.getMetadata().get("totalFieldsCompared"));
    }

    @Test
    void testSuccessRateIsHighWithDefaultRatios() throws Exception {
        ReconResult result = engine.execute(context, new HashMap<>());
        assertTrue(result.getSuccessRate() > 98.0,
                "Expected success rate > 98% but got " + result.getSuccessRate());
    }

    @Test
    void testMatchedCountDoesNotExceedItemCount() throws Exception {
        ReconResult result = engine.execute(context, new HashMap<>());
        assertTrue(result.getMatchedCount() <= result.getProcessedCount());
    }

    @Test
    void testHighMismatchRatioReducesMatchRate() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("itemCount", 1_000L);
        params.put("mismatchRatio", 0.30);

        ReconResult result = engine.execute(context, params);

        assertTrue(result.getSuccessRate() < 80.0);
    }

    @Test
    void testReconIdIsUnique() throws Exception {
        ReconResult r1 = engine.execute(context, new HashMap<>());
        ReconResult r2 = engine.execute(context, new HashMap<>());
        assertNotEquals(r1.getReconId(), r2.getReconId());
    }
}
