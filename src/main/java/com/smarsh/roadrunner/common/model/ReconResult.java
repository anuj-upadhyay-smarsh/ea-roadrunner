package com.smarsh.roadrunner.common.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
public class ReconResult {
    private String reconId;
    private String tenantId;
    private String engineType;
    private ReconStatus status;
    private long processedCount;
    private long matchedCount;
    private long unreconciled;
    private long errors;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMs;
    private Map<String, Object> metadata;
    private String errorMessage;

    public enum ReconStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    public ReconResult(String tenantId, String engineType) {
        this.tenantId = tenantId;
        this.engineType = engineType;
        this.status = ReconStatus.PENDING;
        this.startTime = LocalDateTime.now();
    }

    /** Custom setter: also derives {@link #durationMs} from {@link #startTime}. */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        this.durationMs = java.time.temporal.ChronoUnit.MILLIS.between(startTime, endTime);
    }

    public double getSuccessRate() {
        if (processedCount == 0) return 0;
        return (double) matchedCount / processedCount * 100;
    }
}
