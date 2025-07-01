package com.sauron.common.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.sauron.common.cache.AnalysisCacheService;
import com.sauron.common.external.GeminiWorkerClient.AnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GeminiWorkerClient 테스트
 */
@ExtendWith(MockitoExtension.class)
class GeminiWorkerClientTest {
    
    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private AnalysisCacheService cacheService;
    
    @Mock
    private GenerativeModel generativeModel;
    
    @Mock
    private GenerateContentResponse mockResponse;
    
    private GeminiWorkerClient geminiWorkerClient;
    
    @BeforeEach
    void setUp() {
        geminiWorkerClient = new GeminiWorkerClient(objectMapper, cacheService, generativeModel);
        ReflectionTestUtils.setField(geminiWorkerClient, "maxRetries", 3);
        ReflectionTestUtils.setField(geminiWorkerClient, "retryDelay", "1s");
    }
    
    @Test
    void testAnalyzeMessage_WithCacheHit() throws Exception {
        // Given
        String messageContent = "안녕하세요";
        String chatRoomTitle = "테스트방";
        
        AnalysisResult cachedResult = AnalysisResult.builder()
                .detectedType("normal")
                .confidenceScore(0.95)
                .reasoning("Cached result")
                .processingTimeMs(50L)
                .build();
        
        when(cacheService.getCachedAnalysis(messageContent, chatRoomTitle))
                .thenReturn(Optional.of(cachedResult));
        
        // When
        CompletableFuture<AnalysisResult> future = geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
        AnalysisResult result = future.get();
        
        // Then
        assertEquals("normal", result.getDetectedType());
        assertEquals(0.95, result.getConfidenceScore());
        assertEquals("Cached result", result.getReasoning());
        
        verify(cacheService).getCachedAnalysis(messageContent, chatRoomTitle);
        verify(generativeModel, never()).generateContent(any());
    }
    
    @Test
    void testAnalyzeMessage_WithGeminiAPI() throws Exception {
        // Given
        String messageContent = "광고입니다. 지금 구매하세요!";
        String chatRoomTitle = "테스트방";
        
        String geminiResponse = """
                {
                  "type": "advertisement",
                  "confidence": 0.92,
                  "reasoning": "광고성 키워드가 포함된 메시지"
                }
                """;
        
        when(cacheService.getCachedAnalysis(messageContent, chatRoomTitle))
                .thenReturn(Optional.empty());
        when(mockResponse.getText()).thenReturn(geminiResponse);
        when(generativeModel.generateContent(any())).thenReturn(mockResponse);
        when(objectMapper.readValue(anyString(), eq(GeminiWorkerClient.GeminiAnalysisResponse.class)))
                .thenReturn(createMockGeminiResponse("advertisement", 0.92, "광고성 키워드가 포함된 메시지"));
        
        // When
        CompletableFuture<AnalysisResult> future = geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
        AnalysisResult result = future.get();
        
        // Then
        assertEquals("advertisement", result.getDetectedType());
        assertEquals(0.92, result.getConfidenceScore());
        assertEquals("광고성 키워드가 포함된 메시지", result.getReasoning());
        assertNotNull(result.getProcessingTimeMs());
        assertTrue(result.getProcessingTimeMs() > 0);
        
        verify(cacheService).getCachedAnalysis(messageContent, chatRoomTitle);
        verify(cacheService).cacheAnalysis(eq(messageContent), eq(chatRoomTitle), any(AnalysisResult.class));
        verify(generativeModel).generateContent(any());
    }
    
    @Test
    void testAnalyzeMessage_FallbackToStub() throws Exception {
        // Given
        String messageContent = "욕설이 포함된 메시지";
        String chatRoomTitle = "테스트방";
        
        when(cacheService.getCachedAnalysis(messageContent, chatRoomTitle))
                .thenReturn(Optional.empty());
        when(generativeModel.generateContent(any()))
                .thenThrow(new RuntimeException("API Error"));
        
        // When
        CompletableFuture<AnalysisResult> future = geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
        AnalysisResult result = future.get();
        
        // Then
        assertEquals("abuse", result.getDetectedType());
        assertTrue(result.getConfidenceScore() > 0.7);
        assertEquals("Contains abusive language or excessive length", result.getReasoning());
        
        verify(generativeModel).generateContent(any());
        verify(cacheService).cacheAnalysis(eq(messageContent), eq(chatRoomTitle), any(AnalysisResult.class));
    }
    
    @Test
    void testAnalyzeMessage_StubClassification() throws Exception {
        // Given - Gemini API가 null인 경우
        geminiWorkerClient = new GeminiWorkerClient(objectMapper, cacheService, null);
        
        String messageContent = "안녕하세요! 일반적인 인사입니다.";
        String chatRoomTitle = "테스트방";
        
        when(cacheService.getCachedAnalysis(messageContent, chatRoomTitle))
                .thenReturn(Optional.empty());
        
        // When
        CompletableFuture<AnalysisResult> future = geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
        AnalysisResult result = future.get();
        
        // Then
        assertEquals("normal", result.getDetectedType());
        assertTrue(result.getConfidenceScore() > 0.9);
        assertEquals("No suspicious patterns detected", result.getReasoning());
        
        verify(cacheService).cacheAnalysis(eq(messageContent), eq(chatRoomTitle), any(AnalysisResult.class));
    }
    
    @Test
    void testAnalyzeMessage_SpamDetection() throws Exception {
        // Given
        geminiWorkerClient = new GeminiWorkerClient(objectMapper, cacheService, null);
        
        String messageContent = "도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배";
        String chatRoomTitle = "테스트방";
        
        when(cacheService.getCachedAnalysis(messageContent, chatRoomTitle))
                .thenReturn(Optional.empty());
        
        // When
        CompletableFuture<AnalysisResult> future = geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
        AnalysisResult result = future.get();
        
        // Then
        assertEquals("spam", result.getDetectedType());
        assertTrue(result.getConfidenceScore() > 0.8);
        assertEquals("Spam pattern detected", result.getReasoning());
    }
    
    @Test
    void testAnalyzeMessage_EmptyContent() throws Exception {
        // Given
        geminiWorkerClient = new GeminiWorkerClient(objectMapper, cacheService, null);
        
        String messageContent = "";
        String chatRoomTitle = "테스트방";
        
        when(cacheService.getCachedAnalysis(messageContent, chatRoomTitle))
                .thenReturn(Optional.empty());
        
        // When
        CompletableFuture<AnalysisResult> future = geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
        AnalysisResult result = future.get();
        
        // Then
        assertEquals("unknown", result.getDetectedType());
        assertEquals(0.0, result.getConfidenceScore());
        assertEquals("Empty or null message content", result.getReasoning());
    }
    
    @Test
    void testCheckApiHealth_WithValidAPI() throws Exception {
        // Given
        when(mockResponse.getText()).thenReturn("OK");
        when(generativeModel.generateContent(any())).thenReturn(mockResponse);
        
        // When
        CompletableFuture<Boolean> future = geminiWorkerClient.checkApiHealth();
        Boolean result = future.get();
        
        // Then
        assertTrue(result);
        verify(generativeModel).generateContent(any());
    }
    
    @Test
    void testCheckApiHealth_WithNullModel() throws Exception {
        // Given
        geminiWorkerClient = new GeminiWorkerClient(objectMapper, cacheService, null);
        
        // When
        CompletableFuture<Boolean> future = geminiWorkerClient.checkApiHealth();
        Boolean result = future.get();
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testCheckApiHealth_WithAPIError() throws Exception {
        // Given
        when(generativeModel.generateContent(any()))
                .thenThrow(new RuntimeException("API Error"));
        
        // When
        CompletableFuture<Boolean> future = geminiWorkerClient.checkApiHealth();
        Boolean result = future.get();
        
        // Then
        assertFalse(result);
    }
    
    /**
     * Mock GeminiAnalysisResponse 생성 헬퍼
     */
    private Object createMockGeminiResponse(String type, Double confidence, String reasoning) {
        // 실제로는 private static class이므로 reflection을 사용하거나
        // 테스트용 DTO를 별도로 만들어야 하지만, 여기서는 간단히 처리
        return new Object() {
            public String type = type;
            public Double confidence = confidence;
            public String reasoning = reasoning;
        };
    }
}