package com.smarsh.roadrunner.scheduler.job;

import com.smarsh.roadrunner.api.service.ReconService;
import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import com.smarsh.roadrunner.scheduler.config.SchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Config-driven recon scheduler.
 *
 * <p>Registers one cron task per {@link SchedulerProperties.JobDefinition}. Each task
 * iterates the job's configured tenants, builds a synthetic system {@link TenantContext}
 * (since scheduled tasks run outside an HTTP request), and invokes the engine via
 * {@link ReconService#executeRecon(TenantContext, String, java.util.Map)}.
 *
 * <p>A failure for one tenant is logged and does not abort the remaining tenants.
 *
 * <p>In a multi-replica deployment each job+tenant is guarded by its own ShedLock
 * distributed lock (named {@code recon-job-<name>-<tenant>}), so only one pod runs a
 * given tenant's recon per fire, while different tenants of the same job may run
 * concurrently across pods.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SchedulerProperties.class)
public class ScheduledJobs implements SchedulingConfigurer {

    private static final String SCHEDULER_USER = "scheduler";
    private static final String SCHEDULER_EMAIL = "scheduler@system";
    private static final Set<String> SCHEDULER_ROLES = Set.of("ROLE_SYSTEM");
    private static final String LOCK_PREFIX = "recon-job-";

    private final SchedulerProperties properties;
    private final ReconService reconService;
    private final LockingTaskExecutor lockingTaskExecutor;

    @Value("${app.environment:production}")
    private String defaultEnvironment;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(taskScheduler());

        if (!properties.isEnabled()) {
            log.info("Recon scheduler disabled (app.scheduler.enabled=false); no jobs registered");
            return;
        }
        if (properties.getJobs().isEmpty()) {
            log.info("Recon scheduler enabled but no jobs configured under app.scheduler.jobs");
            return;
        }

        int registered = 0;
        for (SchedulerProperties.JobDefinition job : properties.getJobs()) {
            if (!isValid(job)) {
                continue;
            }
            registrar.addTriggerTask(() -> runJob(job), new CronTrigger(job.getCron()));
            log.info("Registered scheduled job '{}': engine={}, cron='{}', tenants={}",
                    job.getName(), job.getEngineType(), job.getCron(), job.getTenants());
            registered++;
        }
        log.info("Recon scheduler registered {} job(s)", registered);
    }

    private boolean isValid(SchedulerProperties.JobDefinition job) {
        if (job.getName() == null || job.getName().isBlank()) {
            log.warn("Skipping scheduled job: missing name (required for distributed lock)");
            return false;
        }
        if (job.getCron() == null || job.getCron().isBlank()) {
            log.warn("Skipping scheduled job '{}': missing cron expression", job.getName());
            return false;
        }
        if (job.getEngineType() == null || job.getEngineType().isBlank()) {
            log.warn("Skipping scheduled job '{}': missing engineType", job.getName());
            return false;
        }
        if (job.getTenants() == null || job.getTenants().isEmpty()) {
            log.warn("Skipping scheduled job '{}': no tenants configured", job.getName());
            return false;
        }
        return true;
    }

    void runJob(SchedulerProperties.JobDefinition job) {
        log.info("Running scheduled job '{}' (engine={}) for {} tenant(s)",
                job.getName(), job.getEngineType(), job.getTenants().size());
        for (String tenantId : job.getTenants()) {
            runTenantLocked(job, tenantId);
        }
    }

    /**
     * Execute a single tenant's recon under a per-tenant ShedLock distributed lock
     * (named {@code recon-job-<job>-<tenant>}). Only one replica runs a given
     * job+tenant per fire, while different tenants of the same job may run
     * concurrently across pods. If the lock is held elsewhere, this pod skips.
     */
    void runTenantLocked(SchedulerProperties.JobDefinition job, String tenantId) {
        LockConfiguration lockConfiguration = new LockConfiguration(
                Instant.now(),
                LOCK_PREFIX + job.getName() + "-" + tenantId,
                properties.getLockAtMostFor(),
                properties.getLockAtLeastFor());
        lockingTaskExecutor.executeWithLock((Runnable) () -> runTenant(job, tenantId), lockConfiguration);
    }

    void runTenant(SchedulerProperties.JobDefinition job, String tenantId) {
        String environment = job.getEnvironment() != null ? job.getEnvironment() : defaultEnvironment;
        try {
            TenantContext context = new TenantContext(
                    tenantId, SCHEDULER_USER, SCHEDULER_EMAIL, SCHEDULER_ROLES, environment);
            ReconResult result = reconService.executeRecon(
                    context, job.getEngineType(), job.getParameters());
            log.info("Scheduled job '{}' completed for tenant={}: reconId={}, status={}",
                    job.getName(), tenantId, result.getReconId(), result.getStatus());
        } catch (Exception e) {
            log.error("Scheduled job '{}' failed for tenant={}: {}",
                    job.getName(), tenantId, e.getMessage(), e);
        }
    }

    private ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(1, properties.getPoolSize()));
        scheduler.setThreadNamePrefix("recon-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }
}
