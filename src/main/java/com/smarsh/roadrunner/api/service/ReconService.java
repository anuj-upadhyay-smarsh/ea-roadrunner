package com.smarsh.roadrunner.api.service;

import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import com.smarsh.roadrunner.data.entity.ReconMetrics;
import com.smarsh.roadrunner.data.repository.ReconMetricsRepository;
import com.smarsh.roadrunner.engine.api.ReconEngine;
import com.smarsh.roadrunner.engine.registry.EngineRegistry;
import com.smarsh.roadrunner.infra.audit.Audited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconService {
    private final EngineRegistry engineRegistry;
    private final ReconMetricsRepository reconMetricsRepository;
    private final TenantContext tenantContext;

    @Audited(action = "EXECUTE_RECON")
    @Async
    public void executeReconAsync(String engineType, Map<String, Object> parameters) {
        try {
            executeReconSync(engineType, parameters);
        } catch (Exception e) {
            log.error("Async recon execution failed for tenant={}, engine={}: {}",
                    tenantContext.getTenantId(), engineType, e.getMessage(), e);
        }
    }

    @Audited(action = "EXECUTE_RECON")
    public ReconResult executeReconSync(String engineType, Map<String, Object> parameters)
            throws Exception {
        return executeRecon(tenantContext, engineType, parameters);
    }

    /**
     * Execute a recon for an explicitly supplied {@link TenantContext}.
     *
     * <p>Used by callers that run outside an HTTP request (e.g. the scheduler),
     * where the request-scoped {@code TenantContext} bean is not available.
     */
    public ReconResult executeRecon(TenantContext context, String engineType,
                                    Map<String, Object> parameters) throws Exception {

        String tenantId = context.getTenantId();
        log.info("Recon execution started for tenant={}, engine={}", tenantId, engineType);

        ReconEngine engine = engineRegistry.getEngine(engineType);
        ReconResult result = engine.execute(context, parameters);

        if (result.getReconId() == null || result.getReconId().isBlank()) {
            result.setReconId(UUID.randomUUID().toString());
        }
        persist(result);

        log.info("Recon execution completed for tenant={}: reconId={}, status={}",
                tenantId, result.getReconId(), result.getStatus());
        return result;
    }

    /**
     * Look up a previously executed recon for the current tenant.
     */
    public Optional<ReconResult> getReconStatus(String reconId) {
        return reconMetricsRepository
                .findByIdAndTenantId(reconId, tenantContext.getTenantId())
                .map(this::toResult);
    }

    public boolean isEngineHealthy(String engineType) {
        try {
            return engineRegistry.getEngine(engineType).isHealthy();
        } catch (Exception e) {
            log.warn("Engine health check failed for engine={}: {}", engineType, e.getMessage());
            return false;
        }
    }

    private void persist(ReconResult result) {
        ReconMetrics metrics = new ReconMetrics();
        metrics.setId(result.getReconId());
        metrics.setTenantId(result.getTenantId());
        metrics.setEngineType(result.getEngineType());
        metrics.setStatus(result.getStatus() != null ? result.getStatus().name() : null);
        metrics.setProcessedCount(result.getProcessedCount());
        metrics.setMatchedCount(result.getMatchedCount());
        metrics.setErrors(result.getErrors());
        metrics.setCompletedAt(result.getEndTime() != null ? result.getEndTime() : LocalDateTime.now());
        metrics.setDurationMs(result.getDurationMs());
        metrics.setMetadata(result.getMetadata());
        reconMetricsRepository.save(metrics);
    }

    private ReconResult toResult(ReconMetrics metrics) {
        ReconResult result = new ReconResult(metrics.getTenantId(), metrics.getEngineType());
        result.setReconId(metrics.getId());
        if (metrics.getStatus() != null) {
            result.setStatus(ReconResult.ReconStatus.valueOf(metrics.getStatus()));
        }
        result.setProcessedCount(metrics.getProcessedCount());
        result.setMatchedCount(metrics.getMatchedCount());
        result.setUnreconciled(metrics.getProcessedCount() - metrics.getMatchedCount());
        result.setErrors(metrics.getErrors());
        if (metrics.getCreatedAt() != null) {
            result.setStartTime(metrics.getCreatedAt());
        }
        if (metrics.getCompletedAt() != null) {
            result.setEndTime(metrics.getCompletedAt());
        }
        result.setMetadata(metrics.getMetadata());
        return result;
    }
}
