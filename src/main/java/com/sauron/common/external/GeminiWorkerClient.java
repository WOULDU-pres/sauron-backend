package com.sauron.common.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauron.common.dto.BusinessException;
import com.sauron.common.dto.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gemini AI 워커 클라이언트
 * Gemini API를 통한 메시지 분석 기능을 제공합니다.
 * 현재는 실제 API 호출 대신 stub 구현으로 동작합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiWorkerClient {
    
    private final ObjectMapper objectMapper;
    
    // 분석 가능한 메시지 타입들
    private static final String[] DETECTION_TYPES = {
        "normal", "spam", "advertisement", "abuse", "conflict", "inappropriate"
    };
    
    // Gemini API 엔드포인트 (실제 구현 시 사용)
    private static final String GEMINI_API_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent";
    
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
                
                // 실제 Gemini API 호출 시뮬레이션 (처리 시간)
                Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1500));
                
                // Stub 구현: 실제로는 Gemini API를 호출
                AnalysisResult result = performStubAnalysis(messageContent, chatRoomTitle);
                
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
                    AnalysisResult result = performStubAnalysis(
                        messageRequest.getContent(), 
                        messageRequest.getChatRoomTitle()
                    );
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
                
                // 실제로는 Gemini API의 헬스체크 엔드포인트 호출
                Thread.sleep(100);
                
                // Stub: 90% 확률로 정상
                boolean healthy = ThreadLocalRandom.current().nextDouble() > 0.1;
                
                log.debug("Gemini API health check result: {}", healthy);
                return healthy;
                
            } catch (Exception e) {
                log.error("Gemini API health check failed", e);
                return false;
            }
        });
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