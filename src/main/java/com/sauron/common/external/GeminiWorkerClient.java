package com.sauron.common.external;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.sauron.common.cache.AnalysisCacheService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Gemini API 클라이언트
 * 메시지 분석을 위한 Gemini AI 서비스 연동
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiWorkerClient {
    
    private final Client geminiClient;
    private final AnalysisCacheService cacheService;
    
    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String modelName;
    
    @Value("${gemini.api.max-retries:3}")
    private int maxRetries;
    
    @Value("${gemini.api.retry-delay:1s}")
    private Duration retryDelay;
    
    /**
     * 메시지 분석 요청
     */
    public CompletableFuture<AnalysisResult> analyzeMessage(String messageContent, String chatRoomTitle) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 캐시 확인
                var cachedResult = cacheService.getCachedAnalysis(messageContent, chatRoomTitle);
                if (cachedResult.isPresent()) {
                    log.debug("Cache hit for message analysis");
                    return cachedResult.get();
                }
                
                // 2. Gemini 분석 수행
                AnalysisResult result;
                if (geminiClient != null) {
                    result = performGeminiAnalysis(messageContent, chatRoomTitle);
                } else {
                    log.warn("Gemini client not available, using stub analysis");
                    result = performStubAnalysis(messageContent, chatRoomTitle);
                }
                
                // 3. 결과 캐시 저장
                cacheService.cacheAnalysis(messageContent, chatRoomTitle, result);
                
                return result;
                
            } catch (Exception e) {
                log.error("Error analyzing message", e);
                return new AnalysisResult("error", 0.0, "Analysis failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * 실제 Gemini API 분석 수행
     */
    private AnalysisResult performGeminiAnalysis(String messageContent, String chatRoomTitle) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String prompt = buildAnalysisPrompt(messageContent, chatRoomTitle);
                
                GenerateContentResponse response = geminiClient.models.generateContent(
                        modelName,
                        prompt,
                        null
                );
                
                String responseText = response.text();
                return parseGeminiResponse(responseText);
                
            } catch (Exception e) {
                lastException = e;
                log.warn("Gemini API call failed on attempt {} of {}: {}", attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelay.multipliedBy(attempt).toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        log.error("Gemini API failed after {} attempts, using fallback", maxRetries, lastException);
        return performStubAnalysis(messageContent, chatRoomTitle);
    }
    
    /**
     * 분석 프롬프트 생성
     */
    private String buildAnalysisPrompt(String messageContent, String chatRoomTitle) {
        return String.format("""
                다음 메시지를 분석하여 이상 메시지인지 판단해주세요.
                
                채팅방: %s
                메시지: %s
                
                다음 기준으로 분류해주세요:
                - normal: 정상 메시지
                - spam: 도배/스팸
                - advertisement: 광고
                - abuse: 욕설/비방
                - inappropriate: 부적절한 내용
                - conflict: 분쟁/갈등
                
                응답 형식 (JSON):
                {
                  "type": "분류결과",
                  "confidence": 0.0-1.0,
                  "reason": "판단근거"
                }
                """, chatRoomTitle, messageContent);
    }
    
    /**
     * Gemini 응답 파싱
     */
    private AnalysisResult parseGeminiResponse(String responseText) {
        try {
            // JSON 응답 파싱 (간단한 구현)
            String type = extractJsonValue(responseText, "type");
            String confidenceStr = extractJsonValue(responseText, "confidence");
            String reason = extractJsonValue(responseText, "reason");
            
            double confidence = Double.parseDouble(confidenceStr);
            
            return new AnalysisResult(type, confidence, reason);
            
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response, using fallback: {}", e.getMessage());
            return new AnalysisResult("normal", 0.5, "Failed to parse response");
        }
    }
    
    /**
     * 간단한 JSON 값 추출
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        
        // confidence는 숫자일 수 있음
        if ("confidence".equals(key)) {
            pattern = "\"" + key + "\"\\s*:\\s*([0-9.]+)";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        }
        
        return "";
    }
    
    /**
     * 스텁 분석 (Gemini API 사용 불가 시)
     */
    private AnalysisResult performStubAnalysis(String messageContent, String chatRoomTitle) {
        // 간단한 규칙 기반 분석
        String lowerContent = messageContent.toLowerCase();
        
        if (lowerContent.contains("광고") || lowerContent.contains("할인") || lowerContent.contains("무료")) {
            return new AnalysisResult("advertisement", 0.8, "Keyword-based detection: advertisement");
        }
        
        if (lowerContent.contains("욕설") || lowerContent.contains("바보") || lowerContent.contains("멍청")) {
            return new AnalysisResult("abuse", 0.7, "Keyword-based detection: abuse");
        }
        
        if (messageContent.length() > 500 && messageContent.chars().distinct().count() < 10) {
            return new AnalysisResult("spam", 0.6, "Pattern-based detection: repetitive content");
        }
        
        return new AnalysisResult("normal", 0.9, "Stub analysis: appears normal");
    }
    
    /**
     * API 상태 확인
     */
    public CompletableFuture<Boolean> checkApiHealth() {
        return CompletableFuture.supplyAsync(() -> {
            if (geminiClient == null) {
                return false;
            }
            
            try {
                GenerateContentResponse response = geminiClient.models.generateContent(
                        modelName,
                        "Test message for health check",
                        null
                );
                return response.text() != null && !response.text().isEmpty();
            } catch (Exception e) {
                log.warn("Gemini API health check failed", e);
                return false;
            }
        });
    }
    
    /**
     * 분석 결과 클래스
     */
    @Data
    @Builder
    public static class AnalysisResult {
        private final String detectedType;
        private final Double confidenceScore;
        private final String metadata;
        private final String messageId;
        private final long processingTimeMs;
        
        public AnalysisResult(String detectedType, Double confidenceScore, String metadata) {
            this.detectedType = detectedType;
            this.confidenceScore = confidenceScore;
            this.metadata = metadata;
            this.messageId = null;
            this.processingTimeMs = 0L;
        }
        
        public AnalysisResult(String detectedType, Double confidenceScore, String metadata, String messageId, long processingTimeMs) {
            this.detectedType = detectedType;
            this.confidenceScore = confidenceScore;
            this.metadata = metadata;
            this.messageId = messageId;
            this.processingTimeMs = processingTimeMs;
        }
        
        // Additional getters for test compatibility
        public String getReasoning() {
            return metadata;
        }
        
        public String getMessageId() {
            return messageId;
        }
        
        public long getProcessingTimeMs() {
            return processingTimeMs;
        }
    }
}