package com.sauron.routing.service;

import com.sauron.alert.service.AlertChannelAdapter;
import com.sauron.alert.service.FormattedAlert;
import com.sauron.common.core.async.AsyncExecutor;
import com.sauron.routing.dto.RoutingRule;
import com.sauron.routing.dto.RoutingResult;
import com.sauron.routing.dto.RoutingStatistics;
import com.sauron.routing.entity.AdminUser;
import com.sauron.routing.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 채널 라우팅 서비스
 * 다중 채널 및 수신자별 알림 라우팅을 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelRoutingService {
    
    private final AsyncExecutor asyncExecutor;
    private final AdminUserRepository adminUserRepository;
    private final List<AlertChannelAdapter> channelAdapters;
    private final AdminPermissionService permissionService;
    
    @Value("${routing.default-channels:console,telegram}")
    private String defaultChannels;
    
    @Value("${routing.high-priority-channels:telegram}")
    private String highPriorityChannels;
    
    @Value("${routing.fallback-channels:console}")
    private String fallbackChannels;
    
    @Value("${routing.max-concurrent-sends:10}")
    private int maxConcurrentSends;
    
    @Value("${routing.timeout-ms:5000}")
    private long timeoutMs;
    
    /**
     * 알림을 적절한 채널과 수신자에게 라우팅
     */
    public CompletableFuture<RoutingResult> routeAlert(FormattedAlert alert, RoutingRule rule) {
        return asyncExecutor.executeWithTimeout(() -> {
            try {
                log.debug("Starting alert routing - Alert ID: {}, Rule: {}", 
                         alert.getAlertId(), rule.getName());
                
                // 1. 라우팅 규칙 적용
                List<String> targetChannels = determineTargetChannels(alert, rule);
                List<AdminUser> targetUsers = determineTargetUsers(alert, rule);
                
                // 2. 채널별 병렬 전송
                List<CompletableFuture<ChannelResult>> channelFutures = new ArrayList<>();
                
                for (String channelName : targetChannels) {
                    AlertChannelAdapter adapter = findChannelAdapter(channelName);
                    if (adapter != null && adapter.isEnabled() && adapter.isHealthy()) {
                        CompletableFuture<ChannelResult> future = sendToChannel(adapter, alert, targetUsers, rule);
                        channelFutures.add(future);
                    } else {
                        log.warn("Channel {} is not available for alert {}", channelName, alert.getAlertId());
                    }
                }
                
                // 3. 모든 채널 전송 결과 수집
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    channelFutures.toArray(new CompletableFuture[0])
                );
                
                List<ChannelResult> results = allFutures
                    .thenApply(v -> channelFutures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()))
                    .get();
                
                // 4. 결과 분석 및 폴백 처리
                RoutingResult routingResult = analyzeResults(results, alert, rule);
                
                if (routingResult.hasFailures() && !routingResult.hasSuccesses()) {
                    log.warn("All primary channels failed for alert {}, executing fallback", alert.getAlertId());
                    routingResult = executeFallback(alert, rule);
                }
                
                log.info("Alert routing completed - Alert ID: {}, Success: {}, Failures: {}", 
                        alert.getAlertId(), routingResult.getSuccessCount(), routingResult.getFailureCount());
                
                return routingResult;
                
            } catch (Exception e) {
                log.error("Failed to route alert - Alert ID: {}", alert.getAlertId(), e);
                return RoutingResult.failure("Routing failed: " + e.getMessage());
            }
        }, "AlertRouting", timeoutMs);
    }
    
    /**
     * 대상 채널 결정
     */
    private List<String> determineTargetChannels(FormattedAlert alert, RoutingRule rule) {
        List<String> channels = new ArrayList<>();
        
        // 1. 라우팅 규칙의 채널 설정
        if (rule.getTargetChannels() != null && !rule.getTargetChannels().isEmpty()) {
            channels.addAll(rule.getTargetChannels());
        } else {
            // 2. 심각도별 기본 채널
            if ("HIGH".equals(alert.getSeverity())) {
                channels.addAll(Arrays.asList(highPriorityChannels.split(",")));
            } else {
                channels.addAll(Arrays.asList(defaultChannels.split(",")));
            }
        }
        
        // 3. 알림 타입별 채널 필터링
        channels = channels.stream()
            .filter(channel -> supportsAlertType(channel, alert.getAlertType()))
            .map(String::trim)
            .collect(Collectors.toList());
        
        log.debug("Determined target channels for alert {}: {}", alert.getAlertId(), channels);
        return channels;
    }
    
    /**
     * 대상 사용자 결정
     */
    private List<AdminUser> determineTargetUsers(FormattedAlert alert, RoutingRule rule) {
        List<AdminUser> users = new ArrayList<>();
        
        try {
            // 1. 라우팅 규칙의 사용자 설정
            if (rule.getTargetUserIds() != null && !rule.getTargetUserIds().isEmpty()) {
                users.addAll(adminUserRepository.findByIdIn(rule.getTargetUserIds()));
            } else {
                // 2. 권한별 기본 사용자
                if ("HIGH".equals(alert.getSeverity())) {
                    users.addAll(adminUserRepository.findByRoleAndActive("ADMIN", true));
                    users.addAll(adminUserRepository.findByRoleAndActive("SUPER_ADMIN", true));
                } else {
                    users.addAll(adminUserRepository.findByActive(true));
                }
            }
            
            // 3. 권한 및 가용성 필터링
            users = users.stream()
                .filter(user -> permissionService.canReceiveAlert(user, alert))
                .filter(user -> permissionService.isUserAvailable(user))
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to determine target users for alert {}", alert.getAlertId(), e);
            // 기본 관리자들로 폴백
            users.addAll(adminUserRepository.findByRoleAndActive("ADMIN", true));
        }
        
        log.debug("Determined target users for alert {}: {} users", alert.getAlertId(), users.size());
        return users;
    }
    
    /**
     * 채널에 알림 전송
     */
    private CompletableFuture<ChannelResult> sendToChannel(AlertChannelAdapter adapter, 
                                                          FormattedAlert alert, 
                                                          List<AdminUser> targetUsers, 
                                                          RoutingRule rule) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Sending alert {} to channel {}", alert.getAlertId(), adapter.getChannelName());
                
                Instant startTime = Instant.now();
                
                // 고우선순위 알림 처리
                if ("HIGH".equals(alert.getSeverity()) && adapter.supportsHighPriority()) {
                    adapter.sendHighPriorityAlert(alert);
                } else {
                    adapter.sendAlert(alert);
                }
                
                long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
                
                return ChannelResult.success(adapter.getChannelName(), targetUsers.size(), duration);
                
            } catch (Exception e) {
                log.error("Failed to send alert {} to channel {}", 
                         alert.getAlertId(), adapter.getChannelName(), e);
                return ChannelResult.failure(adapter.getChannelName(), e.getMessage());
            }
        });
    }
    
    /**
     * 결과 분석
     */
    private RoutingResult analyzeResults(List<ChannelResult> results, FormattedAlert alert, RoutingRule rule) {
        int successCount = 0;
        int failureCount = 0;
        List<String> successChannels = new ArrayList<>();
        List<String> failureChannels = new ArrayList<>();
        long totalDuration = 0;
        
        for (ChannelResult result : results) {
            if (result.isSuccess()) {
                successCount++;
                successChannels.add(result.getChannelName());
                totalDuration += result.getDuration();
            } else {
                failureCount++;
                failureChannels.add(result.getChannelName());
            }
        }
        
        boolean overallSuccess = successCount > 0;
        String message = String.format("Routed to %d channels: %d success, %d failures", 
                                     results.size(), successCount, failureCount);
        
        return RoutingResult.builder()
            .success(overallSuccess)
            .message(message)
            .successCount(successCount)
            .failureCount(failureCount)
            .successChannels(successChannels)
            .failureChannels(failureChannels)
            .totalDuration(totalDuration)
            .alertId(alert.getAlertId())
            .routingRule(rule.getName())
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * 폴백 실행
     */
    private RoutingResult executeFallback(FormattedAlert alert, RoutingRule rule) {
        try {
            log.info("Executing fallback routing for alert {}", alert.getAlertId());
            
            List<String> fallbackChannelList = Arrays.asList(fallbackChannels.split(","));
            
            for (String channelName : fallbackChannelList) {
                AlertChannelAdapter adapter = findChannelAdapter(channelName.trim());
                if (adapter != null && adapter.isEnabled() && adapter.supportsFallback()) {
                    try {
                        adapter.sendFallbackAlert(alert);
                        
                        return RoutingResult.builder()
                            .success(true)
                            .message("Fallback routing successful via " + channelName)
                            .successCount(1)
                            .failureCount(0)
                            .successChannels(List.of(channelName))
                            .failureChannels(new ArrayList<>())
                            .alertId(alert.getAlertId())
                            .routingRule("FALLBACK")
                            .timestamp(Instant.now())
                            .build();
                            
                    } catch (Exception e) {
                        log.warn("Fallback channel {} also failed for alert {}", 
                                channelName, alert.getAlertId(), e);
                    }
                }
            }
            
            // 모든 폴백 채널도 실패
            return RoutingResult.failure("All fallback channels failed");
            
        } catch (Exception e) {
            log.error("Failed to execute fallback routing for alert {}", alert.getAlertId(), e);
            return RoutingResult.failure("Fallback execution failed: " + e.getMessage());
        }
    }
    
    /**
     * 채널 어댑터 찾기
     */
    private AlertChannelAdapter findChannelAdapter(String channelName) {
        return channelAdapters.stream()
            .filter(adapter -> adapter.getChannelName().equalsIgnoreCase(channelName))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 채널이 알림 타입을 지원하는지 확인
     */
    private boolean supportsAlertType(String channelName, String alertType) {
        AlertChannelAdapter adapter = findChannelAdapter(channelName);
        return adapter != null && adapter.supportsAlertType(alertType);
    }
    
    /**
     * 라우팅 통계 조회
     */
    public RoutingStatistics getRoutingStatistics() {
        try {
            // 최근 24시간 통계
            Instant since = Instant.now().minusSeconds(24 * 3600);
            
            int totalAdmins = adminUserRepository.countByActive(true);
            int availableChannels = (int) channelAdapters.stream()
                .filter(adapter -> adapter.isEnabled() && adapter.isHealthy())
                .count();
            
            Map<String, Integer> channelHealth = channelAdapters.stream()
                .collect(Collectors.toMap(
                    AlertChannelAdapter::getChannelName,
                    adapter -> adapter.isHealthy() ? 1 : 0
                ));
            
            return RoutingStatistics.builder()
                .totalAdmins(totalAdmins)
                .availableChannels(availableChannels)
                .totalChannels(channelAdapters.size())
                .channelHealth(channelHealth)
                .generatedAt(Instant.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate routing statistics", e);
            throw new RoutingException("Failed to generate routing statistics", e);
        }
    }
    
    /**
     * 라우팅 설정 업데이트
     */
    public void updateRoutingConfig(String defaultChannels, String highPriorityChannels, 
                                  String fallbackChannels, int maxConcurrentSends) {
        this.defaultChannels = defaultChannels;
        this.highPriorityChannels = highPriorityChannels;
        this.fallbackChannels = fallbackChannels;
        this.maxConcurrentSends = maxConcurrentSends;
        
        log.info("Routing configuration updated - Default: {}, High priority: {}, Fallback: {}, Max concurrent: {}", 
                defaultChannels, highPriorityChannels, fallbackChannels, maxConcurrentSends);
    }
    
    /**
     * 채널 결과 클래스
     */
    public static class ChannelResult {
        private final boolean success;
        private final String channelName;
        private final String errorMessage;
        private final int recipientCount;
        private final long duration;
        
        private ChannelResult(boolean success, String channelName, String errorMessage, 
                            int recipientCount, long duration) {
            this.success = success;
            this.channelName = channelName;
            this.errorMessage = errorMessage;
            this.recipientCount = recipientCount;
            this.duration = duration;
        }
        
        public static ChannelResult success(String channelName, int recipientCount, long duration) {
            return new ChannelResult(true, channelName, null, recipientCount, duration);
        }
        
        public static ChannelResult failure(String channelName, String errorMessage) {
            return new ChannelResult(false, channelName, errorMessage, 0, 0);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getChannelName() { return channelName; }
        public String getErrorMessage() { return errorMessage; }
        public int getRecipientCount() { return recipientCount; }
        public long getDuration() { return duration; }
    }
    
    /**
     * 라우팅 예외 클래스
     */
    public static class RoutingException extends RuntimeException {
        public RoutingException(String message) {
            super(message);
        }
        
        public RoutingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}