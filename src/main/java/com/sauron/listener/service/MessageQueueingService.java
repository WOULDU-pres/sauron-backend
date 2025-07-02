package com.sauron.listener.service;

import com.sauron.common.core.async.AsyncExecutor;
import com.sauron.common.queue.MessageQueueException;
import com.sauron.common.queue.MessageQueueService;
import com.sauron.listener.dto.MessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 메시지 큐잉 전용 서비스
 * 분석을 위한 메시지 큐 전송을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageQueueingService {
    
    private final MessageQueueService messageQueueService;
    private final AsyncExecutor asyncExecutor;
    
    /**
     * 큐잉 결과 DTO
     */
    public static class QueuingResult {
        private final boolean success;
        private final String queueId;
        private final String errorMessage;
        
        private QueuingResult(boolean success, String queueId, String errorMessage) {
            this.success = success;
            this.queueId = queueId;
            this.errorMessage = errorMessage;
        }
        
        public static QueuingResult success(String queueId) {
            return new QueuingResult(true, queueId, null);
        }
        
        public static QueuingResult failure(String errorMessage) {
            return new QueuingResult(false, null, errorMessage);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getQueueId() { return queueId; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * 분석을 위해 메시지를 큐에 전송 (비동기, 폴백 포함)
     * 
     * @param request 메시지 요청
     * @return CompletableFuture<QueuingResult>
     */
    public CompletableFuture<QueuingResult> enqueueForAnalysis(MessageRequest request) {
        String messageId = request.getMessageId();
        
        log.debug("Starting message queuing for analysis - ID: {}", messageId);
        
        return asyncExecutor.executeWithFallback(
            // 주 작업: 비동기 큐 전송
            () -> enqueueAsync(request),
            // 폴백 작업: 동기 큐 전송
            () -> enqueueSync(request),
            "message-queuing-" + messageId
        ).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Message queuing failed for ID: {}", messageId, throwable);
            } else if (result.isSuccess()) {
                log.info("Message queued successfully for analysis - ID: {}, Queue ID: {}", 
                        messageId, result.getQueueId());
            } else {
                log.warn("Message queuing failed for ID: {} - {}", messageId, result.getErrorMessage());
            }
        });
    }
    
    /**
     * 비동기 큐 전송
     */
    private QueuingResult enqueueAsync(MessageRequest request) {
        try {
            CompletableFuture<Boolean> queueResult = messageQueueService.enqueueForAnalysis(request);
            
            // 비동기 결과를 기다림 (타임아웃 설정)
            Boolean success = queueResult.get(5, java.util.concurrent.TimeUnit.SECONDS);
            
            if (success) {
                String queueId = "async-" + System.currentTimeMillis();
                log.debug("Async queue enqueue succeeded for message: {}", request.getMessageId());
                return QueuingResult.success(queueId);
            } else {
                return QueuingResult.failure("Async queue enqueue returned false");
            }
            
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Async queue enqueue timeout for message: {}", request.getMessageId());
            throw new MessageQueueException("Queue operation timed out", e);
            
        } catch (Exception e) {
            log.error("Async queue enqueue failed for message: {}", request.getMessageId(), e);
            throw new MessageQueueException("Async queue operation failed", e);
        }
    }
    
    /**
     * 동기 큐 전송 (폴백)
     */
    private QueuingResult enqueueSync(MessageRequest request) {
        try {
            boolean success = messageQueueService.enqueueForAnalysisSync(request);
            
            if (success) {
                String queueId = "sync-" + System.currentTimeMillis();
                log.info("Sync queue enqueue succeeded for message: {}", request.getMessageId());
                return QueuingResult.success(queueId);
            } else {
                return QueuingResult.failure("Sync queue enqueue returned false");
            }
            
        } catch (MessageQueueException e) {
            log.error("Sync queue enqueue failed for message: {}", request.getMessageId(), e);
            return QueuingResult.failure("Sync queue operation failed: " + e.getMessage());
            
        } catch (Exception e) {
            log.error("Unexpected sync queue error for message: {}", request.getMessageId(), e);
            return QueuingResult.failure("Unexpected queue error: " + e.getMessage());
        }
    }
    
    /**
     * 우선순위 메시지 큐잉
     * 
     * @param request 메시지 요청
     * @param priority 우선순위 (HIGH, NORMAL, LOW)
     * @return CompletableFuture<QueuingResult>
     */
    public CompletableFuture<QueuingResult> enqueueWithPriority(MessageRequest request, String priority) {
        String messageId = request.getMessageId();
        
        log.debug("Starting priority message queuing - ID: {}, Priority: {}", messageId, priority);
        
        return asyncExecutor.executeWithRetry(
            () -> {
                try {
                    // 우선순위 설정
                    MessageRequest priorityRequest = createPriorityRequest(request, priority);
                    
                    // 우선순위 큐 전송
                    boolean success = messageQueueService.enqueueWithPriority(priorityRequest, priority);
                    
                    if (success) {
                        String queueId = "priority-" + priority.toLowerCase() + "-" + System.currentTimeMillis();
                        return QueuingResult.success(queueId);
                    } else {
                        return QueuingResult.failure("Priority queue enqueue returned false");
                    }
                    
                } catch (Exception e) {
                    log.error("Priority queue enqueue failed for message: {}", messageId, e);
                    return QueuingResult.failure("Priority queue operation failed: " + e.getMessage());
                }
            },
            "priority-queuing-" + messageId,
            2  // 최대 2회 재시도
        );
    }
    
    /**
     * 우선순위 요청 생성
     */
    private MessageRequest createPriorityRequest(MessageRequest original, String priority) {
        // MessageRequest의 우선순위 필드 설정
        // 실제 구현에서는 MessageRequest에 우선순위 설정 메서드가 필요할 수 있음
        return MessageRequest.builder()
            .messageId(original.getMessageId())
            .deviceId(original.getDeviceId())
            .chatRoomTitle(original.getChatRoomTitle())
            .messageContent(original.getMessageContent())
            .senderHash(original.getSenderHash())
            .priority(priority)
            .build();
    }
    
    /**
     * 큐 상태 확인
     * 
     * @return 큐 상태 정보
     */
    public MessageQueueService.QueueStatus getQueueStatus() {
        try {
            return messageQueueService.getQueueStatus();
        } catch (Exception e) {
            log.error("Failed to get queue status", e);
            return MessageQueueService.QueueStatus.builder()
                .healthy(false)
                .pendingMessages(0)
                .processingMessages(0)
                .errorMessage("Failed to get queue status: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 큐잉 서비스 상태 확인
     */
    public boolean isHealthy() {
        try {
            MessageQueueService.QueueStatus status = messageQueueService.getQueueStatus();
            boolean healthy = status.isHealthy();
            
            if (!healthy) {
                log.warn("Message queue is unhealthy: {}", status.getErrorMessage());
            }
            
            return healthy;
            
        } catch (Exception e) {
            log.error("Queue service health check failed", e);
            return false;
        }
    }
    
    /**
     * 큐 통계 조회
     */
    public QueueStatistics getQueueStatistics() {
        try {
            MessageQueueService.QueueStatus status = messageQueueService.getQueueStatus();
            
            return QueueStatistics.builder()
                .totalEnqueued(0L) // TODO: 실제 메트릭 수집 구현
                .successfullyEnqueued(0L)
                .failedToEnqueue(0L)
                .currentPending(status.getPendingMessages())
                .currentProcessing(status.getProcessingMessages())
                .averageWaitTime(0.0)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get queue statistics", e);
            return QueueStatistics.empty();
        }
    }
    
    /**
     * 특정 메시지의 큐 상태 조회
     */
    public MessageQueueStatus getMessageQueueStatus(String messageId) {
        try {
            // TODO: 메시지별 큐 상태 추적 구현 필요
            return MessageQueueStatus.builder()
                .messageId(messageId)
                .queueStatus("UNKNOWN")
                .enqueuedAt(null)
                .processedAt(null)
                .position(0)
                .estimatedProcessingTime(0L)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get message queue status for ID: {}", messageId, e);
            return MessageQueueStatus.builder()
                .messageId(messageId)
                .queueStatus("ERROR")
                .errorMessage("Failed to get status: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 큐 통계 DTO
     */
    public static class QueueStatistics {
        private final long totalEnqueued;
        private final long successfullyEnqueued;
        private final long failedToEnqueue;
        private final long currentPending;
        private final long currentProcessing;
        private final double averageWaitTime;
        
        private QueueStatistics(long totalEnqueued, long successfullyEnqueued, long failedToEnqueue,
                               long currentPending, long currentProcessing, double averageWaitTime) {
            this.totalEnqueued = totalEnqueued;
            this.successfullyEnqueued = successfullyEnqueued;
            this.failedToEnqueue = failedToEnqueue;
            this.currentPending = currentPending;
            this.currentProcessing = currentProcessing;
            this.averageWaitTime = averageWaitTime;
        }
        
        public static QueueStatisticsBuilder builder() {
            return new QueueStatisticsBuilder();
        }
        
        public static QueueStatistics empty() {
            return new QueueStatistics(0, 0, 0, 0, 0, 0.0);
        }
        
        // Getters
        public long getTotalEnqueued() { return totalEnqueued; }
        public long getSuccessfullyEnqueued() { return successfullyEnqueued; }
        public long getFailedToEnqueue() { return failedToEnqueue; }
        public long getCurrentPending() { return currentPending; }
        public long getCurrentProcessing() { return currentProcessing; }
        public double getAverageWaitTime() { return averageWaitTime; }
        
        public double getSuccessRate() {
            return totalEnqueued > 0 ? (double) successfullyEnqueued / totalEnqueued : 0.0;
        }
        
        public static class QueueStatisticsBuilder {
            private long totalEnqueued;
            private long successfullyEnqueued;
            private long failedToEnqueue;
            private long currentPending;
            private long currentProcessing;
            private double averageWaitTime;
            
            public QueueStatisticsBuilder totalEnqueued(long totalEnqueued) {
                this.totalEnqueued = totalEnqueued;
                return this;
            }
            
            public QueueStatisticsBuilder successfullyEnqueued(long successfullyEnqueued) {
                this.successfullyEnqueued = successfullyEnqueued;
                return this;
            }
            
            public QueueStatisticsBuilder failedToEnqueue(long failedToEnqueue) {
                this.failedToEnqueue = failedToEnqueue;
                return this;
            }
            
            public QueueStatisticsBuilder currentPending(long currentPending) {
                this.currentPending = currentPending;
                return this;
            }
            
            public QueueStatisticsBuilder currentProcessing(long currentProcessing) {
                this.currentProcessing = currentProcessing;
                return this;
            }
            
            public QueueStatisticsBuilder averageWaitTime(double averageWaitTime) {
                this.averageWaitTime = averageWaitTime;
                return this;
            }
            
            public QueueStatistics build() {
                return new QueueStatistics(totalEnqueued, successfullyEnqueued, failedToEnqueue,
                                          currentPending, currentProcessing, averageWaitTime);
            }
        }
    }
    
    /**
     * 메시지 큐 상태 DTO
     */
    public static class MessageQueueStatus {
        private final String messageId;
        private final String queueStatus;
        private final java.time.Instant enqueuedAt;
        private final java.time.Instant processedAt;
        private final int position;
        private final long estimatedProcessingTime;
        private final String errorMessage;
        
        private MessageQueueStatus(String messageId, String queueStatus, java.time.Instant enqueuedAt,
                                  java.time.Instant processedAt, int position, long estimatedProcessingTime,
                                  String errorMessage) {
            this.messageId = messageId;
            this.queueStatus = queueStatus;
            this.enqueuedAt = enqueuedAt;
            this.processedAt = processedAt;
            this.position = position;
            this.estimatedProcessingTime = estimatedProcessingTime;
            this.errorMessage = errorMessage;
        }
        
        public static MessageQueueStatusBuilder builder() {
            return new MessageQueueStatusBuilder();
        }
        
        // Getters
        public String getMessageId() { return messageId; }
        public String getQueueStatus() { return queueStatus; }
        public java.time.Instant getEnqueuedAt() { return enqueuedAt; }
        public java.time.Instant getProcessedAt() { return processedAt; }
        public int getPosition() { return position; }
        public long getEstimatedProcessingTime() { return estimatedProcessingTime; }
        public String getErrorMessage() { return errorMessage; }
        
        public static class MessageQueueStatusBuilder {
            private String messageId;
            private String queueStatus;
            private java.time.Instant enqueuedAt;
            private java.time.Instant processedAt;
            private int position;
            private long estimatedProcessingTime;
            private String errorMessage;
            
            public MessageQueueStatusBuilder messageId(String messageId) {
                this.messageId = messageId;
                return this;
            }
            
            public MessageQueueStatusBuilder queueStatus(String queueStatus) {
                this.queueStatus = queueStatus;
                return this;
            }
            
            public MessageQueueStatusBuilder enqueuedAt(java.time.Instant enqueuedAt) {
                this.enqueuedAt = enqueuedAt;
                return this;
            }
            
            public MessageQueueStatusBuilder processedAt(java.time.Instant processedAt) {
                this.processedAt = processedAt;
                return this;
            }
            
            public MessageQueueStatusBuilder position(int position) {
                this.position = position;
                return this;
            }
            
            public MessageQueueStatusBuilder estimatedProcessingTime(long estimatedProcessingTime) {
                this.estimatedProcessingTime = estimatedProcessingTime;
                return this;
            }
            
            public MessageQueueStatusBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }
            
            public MessageQueueStatus build() {
                return new MessageQueueStatus(messageId, queueStatus, enqueuedAt, processedAt,
                                            position, estimatedProcessingTime, errorMessage);
            }
        }
    }
}