package com.sauron.routing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 사용자 권한 요약 DTO
 */
@Data
@Builder
public class UserPermissionSummary {
    
    private Long userId;
    private String username;
    private String role;
    private boolean active;
    private boolean canReceiveAlerts;
    private boolean isAvailable;
    private boolean notificationEnabled;
    private boolean receiveHighPriorityOnly;
    private String availabilityStatus;
    private int groupCount;
    private Instant lastActiveAt;
}