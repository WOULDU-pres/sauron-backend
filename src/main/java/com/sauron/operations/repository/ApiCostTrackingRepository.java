package com.sauron.operations.repository;

import com.sauron.operations.entity.ApiCostTrackingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiCostTrackingRepository extends JpaRepository<ApiCostTrackingEntity, Long> {
    
    @Query(value = """
        SELECT 
            date_trunc('day', time_bucket)::date as analysis_date,
            cost_provider,
            SUM(request_count) as total_requests,
            SUM(estimated_cost) as total_cost,
            AVG(estimated_cost/NULLIF(request_count,0)) as avg_cost_per_request,
            COUNT(CASE WHEN status_code >= 400 THEN 1 END) as error_count
        FROM api_cost_tracking 
        WHERE time_bucket >= :startDate AND time_bucket < :endDate
        GROUP BY date_trunc('day', time_bucket), cost_provider
        ORDER BY analysis_date DESC, cost_provider
        """, nativeQuery = true)
    List<Object[]> getDailyCostAnalysis(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query(value = """
        SELECT 
            date_trunc('month', time_bucket)::date as month_year,
            cost_provider,
            SUM(request_count) as total_requests,
            SUM(estimated_cost) as total_cost,
            AVG(estimated_cost/NULLIF(request_count,0)) as avg_cost_per_request,
            COUNT(DISTINCT api_endpoint) as unique_endpoints,
            COUNT(CASE WHEN status_code >= 400 THEN 1 END) as total_errors
        FROM api_cost_tracking 
        WHERE time_bucket >= date_trunc('month', CURRENT_DATE) - INTERVAL '12 months'
        GROUP BY date_trunc('month', time_bucket), cost_provider
        ORDER BY month_year DESC, cost_provider
        """, nativeQuery = true)
    List<Object[]> getMonthlyCostSummary();
    
    @Query(value = """
        SELECT COALESCE(SUM(estimated_cost), 0) 
        FROM api_cost_tracking 
        WHERE time_bucket >= date_trunc('month', CURRENT_DATE)
        AND cost_provider = :provider
        """, nativeQuery = true)
    BigDecimal getCurrentMonthCost(@Param("provider") String provider);
    
    @Query(value = """
        SELECT 
            api_endpoint,
            SUM(request_count) as total_requests,
            SUM(estimated_cost) as total_cost,
            AVG(response_time_ms) as avg_response_time
        FROM api_cost_tracking 
        WHERE time_bucket >= :startDate AND time_bucket < :endDate
        GROUP BY api_endpoint
        ORDER BY total_cost DESC
        LIMIT 10
        """, nativeQuery = true)
    List<Object[]> getTopCostlyEndpoints(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query(value = """
        SELECT * FROM get_budget_utilization(:provider, :monthYear)
        """, nativeQuery = true)
    List<Object[]> getBudgetUtilization(
        @Param("provider") String provider, 
        @Param("monthYear") LocalDate monthYear
    );
    
    @Query("SELECT a FROM ApiCostTrackingEntity a WHERE a.apiEndpoint = :apiEndpoint AND a.timeBucket = :timeBucket AND a.costProvider = :costProvider")
    Optional<ApiCostTrackingEntity> findByApiEndpointAndTimeBucketAndCostProvider(
        @Param("apiEndpoint") String apiEndpoint,
        @Param("timeBucket") LocalDateTime timeBucket,
        @Param("costProvider") String costProvider
    );
}