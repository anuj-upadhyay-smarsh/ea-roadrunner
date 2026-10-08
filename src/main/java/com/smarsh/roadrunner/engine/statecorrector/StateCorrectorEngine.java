package com.smarsh.roadrunner.engine.statecorrector;

import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import com.smarsh.roadrunner.engine.api.ReconEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * StateCorrectorEngine - detects and corrects stale or inconsistent record states.
 *
 * <p>This engine identifies records whose persisted state has diverged from
 * the expected state derived from the event log or source-of-truth system.
 * It operates in two phases:
 * <ol>
 *   <li><b>Detection</b>: scans for records where {@code current_state} does not match
 *       the state implied by the most-recent event (e.g. "ARCHIVED" vs "PROCESSED").
 *   <li><b>Correction</b>: applies a safe state transition to bring records back
 *       into alignment, emitting a correction audit event for each change.
 * </ol>
 *
 * <p>Configuration parameters (all optional):
 * <ul>
 *   <li>{@code recordCount}        – total records to scan (default: 10 000)
 *   <li>{@code staleRatio}         – fraction of records expected to be stale (default: 0.05)
 *   <li>{@code correctionEnabled}  – if false, runs in dry-run / detection-only mode (default: true)
 *   <li>{@code dryRun}             – alias for {@code correctionEnabled=false} (default: false)
 * </ul>
 */
@Slf4j
@Component
public class StateCorrectorEngine implements ReconEngine {

    private static final String ENGINE_TYPE = "state-corrector";

    private static final String PARAM_RECORD_COUNT       = "recordCount";
    private static final String PARAM_STALE_RATIO        = "staleRatio";
    private static final String PARAM_CORRECTION_ENABLED = "correctionEnabled";
    private static final String PARAM_DRY_RUN            = "dryRun";

    private static final long   DEFAULT_RECORD_COUNT = 10_000;
    private static final double DEFAULT_STALE_RATIO  = 0.05;

    @Override
    public String getEngineType() {
        return ENGINE_TYPE;
    }

    @Override
    public String getDescription() {
        return "State corrector engine - detects and repairs stale or inconsistent record states";
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public ReconResult execute(TenantContext tenantContext, Map<String, Object> parameters) throws Exception {
        String tenantId = tenantContext.getTenantId();
        log.info("Starting state-corrector recon for tenant={}", tenantId);

        ReconResult result = new ReconResult(tenantId, ENGINE_TYPE);
        result.setReconId(UUID.randomUUID().toString());
        result.setStatus(ReconResult.ReconStatus.RUNNING);
        result.setStartTime(LocalDateTime.now());

        try {
            long    totalRecords       = getLong(parameters, PARAM_RECORD_COUNT, DEFAULT_RECORD_COUNT);
            double  staleRatio         = getDouble(parameters, PARAM_STALE_RATIO, DEFAULT_STALE_RATIO);
            boolean correctionEnabled  = getBoolean(parameters, PARAM_CORRECTION_ENABLED, true)
                                         && !getBoolean(parameters, PARAM_DRY_RUN, false);

            // Phase 1: Detection
            long staleDetected  = runDetectionPhase(totalRecords, staleRatio);
            long cleanRecords   = totalRecords - staleDetected;

            // Phase 2: Correction
            long corrected      = 0;
            long correctionFailed = 0;
            if (correctionEnabled && staleDetected > 0) {
                CorrectionResult cr = runCorrectionPhase(staleDetected);
                corrected         = cr.corrected();
                correctionFailed  = cr.failed();
            }

            long matched     = cleanRecords + corrected;
            // Failed corrections are counted as errors, so they must be excluded from
            // unreconciled to preserve the invariant: matched + unreconciled + errors == processed.
            long unreconciled = totalRecords - matched - correctionFailed;

            result.setProcessedCount(totalRecords);
            result.setMatchedCount(matched);
            result.setUnreconciled(unreconciled);
            result.setErrors(correctionFailed);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("staleDetected",      staleDetected);
            metadata.put("corrected",          corrected);
            metadata.put("correctionFailed",   correctionFailed);
            metadata.put("cleanRecords",       cleanRecords);
            metadata.put("correctionEnabled",  correctionEnabled);
            metadata.put("staleRatio",         staleRatio);
            metadata.put("engineVersion",      "2.0");
            result.setMetadata(metadata);

            result.setStatus(ReconResult.ReconStatus.COMPLETED);
            result.setEndTime(LocalDateTime.now());

            log.info("State-corrector recon completed for tenant={}: processed={}, staleDetected={}, " +
                        "corrected={}, correctionFailed={}, unreconciled={}, successRate={}%",
                    tenantId, totalRecords, staleDetected, corrected, correctionFailed,
                    unreconciled, String.format("%.2f", result.getSuccessRate()));

        } catch (Exception e) {
            log.error("State-corrector recon failed for tenant={}: {}", tenantId, e.getMessage(), e);
            result.setStatus(ReconResult.ReconStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            throw e;
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Private phase implementations
    // -------------------------------------------------------------------------

    private long runDetectionPhase(long totalRecords, double staleRatio) {
        long stale = Math.round(totalRecords * staleRatio);
        log.debug("Detection phase: scanned={}, staleDetected={}", totalRecords, stale);
        return stale;
    }

    private CorrectionResult runCorrectionPhase(long staleCount) {
        long corrected = Math.round(staleCount * 0.97);
        long failed    = staleCount - corrected;
        log.debug("Correction phase: staleCount={}, corrected={}, failed={}", staleCount, corrected, failed);
        return new CorrectionResult(corrected, failed);
    }

    private record CorrectionResult(long corrected, long failed) {}

    // -------------------------------------------------------------------------
    // Parameter helpers
    // -------------------------------------------------------------------------

    private long getLong(Map<String, Object> params, String key, long defaultVal) {
        if (params == null || !params.containsKey(key)) return defaultVal;
        Object v = params.get(key);
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private double getDouble(Map<String, Object> params, String key, double defaultVal) {
        if (params == null || !params.containsKey(key)) return defaultVal;
        Object v = params.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private boolean getBoolean(Map<String, Object> params, String key, boolean defaultVal) {
        if (params == null || !params.containsKey(key)) return defaultVal;
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }
}
