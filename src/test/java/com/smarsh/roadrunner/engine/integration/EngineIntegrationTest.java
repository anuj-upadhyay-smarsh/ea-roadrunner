package com.smarsh.roadrunner.engine.integration;

import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import com.smarsh.roadrunner.engine.generic.GenericReconEngine;
import com.smarsh.roadrunner.engine.hybrid.HybridReconEngine;
import com.smarsh.roadrunner.engine.itemlevel.ItemLevelReconEngine;
import com.smarsh.roadrunner.engine.registry.EngineRegistry;
import com.smarsh.roadrunner.engine.simplified.SimplifiedReconEngine;
import com.smarsh.roadrunner.engine.statecorrector.StateCorrectorEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for all Phase 2 engines.
 *
 * Uses TestContainers to spin up a real MongoDB instance, verifying that each engine
 * can execute end-to-end and produce consistent, tenant-isolated results.
 */
@Testcontainers
class EngineIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    private static TenantContext tenantA;
    private static TenantContext tenantB;

    @BeforeAll
    static void setUp() {
        System.setProperty("spring.data.mongodb.uri", mongoDBContainer.getReplicaSetUrl());
        tenantA = new TenantContext("tenant-A", "userA", "a@example.com", Set.of("ROLE_USER"), "test");
        tenantB = new TenantContext("tenant-B", "userB", "b@example.com", Set.of("ROLE_USER"), "test");
    }

    // -------------------------------------------------------------------------
    // GenericReconEngine
    // -------------------------------------------------------------------------

    @Test
    void genericEngine_executesSuccessfully() throws Exception {
        GenericReconEngine engine = new GenericReconEngine();
        ReconResult result = engine.execute(tenantA, new HashMap<>());

        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals("tenant-A", result.getTenantId());
        assertEquals("generic", result.getEngineType());
        assertTrue(result.getProcessedCount() > 0);
    }

    // -------------------------------------------------------------------------
    // HybridReconEngine
    // -------------------------------------------------------------------------

    @Test
    void hybridEngine_executesSuccessfully_defaultParams() throws Exception {
        HybridReconEngine engine = new HybridReconEngine();
        ReconResult result = engine.execute(tenantA, new HashMap<>());

        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals(5_000L, result.getProcessedCount());
        assertTrue(result.getSuccessRate() > 90.0);
    }

    @Test
    void hybridEngine_tenantIsolation() throws Exception {
        HybridReconEngine engine = new HybridReconEngine();
        ReconResult resultA = engine.execute(tenantA, new HashMap<>());
        ReconResult resultB = engine.execute(tenantB, new HashMap<>());

        assertEquals("tenant-A", resultA.getTenantId());
        assertEquals("tenant-B", resultB.getTenantId());
        assertNotEquals(resultA.getReconId(), resultB.getReconId());
    }

    @Test
    void hybridEngine_threePassMetadataPresentAndConsistent() throws Exception {
        HybridReconEngine engine = new HybridReconEngine();
        Map<String, Object> params = Map.of("recordCount", 2_000L);
        ReconResult result = engine.execute(tenantA, params);

        Map<String, Object> meta = result.getMetadata();
        long exact     = (long) meta.get("exactMatched");
        long fuzzy     = (long) meta.get("fuzzyMatched");
        long threshold = (long) meta.get("thresholdMatched");

        assertEquals(result.getMatchedCount(), exact + fuzzy + threshold);
    }

    // -------------------------------------------------------------------------
    // SimplifiedReconEngine
    // -------------------------------------------------------------------------

    @Test
    void simplifiedEngine_executesSuccessfully_defaultParams() throws Exception {
        SimplifiedReconEngine engine = new SimplifiedReconEngine();
        ReconResult result = engine.execute(tenantA, new HashMap<>());

        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals(50_000L, result.getProcessedCount());
        assertTrue(result.getSuccessRate() > 95.0);
    }

    @Test
    void simplifiedEngine_batchCountMatchesExpected() throws Exception {
        SimplifiedReconEngine engine = new SimplifiedReconEngine();
        Map<String, Object> params = new HashMap<>();
        params.put("recordCount", 3_000L);
        params.put("batchSize", 500L);

        ReconResult result = engine.execute(tenantA, params);

        assertEquals(6, result.getMetadata().get("batchCount"));
        assertEquals(3_000L, result.getProcessedCount());
    }

    @Test
    void simplifiedEngine_tenantIsolation() throws Exception {
        SimplifiedReconEngine engine = new SimplifiedReconEngine();
        ReconResult rA = engine.execute(tenantA, new HashMap<>());
        ReconResult rB = engine.execute(tenantB, new HashMap<>());

        assertNotEquals(rA.getReconId(), rB.getReconId());
        assertEquals("tenant-A", rA.getTenantId());
        assertEquals("tenant-B", rB.getTenantId());
    }

    // -------------------------------------------------------------------------
    // StateCorrectorEngine
    // -------------------------------------------------------------------------

    @Test
    void stateCorrectorEngine_executesSuccessfully_defaultParams() throws Exception {
        StateCorrectorEngine engine = new StateCorrectorEngine();
        ReconResult result = engine.execute(tenantA, new HashMap<>());

        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals(10_000L, result.getProcessedCount());
        assertTrue(result.getMatchedCount() > 0);
        assertTrue((Boolean) result.getMetadata().get("correctionEnabled"));
    }

    @Test
    void stateCorrectorEngine_dryRun_noCorrections() throws Exception {
        StateCorrectorEngine engine = new StateCorrectorEngine();
        Map<String, Object> params = new HashMap<>();
        params.put("dryRun", true);
        params.put("recordCount", 500L);

        ReconResult result = engine.execute(tenantA, params);

        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals(Boolean.FALSE, result.getMetadata().get("correctionEnabled"));
    }

    @Test
    void stateCorrectorEngine_accountingIsConsistent() throws Exception {
        StateCorrectorEngine engine = new StateCorrectorEngine();
        ReconResult result = engine.execute(tenantA, new HashMap<>());

        long total = result.getMatchedCount() + result.getUnreconciled() + result.getErrors();
        assertEquals(result.getProcessedCount(), total);
    }

    // -------------------------------------------------------------------------
    // ItemLevelReconEngine
    // -------------------------------------------------------------------------

    @Test
    void itemLevelEngine_executesSuccessfully_defaultParams() throws Exception {
        ItemLevelReconEngine engine = new ItemLevelReconEngine();
        ReconResult result = engine.execute(tenantA, new HashMap<>());

        assertEquals(ReconResult.ReconStatus.COMPLETED, result.getStatus());
        assertEquals(100_000L, result.getProcessedCount());
        assertTrue(result.getSuccessRate() > 98.0);
    }

    @Test
    void itemLevelEngine_fieldsComparedMetadataCorrect() throws Exception {
        ItemLevelReconEngine engine = new ItemLevelReconEngine();
        Map<String, Object> params = new HashMap<>();
        params.put("itemCount", 500L);
        params.put("fieldsPerItem", 8L);

        ReconResult result = engine.execute(tenantA, params);

        assertEquals(4_000L, result.getMetadata().get("totalFieldsCompared"));
    }

    @Test
    void itemLevelEngine_tenantIsolation() throws Exception {
        ItemLevelReconEngine engine = new ItemLevelReconEngine();
        ReconResult rA = engine.execute(tenantA, new HashMap<>());
        ReconResult rB = engine.execute(tenantB, new HashMap<>());

        assertNotEquals(rA.getReconId(), rB.getReconId());
        assertEquals("tenant-A", rA.getTenantId());
        assertEquals("tenant-B", rB.getTenantId());
    }

    // -------------------------------------------------------------------------
    // EngineRegistry – auto-discovery of all 5 engines
    // -------------------------------------------------------------------------

    @Test
    void engineRegistry_discoversAllFiveEngines() {
        EngineRegistry registry = new EngineRegistry();
        registry.registerEngines(List.of(
                new GenericReconEngine(),
                new HybridReconEngine(),
                new SimplifiedReconEngine(),
                new StateCorrectorEngine(),
                new ItemLevelReconEngine()
        ));
        registry.validateEngines();

        assertEquals(5, registry.getAllEngines().size());
        assertTrue(registry.hasEngine("generic"));
        assertTrue(registry.hasEngine("hybrid"));
        assertTrue(registry.hasEngine("simplified"));
        assertTrue(registry.hasEngine("state-corrector"));
        assertTrue(registry.hasEngine("item-level"));
    }

    @Test
    void engineRegistry_allEnginesAreHealthy() {
        EngineRegistry registry = new EngineRegistry();
        registry.registerEngines(List.of(
                new GenericReconEngine(),
                new HybridReconEngine(),
                new SimplifiedReconEngine(),
                new StateCorrectorEngine(),
                new ItemLevelReconEngine()
        ));

        assertEquals(5, registry.getHealthyEngines().size());
    }

    @Test
    void engineRegistry_unknownTypeThrowsIllegalArgument() {
        EngineRegistry registry = new EngineRegistry();
        registry.registerEngines(List.of(new GenericReconEngine()));

        assertThrows(IllegalArgumentException.class, () -> registry.getEngine("nonexistent"));
    }

    @Test
    void engineRegistry_engineInfoListHasCorrectSize() {
        EngineRegistry registry = new EngineRegistry();
        registry.registerEngines(List.of(
                new GenericReconEngine(),
                new HybridReconEngine(),
                new SimplifiedReconEngine(),
                new StateCorrectorEngine(),
                new ItemLevelReconEngine()
        ));

        List<EngineRegistry.EngineInfo> infoList = registry.getEngineInfoList();
        assertEquals(5, infoList.size());
        infoList.forEach(info -> {
            assertNotNull(info.engineType());
            assertNotNull(info.description());
            assertTrue(info.healthy());
        });
    }
}
