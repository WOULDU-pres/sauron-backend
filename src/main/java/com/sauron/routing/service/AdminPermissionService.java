package com.sauron.routing.service;

import com.sauron.alert.service.FormattedAlert;
import com.sauron.routing.dto.UserPermissionSummary;
import com.sauron.routing.entity.AdminUser;
import com.sauron.routing.entity.PermissionGroup;
import com.sauron.routing.repository.PermissionGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * 관리자 권한 서비스
 * 관리자별 알림 수신 권한과 가용성을 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPermissionService {
    
    private final PermissionGroupRepository permissionGroupRepository;
    
    @Value("${admin.work-hours.start:09:00}")
    private String workHoursStart;
    
    @Value("${admin.work-hours.end:18:00}")
    private String workHoursEnd;
    
    @Value("${admin.emergency-override:true}")
    private boolean emergencyOverride;
    
    // 기본 권한 레벨
    private static final Set<String> SUPER_ADMIN_ROLES = Set.of("SUPER_ADMIN", "SYSTEM_ADMIN");
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "MANAGER");
    private static final Set<String> MODERATOR_ROLES = Set.of("MODERATOR", "OPERATOR");
    
    // 알림 타입별 최소 권한 레벨
    private static final Set<String> HIGH_SECURITY_ALERT_TYPES = Set.of("abuse", "inappropriate", "security");
    private static final Set<String> BUSINESS_ALERT_TYPES = Set.of("advertisement", "spam", "announcement");
    
    /**
     * 사용자가 알림을 받을 수 있는지 확인
     */
    @Cacheable(value = "admin-permissions", key = "#user.id + ':' + #alert.alertType + ':' + #alert.severity")
    public boolean canReceiveAlert(AdminUser user, FormattedAlert alert) {
        try {
            log.debug("Checking alert permission for user {} - Alert type: {}, Severity: {}", 
                     user.getUsername(), alert.getAlertType(), alert.getSeverity());
            
            // 1. 기본 활성화 상태 확인
            if (!user.isActive()) {
                log.debug("User {} is not active", user.getUsername());
                return false;
            }
            
            // 2. 역할 기반 권한 확인
            if (!hasRolePermission(user, alert)) {
                log.debug("User {} does not have role permission for alert type {}", 
                         user.getUsername(), alert.getAlertType());
                return false;
            }
            
            // 3. 알림 타입별 권한 확인
            if (!hasAlertTypePermission(user, alert.getAlertType())) {
                log.debug("User {} does not have permission for alert type {}", 
                         user.getUsername(), alert.getAlertType());
                return false;
            }
            
            // 4. 심각도별 권한 확인
            if (!hasSeverityPermission(user, alert.getSeverity())) {
                log.debug("User {} does not have permission for severity level {}", 
                         user.getUsername(), alert.getSeverity());
                return false;
            }
            
            // 5. 그룹 권한 확인
            if (!hasGroupPermission(user, alert)) {
                log.debug("User {} does not have group permission for this alert", user.getUsername());
                return false;
            }
            
            log.debug("User {} has permission to receive alert", user.getUsername());
            return true;
            
        } catch (Exception e) {
            log.error("Error checking alert permission for user {}", user.getUsername(), e);
            // 오류 시 보수적으로 권한 거부
            return false;
        }
    }
    
    /**
     * 사용자가 현재 사용 가능한지 확인
     */
    public boolean isUserAvailable(AdminUser user) {
        try {
            log.debug("Checking availability for user {}", user.getUsername());
            
            // 1. 기본 활성화 상태 확인
            if (!user.isActive()) {
                return false;
            }
            
            // 2. 알림 수신 설정 확인
            if (!user.isNotificationEnabled()) {
                log.debug("User {} has notifications disabled", user.getUsername());
                return false;
            }
            
            // 3. 근무 시간 확인 (긴급 상황에서는 무시)
            if (!emergencyOverride && !isWithinWorkHours()) {
                // 근무 시간 외에는 고위 관리자만
                if (!SUPER_ADMIN_ROLES.contains(user.getRole())) {
                    log.debug("User {} is not available outside work hours", user.getUsername());
                    return false;
                }
            }
            
            // 4. 사용자별 가용성 상태 확인
            if (user.getAvailabilityStatus() != null) {
                switch (user.getAvailabilityStatus().toUpperCase()) {
                    case "AVAILABLE":
                        return true;
                    case "BUSY":
                        // BUSY 상태에서는 고우선순위만
                        return user.isReceiveHighPriorityOnly();
                    case "AWAY":
                    case "DND":
                        return false;
                    default:
                        return true; // 알 수 없는 상태는 기본적으로 허용
                }
            }
            
            // 5. 최근 활동 시간 확인
            if (user.getLastActiveAt() != null) {
                Instant oneHourAgo = Instant.now().minusSeconds(3600);
                if (user.getLastActiveAt().isBefore(oneHourAgo)) {
                    log.debug("User {} has been inactive for over an hour", user.getUsername());
                    // 1시간 이상 비활성 상태면 긴급 알림만
                    return SUPER_ADMIN_ROLES.contains(user.getRole());
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error checking user availability for {}", user.getUsername(), e);
            return false;
        }
    }
    
    /**
     * 역할 기반 권한 확인
     */
    private boolean hasRolePermission(AdminUser user, FormattedAlert alert) {
        String role = user.getRole();
        String alertType = alert.getAlertType();
        String severity = alert.getSeverity();
        
        // 수퍼 관리자는 모든 권한
        if (SUPER_ADMIN_ROLES.contains(role)) {
            return true;
        }
        
        // 관리자는 대부분의 알림
        if (ADMIN_ROLES.contains(role)) {
            // 보안 관련 고심각도 알림은 수퍼 관리자만
            if (HIGH_SECURITY_ALERT_TYPES.contains(alertType) && "HIGH".equals(severity)) {
                return false;
            }
            return true;
        }
        
        // 모더레이터는 일반적인 알림만
        if (MODERATOR_ROLES.contains(role)) {
            return BUSINESS_ALERT_TYPES.contains(alertType) || "normal".equals(alertType);
        }
        
        // 기타 역할은 일반 알림만
        return "normal".equals(alertType) && !"HIGH".equals(severity);
    }
    
    /**
     * 알림 타입별 권한 확인
     */
    private boolean hasAlertTypePermission(AdminUser user, String alertType) {
        // 사용자의 알림 타입 필터 설정 확인
        if (user.getAllowedAlertTypes() != null && !user.getAllowedAlertTypes().isEmpty()) {
            return user.getAllowedAlertTypes().contains(alertType);
        }
        
        // 기본적으로 모든 타입 허용
        return true;
    }
    
    /**
     * 심각도별 권한 확인
     */
    private boolean hasSeverityPermission(AdminUser user, String severity) {
        // 고우선순위만 받는 설정인 경우
        if (user.isReceiveHighPriorityOnly()) {
            return "HIGH".equals(severity);
        }
        
        // 최소 심각도 레벨 확인
        if (user.getMinSeverityLevel() != null) {
            int userMinLevel = getSeverityLevel(user.getMinSeverityLevel());
            int alertLevel = getSeverityLevel(severity);
            return alertLevel >= userMinLevel;
        }
        
        return true;
    }
    
    /**
     * 그룹 권한 확인
     */
    private boolean hasGroupPermission(AdminUser user, FormattedAlert alert) {
        try {
            // 사용자가 속한 권한 그룹 조회
            List<PermissionGroup> userGroups = permissionGroupRepository.findByMembersContaining(user.getId());
            
            if (userGroups.isEmpty()) {
                // 그룹에 속하지 않은 경우 기본 권한 적용
                return true;
            }
            
            // 하나라도 허용하는 그룹이 있으면 통과
            for (PermissionGroup group : userGroups) {
                if (group.isActive() && groupAllowsAlert(group, alert)) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking group permission for user {}", user.getUsername(), e);
            return true; // 오류 시 허용
        }
    }
    
    /**
     * 그룹이 알림을 허용하는지 확인
     */
    private boolean groupAllowsAlert(PermissionGroup group, FormattedAlert alert) {
        // 그룹의 알림 타입 필터
        if (group.getAllowedAlertTypes() != null && !group.getAllowedAlertTypes().isEmpty()) {
            if (!group.getAllowedAlertTypes().contains(alert.getAlertType())) {
                return false;
            }
        }
        
        // 그룹의 심각도 필터
        if (group.getMinSeverityLevel() != null) {
            int groupMinLevel = getSeverityLevel(group.getMinSeverityLevel());
            int alertLevel = getSeverityLevel(alert.getSeverity());
            if (alertLevel < groupMinLevel) {
                return false;
            }
        }
        
        // 시간 기반 필터
        if (group.getActiveHoursStart() != null && group.getActiveHoursEnd() != null) {
            if (!isWithinTimeRange(group.getActiveHoursStart(), group.getActiveHoursEnd())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 근무 시간 내인지 확인
     */
    private boolean isWithinWorkHours() {
        try {
            LocalTime now = LocalTime.now(ZoneOffset.UTC);
            LocalTime start = LocalTime.parse(workHoursStart);
            LocalTime end = LocalTime.parse(workHoursEnd);
            
            return isWithinTimeRange(start, end);
            
        } catch (Exception e) {
            log.warn("Error checking work hours", e);
            return true; // 오류 시 허용
        }
    }
    
    /**
     * 시간 범위 내인지 확인
     */
    private boolean isWithinTimeRange(LocalTime start, LocalTime end) {
        LocalTime now = LocalTime.now(ZoneOffset.UTC);
        
        if (start.isBefore(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        } else {
            // 자정을 넘나드는 경우
            return !now.isBefore(start) || !now.isAfter(end);
        }
    }
    
    /**
     * 심각도 레벨 숫자 반환
     */
    private int getSeverityLevel(String severity) {
        switch (severity.toUpperCase()) {
            case "LOW": return 1;
            case "MEDIUM": return 2;
            case "HIGH": return 3;
            case "CRITICAL": return 4;
            default: return 2; // MEDIUM 기본값
        }
    }
    
    /**
     * 권한 설정 업데이트
     */
    public void updatePermissionConfig(String workHoursStart, String workHoursEnd, boolean emergencyOverride) {
        this.workHoursStart = workHoursStart;
        this.workHoursEnd = workHoursEnd;
        this.emergencyOverride = emergencyOverride;
        
        log.info("Permission configuration updated - Work hours: {}-{}, Emergency override: {}", 
                workHoursStart, workHoursEnd, emergencyOverride);
    }
    
    /**
     * 사용자 권한 요약 조회
     */
    public UserPermissionSummary getUserPermissionSummary(AdminUser user) {
        try {
            boolean canReceiveAlerts = user.isActive() && user.isNotificationEnabled();
            boolean isAvailable = isUserAvailable(user);
            List<PermissionGroup> groups = permissionGroupRepository.findByMembersContaining(user.getId());
            
            return UserPermissionSummary.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .active(user.isActive())
                .canReceiveAlerts(canReceiveAlerts)
                .isAvailable(isAvailable)
                .notificationEnabled(user.isNotificationEnabled())
                .receiveHighPriorityOnly(user.isReceiveHighPriorityOnly())
                .availabilityStatus(user.getAvailabilityStatus())
                .groupCount(groups.size())
                .lastActiveAt(user.getLastActiveAt())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating user permission summary for {}", user.getUsername(), e);
            throw new PermissionException("Failed to generate user permission summary", e);
        }
    }
    
    /**
     * 권한 예외 클래스
     */
    public static class PermissionException extends RuntimeException {
        public PermissionException(String message) {
            super(message);
        }
        
        public PermissionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}