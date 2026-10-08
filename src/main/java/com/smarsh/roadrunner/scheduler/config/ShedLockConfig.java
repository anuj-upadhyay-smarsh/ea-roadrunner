package com.smarsh.roadrunner.scheduler.config;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Distributed scheduler locking with ShedLock, backed by MongoDB.
 *
 * <p>In a multi-replica Kubernetes deployment every pod's scheduler fires the same
 * cron tasks. ShedLock acquires a lock document (collection {@code shedLock}) before a
 * job body runs, so only one replica executes a given job per scheduled fire; the other
 * replicas observe the held lock and skip.
 *
 * <p>Locking is applied programmatically via {@link LockingTaskExecutor} because the
 * recon scheduler registers cron tasks dynamically (not as annotated {@code @Scheduled}
 * methods).
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(MongoTemplate mongoTemplate) {
        return new MongoLockProvider(mongoTemplate.getDb());
    }

    @Bean
    public LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
        return new DefaultLockingTaskExecutor(lockProvider);
    }
}
