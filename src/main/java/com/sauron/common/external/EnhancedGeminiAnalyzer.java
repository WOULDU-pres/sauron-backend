package com.sauron.common.external;

import com.sauron.common.core.async.AsyncExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 향상된 Gemini 메시지 분석기
 * 성능 최적화, 에러 처리, 메트릭 수집을 포함합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedGeminiAnalyzer {
    
    private final GeminiWorkerClient geminiWorkerClient;
    private final AsyncExecutor asyncExecutor;
    
    @Value("${gemini.analysis.timeout:30}")
    private int analysisTimeoutSeconds;
    
    @Value("${gemini.analysis.max-retries:3}")
    private int maxRetries;
    
    @Value("${gemini.analysis.enable-fallback:true}")
    private boolean enableFallback;
    
    @Value("${gemini.analysis.confidence-threshold:0.7}")
    private double confidenceThreshold;
    
    /**
     * 향상된 분석 결과 DTO
     */
    public static class EnhancedAnalysisResult {
        private final boolean success;
        private final String detectedType;
        private final Double confidence;
        private final String reasoning;
        private final long processingTimeMs;
        private final boolean fallbackUsed;
        private final String errorMessage;
        private final AnalysisMetrics metrics;
        
        private EnhancedAnalysisResult(boolean success, String detectedType, Double confidence,
                                     String reasoning, long processingTimeMs, boolean fallbackUsed,
                                     String errorMessage, AnalysisMetrics metrics) {
            this.success = success;
            this.detectedType = detectedType;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.processingTimeMs = processingTimeMs;
            this.fallbackUsed = fallbackUsed;
            this.errorMessage = errorMessage;
            this.metrics = metrics;
        }
        
        public static EnhancedAnalysisResult success(String detectedType, Double confidence, String reasoning,
                                                   long processingTimeMs, boolean fallbackUsed, AnalysisMetrics metrics) {
            return new EnhancedAnalysisResult(true, detectedType, confidence, reasoning, 
                                            processingTimeMs, fallbackUsed, null, metrics);
        }
        
        public static EnhancedAnalysisResult failure(String errorMessage, long processingTimeMs, AnalysisMetrics metrics) {
            return new EnhancedAnalysisResult(false, "unknown", 0.0, null, 
                                            processingTimeMs, false, errorMessage, metrics);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getDetectedType() { return detectedType; }
        public Double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public boolean isFallbackUsed() { return fallbackUsed; }
        public String getErrorMessage() { return errorMessage; }
        public AnalysisMetrics getMetrics() { return metrics; }
        
        /**
         * 1초 이내 처리 여부 확인
         */
        public boolean isWithinTimeLimit() {
            return processingTimeMs <= 1000;
        }
        
        /**
         * 신뢰도 임계값 충족 여부 확인
         */
        public boolean meetsConfidenceThreshold(double threshold) {
            return confidence != null && confidence >= threshold;
        }
    }
    
    /**
     * 분석 메트릭 DTO
     */
    public static class AnalysisMetrics {
        private final Instant startTime;
        private final Instant endTime;
        private final int retryCount;
        private final boolean cacheHit;
        private final String geminiModel;
        private final int messageLength;
        
        public AnalysisMetrics(Instant startTime, Instant endTime, int retryCount, 
                             boolean cacheHit, String geminiModel, int messageLength) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.retryCount = retryCount;
            this.cacheHit = cacheHit;
            this.geminiModel = geminiModel;
            this.messageLength = messageLength;
        }
        
        // Getters
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public int getRetryCount() { return retryCount; }
        public boolean isCacheHit() { return cacheHit; }
        public String getGeminiModel() { return geminiModel; }
        public int getMessageLength() { return messageLength; }
        
        public long getDurationMs() {
            return Duration.between(startTime, endTime).toMillis();
        }
    }
    
    /**
     * 고성능 메시지 분석 수행
     * 
     * @param messageContent 분석할 메시지 내용
     * @param chatRoomTitle 채팅방 제목
     * @return CompletableFuture<EnhancedAnalysisResult>
     */
    public CompletableFuture<EnhancedAnalysisResult> analyzeMessageEnhanced(String messageContent, String chatRoomTitle) {
        Instant startTime = Instant.now();
        
        log.debug("Starting enhanced message analysis - content length: {}, chatroom: {}", 
                 messageContent.length(), chatRoomTitle);
        
        // 타임아웃이 있는 비동기 실행
        return asyncExecutor.executeWithTimeout(
            () -> performAnalysisWithMetrics(messageContent, chatRoomTitle, startTime),
            "enhanced-gemini-analysis",
            analysisTimeoutSeconds * 1000L
        ).handle((result, throwable) -> {
            Instant endTime = Instant.now();
            long processingTime = Duration.between(startTime, endTime).toMillis();
            
            if (throwable != null) {
                log.error("Enhanced analysis failed after {}ms: {}", processingTime, throwable.getMessage());
                
                AnalysisMetrics metrics = new AnalysisMetrics(
                    startTime, endTime, 0, false, "unknown", messageContent.length()
                );
                
                if (enableFallback) {
                    return performFallbackAnalysis(messageContent, chatRoomTitle, processingTime, metrics);
                } else {
                    return EnhancedAnalysisResult.failure("Analysis failed: " + throwable.getMessage(), processingTime, metrics);
                }
            }
            
            return result;
        });
    }
    
    /**
     * 메트릭을 포함한 분석 수행
     */
    private EnhancedAnalysisResult performAnalysisWithMetrics(String messageContent, String chatRoomTitle, Instant startTime) {
        try {
            // 1. 사전 검증
            if (messageContent == null || messageContent.trim().isEmpty()) {
                throw new IllegalArgumentException("Message content cannot be empty");
            }
            
            if (messageContent.length() > 5000) {
                log.warn("Message content too long ({}), truncating to 5000 characters", messageContent.length());
                messageContent = messageContent.substring(0, 5000);
            }
            
            // 2. Gemini API 호출
            CompletableFuture<GeminiWorkerClient.AnalysisResult> geminiTask = 
                geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
            
            GeminiWorkerClient.AnalysisResult geminiResult;
            try {
                geminiResult = geminiTask.get(analysisTimeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("Gemini API call failed", e);
            }
            
            Instant endTime = Instant.now();
            long processingTime = Duration.between(startTime, endTime).toMillis();
            
            // 3. 결과 검증 및 메트릭 생성
            AnalysisMetrics metrics = new AnalysisMetrics(
                startTime, endTime, 0, false, "gemini-2.5-flash", messageContent.length()
            );
            
            // 4. 신뢰도 검증
            if (geminiResult.getConfidenceScore() < confidenceThreshold) {
                log.warn("Low confidence result: {} (threshold: {})", 
                        geminiResult.getConfidenceScore(), confidenceThreshold);
            }
            
            // 5. 성능 검증
            if (processingTime > 1000) {
                log.warn("Analysis took {}ms, exceeding 1-second target", processingTime);
            }
            
            return EnhancedAnalysisResult.success(
                geminiResult.getDetectedType(),
                geminiResult.getConfidenceScore(),
                geminiResult.getMetadata(),
                processingTime,
                false,
                metrics
            );
            
        } catch (Exception e) {
            Instant endTime = Instant.now();
            long processingTime = Duration.between(startTime, endTime).toMillis();
            
            AnalysisMetrics metrics = new AnalysisMetrics(
                startTime, endTime, 0, false, "unknown", messageContent.length()
            );
            
            log.error("Analysis failed after {}ms: {}", processingTime, e.getMessage());
            
            if (enableFallback) {
                return performFallbackAnalysis(messageContent, chatRoomTitle, processingTime, metrics);
            } else {
                return EnhancedAnalysisResult.failure("Analysis failed: " + e.getMessage(), processingTime, metrics);
            }
        }
    }
    
    /**
     * 폴백 분석 수행
     */
    private EnhancedAnalysisResult performFallbackAnalysis(String messageContent, String chatRoomTitle, 
                                                          long existingProcessingTime, AnalysisMetrics originalMetrics) {
        try {
            log.info("Performing fallback analysis for message length: {}", messageContent.length());
            
            Instant fallbackStart = Instant.now();
            
            // 규칙 기반 간단 분석
            FallbackAnalysisResult fallbackResult = performRuleBasedAnalysis(messageContent, chatRoomTitle);
            
            Instant fallbackEnd = Instant.now();
            long fallbackTime = Duration.between(fallbackStart, fallbackEnd).toMillis();
            long totalTime = existingProcessingTime + fallbackTime;
            
            AnalysisMetrics fallbackMetrics = new AnalysisMetrics(
                originalMetrics.getStartTime(), fallbackEnd, 1, false, "fallback-rules", messageContent.length()
            );
            
            return EnhancedAnalysisResult.success(
                fallbackResult.getDetectedType(),
                fallbackResult.getConfidence(),
                fallbackResult.getReasoning(),
                totalTime,
                true,
                fallbackMetrics
            );
            
        } catch (Exception e) {
            log.error("Fallback analysis also failed: {}", e.getMessage());
            return EnhancedAnalysisResult.failure("Both primary and fallback analysis failed", 
                                                existingProcessingTime, originalMetrics);
        }
    }
    
    /**
     * 규칙 기반 분석 수행
     */
    private FallbackAnalysisResult performRuleBasedAnalysis(String messageContent, String chatRoomTitle) {
        String content = messageContent.toLowerCase();
        
        // 광고 패턴 검사
        if (content.contains("광고") || content.contains("할인") || content.contains("무료") || 
            content.contains("이벤트") || content.contains("프로모션")) {
            return new FallbackAnalysisResult("advertisement", 0.8, "Fallback: Advertisement keywords detected");
        }
        
        // 욕설/비방 패턴 검사
        if (content.contains("욕설") || content.contains("바보") || content.contains("멍청") ||
            content.contains("씨발") || content.contains("개새끼")) {
            return new FallbackAnalysisResult("abuse", 0.9, "Fallback: Abusive language detected");
        }
        
        // 스팸/도배 패턴 검사
        if (isSpamPattern(messageContent)) {
            return new FallbackAnalysisResult("spam", 0.7, "Fallback: Spam pattern detected");
        }
        
        // 분쟁 패턴 검사
        if (content.contains("싸움") || content.contains("갈등") || content.contains("문제")) {
            return new FallbackAnalysisResult("conflict", 0.6, "Fallback: Conflict keywords detected");
        }
        
        // 기본값: 정상
        return new FallbackAnalysisResult("normal", 0.9, "Fallback: No problematic patterns found");
    }
    
    /**
     * 스팸 패턴 검사
     */
    private boolean isSpamPattern(String content) {
        // 반복 문자 검사
        if (content.length() > 100) {
            long uniqueChars = content.chars().distinct().count();
            double uniqueRatio = (double) uniqueChars / content.length();
            if (uniqueRatio < 0.1) { // 10% 미만이 유니크 문자
                return true;
            }
        }
        
        // 같은 단어 반복 검사
        String[] words = content.split("\\s+");
        if (words.length > 10) {
            long uniqueWords = java.util.Arrays.stream(words).distinct().count();
            double wordUniqueRatio = (double) uniqueWords / words.length;
            if (wordUniqueRatio < 0.3) { // 30% 미만이 유니크 단어
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 배치 분석 수행 (여러 메시지 동시 처리)
     */
    public CompletableFuture<java.util.List<EnhancedAnalysisResult>> analyzeMessagesBatch(
            java.util.List<MessageAnalysisRequest> requests) {
        
        log.info("Starting batch analysis for {} messages", requests.size());
        
        java.util.List<CompletableFuture<EnhancedAnalysisResult>> tasks = requests.stream()
            .map(req -> analyzeMessageEnhanced(req.getContent(), req.getChatRoomTitle()))
            .toList();
        
        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
            .thenApply(v -> tasks.stream()
                .map(CompletableFuture::join)
                .toList());
    }
    
    /**
     * 분석 성능 통계 조회
     */
    public AnalysisPerformanceStats getPerformanceStats() {
        // TODO: 실제 메트릭 수집 구현
        return AnalysisPerformanceStats.builder()
            .totalAnalyses(0L)
            .averageProcessingTime(0.0)
            .successRate(0.0)
            .fallbackUsageRate(0.0)
            .accuracyRate(0.0)
            .build();
    }
    
    /**
     * API 상태 확인 (향상된 버전)
     */
    public CompletableFuture<Boolean> checkEnhancedApiHealth() {
        return asyncExecutor.executeWithTimeout(
            () -> {
                try {
                    CompletableFuture<Boolean> healthCheck = geminiWorkerClient.checkApiHealth();
                    return healthCheck.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Enhanced API health check failed: {}", e.getMessage());
                    return false;
                }
            },
            "enhanced-api-health-check",
            10000L // 10초 타임아웃
        );
    }
    
    /**
     * 폴백 분석 결과 DTO
     */
    private static class FallbackAnalysisResult {
        private final String detectedType;
        private final Double confidence;
        private final String reasoning;
        
        public FallbackAnalysisResult(String detectedType, Double confidence, String reasoning) {
            this.detectedType = detectedType;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
        
        public String getDetectedType() { return detectedType; }
        public Double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
    }
    
    /**
     * 메시지 분석 요청 DTO
     */
    public static class MessageAnalysisRequest {
        private final String content;
        private final String chatRoomTitle;
        private final String messageId;
        
        public MessageAnalysisRequest(String content, String chatRoomTitle, String messageId) {
            this.content = content;
            this.chatRoomTitle = chatRoomTitle;
            this.messageId = messageId;
        }
        
        public String getContent() { return content; }
        public String getChatRoomTitle() { return chatRoomTitle; }
        public String getMessageId() { return messageId; }
    }
    
    /**
     * 분석 성능 통계 DTO
     */
    public static class AnalysisPerformanceStats {
        private final long totalAnalyses;
        private final double averageProcessingTime;
        private final double successRate;
        private final double fallbackUsageRate;
        private final double accuracyRate;
        
        private AnalysisPerformanceStats(long totalAnalyses, double averageProcessingTime, 
                                       double successRate, double fallbackUsageRate, double accuracyRate) {
            this.totalAnalyses = totalAnalyses;
            this.averageProcessingTime = averageProcessingTime;
            this.successRate = successRate;
            this.fallbackUsageRate = fallbackUsageRate;
            this.accuracyRate = accuracyRate;
        }
        
        public static AnalysisPerformanceStatsBuilder builder() {
            return new AnalysisPerformanceStatsBuilder();
        }
        
        // Getters
        public long getTotalAnalyses() { return totalAnalyses; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public double getSuccessRate() { return successRate; }
        public double getFallbackUsageRate() { return fallbackUsageRate; }
        public double getAccuracyRate() { return accuracyRate; }
        
        public boolean meetsPerformanceTargets() {
            return averageProcessingTime <= 1000 && // 1초 이내
                   successRate >= 0.95 && // 95% 이상 성공률
                   accuracyRate >= 0.95; // 95% 이상 정확도
        }
        
        public static class AnalysisPerformanceStatsBuilder {
            private long totalAnalyses;
            private double averageProcessingTime;
            private double successRate;
            private double fallbackUsageRate;
            private double accuracyRate;
            
            public AnalysisPerformanceStatsBuilder totalAnalyses(long totalAnalyses) {
                this.totalAnalyses = totalAnalyses;
                return this;
            }
            
            public AnalysisPerformanceStatsBuilder averageProcessingTime(double averageProcessingTime) {
                this.averageProcessingTime = averageProcessingTime;
                return this;
            }
            
            public AnalysisPerformanceStatsBuilder successRate(double successRate) {
                this.successRate = successRate;
                return this;
            }
            
            public AnalysisPerformanceStatsBuilder fallbackUsageRate(double fallbackUsageRate) {
                this.fallbackUsageRate = fallbackUsageRate;
                return this;
            }
            
            public AnalysisPerformanceStatsBuilder accuracyRate(double accuracyRate) {
                this.accuracyRate = accuracyRate;
                return this;
            }
            
            public AnalysisPerformanceStats build() {
                return new AnalysisPerformanceStats(totalAnalyses, averageProcessingTime, 
                                                  successRate, fallbackUsageRate, accuracyRate);
            }
        }
    }
}