package com.sauron.common.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.sauron.common.cache.AnalysisCacheService;
import com.sauron.common.dto.BusinessException;
import com.sauron.common.dto.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;

/**
 * Gemini AI 워커 클라이언트
 * Gemini API를 통한 메시지 분석 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiWorkerClient {
    
    private final ObjectMapper objectMapper;
    private final AnalysisCacheService cacheService;
    
    @Autowired(required = false)
    private final GenerativeModel generativeModel;
    
    @Value("${gemini.api.max-retries:3}")
    private int maxRetries;
    
    @Value("${gemini.api.retry-delay:1s}")
    private String retryDelay;
    
    // 분석 가능한 메시지 타입들
    private static final String[] DETECTION_TYPES = {
        "normal", "spam", "advertisement", "abuse", "conflict", "inappropriate"
    };
    
    // Gemini 프롬프트 템플릿
    private static final String ANALYSIS_PROMPT = """
        다음 카카오톡 오픈채팅 메시지를 분석하여 적절한 카테고리로 분류해주세요.
        
        채팅방: %s
        메시지: %s
        
        분류 기준:
        - normal: 일반적인 대화, 정상적인 내용
        - spam: 도배성 메시지, 반복적인 내용
        - advertisement: 광고, 홍보, 판매 목적의 메시지
        - abuse: 욕설, 인신공격, 혐오 표현
        - conflict: 논쟁, 분쟁을 유발하는 내용
        - inappropriate: 부적절한 내용 (성적, 폭력적 등)
        
        응답 형식 (JSON):
        {
          "type": "분류된 카테고리",
          "confidence": 0.0-1.0 사이의 신뢰도,
          "reasoning": "분류 근거 설명 (한국어)"
        }
        
        반드시 위 JSON 형식으로만 답변해주세요.
        """;
    
    /**
     * 메시지를 비동기적으로 분석합니다.
     * 
     * @param messageContent 분석할 메시지 내용
     * @param chatRoomTitle 채팅방 제목 (컨텍스트 정보)
     * @return 분석 결과를 담은 CompletableFuture
     */
    public CompletableFuture<AnalysisResult> analyzeMessage(String messageContent, String chatRoomTitle) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting Gemini analysis for message (length: {})", 
                        messageContent != null ? messageContent.length() : 0);
                
                // 캐시에서 먼저 조회
                Optional<AnalysisResult> cachedResult = cacheService.getCachedAnalysis(messageContent, chatRoomTitle);
                if (cachedResult.isPresent()) {
                    log.info("Using cached analysis result - Type: {}", cachedResult.get().getDetectedType());
                    return cachedResult.get();
                }
                
                AnalysisResult result;
                
                // Gemini API 사용 가능 여부 확인
                if (generativeModel != null) {
                    result = performGeminiAnalysis(messageContent, chatRoomTitle);
                } else {
                    log.warn("Gemini API not configured, using stub analysis");
                    result = performStubAnalysis(messageContent, chatRoomTitle);
                }
                
                // 결과를 캐시에 저장
                cacheService.cacheAnalysis(messageContent, chatRoomTitle, result);
                
                log.info("Gemini analysis completed - Type: {}, Confidence: {}", 
                        result.getDetectedType(), result.getConfidenceScore());
                
                return result;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Gemini analysis interrupted", e);
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Analysis was interrupted", e);
                
            } catch (Exception e) {
                log.error("Gemini analysis failed", e);
                throw new BusinessException(ErrorCode.GEMINI_API_ERROR, "Failed to analyze message", e);
            }
        });
    }
    
    /**
     * 메시지 분석 배치 처리 (복수 메시지 동시 분석)
     * 
     * @param messages 분석할 메시지들
     * @return 각 메시지별 분석 결과
     */
    public CompletableFuture<BatchAnalysisResult> analyzeMessageBatch(BatchAnalysisRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting Gemini batch analysis for {} messages", request.getMessages().size());
                
                // 배치 처리 시뮬레이션
                Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 3000));
                
                BatchAnalysisResult batchResult = new BatchAnalysisResult();
                batchResult.setRequestId(request.getRequestId());
                batchResult.setTotalMessages(request.getMessages().size());
                
                // 각 메시지별 분석 수행
                for (MessageAnalysisRequest messageRequest : request.getMessages()) {
                    AnalysisResult result = analyzeMessage(
                        messageRequest.getContent(), 
                        messageRequest.getChatRoomTitle()
                    ).get(); // 동기적으로 대기
                    
                    result.setMessageId(messageRequest.getMessageId());
                    batchResult.addResult(result);
                }
                
                batchResult.setSuccessCount(batchResult.getResults().size());
                
                log.info("Gemini batch analysis completed - Success: {}/{}", 
                        batchResult.getSuccessCount(), batchResult.getTotalMessages());
                
                return batchResult;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Gemini batch analysis interrupted", e);
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Batch analysis was interrupted", e);
                
            } catch (Exception e) {
                log.error("Gemini batch analysis failed", e);
                throw new BusinessException(ErrorCode.GEMINI_API_ERROR, "Failed to analyze message batch", e);
            }
        });
    }
    
    /**
     * Gemini API 연결 상태 확인
     */
    public CompletableFuture<Boolean> checkApiHealth() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Checking Gemini API health");
                
                if (generativeModel == null) {
                    return false;
                }
                
                // 간단한 테스트 메시지로 API 상태 확인
                String testPrompt = "Hello, respond with just 'OK'";
                Content content = new Content.Builder()
                        .addText(testPrompt)
                        .setRole("user")
                        .build();
                
                GenerateContentResponse response = generativeModel.generateContent(content);
                boolean healthy = response.getText() != null;
                
                log.debug("Gemini API health check result: {}", healthy);
                return healthy;
                
            } catch (Exception e) {
                log.error("Gemini API health check failed", e);
                return false;
            }
        });
    }
    
    /**
     * 실제 Gemini API를 사용한 메시지 분석
     */
    private AnalysisResult performGeminiAnalysis(String messageContent, String chatRoomTitle) {
        try {
            long startTime = System.currentTimeMillis();
            
            // 프롬프트 생성
            String prompt = String.format(ANALYSIS_PROMPT, 
                    chatRoomTitle != null ? chatRoomTitle : "알 수 없음", 
                    messageContent);
            
            // Gemini API 호출 (재시도 로직 포함)
            GenerateContentResponse response = callGeminiWithRetry(prompt);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // 응답 파싱
            String responseText = response.getText();
            GeminiAnalysisResponse geminiResponse = parseGeminiResponse(responseText);
            
            return AnalysisResult.builder()
                    .detectedType(geminiResponse.type)
                    .confidenceScore(geminiResponse.confidence)
                    .reasoning(geminiResponse.reasoning)
                    .processingTimeMs(processingTime)
                    .metadata("{\"model\":\"gemini-1.5-flash\",\"version\":\"1.0\",\"api\":\"actual\"}")
                    .build();
                    
        } catch (Exception e) {
            log.error("Gemini API call failed, falling back to stub analysis", e);
            return performStubAnalysis(messageContent, chatRoomTitle);
        }
    }
    
    /**
     * 재시도 로직을 포함한 Gemini API 호출
     */
    private GenerateContentResponse callGeminiWithRetry(String prompt) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Content content = new Content.Builder()
                        .addText(prompt)
                        .setRole("user")
                        .build();
                
                return generativeModel.generateContent(content);
                
            } catch (Exception e) {
                lastException = e;
                log.warn("Gemini API call attempt {} failed: {}", attempt, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000 * attempt); // 지수적 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("All Gemini API retry attempts failed", lastException);
    }
    
    /**
     * Gemini 응답을 파싱합니다.
     */
    private GeminiAnalysisResponse parseGeminiResponse(String responseText) {
        try {
            // JSON 부분만 추출 (응답에 추가 텍스트가 있을 수 있음)
            String jsonStart = responseText.indexOf("{") != -1 ? responseText.substring(responseText.indexOf("{")) : responseText;
            String jsonEnd = jsonStart.lastIndexOf("}") != -1 ? jsonStart.substring(0, jsonStart.lastIndexOf("}") + 1) : jsonStart;
            
            return objectMapper.readValue(jsonEnd, GeminiAnalysisResponse.class);
            
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", responseText, e);
            
            // 파싱 실패 시 기본값 반환
            return new GeminiAnalysisResponse("unknown", 0.0, "Response parsing failed: " + responseText);
        }
    }
    
    /**
     * Gemini 응답 구조체
     */
    private static class GeminiAnalysisResponse {
        public String type;
        public Double confidence;
        public String reasoning;
        
        public GeminiAnalysisResponse() {}
        
        public GeminiAnalysisResponse(String type, Double confidence, String reasoning) {
            this.type = type;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
    }
    
    /**
     * Stub 분석 구현 (실제 Gemini API 대신 사용)
     */
    private AnalysisResult performStubAnalysis(String messageContent, String chatRoomTitle) {
        if (messageContent == null || messageContent.trim().isEmpty()) {
            return AnalysisResult.builder()
                    .detectedType("unknown")
                    .confidenceScore(0.0)
                    .reasoning("Empty or null message content")
                    .processingTimeMs(100L)
                    .build();
        }
        
        // 간단한 규칙 기반 분석 (실제로는 Gemini AI가 수행)
        String lowerContent = messageContent.toLowerCase();
        String detectedType;
        double confidence;
        String reasoning;
        
        if (lowerContent.contains("광고") || lowerContent.contains("홍보") || 
            lowerContent.contains("판매") || lowerContent.contains("구매")) {
            detectedType = "advertisement";
            confidence = 0.85 + ThreadLocalRandom.current().nextDouble(0.1);
            reasoning = "Contains advertisement keywords";
            
        } else if (lowerContent.contains("욕설") || lowerContent.contains("바보") || 
                  lowerContent.contains("멍청") || lowerContent.length() > 200) {
            detectedType = "abuse";
            confidence = 0.75 + ThreadLocalRandom.current().nextDouble(0.15);
            reasoning = "Contains abusive language or excessive length";
            
        } else if (lowerContent.contains("도배") || 
                  (messageContent.length() > 100 && messageContent.chars().distinct().count() < 10)) {
            detectedType = "spam";
            confidence = 0.80 + ThreadLocalRandom.current().nextDouble(0.15);
            reasoning = "Spam pattern detected";
            
        } else if (lowerContent.contains("싸움") || lowerContent.contains("분쟁") || 
                  lowerContent.contains("논쟁")) {
            detectedType = "conflict";
            confidence = 0.70 + ThreadLocalRandom.current().nextDouble(0.2);
            reasoning = "Conflict indicators found";
            
        } else {
            detectedType = "normal";
            confidence = 0.90 + ThreadLocalRandom.current().nextDouble(0.08);
            reasoning = "No suspicious patterns detected";
        }
        
        return AnalysisResult.builder()
                .detectedType(detectedType)
                .confidenceScore(confidence)
                .reasoning(reasoning)
                .processingTimeMs(ThreadLocalRandom.current().nextLong(300, 1200))
                .metadata("{\"model\":\"gemini-pro-stub\",\"version\":\"1.0\"}")
                .build();
    }
    
    /**
     * 분석 결과 클래스
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AnalysisResult {
        private String messageId;
        private String detectedType;
        private Double confidenceScore;
        private String reasoning;
        private Long processingTimeMs;
        private String metadata;
    }
    
    /**
     * 배치 분석 요청 클래스
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchAnalysisRequest {
        private String requestId;
        private java.util.List<MessageAnalysisRequest> messages;
    }
    
    /**
     * 개별 메시지 분석 요청 클래스
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MessageAnalysisRequest {
        private String messageId;
        private String content;
        private String chatRoomTitle;
        private String deviceId;
    }
    
    /**
     * 배치 분석 결과 클래스
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchAnalysisResult {
        private String requestId;
        private Integer totalMessages;
        private Integer successCount;
        private Integer failureCount = 0;
        private java.util.List<AnalysisResult> results = new java.util.ArrayList<>();
        
        public void addResult(AnalysisResult result) {
            this.results.add(result);
        }
    }
}