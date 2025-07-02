package com.sauron.listener.service;

import com.sauron.common.core.async.AsyncExecutor;
import com.sauron.listener.dto.MessageRequest;
import com.sauron.listener.dto.MessageResponse;
import com.sauron.listener.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * 메시지 처리 오케스트레이터
 * 분해된 서비스들을 조합하여 메시지 처리 전체 워크플로우를 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageProcessingOrchestrator {
    
    private final MessageValidationService validationService;
    private final MessagePersistenceService persistenceService;
    private final MessageQueueingService queueingService;
    private final MessageAnalysisService analysisService;
    private final AsyncExecutor asyncExecutor;
    
    /**
     * 전체 처리 결과 DTO
     */
    public static class ProcessingResult {
        private final boolean success;
        private final Long savedMessageId;
        private final String messageId;
        private final boolean queued;
        private final boolean analyzed;
        private final String errorMessage;
        private final long processingTimeMs;
        
        private ProcessingResult(boolean success, Long savedMessageId, String messageId,
                               boolean queued, boolean analyzed, String errorMessage, long processingTimeMs) {
            this.success = success;
            this.savedMessageId = savedMessageId;
            this.messageId = messageId;
            this.queued = queued;
            this.analyzed = analyzed;
            this.errorMessage = errorMessage;
            this.processingTimeMs = processingTimeMs;
        }
        
        public static ProcessingResult success(Long savedMessageId, String messageId, boolean queued, 
                                             boolean analyzed, long processingTimeMs) {
            return new ProcessingResult(true, savedMessageId, messageId, queued, analyzed, null, processingTimeMs);
        }
        
        public static ProcessingResult failure(String messageId, String errorMessage, long processingTimeMs) {
            return new ProcessingResult(false, null, messageId, false, false, errorMessage, processingTimeMs);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public Long getSavedMessageId() { return savedMessageId; }
        public String getMessageId() { return messageId; }
        public boolean isQueued() { return queued; }
        public boolean isAnalyzed() { return analyzed; }
        public String getErrorMessage() { return errorMessage; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        
        /**
         * MessageResponse 변환
         */
        public MessageResponse toMessageResponse() {
            if (success) {
                return MessageResponse.success(savedMessageId, messageId, queued);
            } else {
                return MessageResponse.failure(messageId, "Processing failed: " + errorMessage);
            }
        }
    }
    
    /**
     * 메시지 처리 메인 워크플로우 (기존 MessageService.processMessage 대체)
     * 
     * @param request 메시지 요청
     * @return 처리 결과
     */
    @Transactional
    public ProcessingResult processMessage(MessageRequest request) {
        String messageId = request.getMessageId();
        long startTime = System.currentTimeMillis();
        
        log.debug("Starting message processing workflow for ID: {}", messageId);
        
        try {
            // 1. 메시지 검증
            MessageValidationService.ValidationResult validationResult = validationService.validateMessage(request);
            if (!validationResult.isValid()) {
                long processingTime = System.currentTimeMillis() - startTime;
                log.warn("Message validation failed for ID: {} - {}", messageId, validationResult.getErrorMessage());
                return ProcessingResult.failure(messageId, "Validation failed: " + validationResult.getErrorMessage(), processingTime);
            }
            
            // 2. 메시지 저장
            MessagePersistenceService.PersistenceResult persistenceResult = persistenceService.saveMessage(request);
            if (!persistenceResult.isSuccess()) {
                long processingTime = System.currentTimeMillis() - startTime;
                log.error("Message persistence failed for ID: {} - {}", messageId, persistenceResult.getErrorMessage());
                return ProcessingResult.failure(messageId, "Persistence failed: " + persistenceResult.getErrorMessage(), processingTime);
            }
            
            Message savedMessage = persistenceResult.getSavedMessage();
            
            // 3. 큐잉 (비동기)
            boolean queued = false;
            try {
                CompletableFuture<MessageQueueingService.QueuingResult> queueingTask = 
                    queueingService.enqueueForAnalysis(request);
                
                // 큐잉 결과를 기다리지 않고 즉시 진행 (비동기 처리)
                queueingTask.whenComplete((queueResult, queueThrowable) -> {
                    if (queueThrowable != null) {
                        log.error("Async queuing failed for message: {}", messageId, queueThrowable);
                    } else if (queueResult.isSuccess()) {
                        log.info("Message queued successfully - ID: {}, Queue ID: {}", messageId, queueResult.getQueueId());
                        
                        // 4. AI 분석 시작 (비동기)
                        startAsyncAnalysis(savedMessage);
                    } else {
                        log.warn("Message queuing failed - ID: {} - {}", messageId, queueResult.getErrorMessage());
                    }
                });
                
                queued = true; // 큐잉 시도는 성공으로 처리
                
            } catch (Exception e) {
                log.error("Failed to initiate queuing for message: {}", messageId, e);
                // 큐잉 실패는 치명적이지 않으므로 계속 진행
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Message processing workflow completed - ID: {}, DB ID: {}, Time: {}ms, Queued: {}", 
                    messageId, savedMessage.getId(), processingTime, queued);
            
            return ProcessingResult.success(savedMessage.getId(), messageId, queued, false, processingTime);
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Message processing workflow failed for ID: {} - Time: {}ms", messageId, processingTime, e);
            return ProcessingResult.failure(messageId, "Unexpected error: " + e.getMessage(), processingTime);
        }
    }
    
    /**
     * 메시지 처리 (비동기 버전)
     * 
     * @param request 메시지 요청
     * @return CompletableFuture<ProcessingResult>
     */
    @Transactional
    public CompletableFuture<ProcessingResult> processMessageAsync(MessageRequest request) {
        return asyncExecutor.executeWithRetry(
            () -> processMessage(request),
            "message-processing-" + request.getMessageId(),
            2  // 최대 2회 재시도
        );
    }
    
    /**
     * 비동기 AI 분석 시작
     */
    private void startAsyncAnalysis(Message message) {
        try {
            log.debug("Starting async AI analysis for message: {}", message.getMessageId());
            
            CompletableFuture<MessageAnalysisService.AnalysisResult> analysisTask = 
                analysisService.analyzeMessage(message);
            
            analysisTask.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Async AI analysis failed for message: {}", message.getMessageId(), throwable);
                } else if (result.isSuccess()) {
                    log.info("Async AI analysis completed successfully for message: {} - Type: {}, Confidence: {}", 
                            message.getMessageId(), result.getDetectedType(), result.getConfidence());
                } else {
                    log.warn("Async AI analysis returned failure for message: {} - {}", 
                            message.getMessageId(), result.getErrorMessage());
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to start async analysis for message: {}", message.getMessageId(), e);
        }
    }
    
    /**
     * 메시지 재처리 (실패한 메시지 재시도용)
     * 
     * @param messageId 재처리할 메시지 ID
     * @return CompletableFuture<ProcessingResult>
     */
    public CompletableFuture<ProcessingResult> reprocessMessage(String messageId) {
        return asyncExecutor.executeWithRetry(
            () -> {
                log.info("Starting message reprocessing for ID: {}", messageId);
                
                // TODO: 저장된 메시지에서 MessageRequest 재구성 로직 필요
                // 현재는 분석만 재시도하는 간단한 구현
                CompletableFuture<MessageAnalysisService.AnalysisResult> reanalysisTask = 
                    analysisService.reanalyzeMessage(messageId);
                
                MessageAnalysisService.AnalysisResult result;
                try {
                    result = reanalysisTask.get();
                } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                    throw new RuntimeException("Reanalysis execution failed", e);
                }
                
                if (result.isSuccess()) {
                    return ProcessingResult.success(null, messageId, false, true, 0);
                } else {
                    return ProcessingResult.failure(messageId, "Reanalysis failed: " + result.getErrorMessage(), 0);
                }
            },
            "message-reprocessing-" + messageId,
            1  // 재처리는 1회만 시도
        );
    }
    
    /**
     * 우선순위 메시지 처리
     * 
     * @param request 메시지 요청
     * @param priority 우선순위 (HIGH, NORMAL, LOW)
     * @return CompletableFuture<ProcessingResult>
     */
    @Transactional
    public CompletableFuture<ProcessingResult> processMessageWithPriority(MessageRequest request, String priority) {
        String messageId = request.getMessageId();
        
        log.info("Starting priority message processing - ID: {}, Priority: {}", messageId, priority);
        
        return asyncExecutor.executeWithRetry(
            () -> {
                long startTime = System.currentTimeMillis();
                
                try {
                    // 1. 검증 (동기)
                    MessageValidationService.ValidationResult validationResult = validationService.validateMessage(request);
                    if (!validationResult.isValid()) {
                        long processingTime = System.currentTimeMillis() - startTime;
                        return ProcessingResult.failure(messageId, "Validation failed: " + validationResult.getErrorMessage(), processingTime);
                    }
                    
                    // 2. 저장 (동기)
                    MessagePersistenceService.PersistenceResult persistenceResult = persistenceService.saveMessage(request);
                    if (!persistenceResult.isSuccess()) {
                        long processingTime = System.currentTimeMillis() - startTime;
                        return ProcessingResult.failure(messageId, "Persistence failed: " + persistenceResult.getErrorMessage(), processingTime);
                    }
                    
                    // 3. 우선순위 큐잉 (비동기)
                    CompletableFuture<MessageQueueingService.QueuingResult> priorityQueueingTask = 
                        queueingService.enqueueWithPriority(request, priority);
                    
                    MessageQueueingService.QueuingResult queueResult = priorityQueueingTask.get();
                    boolean queued = queueResult.isSuccess();
                    
                    if (queued) {
                        // 4. 즉시 분석 시작 (우선순위 메시지는 즉시 처리)
                        startAsyncAnalysis(persistenceResult.getSavedMessage());
                    }
                    
                    long processingTime = System.currentTimeMillis() - startTime;
                    return ProcessingResult.success(persistenceResult.getSavedMessage().getId(), messageId, queued, false, processingTime);
                    
                } catch (Exception e) {
                    long processingTime = System.currentTimeMillis() - startTime;
                    log.error("Priority message processing failed for ID: {}", messageId, e);
                    return ProcessingResult.failure(messageId, "Priority processing failed: " + e.getMessage(), processingTime);
                }
            },
            "priority-processing-" + messageId,
            2  // 우선순위 메시지는 최대 2회 재시도
        );
    }
    
    /**
     * 처리 서비스 전체 상태 확인
     */
    public ProcessingHealthStatus getHealthStatus() {
        try {
            boolean validationHealthy = validationService.isHealthy();
            boolean persistenceHealthy = persistenceService.isHealthy();
            boolean queueingHealthy = queueingService.isHealthy();
            boolean analysisHealthy = analysisService.isHealthy();
            
            boolean overallHealthy = validationHealthy && persistenceHealthy && queueingHealthy && analysisHealthy;
            
            return ProcessingHealthStatus.builder()
                .overallHealthy(overallHealthy)
                .validationHealthy(validationHealthy)
                .persistenceHealthy(persistenceHealthy)
                .queueingHealthy(queueingHealthy)
                .analysisHealthy(analysisHealthy)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get processing health status", e);
            return ProcessingHealthStatus.builder()
                .overallHealthy(false)
                .errorMessage("Health check failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 처리 통계 조회
     */
    public ProcessingStatistics getProcessingStatistics() {
        try {
            // 각 서비스별 통계 수집
            MessageValidationService.ValidationStatistics validationStats = validationService.getValidationStatistics();
            MessageQueueingService.QueueStatistics queueStats = queueingService.getQueueStatistics();
            MessageAnalysisService.AnalysisStatistics analysisStats = analysisService.getAnalysisStatistics();
            
            return ProcessingStatistics.builder()
                .totalProcessed(validationStats.getTotalValidations())
                .successfullyProcessed(validationStats.getSuccessfulValidations())
                .failedProcessed(validationStats.getFailedValidations())
                .averageProcessingTime(0.0) // TODO: 실제 처리 시간 메트릭 수집
                .currentQueueSize(queueStats.getCurrentPending())
                .totalAnalyzed(analysisStats.getTotalAnalyzed())
                .averageAnalysisTime(analysisStats.getAverageAnalysisTime())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get processing statistics", e);
            return ProcessingStatistics.empty();
        }
    }
    
    /**
     * 처리 상태 DTO
     */
    public static class ProcessingHealthStatus {
        private final boolean overallHealthy;
        private final boolean validationHealthy;
        private final boolean persistenceHealthy;
        private final boolean queueingHealthy;
        private final boolean analysisHealthy;
        private final String errorMessage;
        
        private ProcessingHealthStatus(boolean overallHealthy, boolean validationHealthy, boolean persistenceHealthy,
                                     boolean queueingHealthy, boolean analysisHealthy, String errorMessage) {
            this.overallHealthy = overallHealthy;
            this.validationHealthy = validationHealthy;
            this.persistenceHealthy = persistenceHealthy;
            this.queueingHealthy = queueingHealthy;
            this.analysisHealthy = analysisHealthy;
            this.errorMessage = errorMessage;
        }
        
        public static ProcessingHealthStatusBuilder builder() {
            return new ProcessingHealthStatusBuilder();
        }
        
        // Getters
        public boolean isOverallHealthy() { return overallHealthy; }
        public boolean isValidationHealthy() { return validationHealthy; }
        public boolean isPersistenceHealthy() { return persistenceHealthy; }
        public boolean isQueueingHealthy() { return queueingHealthy; }
        public boolean isAnalysisHealthy() { return analysisHealthy; }
        public String getErrorMessage() { return errorMessage; }
        
        public static class ProcessingHealthStatusBuilder {
            private boolean overallHealthy = true;
            private boolean validationHealthy = true;
            private boolean persistenceHealthy = true;
            private boolean queueingHealthy = true;
            private boolean analysisHealthy = true;
            private String errorMessage;
            
            public ProcessingHealthStatusBuilder overallHealthy(boolean overallHealthy) {
                this.overallHealthy = overallHealthy;
                return this;
            }
            
            public ProcessingHealthStatusBuilder validationHealthy(boolean validationHealthy) {
                this.validationHealthy = validationHealthy;
                return this;
            }
            
            public ProcessingHealthStatusBuilder persistenceHealthy(boolean persistenceHealthy) {
                this.persistenceHealthy = persistenceHealthy;
                return this;
            }
            
            public ProcessingHealthStatusBuilder queueingHealthy(boolean queueingHealthy) {
                this.queueingHealthy = queueingHealthy;
                return this;
            }
            
            public ProcessingHealthStatusBuilder analysisHealthy(boolean analysisHealthy) {
                this.analysisHealthy = analysisHealthy;
                return this;
            }
            
            public ProcessingHealthStatusBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }
            
            public ProcessingHealthStatus build() {
                return new ProcessingHealthStatus(overallHealthy, validationHealthy, persistenceHealthy,
                                                queueingHealthy, analysisHealthy, errorMessage);
            }
        }
    }
    
    /**
     * 처리 통계 DTO
     */
    public static class ProcessingStatistics {
        private final long totalProcessed;
        private final long successfullyProcessed;
        private final long failedProcessed;
        private final double averageProcessingTime;
        private final long currentQueueSize;
        private final long totalAnalyzed;
        private final double averageAnalysisTime;
        
        private ProcessingStatistics(long totalProcessed, long successfullyProcessed, long failedProcessed,
                                   double averageProcessingTime, long currentQueueSize, long totalAnalyzed,
                                   double averageAnalysisTime) {
            this.totalProcessed = totalProcessed;
            this.successfullyProcessed = successfullyProcessed;
            this.failedProcessed = failedProcessed;
            this.averageProcessingTime = averageProcessingTime;
            this.currentQueueSize = currentQueueSize;
            this.totalAnalyzed = totalAnalyzed;
            this.averageAnalysisTime = averageAnalysisTime;
        }
        
        public static ProcessingStatisticsBuilder builder() {
            return new ProcessingStatisticsBuilder();
        }
        
        public static ProcessingStatistics empty() {
            return new ProcessingStatistics(0, 0, 0, 0.0, 0, 0, 0.0);
        }
        
        // Getters
        public long getTotalProcessed() { return totalProcessed; }
        public long getSuccessfullyProcessed() { return successfullyProcessed; }
        public long getFailedProcessed() { return failedProcessed; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public long getCurrentQueueSize() { return currentQueueSize; }
        public long getTotalAnalyzed() { return totalAnalyzed; }
        public double getAverageAnalysisTime() { return averageAnalysisTime; }
        
        public double getSuccessRate() {
            return totalProcessed > 0 ? (double) successfullyProcessed / totalProcessed : 0.0;
        }
        
        public static class ProcessingStatisticsBuilder {
            private long totalProcessed;
            private long successfullyProcessed;
            private long failedProcessed;
            private double averageProcessingTime;
            private long currentQueueSize;
            private long totalAnalyzed;
            private double averageAnalysisTime;
            
            public ProcessingStatisticsBuilder totalProcessed(long totalProcessed) {
                this.totalProcessed = totalProcessed;
                return this;
            }
            
            public ProcessingStatisticsBuilder successfullyProcessed(long successfullyProcessed) {
                this.successfullyProcessed = successfullyProcessed;
                return this;
            }
            
            public ProcessingStatisticsBuilder failedProcessed(long failedProcessed) {
                this.failedProcessed = failedProcessed;
                return this;
            }
            
            public ProcessingStatisticsBuilder averageProcessingTime(double averageProcessingTime) {
                this.averageProcessingTime = averageProcessingTime;
                return this;
            }
            
            public ProcessingStatisticsBuilder currentQueueSize(long currentQueueSize) {
                this.currentQueueSize = currentQueueSize;
                return this;
            }
            
            public ProcessingStatisticsBuilder totalAnalyzed(long totalAnalyzed) {
                this.totalAnalyzed = totalAnalyzed;
                return this;
            }
            
            public ProcessingStatisticsBuilder averageAnalysisTime(double averageAnalysisTime) {
                this.averageAnalysisTime = averageAnalysisTime;
                return this;
            }
            
            public ProcessingStatistics build() {
                return new ProcessingStatistics(totalProcessed, successfullyProcessed, failedProcessed,
                                              averageProcessingTime, currentQueueSize, totalAnalyzed, averageAnalysisTime);
            }
        }
    }
}