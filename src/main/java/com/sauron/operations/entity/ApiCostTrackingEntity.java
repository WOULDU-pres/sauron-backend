package com.sauron.operations.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "api_cost_tracking")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCostTrackingEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "api_endpoint", nullable = false, length = 200)
    private String apiEndpoint;
    
    @Column(name = "request_count", nullable = false)
    private Integer requestCount;
    
    @Column(name = "estimated_cost", nullable = false, precision = 10, scale = 4)
    private BigDecimal estimatedCost;
    
    @Column(name = "time_bucket", nullable = false)
    private LocalDateTime timeBucket;
    
    @Column(name = "cost_provider", nullable = false, length = 50)
    private String costProvider;
    
    @Column(name = "status_code")
    private Integer statusCode;
    
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (requestCount == null) {
            requestCount = 0;
        }
        if (estimatedCost == null) {
            estimatedCost = BigDecimal.ZERO;
        }
        if (statusCode == null) {
            statusCode = 200;
        }
    }
}