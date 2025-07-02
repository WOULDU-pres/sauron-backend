package com.sauron.operations.controller;

import com.sauron.operations.dto.CostBudgetDto;
import com.sauron.operations.service.CostBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/operations/budgets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cost Budget Management", description = "APIs for managing cost budgets and alerts")
@PreAuthorize("hasRole('ADMIN')")
public class CostBudgetController {
    
    private final CostBudgetService costBudgetService;
    
    @GetMapping
    @Operation(
        summary = "Get all cost budgets",
        description = "Retrieves all configured cost budgets"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved budgets"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required")
    })
    public ResponseEntity<List<CostBudgetDto>> getAllBudgets() {
        log.info("Retrieving all cost budgets");
        
        List<CostBudgetDto> budgets = costBudgetService.getAllBudgets();
        return ResponseEntity.ok(budgets);
    }
    
    @GetMapping("/{provider}")
    @Operation(
        summary = "Get budget by provider",
        description = "Retrieves cost budget for a specific provider"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved budget"),
        @ApiResponse(responseCode = "404", description = "Budget not found for provider"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required")
    })
    public ResponseEntity<CostBudgetDto> getBudgetByProvider(
        @Parameter(description = "Cost provider name", example = "gemini")
        @PathVariable String provider
    ) {
        log.info("Retrieving budget for provider: {}", provider);
        
        return costBudgetService.getBudgetByProvider(provider)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    @Operation(
        summary = "Create new cost budget",
        description = "Creates a new cost budget for a provider"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Budget created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or budget already exists"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required")
    })
    public ResponseEntity<CostBudgetDto> createBudget(
        @Valid @RequestBody CostBudgetDto.CreateCostBudgetRequest request
    ) {
        log.info("Creating new budget for provider: {}", request.getCostProvider());
        
        try {
            CostBudgetDto createdBudget = costBudgetService.createBudget(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdBudget);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create budget: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/{budgetId}")
    @Operation(
        summary = "Update cost budget",
        description = "Updates an existing cost budget"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Budget updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required")
    })
    public ResponseEntity<CostBudgetDto> updateBudget(
        @Parameter(description = "Budget ID", example = "1")
        @PathVariable Long budgetId,
        @Valid @RequestBody CostBudgetDto.CreateCostBudgetRequest request
    ) {
        log.info("Updating budget ID: {}", budgetId);
        
        try {
            CostBudgetDto updatedBudget = costBudgetService.updateBudget(budgetId, request);
            return ResponseEntity.ok(updatedBudget);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update budget: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{budgetId}")
    @Operation(
        summary = "Delete cost budget",
        description = "Deletes a cost budget"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Budget deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required")
    })
    public ResponseEntity<Void> deleteBudget(
        @Parameter(description = "Budget ID", example = "1")
        @PathVariable Long budgetId
    ) {
        log.info("Deleting budget ID: {}", budgetId);
        
        try {
            costBudgetService.deleteBudget(budgetId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Failed to delete budget: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/utilization")
    @Operation(
        summary = "Get budget utilization for all providers",
        description = "Retrieves current budget utilization status for all configured providers"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved utilization data"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required")
    })
    public ResponseEntity<List<CostBudgetDto.BudgetUtilizationResponse>> getBudgetUtilization() {
        log.info("Retrieving budget utilization for all providers");
        
        List<CostBudgetDto.BudgetUtilizationResponse> utilization = costBudgetService.getBudgetUtilization();
        return ResponseEntity.ok(utilization);
    }
    
    @GetMapping("/utilization/{provider}")
    @Operation(
        summary = "Get budget utilization by provider",
        description = "Retrieves current budget utilization status for a specific provider"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved utilization data"),
        @ApiResponse(responseCode = "404", description = "No budget found for provider"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required")
    })
    public ResponseEntity<CostBudgetDto.BudgetUtilizationResponse> getBudgetUtilizationByProvider(
        @Parameter(description = "Cost provider name", example = "gemini")
        @PathVariable String provider
    ) {
        log.info("Retrieving budget utilization for provider: {}", provider);
        
        try {
            CostBudgetDto.BudgetUtilizationResponse utilization = costBudgetService.getBudgetUtilizationByProvider(provider);
            return ResponseEntity.ok(utilization);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to get utilization for provider {}: {}", provider, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/{budgetId}/toggle-alert")
    @Operation(
        summary = "Toggle budget alert",
        description = "Enables or disables alerts for a specific budget"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alert status updated successfully"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin role required")
    })
    public ResponseEntity<Map<String, Object>> toggleBudgetAlert(
        @Parameter(description = "Budget ID", example = "1")
        @PathVariable Long budgetId,
        @RequestBody Map<String, Boolean> request
    ) {
        log.info("Toggling alert for budget ID: {}", budgetId);
        
        Boolean alertEnabled = request.get("alertEnabled");
        if (alertEnabled == null) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            // Get existing budget
            CostBudgetDto existingBudget = costBudgetService.getAllBudgets().stream()
                .filter(b -> b.getId().equals(budgetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
            
            // Create update request with only alert status changed
            CostBudgetDto.CreateCostBudgetRequest updateRequest = CostBudgetDto.CreateCostBudgetRequest.builder()
                .budgetName(existingBudget.getBudgetName())
                .costProvider(existingBudget.getCostProvider())
                .monthlyLimit(existingBudget.getMonthlyLimit())
                .warningThreshold(existingBudget.getWarningThreshold())
                .alertEnabled(alertEnabled)
                .notificationChannels(existingBudget.getNotificationChannels())
                .build();
            
            costBudgetService.updateBudget(budgetId, updateRequest);
            
            Map<String, Object> response = Map.of(
                "budgetId", budgetId,
                "alertEnabled", alertEnabled,
                "message", "Alert status updated successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to toggle alert for budget {}: {}", budgetId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}