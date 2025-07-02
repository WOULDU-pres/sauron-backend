package com.sauron.operations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsDashboardDto {
    
    @JsonProperty("totalMessages")
    private Long totalMessages;
    
    @JsonProperty("totalCost")
    private BigDecimal totalCost;
    
    @JsonProperty("averageResponseTime")
    private Double averageResponseTime;
    
    @JsonProperty("systemHealth")
    private String systemHealth;
    
    @JsonProperty("costMetrics")
    private List<CostMetricDto> costMetrics;
    
    @JsonProperty("performanceMetrics")
    private List<PerformanceMetricDto> performanceMetrics;
    
    @JsonProperty("budgetUtilization")
    private List<BudgetUtilizationDto> budgetUtilization;
    
    @JsonProperty("recentAlerts")
    private List<SystemAlertDto> recentAlerts;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostMetricDto {
        @JsonProperty("date")
        private LocalDate date;
        
        @JsonProperty("provider")
        private String provider;
        
        @JsonProperty("totalRequests")
        private Long totalRequests;
        
        @JsonProperty("totalCost")
        private BigDecimal totalCost;
        
        @JsonProperty("averageCostPerRequest")
        private BigDecimal averageCostPerRequest;
        
        @JsonProperty("errorCount")
        private Long errorCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetricDto {
        @JsonProperty("timestamp")
        private LocalDateTime timestamp;
        
        @JsonProperty("metricName")
        private String metricName;
        
        @JsonProperty("metricValue")
        private BigDecimal metricValue;
        
        @JsonProperty("metricUnit")
        private String metricUnit;
        
        @JsonProperty("serviceComponent")
        private String serviceComponent;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetUtilizationDto {
        @JsonProperty("provider")
        private String provider;
        
        @JsonProperty("monthYear")
        private LocalDate monthYear;
        
        @JsonProperty("budgetLimit")
        private BigDecimal budgetLimit;
        
        @JsonProperty("currentUsage")
        private BigDecimal currentUsage;
        
        @JsonProperty("utilizationPercentage")
        private BigDecimal utilizationPercentage;
        
        @JsonProperty("daysRemaining")
        private Integer daysRemaining;
        
        @JsonProperty("projectedMonthlyCost")
        private BigDecimal projectedMonthlyCost;
        
        @JsonProperty("status")
        private String status;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemAlertDto {
        @JsonProperty("id")
        private Long id;
        
        @JsonProperty("alertType")
        private String alertType;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("severity")
        private String severity;
        
        @JsonProperty("timestamp")
        private LocalDateTime timestamp;
        
        @JsonProperty("resolved")
        private Boolean resolved;
    }
}