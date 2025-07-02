package com.sauron.operations.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cost_budgets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostBudgetEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "budget_name", nullable = false, length = 100)
    private String budgetName;
    
    @Column(name = "cost_provider", nullable = false, length = 50)
    private String costProvider;
    
    @Column(name = "monthly_limit", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyLimit;
    
    @Column(name = "warning_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal warningThreshold;
    
    @Column(name = "alert_enabled", nullable = false)
    private Boolean alertEnabled;
    
    @Column(name = "notification_channels")
    private String[] notificationChannels;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (alertEnabled == null) {
            alertEnabled = true;
        }
        if (warningThreshold == null) {
            warningThreshold = new BigDecimal("80.00");
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}