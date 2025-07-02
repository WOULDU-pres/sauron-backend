package com.sauron.operations.controller;

import com.sauron.operations.dto.OperationsDashboardDto;
import com.sauron.operations.service.OperationalMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/operations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Operations", description = "Operations and cost dashboard APIs")
@PreAuthorize("hasRole('ADMIN')")
public class OperationsController {
    
    private final OperationalMetricsService metricsService;
    
    @GetMapping("/dashboard")
    @Operation(
        summary = "Get operations dashboard data",
        description = "Retrieves comprehensive operations dashboard including cost metrics, system health, and budget utilization"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved dashboard data"),
        @ApiResponse(responseCode = "400", description = "Invalid date parameters"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OperationsDashboardDto> getDashboardMetrics(
        @Parameter(description = "Start date for analytics (YYYY-MM-DD)", example = "2025-07-01")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        
        @Parameter(description = "End date for analytics (YYYY-MM-DD)", example = "2025-07-31")  
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        
        log.info("Dashboard metrics requested for period: {} to {}", startDate, endDate);
        
        // Validate date range
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start date {} is after end date {}", startDate, endDate);
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
        
        if (startDate.isBefore(LocalDate.now().minusYears(1))) {
            log.warn("Date range too old: start date {}", startDate);
            throw new IllegalArgumentException("Start date cannot be more than 1 year ago");
        }
        
        OperationsDashboardDto metrics = metricsService.getDashboardMetrics(startDate, endDate);
        return ResponseEntity.ok(metrics);
    }
    
    @GetMapping("/cost/current-month")
    @Operation(
        summary = "Get current month cost by provider",
        description = "Retrieves the current month's cost for a specific provider or all providers"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved cost data"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required")
    })
    public ResponseEntity<Map<String, Object>> getCurrentMonthCost(
        @Parameter(description = "Cost provider (gemini, kakao, telegram, etc.)", example = "gemini")
        @RequestParam(required = false) String provider
    ) {
        
        log.info("Current month cost requested for provider: {}", provider);
        
        BigDecimal cost = metricsService.getCurrentMonthCost(provider);
        
        Map<String, Object> response = Map.of(
            "provider", provider != null ? provider : "all",
            "currentMonthCost", cost,
            "asOfDate", LocalDate.now()
        );
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/endpoints/top-costly")
    @Operation(
        summary = "Get top costly API endpoints",
        description = "Retrieves the top 10 most expensive API endpoints for the specified date range"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved endpoint cost data"),
        @ApiResponse(responseCode = "400", description = "Invalid date parameters"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required")
    })
    public ResponseEntity<List<Map<String, Object>>> getTopCostlyEndpoints(
        @Parameter(description = "Start date for analysis (YYYY-MM-DD)", example = "2025-07-01")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        
        @Parameter(description = "End date for analysis (YYYY-MM-DD)", example = "2025-07-31")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        
        log.info("Top costly endpoints requested for period: {} to {}", startDate, endDate);
        
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
        
        List<Object[]> rawData = metricsService.getTopCostlyEndpoints(startDate, endDate);
        
        List<Map<String, Object>> response = rawData.stream()
            .map(row -> {
                Map<String, Object> item = new HashMap<>();
                item.put("endpoint", (String) row[0]);
                item.put("totalRequests", ((Number) row[1]).longValue());
                item.put("totalCost", (BigDecimal) row[2]);
                item.put("averageResponseTime", row[3] != null ? ((Number) row[3]).doubleValue() : 0.0);
                return item;
            })
            .toList();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/health")
    @Operation(
        summary = "Get system health status",
        description = "Retrieves current system health status based on recent metrics"
    )
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        
        log.debug("System health status requested");
        
        // Get last 24 hours metrics for health calculation
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today = LocalDate.now();
        
        OperationsDashboardDto metrics = metricsService.getDashboardMetrics(yesterday, today);
        
        Map<String, Object> healthStatus = Map.of(
            "status", metrics.getSystemHealth(),
            "totalMessages", metrics.getTotalMessages(),
            "totalCost", metrics.getTotalCost(),
            "lastUpdated", LocalDate.now(),
            "details", "System health calculated based on 24-hour error rates and performance metrics"
        );
        
        return ResponseEntity.ok(healthStatus);
    }
}