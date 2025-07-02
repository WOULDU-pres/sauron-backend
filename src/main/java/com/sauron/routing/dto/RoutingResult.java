package com.sauron.routing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 라우팅 결과 DTO
 */
@Data
@Builder
public class RoutingResult {
    
    private boolean success;
    private String message;
    private int successCount;
    private int failureCount;
    private List<String> successChannels;
    private List<String> failureChannels;
    private long totalDuration;
    private String alertId;
    private String routingRule;
    private Instant timestamp;
    
    /**
     * 성공 여부 확인
     */
    public boolean hasSuccesses() {
        return successCount > 0;
    }
    
    /**
     * 실패 여부 확인
     */
    public boolean hasFailures() {
        return failureCount > 0;
    }
    
    /**
     * 실패 결과 생성
     */
    public static RoutingResult failure(String message) {
        return RoutingResult.builder()
            .success(false)
            .message(message)
            .successCount(0)
            .failureCount(1)
            .timestamp(Instant.now())
            .build();
    }
}