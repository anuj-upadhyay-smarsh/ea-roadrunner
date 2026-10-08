package com.smarsh.roadrunner.engine.registry;

import com.smarsh.roadrunner.engine.api.ReconEngine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * EngineRegistry - auto-discovers all {@link ReconEngine} @Component beans at startup,
 * validates them, and provides a central lookup for engine instances.
 *
 * <p>Any new engine added as a @Component is automatically registered at startup
 * without modifying this class (Open/Closed principle).
 */
@Slf4j
@Service
public class EngineRegistry {

    private final Map<String, ReconEngine> engines = new LinkedHashMap<>();

    @Autowired(required = false)
    public void registerEngines(List<ReconEngine> engineList) {
        if (engineList == null) return;
        for (ReconEngine engine : engineList) {
            String type = engine.getEngineType();
            if (type == null || type.isBlank()) {
                log.warn("Skipping engine {} - getEngineType() returned blank", engine.getClass().getSimpleName());
                continue;
            }
            if (engines.containsKey(type)) {
                log.warn("Duplicate engine type '{}' detected - {} overwrites {}",
                        type, engine.getClass().getSimpleName(),
                        engines.get(type).getClass().getSimpleName());
            }
            engines.put(type, engine);
        }
    }

    @PostConstruct
    public void validateEngines() {
        if (engines.isEmpty()) {
            log.warn("EngineRegistry: no ReconEngine implementations found on classpath");
            return;
        }
        log.info("EngineRegistry: {} engine(s) registered: {}", engines.size(), engines.keySet());
        engines.forEach((type, engine) -> {
            try {
                boolean healthy = engine.isHealthy();
                log.info("  Engine '{}' ({}) - healthy={}",
                        type, engine.getClass().getSimpleName(), healthy);
            } catch (Exception e) {
                log.error("  Engine '{}' health-check threw exception: {}", type, e.getMessage());
            }
        });
    }

    public ReconEngine getEngine(String engineType) {
        ReconEngine engine = engines.get(engineType);
        if (engine == null) {
            throw new IllegalArgumentException(
                    "Unknown engine type '" + engineType + "'. Available: " + engines.keySet());
        }
        return engine;
    }

    public Collection<ReconEngine> getAllEngines() {
        return Collections.unmodifiableCollection(engines.values());
    }

    public List<ReconEngine> getHealthyEngines() {
        return engines.values().stream()
                .filter(e -> {
                    try { return e.isHealthy(); } catch (Exception ex) { return false; }
                })
                .collect(Collectors.toList());
    }

    public Set<String> getAvailableEngineTypes() {
        return Collections.unmodifiableSet(engines.keySet());
    }

    public boolean hasEngine(String engineType) {
        return engines.containsKey(engineType);
    }

    public List<EngineInfo> getEngineInfoList() {
        return engines.values().stream()
                .map(e -> new EngineInfo(
                        e.getEngineType(),
                        e.getDescription(),
                        e.getClass().getSimpleName(),
                        isHealthySafe(e)))
                .collect(Collectors.toList());
    }

    private boolean isHealthySafe(ReconEngine engine) {
        try { return engine.isHealthy(); } catch (Exception e) { return false; }
    }

    public record EngineInfo(String engineType, String description, String className, boolean healthy) {}
}
