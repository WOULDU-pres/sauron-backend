package com.sauron.operations.repository;

import com.sauron.operations.entity.CostBudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CostBudgetRepository extends JpaRepository<CostBudgetEntity, Long> {
    
    Optional<CostBudgetEntity> findByCostProvider(String costProvider);
    
    List<CostBudgetEntity> findByAlertEnabledTrue();
    
    @Query("SELECT cb FROM CostBudgetEntity cb WHERE cb.alertEnabled = true")
    List<CostBudgetEntity> findAllActiveAlerts();
    
    @Query("SELECT cb FROM CostBudgetEntity cb WHERE cb.costProvider IN :providers")
    List<CostBudgetEntity> findByProviders(@Param("providers") List<String> providers);
    
    boolean existsByCostProvider(String costProvider);
}