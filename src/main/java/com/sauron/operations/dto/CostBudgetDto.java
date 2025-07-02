package com.sauron.operations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostBudgetDto {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("budgetName")
    @NotBlank(message = "Budget name is required")
    @Size(min = 1, max = 100, message = "Budget name must be between 1 and 100 characters")
    private String budgetName;
    
    @JsonProperty("costProvider")
    @NotBlank(message = "Cost provider is required")
    @Pattern(regexp = "^(gemini|kakao|telegram|discord)$", message = "Invalid cost provider")
    private String costProvider;
    
    @JsonProperty("monthlyLimit")
    @NotNull(message = "Monthly limit is required")
    @DecimalMin(value = "0.01", message = "Monthly limit must be greater than 0")
    @DecimalMax(value = "10000.00", message = "Monthly limit cannot exceed $10,000")
    private BigDecimal monthlyLimit;
    
    @JsonProperty("warningThreshold")
    @DecimalMin(value = "0.01", message = "Warning threshold must be greater than 0")
    @DecimalMax(value = "100.00", message = "Warning threshold cannot exceed 100%")
    private BigDecimal warningThreshold;
    
    @JsonProperty("alertEnabled")
    private Boolean alertEnabled;
    
    @JsonProperty("notificationChannels")
    private List<String> notificationChannels;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
    
    // Request DTO for creating/updating cost budgets
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCostBudgetRequest {
        
        @JsonProperty("budgetName")
        @NotBlank(message = "Budget name is required")
        @Size(min = 1, max = 100, message = "Budget name must be between 1 and 100 characters")
        private String budgetName;
        
        @JsonProperty("costProvider")
        @NotBlank(message = "Cost provider is required")
        @Pattern(regexp = "^(gemini|kakao|telegram|discord)$", message = "Invalid cost provider")
        private String costProvider;
        
        @JsonProperty("monthlyLimit")
        @NotNull(message = "Monthly limit is required")
        @DecimalMin(value = "0.01", message = "Monthly limit must be greater than 0")
        @DecimalMax(value = "10000.00", message = "Monthly limit cannot exceed $10,000")
        private BigDecimal monthlyLimit;
        
        @JsonProperty("warningThreshold")
        @DecimalMin(value = "1.00", message = "Warning threshold must be at least 1%")
        @DecimalMax(value = "100.00", message = "Warning threshold cannot exceed 100%")
        private BigDecimal warningThreshold;
        
        @JsonProperty("alertEnabled")
        private Boolean alertEnabled;
        
        @JsonProperty("notificationChannels")
        private List<@Pattern(regexp = "^(email|telegram|discord|slack)$") String> notificationChannels;
    }
    
    // Response DTO for budget utilization
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetUtilizationResponse {
        
        @JsonProperty("provider")
        private String provider;
        
        @JsonProperty("budgetName")
        private String budgetName;
        
        @JsonProperty("monthlyLimit")
        private BigDecimal monthlyLimit;
        
        @JsonProperty("currentUsage")
        private BigDecimal currentUsage;
        
        @JsonProperty("utilizationPercentage")
        private BigDecimal utilizationPercentage;
        
        @JsonProperty("remainingBudget")
        private BigDecimal remainingBudget;
        
        @JsonProperty("daysRemaining")
        private Integer daysRemaining;
        
        @JsonProperty("projectedMonthlyCost")
        private BigDecimal projectedMonthlyCost;
        
        @JsonProperty("status")
        private String status; // NORMAL, WARNING, CRITICAL
        
        @JsonProperty("alertEnabled")
        private Boolean alertEnabled;
        
        @JsonProperty("warningThreshold")
        private BigDecimal warningThreshold;
    }
}