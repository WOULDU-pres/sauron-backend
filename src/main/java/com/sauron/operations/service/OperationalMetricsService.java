package com.sauron.operations.service;

import com.sauron.operations.dto.OperationsDashboardDto;
import com.sauron.operations.repository.ApiCostTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OperationalMetricsService {
    
    private final ApiCostTrackingRepository costTrackingRepository;
    
    public OperationsDashboardDto getDashboardMetrics(LocalDate startDate, LocalDate endDate) {
        log.info("Generating operations dashboard for period: {} to {}", startDate, endDate);
        
        try {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
            
            // Get cost metrics
            List<OperationsDashboardDto.CostMetricDto> costMetrics = getCostMetrics(startDateTime, endDateTime);
            
            // Get budget utilization
            List<OperationsDashboardDto.BudgetUtilizationDto> budgetUtilization = getBudgetUtilization();
            
            // Calculate summary metrics
            BigDecimal totalCost = costMetrics.stream()
                .map(OperationsDashboardDto.CostMetricDto::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Long totalMessages = costMetrics.stream()
                .mapToLong(OperationsDashboardDto.CostMetricDto::getTotalRequests)
                .sum();
            
            // Build dashboard response
            return OperationsDashboardDto.builder()
                .totalMessages(totalMessages)
                .totalCost(totalCost)
                .systemHealth(calculateSystemHealth(costMetrics))
                .costMetrics(costMetrics)
                .budgetUtilization(budgetUtilization)
                .recentAlerts(getRecentSystemAlerts())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating operations dashboard: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate operations dashboard", e);
        }
    }
    
    private List<OperationsDashboardDto.CostMetricDto> getCostMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> rawData = costTrackingRepository.getDailyCostAnalysis(startDate, endDate);
        List<OperationsDashboardDto.CostMetricDto> costMetrics = new ArrayList<>();
        
        for (Object[] row : rawData) {
            Date sqlDate = (Date) row[0];
            LocalDate analysisDate = sqlDate != null ? sqlDate.toLocalDate() : LocalDate.now();
            
            OperationsDashboardDto.CostMetricDto metric = OperationsDashboardDto.CostMetricDto.builder()
                .date(analysisDate)
                .provider((String) row[1])
                .totalRequests(((Number) row[2]).longValue())
                .totalCost((BigDecimal) row[3])
                .averageCostPerRequest(row[4] != null ? ((BigDecimal) row[4]).setScale(6, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .errorCount(((Number) row[5]).longValue())
                .build();
            
            costMetrics.add(metric);
        }
        
        return costMetrics;
    }
    
    private List<OperationsDashboardDto.BudgetUtilizationDto> getBudgetUtilization() {
        List<Object[]> rawData = costTrackingRepository.getBudgetUtilization(null, LocalDate.now().withDayOfMonth(1));
        List<OperationsDashboardDto.BudgetUtilizationDto> budgetUtilization = new ArrayList<>();
        
        for (Object[] row : rawData) {
            Date sqlDate = (Date) row[1];
            LocalDate monthYear = sqlDate != null ? sqlDate.toLocalDate() : LocalDate.now();
            
            OperationsDashboardDto.BudgetUtilizationDto budget = OperationsDashboardDto.BudgetUtilizationDto.builder()
                .provider((String) row[0])
                .monthYear(monthYear)
                .budgetLimit((BigDecimal) row[2])
                .currentUsage((BigDecimal) row[3])
                .utilizationPercentage((BigDecimal) row[4])
                .daysRemaining(((Number) row[5]).intValue())
                .projectedMonthlyCost((BigDecimal) row[6])
                .status((String) row[7])
                .build();
                
            budgetUtilization.add(budget);
        }
        
        return budgetUtilization;
    }
    
    private String calculateSystemHealth(List<OperationsDashboardDto.CostMetricDto> costMetrics) {
        if (costMetrics.isEmpty()) {
            return "UNKNOWN";
        }
        
        // Calculate health based on error rates
        long totalRequests = costMetrics.stream()
            .mapToLong(OperationsDashboardDto.CostMetricDto::getTotalRequests)
            .sum();
        
        long totalErrors = costMetrics.stream()
            .mapToLong(OperationsDashboardDto.CostMetricDto::getErrorCount)
            .sum();
        
        if (totalRequests == 0) {
            return "UNKNOWN";
        }
        
        double errorRate = (double) totalErrors / totalRequests * 100;
        
        if (errorRate < 1.0) {
            return "HEALTHY";
        } else if (errorRate < 5.0) {
            return "WARNING";
        } else {
            return "CRITICAL";
        }
    }
    
    private List<OperationsDashboardDto.SystemAlertDto> getRecentSystemAlerts() {
        // For now, return empty list. This would be populated with actual system alerts
        // from a monitoring system or alert table
        return new ArrayList<>();
    }
    
    public BigDecimal getCurrentMonthCost(String provider) {
        try {
            return costTrackingRepository.getCurrentMonthCost(provider);
        } catch (Exception e) {
            log.error("Error getting current month cost for provider {}: {}", provider, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    public List<Object[]> getTopCostlyEndpoints(LocalDate startDate, LocalDate endDate) {
        try {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
            return costTrackingRepository.getTopCostlyEndpoints(startDateTime, endDateTime);
        } catch (Exception e) {
            log.error("Error getting top costly endpoints: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}