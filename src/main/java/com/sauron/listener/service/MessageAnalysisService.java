package com.sauron.listener.service;

import com.sauron.common.core.async.AsyncExecutor;
import com.sauron.common.dto.BusinessException;
import com.sauron.common.dto.ErrorCode;
import com.sauron.common.external.GeminiWorkerClient;
import com.sauron.common.utils.EncryptionUtils;
import com.sauron.filter.service.MessageFilterService;
import com.sauron.listener.entity.Message;
import com.sauron.listener.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * 메시지 AI 분석 전용 서비스
 * Gemini AI를 통한 메시지 분석 및 결과 처리를 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageAnalysisService {
    
    private final GeminiWorkerClient geminiWorkerClient;
    private final MessageFilterService messageFilterService;
    private final MessageRepository messageRepository;
    private final EncryptionUtils encryptionUtils;
    private final AsyncExecutor asyncExecutor;
    
    /**
     * 분석 결과 DTO
     */
    public static class AnalysisResult {
        private final boolean success;
        private final String messageId;
        private final String detectedType;
        private final Double confidence;
        private final String reasoning;
        private final boolean filterApplied;
        private final String errorMessage;
        
        private AnalysisResult(boolean success, String messageId, String detectedType,
                              Double confidence, String reasoning, boolean filterApplied, String errorMessage) {
            this.success = success;
            this.messageId = messageId;
            this.detectedType = detectedType;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.filterApplied = filterApplied;
            this.errorMessage = errorMessage;
        }
        
        public static AnalysisResult success(String messageId, String detectedType, Double confidence,
                                           String reasoning, boolean filterApplied) {
            return new AnalysisResult(true, messageId, detectedType, confidence, reasoning, filterApplied, null);
        }
        
        public static AnalysisResult failure(String messageId, String errorMessage) {
            return new AnalysisResult(false, messageId, null, null, null, false, errorMessage);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessageId() { return messageId; }
        public String getDetectedType() { return detectedType; }
        public Double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public boolean isFilterApplied() { return filterApplied; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * 메시지 AI 분석 수행 (비동기)
     * 
     * @param message 분석할 메시지
     * @return CompletableFuture<AnalysisResult>
     */
    public CompletableFuture<AnalysisResult> analyzeMessage(Message message) {
        String messageId = message.getMessageId();
        
        log.debug("Starting AI analysis for message: {}", messageId);
        
        return asyncExecutor.executeWithRetry(
            () -> performAnalysis(message),
            "message-analysis-" + messageId,
            3  // 최대 3회 재시도
        ).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("AI analysis failed for message: {}", messageId, throwable);
                updateMessageStatusSafely(messageId, "FAILED");
            } else if (result.isSuccess()) {
                log.info("AI analysis completed successfully for message: {} - Type: {}, Confidence: {}", 
                        messageId, result.getDetectedType(), result.getConfidence());
            } else {
                log.warn("AI analysis returned failure for message: {} - {}", messageId, result.getErrorMessage());
                updateMessageStatusSafely(messageId, "FAILED");
            }
        });
    }
    
    /**
     * 실제 분석 수행
     */
    private AnalysisResult performAnalysis(Message message) {
        String messageId = message.getMessageId();
        
        try {
            // 1. 메시지 상태를 PROCESSING으로 업데이트
            updateMessageStatus(messageId, "PROCESSING");
            
            // 2. 메시지 복호화
            String decryptedContent = decryptMessage(message);
            
            // 3. Gemini AI 분석 수행
            GeminiWorkerClient.AnalysisResult geminiResult = performGeminiAnalysis(decryptedContent, message.getChatRoomTitle());
            
            // 4. 필터 적용
            MessageFilterService.FilterResult filterResult = applyFilters(message, decryptedContent, geminiResult);
            
            // 5. 최종 결과 저장
            String finalType = filterResult.getFinalDetectionType();
            Double finalConfidence = calculateFinalConfidence(geminiResult.getConfidenceScore(), filterResult);
            
            updateAnalysisResult(messageId, finalType, finalConfidence);
            
            return AnalysisResult.success(
                messageId,
                finalType,
                finalConfidence,
                "AI analysis completed", // TODO: GeminiWorkerClient.AnalysisResult에 reasoning 필드 추가 시 변경
                filterResult.isFilterApplied()
            );
            
        } catch (EncryptionUtils.EncryptionException e) {
            log.error("Failed to decrypt message content for analysis: {}", messageId, e);
            return AnalysisResult.failure(messageId, "Decryption failed: " + e.getMessage());
            
        } catch (Exception e) {
            log.error("Analysis failed for message: {}", messageId, e);
            return AnalysisResult.failure(messageId, "Analysis failed: " + e.getMessage());
        }
    }
    
    /**
     * 메시지 복호화
     */
    private String decryptMessage(Message message) {
        if (message.getContentEncrypted() == null || message.getContentEncrypted().trim().isEmpty()) {
            throw new IllegalArgumentException("No encrypted content to decrypt");
        }
        
        try {
            return encryptionUtils.decrypt(message.getContentEncrypted());
        } catch (Exception e) {
            throw new EncryptionUtils.EncryptionException("Failed to decrypt message content", e);
        }
    }
    
    /**
     * Gemini AI 분석 수행
     */
    private GeminiWorkerClient.AnalysisResult performGeminiAnalysis(String content, String chatRoomTitle) {
        try {
            CompletableFuture<GeminiWorkerClient.AnalysisResult> analysisTask = 
                geminiWorkerClient.analyzeMessage(content, chatRoomTitle);
            
            // 분석 타임아웃 설정 (30초)
            return analysisTask.get(30, java.util.concurrent.TimeUnit.SECONDS);
            
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Gemini analysis timeout for content length: {}", content.length());
            throw new RuntimeException("AI analysis timed out", e);
            
        } catch (Exception e) {
            log.error("Gemini analysis failed for content length: {}", content.length(), e);
            throw new RuntimeException("AI analysis failed", e);
        }
    }
    
    /**
     * 필터 적용
     */
    private MessageFilterService.FilterResult applyFilters(Message message, String decryptedContent,
                                                         GeminiWorkerClient.AnalysisResult geminiResult) {
        try {
            BigDecimal confidence = BigDecimal.valueOf(geminiResult.getConfidenceScore());
            
            return messageFilterService.applyFilters(
                message.getId(),
                decryptedContent,
                message.getSenderHash(),
                geminiResult.getDetectedType(),
                confidence
            );
            
        } catch (Exception e) {
            log.warn("Filter application failed for message: {}, using original result", message.getMessageId(), e);
            
            // 필터 적용 실패 시 원본 결과 반환
            return new MessageFilterService.FilterResult(
                geminiResult.getDetectedType(),
                geminiResult.getDetectedType(),
                BigDecimal.ZERO,
                java.util.Collections.emptyList()
            );
        }
    }
    
    /**
     * 최종 신뢰도 계산
     */
    private Double calculateFinalConfidence(Double originalConfidence, MessageFilterService.FilterResult filterResult) {
        if (!filterResult.hasConfidenceAdjusted()) {
            return originalConfidence;
        }
        
        BigDecimal adjusted = BigDecimal.valueOf(originalConfidence).add(filterResult.getConfidenceAdjustment());
        
        // 신뢰도 범위 제한 (0.0 ~ 1.0)
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) {
            adjusted = BigDecimal.ZERO;
        } else if (adjusted.compareTo(BigDecimal.ONE) > 0) {
            adjusted = BigDecimal.ONE;
        }
        
        return adjusted.doubleValue();
    }
    
    /**
     * 메시지 상태 업데이트
     */
    private void updateMessageStatus(String messageId, String status) {
        try {
            Instant now = Instant.now();
            int updated = messageRepository.updateMessageStatus(messageId, status, now);
            
            if (updated == 0) {
                log.warn("No message found to update status for ID: {}", messageId);
            } else {
                log.debug("Message status updated - ID: {}, Status: {}", messageId, status);
            }
            
        } catch (Exception e) {
            log.error("Failed to update message status for ID: {}", messageId, e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "Failed to update message status", e);
        }
    }
    
    /**
     * 안전한 메시지 상태 업데이트 (예외를 던지지 않음)
     */
    private void updateMessageStatusSafely(String messageId, String status) {
        try {
            updateMessageStatus(messageId, status);
        } catch (Exception e) {
            log.error("Safe status update failed for message: {}", messageId, e);
            // 예외를 던지지 않음 (로깅만)
        }
    }
    
    /**
     * 분석 결과 업데이트
     */
    @Transactional
    private void updateAnalysisResult(String messageId, String detectedType, Double confidence) {
        try {
            Instant now = Instant.now();
            int updated = messageRepository.updateAnalysisResult(
                messageId, detectedType, confidence, "COMPLETED", now, now
            );
            
            if (updated == 0) {
                log.warn("No message found to update analysis result for ID: {}", messageId);
                throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND, "Message not found: " + messageId);
            }
            
            log.debug("Analysis result updated - ID: {}, Type: {}, Confidence: {}", 
                     messageId, detectedType, confidence);
            
        } catch (Exception e) {
            log.error("Failed to update analysis result for ID: {}", messageId, e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "Failed to update analysis result", e);
        }
    }
    
    /**
     * 메시지 재분석 수행
     * 
     * @param messageId 재분석할 메시지 ID
     * @return CompletableFuture<AnalysisResult>
     */
    public CompletableFuture<AnalysisResult> reanalyzeMessage(String messageId) {
        return asyncExecutor.executeWithRetry(
            () -> {
                Message message = messageRepository.findByMessageId(messageId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND, "Message not found: " + messageId));
                
                log.info("Starting message reanalysis for ID: {}", messageId);
                return performAnalysis(message);
            },
            "message-reanalysis-" + messageId,
            2  // 최대 2회 재시도
        );
    }
    
    /**
     * 분석 서비스 상태 확인
     */
    public boolean isHealthy() {
        try {
            // Gemini API 상태 확인
            CompletableFuture<Boolean> geminiHealthy = geminiWorkerClient.checkApiHealth();
            boolean geminiStatus = geminiHealthy.get(5, java.util.concurrent.TimeUnit.SECONDS);
            
            // 데이터베이스 연결 확인
            boolean dbHealthy = checkDatabaseHealth();
            
            // 암호화 서비스 상태 확인
            boolean encryptionHealthy = checkEncryptionHealth();
            
            if (!geminiStatus) {
                log.warn("Gemini API is unhealthy");
            }
            
            if (!dbHealthy) {
                log.warn("Database connection is unhealthy");
            }
            
            if (!encryptionHealthy) {
                log.warn("Encryption service is unhealthy");
            }
            
            return geminiStatus && dbHealthy && encryptionHealthy;
            
        } catch (Exception e) {
            log.error("Analysis service health check failed", e);
            return false;
        }
    }
    
    /**
     * 데이터베이스 연결 상태 확인
     */
    private boolean checkDatabaseHealth() {
        try {
            messageRepository.count();
            return true;
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return false;
        }
    }
    
    /**
     * 암호화 서비스 상태 확인
     */
    private boolean checkEncryptionHealth() {
        try {
            String testData = "health_check";
            String encrypted = encryptionUtils.encrypt(testData);
            String decrypted = encryptionUtils.decrypt(encrypted);
            return testData.equals(decrypted);
        } catch (Exception e) {
            log.error("Encryption health check failed", e);
            return false;
        }
    }
    
    /**
     * 분석 통계 조회
     */
    public AnalysisStatistics getAnalysisStatistics() {
        try {
            // TODO: 실제 메트릭 수집 구현
            return AnalysisStatistics.builder()
                .totalAnalyzed(0L)
                .successfulAnalyses(0L)
                .failedAnalyses(0L)
                .averageAnalysisTime(0.0)
                .averageConfidence(0.0)
                .geminiApiCalls(0L)
                .filtersApplied(0L)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get analysis statistics", e);
            return AnalysisStatistics.empty();
        }
    }
    
    /**
     * 분석 통계 DTO
     */
    public static class AnalysisStatistics {
        private final long totalAnalyzed;
        private final long successfulAnalyses;
        private final long failedAnalyses;
        private final double averageAnalysisTime;
        private final double averageConfidence;
        private final long geminiApiCalls;
        private final long filtersApplied;
        
        private AnalysisStatistics(long totalAnalyzed, long successfulAnalyses, long failedAnalyses,
                                  double averageAnalysisTime, double averageConfidence, 
                                  long geminiApiCalls, long filtersApplied) {
            this.totalAnalyzed = totalAnalyzed;
            this.successfulAnalyses = successfulAnalyses;
            this.failedAnalyses = failedAnalyses;
            this.averageAnalysisTime = averageAnalysisTime;
            this.averageConfidence = averageConfidence;
            this.geminiApiCalls = geminiApiCalls;
            this.filtersApplied = filtersApplied;
        }
        
        public static AnalysisStatisticsBuilder builder() {
            return new AnalysisStatisticsBuilder();
        }
        
        public static AnalysisStatistics empty() {
            return new AnalysisStatistics(0, 0, 0, 0.0, 0.0, 0, 0);
        }
        
        // Getters
        public long getTotalAnalyzed() { return totalAnalyzed; }
        public long getSuccessfulAnalyses() { return successfulAnalyses; }
        public long getFailedAnalyses() { return failedAnalyses; }
        public double getAverageAnalysisTime() { return averageAnalysisTime; }
        public double getAverageConfidence() { return averageConfidence; }
        public long getGeminiApiCalls() { return geminiApiCalls; }
        public long getFiltersApplied() { return filtersApplied; }
        
        public double getSuccessRate() {
            return totalAnalyzed > 0 ? (double) successfulAnalyses / totalAnalyzed : 0.0;
        }
        
        public static class AnalysisStatisticsBuilder {
            private long totalAnalyzed;
            private long successfulAnalyses;
            private long failedAnalyses;
            private double averageAnalysisTime;
            private double averageConfidence;
            private long geminiApiCalls;
            private long filtersApplied;
            
            public AnalysisStatisticsBuilder totalAnalyzed(long totalAnalyzed) {
                this.totalAnalyzed = totalAnalyzed;
                return this;
            }
            
            public AnalysisStatisticsBuilder successfulAnalyses(long successfulAnalyses) {
                this.successfulAnalyses = successfulAnalyses;
                return this;
            }
            
            public AnalysisStatisticsBuilder failedAnalyses(long failedAnalyses) {
                this.failedAnalyses = failedAnalyses;
                return this;
            }
            
            public AnalysisStatisticsBuilder averageAnalysisTime(double averageAnalysisTime) {
                this.averageAnalysisTime = averageAnalysisTime;
                return this;
            }
            
            public AnalysisStatisticsBuilder averageConfidence(double averageConfidence) {
                this.averageConfidence = averageConfidence;
                return this;
            }
            
            public AnalysisStatisticsBuilder geminiApiCalls(long geminiApiCalls) {
                this.geminiApiCalls = geminiApiCalls;
                return this;
            }
            
            public AnalysisStatisticsBuilder filtersApplied(long filtersApplied) {
                this.filtersApplied = filtersApplied;
                return this;
            }
            
            public AnalysisStatistics build() {
                return new AnalysisStatistics(totalAnalyzed, successfulAnalyses, failedAnalyses,
                                            averageAnalysisTime, averageConfidence, geminiApiCalls, filtersApplied);
            }
        }
    }
}