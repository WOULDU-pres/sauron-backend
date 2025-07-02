package com.sauron.routing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 라우팅 통계 DTO
 */
@Data
@Builder
public class RoutingStatistics {
    
    private int totalAdmins;
    private int availableChannels;
    private int totalChannels;
    private Map<String, Integer> channelHealth;
    private Instant generatedAt;
    
    /**
     * 채널 가용률 계산
     */
    public double getChannelAvailabilityRate() {
        return totalChannels > 0 ? (double) availableChannels / totalChannels * 100.0 : 0.0;
    }
}