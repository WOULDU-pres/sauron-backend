package com.sauron.operations.service;

import com.sauron.operations.dto.CostBudgetDto;
import com.sauron.operations.entity.CostBudgetEntity;
import com.sauron.operations.repository.ApiCostTrackingRepository;
import com.sauron.operations.repository.CostBudgetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CostBudgetService {
    
    private final CostBudgetRepository costBudgetRepository;
    private final ApiCostTrackingRepository costTrackingRepository;
    
    @Transactional(readOnly = true)
    public List<CostBudgetDto> getAllBudgets() {
        log.debug("Retrieving all cost budgets");
        
        List<CostBudgetEntity> budgets = costBudgetRepository.findAll();
        return budgets.stream()
            .map(this::mapToDto)
            .toList();
    }
    
    @Transactional(readOnly = true)
    public Optional<CostBudgetDto> getBudgetByProvider(String provider) {
        log.debug("Retrieving cost budget for provider: {}", provider);
        
        return costBudgetRepository.findByCostProvider(provider)
            .map(this::mapToDto);
    }
    
    @Transactional
    public CostBudgetDto createBudget(CostBudgetDto.CreateCostBudgetRequest request) {
        log.info("Creating new cost budget for provider: {}", request.getCostProvider());
        
        // Check if budget already exists for this provider
        if (costBudgetRepository.existsByCostProvider(request.getCostProvider())) {
            throw new IllegalArgumentException("Budget already exists for provider: " + request.getCostProvider());
        }
        
        CostBudgetEntity entity = CostBudgetEntity.builder()
            .budgetName(request.getBudgetName())
            .costProvider(request.getCostProvider())
            .monthlyLimit(request.getMonthlyLimit())
            .warningThreshold(request.getWarningThreshold() != null ? request.getWarningThreshold() : new BigDecimal("80.00"))
            .alertEnabled(request.getAlertEnabled() != null ? request.getAlertEnabled() : true)
            .notificationChannels(request.getNotificationChannels() != null ? 
                request.getNotificationChannels().toArray(new String[0]) : 
                new String[]{"email"})
            .build();
        
        CostBudgetEntity savedEntity = costBudgetRepository.save(entity);
        log.info("Created cost budget with ID: {} for provider: {}", savedEntity.getId(), savedEntity.getCostProvider());
        
        return mapToDto(savedEntity);
    }
    
    @Transactional
    public CostBudgetDto updateBudget(Long budgetId, CostBudgetDto.CreateCostBudgetRequest request) {
        log.info("Updating cost budget ID: {}", budgetId);
        
        CostBudgetEntity existingEntity = costBudgetRepository.findById(budgetId)
            .orElseThrow(() -> new IllegalArgumentException("Budget not found with ID: " + budgetId));
        
        // Check if changing provider and new provider already exists
        if (!existingEntity.getCostProvider().equals(request.getCostProvider()) &&
            costBudgetRepository.existsByCostProvider(request.getCostProvider())) {
            throw new IllegalArgumentException("Budget already exists for provider: " + request.getCostProvider());
        }
        
        existingEntity.setBudgetName(request.getBudgetName());
        existingEntity.setCostProvider(request.getCostProvider());
        existingEntity.setMonthlyLimit(request.getMonthlyLimit());
        existingEntity.setWarningThreshold(request.getWarningThreshold() != null ? request.getWarningThreshold() : new BigDecimal("80.00"));
        existingEntity.setAlertEnabled(request.getAlertEnabled() != null ? request.getAlertEnabled() : true);
        existingEntity.setNotificationChannels(request.getNotificationChannels() != null ? 
            request.getNotificationChannels().toArray(new String[0]) : 
            new String[]{"email"});
        
        CostBudgetEntity savedEntity = costBudgetRepository.save(existingEntity);
        log.info("Updated cost budget ID: {} for provider: {}", savedEntity.getId(), savedEntity.getCostProvider());
        
        return mapToDto(savedEntity);
    }
    
    @Transactional
    public void deleteBudget(Long budgetId) {
        log.info("Deleting cost budget ID: {}", budgetId);
        
        if (!costBudgetRepository.existsById(budgetId)) {
            throw new IllegalArgumentException("Budget not found with ID: " + budgetId);
        }
        
        costBudgetRepository.deleteById(budgetId);
        log.info("Deleted cost budget ID: {}", budgetId);
    }
    
    @Transactional(readOnly = true)
    public List<CostBudgetDto.BudgetUtilizationResponse> getBudgetUtilization() {
        log.debug("Calculating budget utilization for all providers");
        
        List<CostBudgetEntity> budgets = costBudgetRepository.findAll();
        LocalDate currentMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        
        return budgets.stream()
            .map(budget -> calculateUtilization(budget, currentMonth))
            .toList();
    }
    
    @Transactional(readOnly = true)
    public CostBudgetDto.BudgetUtilizationResponse getBudgetUtilizationByProvider(String provider) {
        log.debug("Calculating budget utilization for provider: {}", provider);
        
        CostBudgetEntity budget = costBudgetRepository.findByCostProvider(provider)
            .orElseThrow(() -> new IllegalArgumentException("No budget found for provider: " + provider));
        
        LocalDate currentMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        return calculateUtilization(budget, currentMonth);
    }
    
    private CostBudgetDto.BudgetUtilizationResponse calculateUtilization(CostBudgetEntity budget, LocalDate monthStart) {
        // Get current month cost
        BigDecimal currentUsage = costTrackingRepository.getCurrentMonthCost(budget.getCostProvider());
        if (currentUsage == null) {
            currentUsage = BigDecimal.ZERO;
        }
        
        // Calculate utilization percentage
        BigDecimal utilizationPercentage = BigDecimal.ZERO;
        if (budget.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0) {
            utilizationPercentage = currentUsage
                .divide(budget.getMonthlyLimit(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
        }
        
        // Calculate remaining budget
        BigDecimal remainingBudget = budget.getMonthlyLimit().subtract(currentUsage);
        
        // Calculate days remaining in month
        LocalDate lastDayOfMonth = monthStart.with(TemporalAdjusters.lastDayOfMonth());
        int daysRemaining = (int) (lastDayOfMonth.toEpochDay() - LocalDate.now().toEpochDay() + 1);
        
        // Calculate projected monthly cost
        int daysPassed = LocalDate.now().getDayOfMonth();
        BigDecimal projectedMonthlyCost = BigDecimal.ZERO;
        if (daysPassed > 0) {
            int totalDaysInMonth = lastDayOfMonth.getDayOfMonth();
            projectedMonthlyCost = currentUsage
                .multiply(BigDecimal.valueOf(totalDaysInMonth))
                .divide(BigDecimal.valueOf(daysPassed), 2, RoundingMode.HALF_UP);
        }
        
        // Determine status
        String status = "NORMAL";
        if (utilizationPercentage.compareTo(budget.getWarningThreshold()) >= 0) {
            status = "WARNING";
        }
        if (utilizationPercentage.compareTo(new BigDecimal("100")) >= 0) {
            status = "CRITICAL";
        }
        
        return CostBudgetDto.BudgetUtilizationResponse.builder()
            .provider(budget.getCostProvider())
            .budgetName(budget.getBudgetName())
            .monthlyLimit(budget.getMonthlyLimit())
            .currentUsage(currentUsage)
            .utilizationPercentage(utilizationPercentage)
            .remainingBudget(remainingBudget)
            .daysRemaining(daysRemaining)
            .projectedMonthlyCost(projectedMonthlyCost)
            .status(status)
            .alertEnabled(budget.getAlertEnabled())
            .warningThreshold(budget.getWarningThreshold())
            .build();
    }
    
    private CostBudgetDto mapToDto(CostBudgetEntity entity) {
        return CostBudgetDto.builder()
            .id(entity.getId())
            .budgetName(entity.getBudgetName())
            .costProvider(entity.getCostProvider())
            .monthlyLimit(entity.getMonthlyLimit())
            .warningThreshold(entity.getWarningThreshold())
            .alertEnabled(entity.getAlertEnabled())
            .notificationChannels(entity.getNotificationChannels() != null ? 
                Arrays.asList(entity.getNotificationChannels()) : 
                List.of("email"))
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}