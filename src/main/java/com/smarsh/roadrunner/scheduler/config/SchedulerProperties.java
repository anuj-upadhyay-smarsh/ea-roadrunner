package com.smarsh.roadrunner.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the recon scheduler.
 *
 * <p>Bound from the {@code app.scheduler} prefix. Each {@link JobDefinition}
 * registers a cron task that runs a given engine across a list of tenants.
 *
 * <pre>
 * app:
 *   scheduler:
 *     enabled: true
 *     pool-size: 4
 *     jobs:
 *       - name: hourly-generic
 *         cron: "0 0 * * * *"
 *         engine-type: generic
 *         environment: production
 *         tenants: [jpmc, natwest]
 *         parameters:
 *           recordCount: 5000
 * </pre>
 */
@ConfigurationProperties(prefix = "app.scheduler")
public class SchedulerProperties {

    /** Master switch for the scheduler. When false, no cron tasks are registered. */
    private boolean enabled = true;

    /** Size of the thread pool backing scheduled tasks. */
    private int poolSize = 4;

    /**
     * Maximum time a distributed lock is held before being released automatically.
     * Acts as a safety net if a pod crashes mid-job; must exceed the longest expected
     * job runtime. Applied to every job unless overridden per job.
     */
    private Duration lockAtMostFor = Duration.ofMinutes(30);

    /**
     * Minimum time a distributed lock is held even if the job finishes sooner.
     * Guards against duplicate runs across replicas with clock skew for very fast jobs.
     */
    private Duration lockAtLeastFor = Duration.ZERO;

    /** Scheduled job definitions. */
    private List<JobDefinition> jobs = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public Duration getLockAtMostFor() {
        return lockAtMostFor;
    }

    public void setLockAtMostFor(Duration lockAtMostFor) {
        this.lockAtMostFor = lockAtMostFor;
    }

    public Duration getLockAtLeastFor() {
        return lockAtLeastFor;
    }

    public void setLockAtLeastFor(Duration lockAtLeastFor) {
        this.lockAtLeastFor = lockAtLeastFor;
    }

    public List<JobDefinition> getJobs() {
        return jobs;
    }

    public void setJobs(List<JobDefinition> jobs) {
        this.jobs = jobs;
    }

    public static class JobDefinition {
        /** Human-readable job identifier (for logging). */
        private String name;

        /** Spring cron expression (6 fields). */
        private String cron;

        /** Engine type key registered in the EngineRegistry (e.g. {@code generic}). */
        private String engineType;

        /** Environment label applied to the synthetic TenantContext (optional). */
        private String environment;

        /** Tenants this job runs for. */
        private List<String> tenants = new ArrayList<>();

        /** Engine parameters passed on each execution. */
        private Map<String, Object> parameters = new HashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getEngineType() {
            return engineType;
        }

        public void setEngineType(String engineType) {
            this.engineType = engineType;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public List<String> getTenants() {
            return tenants;
        }

        public void setTenants(List<String> tenants) {
            this.tenants = tenants;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }
    }
}
