package com.smarsh.roadrunner.scheduler.job;

import com.smarsh.roadrunner.api.service.ReconService;
import com.smarsh.roadrunner.common.context.TenantContext;
import com.smarsh.roadrunner.common.model.ReconResult;
import com.smarsh.roadrunner.scheduler.config.SchedulerProperties;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledJobsTest {

    private ReconService reconService;
    private LockingTaskExecutor lockingTaskExecutor;
    private SchedulerProperties properties;
    private ScheduledJobs scheduledJobs;

    @BeforeEach
    void setUp() {
        reconService = mock(ReconService.class);
        lockingTaskExecutor = mock(LockingTaskExecutor.class);
        properties = new SchedulerProperties();
        scheduledJobs = new ScheduledJobs(properties, reconService, lockingTaskExecutor);

        // By default, run the wrapped task as ShedLock would when the lock is acquired.
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(lockingTaskExecutor).executeWithLock(any(Runnable.class), any(LockConfiguration.class));
    }

    private SchedulerProperties.JobDefinition job(String name, String engineType, List<String> tenants) {
        SchedulerProperties.JobDefinition job = new SchedulerProperties.JobDefinition();
        job.setName(name);
        job.setCron("0 0 * * * *");
        job.setEngineType(engineType);
        job.setTenants(tenants);
        job.setEnvironment("production");
        return job;
    }

    @Test
    void runJobInvokesEngineForEachTenant() throws Exception {
        when(reconService.executeRecon(any(), eq("generic"), anyMap()))
                .thenReturn(new ReconResult("t", "generic"));

        scheduledJobs.runJob(job("hourly-generic", "generic", List.of("jpmc", "natwest", "bny")));

        verify(reconService, times(3)).executeRecon(any(TenantContext.class), eq("generic"), anyMap());
    }

    @Test
    void runJobBuildsSystemTenantContextPerTenant() throws Exception {
        when(reconService.executeRecon(any(), eq("generic"), anyMap()))
                .thenReturn(new ReconResult("t", "generic"));

        scheduledJobs.runJob(job("hourly-generic", "generic", List.of("jpmc")));

        ArgumentCaptor<TenantContext> captor = ArgumentCaptor.forClass(TenantContext.class);
        verify(reconService).executeRecon(captor.capture(), eq("generic"), anyMap());

        TenantContext ctx = captor.getValue();
        assertEquals("jpmc", ctx.getTenantId());
        assertEquals("scheduler", ctx.getUserId());
        assertEquals("production", ctx.getEnvironment());
        assertTrue(ctx.hasRole("SYSTEM"));
    }

    @Test
    void runJobContinuesWhenOneTenantFails() throws Exception {
        when(reconService.executeRecon(any(), eq("generic"), anyMap()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(new ReconResult("t", "generic"));

        scheduledJobs.runJob(job("hourly-generic", "generic", List.of("failing-tenant", "ok-tenant")));

        // Both tenants attempted despite the first throwing.
        verify(reconService, times(2)).executeRecon(any(TenantContext.class), eq("generic"), anyMap());
    }

    @Test
    void acquiresPerTenantNamedLocks() throws Exception {
        when(reconService.executeRecon(any(), eq("generic"), anyMap()))
                .thenReturn(new ReconResult("t", "generic"));

        scheduledJobs.runJob(job("hourly-generic", "generic", List.of("jpmc", "natwest")));

        ArgumentCaptor<LockConfiguration> lockCaptor = ArgumentCaptor.forClass(LockConfiguration.class);
        verify(lockingTaskExecutor, times(2))
                .executeWithLock(any(Runnable.class), lockCaptor.capture());

        assertEquals(List.of("recon-job-hourly-generic-jpmc", "recon-job-hourly-generic-natwest"),
                lockCaptor.getAllValues().stream().map(LockConfiguration::getName).toList());

        // Bodies executed -> engine invoked per tenant.
        verify(reconService, times(2)).executeRecon(any(TenantContext.class), eq("generic"), anyMap());
    }

    @Test
    void disabledSchedulerRegistersNoTasks() {
        properties.setEnabled(false);
        properties.setJobs(List.of(job("hourly-generic", "generic", List.of("jpmc"))));

        var registrar = new org.springframework.scheduling.config.ScheduledTaskRegistrar();
        scheduledJobs.configureTasks(registrar);

        assertTrue(registrar.getTriggerTaskList().isEmpty());
    }

    @Test
    void invalidJobsAreSkippedDuringRegistration() {
        SchedulerProperties.JobDefinition noTenants = job("no-tenants", "generic", List.of());
        SchedulerProperties.JobDefinition valid = job("valid", "generic", List.of("jpmc"));
        properties.setJobs(List.of(noTenants, valid));

        var registrar = new org.springframework.scheduling.config.ScheduledTaskRegistrar();
        scheduledJobs.configureTasks(registrar);

        assertEquals(1, registrar.getTriggerTaskList().size());
    }
}
