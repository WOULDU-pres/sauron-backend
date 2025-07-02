package com.sauron.operations.service;

import com.sauron.operations.entity.ApiCostTrackingEntity;
import com.sauron.operations.repository.ApiCostTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CostTrackingService {
    
    private final ApiCostTrackingRepository costTrackingRepository;
    
    // Cost per request for different providers (in USD)
    private static final Map<String, BigDecimal> PROVIDER_COSTS = Map.of(
        "gemini", new BigDecimal("0.0015"), // $0.0015 per request
        "kakao", new BigDecimal("0.0001"),  // $0.0001 per request  
        "telegram", new BigDecimal("0.0000"), // Free
        "discord", new BigDecimal("0.0000")   // Free
    );
    
    @Transactional
    public void recordApiCost(String apiEndpoint, String costProvider, int requestCount, 
                             Integer statusCode, Integer responseTimeMs) {
        recordApiCost(apiEndpoint, costProvider, requestCount, statusCode, responseTimeMs, new HashMap<>());
    }
    
    @Transactional
    public void recordApiCost(String apiEndpoint, String costProvider, int requestCount, 
                             Integer statusCode, Integer responseTimeMs, Map<String, Object> metadata) {
        try {
            BigDecimal costPerRequest = PROVIDER_COSTS.getOrDefault(costProvider.toLowerCase(), BigDecimal.ZERO);
            BigDecimal estimatedCost = costPerRequest.multiply(BigDecimal.valueOf(requestCount));
            
            // Round to hour for aggregation
            LocalDateTime timeBucket = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
            
            // Check if entry exists for this hour
            ApiCostTrackingEntity existingEntry = costTrackingRepository
                .findByApiEndpointAndTimeBucketAndCostProvider(apiEndpoint, timeBucket, costProvider)
                .orElse(null);
            
            if (existingEntry != null) {
                // Update existing entry
                existingEntry.setRequestCount(existingEntry.getRequestCount() + requestCount);
                existingEntry.setEstimatedCost(existingEntry.getEstimatedCost().add(estimatedCost));
                
                // Update response time (weighted average)
                if (responseTimeMs != null && existingEntry.getResponseTimeMs() != null) {
                    int totalRequests = existingEntry.getRequestCount();
                    int oldRequests = totalRequests - requestCount;
                    int weightedAvg = ((existingEntry.getResponseTimeMs() * oldRequests) + (responseTimeMs * requestCount)) / totalRequests;
                    existingEntry.setResponseTimeMs(weightedAvg);
                } else if (responseTimeMs != null) {
                    existingEntry.setResponseTimeMs(responseTimeMs);
                }
                
                // Merge metadata
                if (metadata != null && !metadata.isEmpty()) {
                    Map<String, Object> mergedMetadata = new HashMap<>(existingEntry.getMetadata() != null ? existingEntry.getMetadata() : new HashMap<>());
                    mergedMetadata.putAll(metadata);
                    existingEntry.setMetadata(mergedMetadata);
                }
                
                costTrackingRepository.save(existingEntry);
                log.debug("Updated API cost tracking for {} - {} requests, ${}", apiEndpoint, requestCount, estimatedCost);
                
            } else {
                // Create new entry
                ApiCostTrackingEntity newEntry = ApiCostTrackingEntity.builder()
                    .apiEndpoint(apiEndpoint)
                    .requestCount(requestCount)
                    .estimatedCost(estimatedCost)
                    .timeBucket(timeBucket)
                    .costProvider(costProvider)
                    .statusCode(statusCode != null ? statusCode : 200)
                    .responseTimeMs(responseTimeMs)
                    .metadata(metadata)
                    .createdAt(LocalDateTime.now())
                    .build();
                
                costTrackingRepository.save(newEntry);
                log.debug("Created new API cost tracking for {} - {} requests, ${}", apiEndpoint, requestCount, estimatedCost);
            }
            
            // Log warning if cost is high
            if (estimatedCost.compareTo(new BigDecimal("10.0")) > 0) {
                log.warn("High API cost detected: {} requests to {} cost ${}", requestCount, apiEndpoint, estimatedCost);
            }
            
        } catch (Exception e) {
            log.error("Failed to record API cost for endpoint {}: {}", apiEndpoint, e.getMessage(), e);
            // Don't throw exception to avoid breaking the main business logic
        }
    }
    
    public void recordGeminiApiCost(int requestCount, Integer responseTimeMs) {
        recordApiCost("/api/v1/gemini/analyze", "gemini", requestCount, 200, responseTimeMs);
    }
    
    public void recordKakaoApiCost(String endpoint, int requestCount, Integer statusCode, Integer responseTimeMs) {
        recordApiCost(endpoint, "kakao", requestCount, statusCode, responseTimeMs);
    }
    
    public void recordTelegramApiCost(String endpoint, int requestCount, Integer responseTimeMs) {
        recordApiCost(endpoint, "telegram", requestCount, 200, responseTimeMs);
    }
    
    public BigDecimal getEstimatedCost(String provider, int requestCount) {
        BigDecimal costPerRequest = PROVIDER_COSTS.getOrDefault(provider.toLowerCase(), BigDecimal.ZERO);
        return costPerRequest.multiply(BigDecimal.valueOf(requestCount));
    }
    
    public Map<String, BigDecimal> getAllProviderCosts() {
        return new HashMap<>(PROVIDER_COSTS);
    }
}