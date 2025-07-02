package com.sauron.routing.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 라우팅 규칙 DTO
 */
@Data
@Builder
public class RoutingRule {
    
    private String name;
    private List<String> targetChannels;
    private List<Long> targetUserIds;
    private String alertTypeFilter;
    private String severityFilter;
    private boolean active;
    
    /**
     * 기본 라우팅 규칙
     */
    public static RoutingRule defaultRule() {
        return RoutingRule.builder()
            .name("DEFAULT")
            .active(true)
            .build();
    }
}