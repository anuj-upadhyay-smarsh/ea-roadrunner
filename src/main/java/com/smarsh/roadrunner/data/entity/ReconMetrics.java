package com.smarsh.roadrunner.data.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Document(collection = "recon_metrics")
public class ReconMetrics {
    
    @Id
    private String id;
    
    @Indexed
    private String tenantId;
    
    @Indexed
    private String engineType;
    
    private String status;
    private long processedCount;
    private long matchedCount;
    private long errors;
    
    @Indexed
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private long durationMs;
    private Map<String, Object> metadata;

    public ReconMetrics() {
        this.createdAt = LocalDateTime.now();
    }
}
