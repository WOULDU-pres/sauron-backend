package com.sauron.alert.service;

import com.sauron.alert.entity.Alert;
import com.sauron.common.core.async.AsyncExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 향상된 알림 디스패처 서비스
 * 채널별 어댑터 패턴을 사용하여 다중 채널 알림을 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertDispatcherService {
    
    private final List<AlertChannelAdapter> channelAdapters;
    private final AlertFormatterService formatterService;
    private final AsyncExecutor asyncExecutor;
    
    /**
     * 알림 전송 결과 DTO
     */
    public static class DispatchResult {
        private final boolean overallSuccess;
        private final List<ChannelResult> channelResults;
        private final long totalProcessingTime;
        private final String alertId;
        
        public DispatchResult(boolean overallSuccess, List<ChannelResult> channelResults,
                            long totalProcessingTime, String alertId) {
            this.overallSuccess = overallSuccess;
            this.channelResults = channelResults;
            this.totalProcessingTime = totalProcessingTime;
            this.alertId = alertId;
        }
        
        // Getters
        public boolean isOverallSuccess() { return overallSuccess; }
        public List<ChannelResult> getChannelResults() { return channelResults; }
        public long getTotalProcessingTime() { return totalProcessingTime; }
        public String getAlertId() { return alertId; }
        
        /**
         * 5초 이내 전송 완료 여부 확인
         */
        public boolean isWithinTimeLimit() {
            return totalProcessingTime <= 5000;
        }
        
        /**
         * 성공한 채널 수 반환
         */
        public long getSuccessfulChannels() {
            return channelResults.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        }
        
        /**
         * 실패한 채널 수 반환
         */
        public long getFailedChannels() {
            return channelResults.stream().mapToLong(r -> r.isSuccess() ? 0 : 1).sum();
        }
    }
    
    /**
     * 채널별 전송 결과 DTO
     */
    public static class ChannelResult {
        private final String channelName;
        private final boolean success;
        private final String errorMessage;
        private final long processingTime;
        private final boolean fallbackUsed;
        
        public ChannelResult(String channelName, boolean success, String errorMessage,
                           long processingTime, boolean fallbackUsed) {
            this.channelName = channelName;
            this.success = success;
            this.errorMessage = errorMessage;
            this.processingTime = processingTime;
            this.fallbackUsed = fallbackUsed;
        }
        
        public static ChannelResult success(String channelName, long processingTime) {
            return new ChannelResult(channelName, true, null, processingTime, false);
        }
        
        public static ChannelResult failure(String channelName, String errorMessage, long processingTime) {
            return new ChannelResult(channelName, false, errorMessage, processingTime, false);
        }
        
        public static ChannelResult fallback(String channelName, long processingTime) {
            return new ChannelResult(channelName, true, null, processingTime, true);
        }
        
        // Getters
        public String getChannelName() { return channelName; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getProcessingTime() { return processingTime; }
        public boolean isFallbackUsed() { return fallbackUsed; }
    }
    
    /**
     * 이상 메시지 감지 시 실시간 알림 전송
     * 
     * @param alert 알림 정보
     * @return CompletableFuture<DispatchResult>
     */
    public CompletableFuture<DispatchResult> dispatchAlert(Alert alert) {
        String alertId = alert.getId() != null ? alert.getId().toString() : "unknown";
        Instant startTime = Instant.now();
        
        log.info("Starting alert dispatch - ID: {}, Type: {}, Severity: {}", 
                alertId, alert.getAlertType(), alert.getSeverity());
        
        return asyncExecutor.executeWithTimeout(
            () -> performDispatch(alert, startTime),
            "alert-dispatch-" + alertId,
            5000L // 5초 타임아웃
        ).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Alert dispatch failed for ID: {} - {}", alertId, throwable.getMessage());
            } else {
                log.info("Alert dispatch completed - ID: {}, Success: {}, Time: {}ms, Channels: {}/{}", 
                        alertId, result.isOverallSuccess(), result.getTotalProcessingTime(),
                        result.getSuccessfulChannels(), result.getChannelResults().size());
            }
        });
    }
    
    /**
     * 실제 디스패치 수행
     */
    private DispatchResult performDispatch(Alert alert, Instant startTime) {
        String alertId = alert.getId() != null ? alert.getId().toString() : "unknown";
        List<ChannelResult> channelResults = new ArrayList<>();
        
        try {
            // 1. 알림 포맷팅
            FormattedAlert formattedAlert = formatterService.formatAlert(alert);
            
            // 2. 활성화된 채널 어댑터들에 병렬 전송
            List<CompletableFuture<ChannelResult>> channelTasks = channelAdapters.stream()
                .filter(adapter -> adapter.isEnabled() && adapter.supportsAlertType(alert.getAlertType()))
                .map(adapter -> sendToChannel(adapter, formattedAlert))
                .toList();
            
            // 3. 모든 채널 전송 완료 대기
            CompletableFuture<Void> allChannels = CompletableFuture.allOf(
                channelTasks.toArray(new CompletableFuture[0])
            );
            
            allChannels.get(4, java.util.concurrent.TimeUnit.SECONDS); // 4초 내에 완료되어야 함
            
            // 4. 결과 수집
            for (CompletableFuture<ChannelResult> task : channelTasks) {
                try {
                    channelResults.add(task.get());
                } catch (Exception e) {
                    log.error("Failed to get channel result: {}", e.getMessage());
                    channelResults.add(ChannelResult.failure("unknown", e.getMessage(), 0));
                }
            }
            
            Instant endTime = Instant.now();
            long totalTime = Duration.between(startTime, endTime).toMillis();
            
            // 5. 전체 성공 여부 판단 (최소 하나의 채널이 성공하면 성공)
            boolean overallSuccess = channelResults.stream().anyMatch(ChannelResult::isSuccess);
            
            return new DispatchResult(overallSuccess, channelResults, totalTime, alertId);
            
        } catch (Exception e) {
            log.error("Dispatch process failed for alert: {}", alertId, e);
            
            Instant endTime = Instant.now();
            long totalTime = Duration.between(startTime, endTime).toMillis();
            
            channelResults.add(ChannelResult.failure("all", "Dispatch process failed: " + e.getMessage(), totalTime));
            return new DispatchResult(false, channelResults, totalTime, alertId);
        }
    }
    
    /**
     * 개별 채널로 전송
     */
    private CompletableFuture<ChannelResult> sendToChannel(AlertChannelAdapter adapter, FormattedAlert formattedAlert) {
        return asyncExecutor.executeWithFallback(
            // 주 전송
            () -> {
                Instant channelStart = Instant.now();
                try {
                    adapter.sendAlert(formattedAlert);
                    long channelTime = Duration.between(channelStart, Instant.now()).toMillis();
                    return ChannelResult.success(adapter.getChannelName(), channelTime);
                } catch (Exception e) {
                    long channelTime = Duration.between(channelStart, Instant.now()).toMillis();
                    throw new RuntimeException("Channel send failed: " + e.getMessage());
                }
            },
            // 폴백 전송
            () -> {
                Instant fallbackStart = Instant.now();
                try {
                    if (adapter.supportsFallback()) {
                        adapter.sendFallbackAlert(formattedAlert);
                        long fallbackTime = Duration.between(fallbackStart, Instant.now()).toMillis();
                        return ChannelResult.fallback(adapter.getChannelName(), fallbackTime);
                    } else {
                        long fallbackTime = Duration.between(fallbackStart, Instant.now()).toMillis();
                        return ChannelResult.failure(adapter.getChannelName(), "No fallback available", fallbackTime);
                    }
                } catch (Exception e) {
                    long fallbackTime = Duration.between(fallbackStart, Instant.now()).toMillis();
                    return ChannelResult.failure(adapter.getChannelName(), "Fallback failed: " + e.getMessage(), fallbackTime);
                }
            },
            "channel-dispatch-" + adapter.getChannelName()
        );
    }
    
    /**
     * 우선순위 알림 전송 (더 빠른 처리가 필요한 알림)
     * 
     * @param alert 알림 정보
     * @return CompletableFuture<DispatchResult>
     */
    public CompletableFuture<DispatchResult> dispatchHighPriorityAlert(Alert alert) {
        String alertId = alert.getId() != null ? alert.getId().toString() : "unknown";
        log.warn("Dispatching HIGH PRIORITY alert - ID: {}, Type: {}", alertId, alert.getAlertType());
        
        return asyncExecutor.executeWithTimeout(
            () -> performHighPriorityDispatch(alert, Instant.now()),
            "high-priority-alert-" + alertId,
            3000L // 3초 타임아웃 (더 짧음)
        );
    }
    
    /**
     * 고우선순위 알림 디스패치 수행
     */
    private DispatchResult performHighPriorityDispatch(Alert alert, Instant startTime) {
        // 고우선순위 채널만 선택하여 전송
        List<AlertChannelAdapter> highPriorityAdapters = channelAdapters.stream()
            .filter(adapter -> adapter.isEnabled() && adapter.supportsHighPriority())
            .toList();
        
        if (highPriorityAdapters.isEmpty()) {
            log.warn("No high priority channels available, falling back to regular dispatch");
            return performDispatch(alert, startTime);
        }
        
        // 고우선순위 어댑터들로만 전송
        FormattedAlert formattedAlert = formatterService.formatAlert(alert);
        List<ChannelResult> results = new ArrayList<>();
        
        for (AlertChannelAdapter adapter : highPriorityAdapters) {
            try {
                Instant channelStart = Instant.now();
                adapter.sendHighPriorityAlert(formattedAlert);
                long channelTime = Duration.between(channelStart, Instant.now()).toMillis();
                results.add(ChannelResult.success(adapter.getChannelName(), channelTime));
            } catch (Exception e) {
                log.error("High priority channel failed: {}", adapter.getChannelName(), e);
                results.add(ChannelResult.failure(adapter.getChannelName(), e.getMessage(), 0));
            }
        }
        
        Instant endTime = Instant.now();
        long totalTime = Duration.between(startTime, endTime).toMillis();
        boolean success = results.stream().anyMatch(ChannelResult::isSuccess);
        
        return new DispatchResult(success, results, totalTime, 
                                alert.getId() != null ? alert.getId().toString() : "unknown");
    }
    
    /**
     * 배치 알림 전송 (여러 알림을 효율적으로 처리)
     * 
     * @param alerts 알림 목록
     * @return CompletableFuture<List<DispatchResult>>
     */
    public CompletableFuture<List<DispatchResult>> dispatchBatchAlerts(List<Alert> alerts) {
        log.info("Starting batch alert dispatch for {} alerts", alerts.size());
        
        List<CompletableFuture<DispatchResult>> tasks = alerts.stream()
            .map(this::dispatchAlert)
            .toList();
        
        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
            .thenApply(v -> tasks.stream()
                .map(CompletableFuture::join)
                .toList());
    }
    
    /**
     * 디스패처 상태 확인
     */
    public DispatcherHealthStatus getHealthStatus() {
        List<ChannelHealthStatus> channelStatuses = channelAdapters.stream()
            .map(adapter -> new ChannelHealthStatus(
                adapter.getChannelName(),
                adapter.isEnabled(),
                adapter.isHealthy(),
                adapter.getLastSuccessTime(),
                adapter.getConsecutiveFailures()
            ))
            .toList();
        
        boolean overallHealthy = channelStatuses.stream()
            .anyMatch(status -> status.isEnabled() && status.isHealthy());
        
        return new DispatcherHealthStatus(overallHealthy, channelStatuses);
    }
    
    /**
     * 디스패처 통계 조회
     */
    public DispatcherStatistics getStatistics() {
        // TODO: 실제 메트릭 수집 구현
        return DispatcherStatistics.builder()
            .totalDispatched(0L)
            .successfulDispatches(0L)
            .failedDispatches(0L)
            .averageDispatchTime(0.0)
            .channelCount(channelAdapters.size())
            .highPriorityDispatches(0L)
            .build();
    }
    
    /**
     * 디스패처 상태 DTO
     */
    public static class DispatcherHealthStatus {
        private final boolean overallHealthy;
        private final List<ChannelHealthStatus> channelStatuses;
        
        public DispatcherHealthStatus(boolean overallHealthy, List<ChannelHealthStatus> channelStatuses) {
            this.overallHealthy = overallHealthy;
            this.channelStatuses = channelStatuses;
        }
        
        public boolean isOverallHealthy() { return overallHealthy; }
        public List<ChannelHealthStatus> getChannelStatuses() { return channelStatuses; }
    }
    
    /**
     * 채널 상태 DTO
     */
    public static class ChannelHealthStatus {
        private final String channelName;
        private final boolean enabled;
        private final boolean healthy;
        private final Instant lastSuccessTime;
        private final int consecutiveFailures;
        
        public ChannelHealthStatus(String channelName, boolean enabled, boolean healthy,
                                 Instant lastSuccessTime, int consecutiveFailures) {
            this.channelName = channelName;
            this.enabled = enabled;
            this.healthy = healthy;
            this.lastSuccessTime = lastSuccessTime;
            this.consecutiveFailures = consecutiveFailures;
        }
        
        public String getChannelName() { return channelName; }
        public boolean isEnabled() { return enabled; }
        public boolean isHealthy() { return healthy; }
        public Instant getLastSuccessTime() { return lastSuccessTime; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
    }
    
    /**
     * 디스패처 통계 DTO
     */
    public static class DispatcherStatistics {
        private final long totalDispatched;
        private final long successfulDispatches;
        private final long failedDispatches;
        private final double averageDispatchTime;
        private final int channelCount;
        private final long highPriorityDispatches;
        
        private DispatcherStatistics(long totalDispatched, long successfulDispatches, long failedDispatches,
                                   double averageDispatchTime, int channelCount, long highPriorityDispatches) {
            this.totalDispatched = totalDispatched;
            this.successfulDispatches = successfulDispatches;
            this.failedDispatches = failedDispatches;
            this.averageDispatchTime = averageDispatchTime;
            this.channelCount = channelCount;
            this.highPriorityDispatches = highPriorityDispatches;
        }
        
        public static DispatcherStatisticsBuilder builder() {
            return new DispatcherStatisticsBuilder();
        }
        
        // Getters
        public long getTotalDispatched() { return totalDispatched; }
        public long getSuccessfulDispatches() { return successfulDispatches; }
        public long getFailedDispatches() { return failedDispatches; }
        public double getAverageDispatchTime() { return averageDispatchTime; }
        public int getChannelCount() { return channelCount; }
        public long getHighPriorityDispatches() { return highPriorityDispatches; }
        
        public double getSuccessRate() {
            return totalDispatched > 0 ? (double) successfulDispatches / totalDispatched : 0.0;
        }
        
        public static class DispatcherStatisticsBuilder {
            private long totalDispatched;
            private long successfulDispatches;
            private long failedDispatches;
            private double averageDispatchTime;
            private int channelCount;
            private long highPriorityDispatches;
            
            public DispatcherStatisticsBuilder totalDispatched(long totalDispatched) {
                this.totalDispatched = totalDispatched;
                return this;
            }
            
            public DispatcherStatisticsBuilder successfulDispatches(long successfulDispatches) {
                this.successfulDispatches = successfulDispatches;
                return this;
            }
            
            public DispatcherStatisticsBuilder failedDispatches(long failedDispatches) {
                this.failedDispatches = failedDispatches;
                return this;
            }
            
            public DispatcherStatisticsBuilder averageDispatchTime(double averageDispatchTime) {
                this.averageDispatchTime = averageDispatchTime;
                return this;
            }
            
            public DispatcherStatisticsBuilder channelCount(int channelCount) {
                this.channelCount = channelCount;
                return this;
            }
            
            public DispatcherStatisticsBuilder highPriorityDispatches(long highPriorityDispatches) {
                this.highPriorityDispatches = highPriorityDispatches;
                return this;
            }
            
            public DispatcherStatistics build() {
                return new DispatcherStatistics(totalDispatched, successfulDispatches, failedDispatches,
                                              averageDispatchTime, channelCount, highPriorityDispatches);
            }
        }
    }
}