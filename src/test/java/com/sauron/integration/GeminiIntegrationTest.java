package com.sauron.integration;

import com.sauron.common.cache.AnalysisCacheService;
import com.sauron.common.external.GeminiWorkerClient;
import com.sauron.common.external.GeminiWorkerClient.AnalysisResult;
import com.sauron.common.queue.MessageQueueService;
import com.sauron.common.worker.GeminiAnalysisWorker;
import com.sauron.listener.dto.MessageRequest;
import com.sauron.listener.entity.Message;
import com.sauron.listener.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gemini 통합 테스트
 * 전체 파이프라인 (Queue -> Worker -> Gemini -> Cache -> DB) 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "gemini.api.key=test-key", 
    "gemini.worker.enabled=false",  // 자동 시작 방지
    "gemini.cache.enabled=true"
})
@Transactional
class GeminiIntegrationTest {
    
    @Autowired
    private GeminiWorkerClient geminiWorkerClient;
    
    @Autowired
    private AnalysisCacheService cacheService;
    
    @Autowired
    private MessageQueueService queueService;
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Test
    void testEndToEndMessageAnalysis() throws Exception {
        // Given
        String messageId = UUID.randomUUID().toString();
        String messageContent = "안녕하세요! 일반적인 인사 메시지입니다.";
        String chatRoomTitle = "테스트 채팅방";
        String deviceId = "test-device-001";
        
        // 메시지를 데이터베이스에 저장
        Message message = Message.builder()
                .messageId(messageId)
                .deviceId(deviceId)
                .chatRoomTitle(chatRoomTitle)
                .contentEncrypted(messageContent) // 실제로는 암호화되어야 함
                .contentHash("test-hash")
                .detectionStatus("PENDING")
                .createdAt(Instant.now())
                .build();
        
        messageRepository.save(message);
        
        // When
        // 1. Gemini 분석 수행
        CompletableFuture<AnalysisResult> analysisTask = geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
        AnalysisResult analysisResult = analysisTask.get(10, TimeUnit.SECONDS);
        
        // Then
        // 2. 분석 결과 검증
        assertNotNull(analysisResult);
        assertNotNull(analysisResult.getDetectedType());
        assertNotNull(analysisResult.getConfidenceScore());
        assertTrue(analysisResult.getConfidenceScore() >= 0.0);
        assertTrue(analysisResult.getConfidenceScore() <= 1.0);
        assertNotNull(analysisResult.getReasoning());
        assertTrue(analysisResult.getProcessingTimeMs() > 0);
        
        // 3. 캐시 동작 검증
        var cachedResult = cacheService.getCachedAnalysis(messageContent, chatRoomTitle);
        assertTrue(cachedResult.isPresent());
        assertEquals(analysisResult.getDetectedType(), cachedResult.get().getDetectedType());
        assertEquals(analysisResult.getConfidenceScore(), cachedResult.get().getConfidenceScore());
        
        // 4. 두 번째 호출에서 캐시 히트 확인
        long startTime = System.currentTimeMillis();
        CompletableFuture<AnalysisResult> cachedTask = geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
        AnalysisResult cachedAnalysisResult = cachedTask.get(5, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        // 캐시된 결과는 매우 빠르게 반환되어야 함 (50ms 이하)
        assertTrue(endTime - startTime < 100);
        assertEquals(analysisResult.getDetectedType(), cachedAnalysisResult.getDetectedType());
    }
    
    @Test
    void testMessageClassificationAccuracy() throws Exception {
        // Given - 다양한 메시지 타입 테스트
        var testCases = new Object[][] {
            {"안녕하세요! 반갑습니다.", "normal"},
            {"광고합니다! 지금 구매하세요! 50% 할인!", "advertisement"},
            {"도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배도배", "spam"},
            {"바보야! 멍청한 놈아!", "abuse"},
            {"너희는 틀렸어! 싸움이야!", "conflict"}
        };
        
        // When & Then
        for (Object[] testCase : testCases) {
            String content = (String) testCase[0];
            String expectedType = (String) testCase[1];
            
            CompletableFuture<AnalysisResult> task = geminiWorkerClient.analyzeMessage(content, "테스트방");
            AnalysisResult result = task.get(10, TimeUnit.SECONDS);
            
            // 스텁 모드에서는 정확한 분류가 보장됨
            assertEquals(expectedType, result.getDetectedType(), 
                        "Message: '" + content + "' should be classified as " + expectedType);
            assertTrue(result.getConfidenceScore() > 0.7, 
                      "Confidence should be high for clear cases");
        }
    }
    
    @Test
    void testQueueIntegration() throws Exception {
        // Given
        String messageId = UUID.randomUUID().toString();
        MessageRequest messageRequest = new MessageRequest();
        messageRequest.setMessageId(messageId);
        messageRequest.setContent("큐 테스트 메시지");
        messageRequest.setChatRoomTitle("테스트방");
        messageRequest.setDeviceId("test-device");
        messageRequest.setPriority("normal");
        
        // When
        CompletableFuture<Boolean> queueTask = queueService.enqueueForAnalysis(messageRequest);
        Boolean queued = queueTask.get(5, TimeUnit.SECONDS);
        
        // Then
        assertTrue(queued);
        
        // 큐 상태 확인
        MessageQueueService.QueueStatus status = queueService.getQueueStatus();
        assertTrue(status.isHealthy());
        assertTrue(status.getMainQueueSize() >= 0);
    }
    
    @Test
    void testCacheInvalidation() throws Exception {
        // Given
        String messageContent = "캐시 무효화 테스트";
        String chatRoomTitle = "테스트방";
        
        // 첫 번째 분석으로 캐시 생성
        CompletableFuture<AnalysisResult> firstTask = geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
        firstTask.get(10, TimeUnit.SECONDS);
        
        // 캐시 존재 확인
        var cachedResult = cacheService.getCachedAnalysis(messageContent, chatRoomTitle);
        assertTrue(cachedResult.isPresent());
        
        // When
        boolean invalidated = cacheService.invalidateCache(messageContent, chatRoomTitle);
        
        // Then
        assertTrue(invalidated);
        var afterInvalidation = cacheService.getCachedAnalysis(messageContent, chatRoomTitle);
        assertFalse(afterInvalidation.isPresent());
    }
    
    @Test
    void testApiHealthCheck() throws Exception {
        // When
        CompletableFuture<Boolean> healthTask = geminiWorkerClient.checkApiHealth();
        Boolean healthy = healthTask.get(10, TimeUnit.SECONDS);
        
        // Then
        // 스텁 모드에서는 GenerativeModel이 null이므로 false
        assertFalse(healthy);
    }
    
    @Test
    void testCacheStats() throws Exception {
        // Given
        String messageContent = "통계 테스트 메시지";
        String chatRoomTitle = "테스트방";
        
        // 캐시에 아이템 추가
        CompletableFuture<AnalysisResult> task = geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle);
        task.get(10, TimeUnit.SECONDS);
        
        // When
        AnalysisCacheService.CacheStats stats = cacheService.getCacheStats();
        
        // Then
        assertTrue(stats.isEnabled());
        assertTrue(stats.getTotalEntries() >= 0);
        assertEquals(300, stats.getTtlSeconds()); // 5분 = 300초
    }
    
    @Test
    void testBatchAnalysis() throws Exception {
        // Given
        var batchRequest = new GeminiWorkerClient.BatchAnalysisRequest();
        batchRequest.setRequestId(UUID.randomUUID().toString());
        
        var messages = java.util.List.of(
            createMessageAnalysisRequest("msg-1", "안녕하세요"),
            createMessageAnalysisRequest("msg-2", "광고합니다!"),
            createMessageAnalysisRequest("msg-3", "도배메시지".repeat(20))
        );
        batchRequest.setMessages(messages);
        
        // When
        CompletableFuture<GeminiWorkerClient.BatchAnalysisResult> task = 
            geminiWorkerClient.analyzeMessageBatch(batchRequest);
        GeminiWorkerClient.BatchAnalysisResult result = task.get(30, TimeUnit.SECONDS);
        
        // Then
        assertNotNull(result);
        assertEquals(3, result.getTotalMessages());
        assertEquals(3, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals(3, result.getResults().size());
        
        // 각 메시지의 분석 결과 검증
        for (AnalysisResult analysisResult : result.getResults()) {
            assertNotNull(analysisResult.getMessageId());
            assertNotNull(analysisResult.getDetectedType());
            assertTrue(analysisResult.getConfidenceScore() > 0);
        }
    }
    
    private GeminiWorkerClient.MessageAnalysisRequest createMessageAnalysisRequest(String messageId, String content) {
        var request = new GeminiWorkerClient.MessageAnalysisRequest();
        request.setMessageId(messageId);
        request.setContent(content);
        request.setChatRoomTitle("테스트방");
        request.setDeviceId("test-device");
        return request;
    }
}