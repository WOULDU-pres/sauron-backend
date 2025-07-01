package com.sauron.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauron.common.external.GeminiWorkerClient.AnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AnalysisCacheService 테스트
 */
@ExtendWith(MockitoExtension.class)
class AnalysisCacheServiceTest {
    
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    @Mock
    private ObjectMapper objectMapper;
    
    private AnalysisCacheService cacheService;
    
    @BeforeEach
    void setUp() {
        cacheService = new AnalysisCacheService(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(cacheService, "cacheEnabled", true);
        ReflectionTestUtils.setField(cacheService, "cacheTtl", Duration.ofMinutes(5));
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
    
    @Test
    void testGetCachedAnalysis_CacheHit() throws Exception {
        // Given
        String messageContent = "테스트 메시지";
        String chatRoomTitle = "테스트방";
        
        AnalysisResult expectedResult = AnalysisResult.builder()
                .detectedType("normal")
                .confidenceScore(0.95)
                .reasoning("Cached result")
                .build();
        
        String serializedResult = "{\"detectedType\":\"normal\",\"confidenceScore\":0.95}";
        
        when(valueOperations.get(anyString())).thenReturn(serializedResult);
        when(objectMapper.readValue(serializedResult, AnalysisResult.class))
                .thenReturn(expectedResult);
        
        // When
        Optional<AnalysisResult> result = cacheService.getCachedAnalysis(messageContent, chatRoomTitle);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals("normal", result.get().getDetectedType());
        assertEquals(0.95, result.get().getConfidenceScore());
        
        verify(valueOperations).get(anyString());
        verify(objectMapper).readValue(serializedResult, AnalysisResult.class);
    }
    
    @Test
    void testGetCachedAnalysis_CacheMiss() {
        // Given
        String messageContent = "테스트 메시지";
        String chatRoomTitle = "테스트방";
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // When
        Optional<AnalysisResult> result = cacheService.getCachedAnalysis(messageContent, chatRoomTitle);
        
        // Then
        assertFalse(result.isPresent());
        verify(valueOperations).get(anyString());
        verify(objectMapper, never()).readValue(anyString(), eq(AnalysisResult.class));
    }
    
    @Test
    void testGetCachedAnalysis_CacheDisabled() {
        // Given
        ReflectionTestUtils.setField(cacheService, "cacheEnabled", false);
        
        String messageContent = "테스트 메시지";
        String chatRoomTitle = "테스트방";
        
        // When
        Optional<AnalysisResult> result = cacheService.getCachedAnalysis(messageContent, chatRoomTitle);
        
        // Then
        assertFalse(result.isPresent());
        verify(valueOperations, never()).get(anyString());
    }
    
    @Test
    void testCacheAnalysis_Success() throws Exception {
        // Given
        String messageContent = "테스트 메시지";
        String chatRoomTitle = "테스트방";
        
        AnalysisResult analysisResult = AnalysisResult.builder()
                .detectedType("advertisement")
                .confidenceScore(0.88)
                .reasoning("Contains ad keywords")
                .build();
        
        String serializedResult = "{\"detectedType\":\"advertisement\",\"confidenceScore\":0.88}";
        
        when(objectMapper.writeValueAsString(analysisResult)).thenReturn(serializedResult);
        
        // When
        cacheService.cacheAnalysis(messageContent, chatRoomTitle, analysisResult);
        
        // Then
        verify(objectMapper).writeValueAsString(analysisResult);
        verify(valueOperations).set(anyString(), eq(serializedResult), eq(Duration.ofMinutes(5)));
    }
    
    @Test
    void testCacheAnalysis_CacheDisabled() throws Exception {
        // Given
        ReflectionTestUtils.setField(cacheService, "cacheEnabled", false);
        
        String messageContent = "테스트 메시지";
        String chatRoomTitle = "테스트방";
        
        AnalysisResult analysisResult = AnalysisResult.builder()
                .detectedType("normal")
                .confidenceScore(0.95)
                .build();
        
        // When
        cacheService.cacheAnalysis(messageContent, chatRoomTitle, analysisResult);
        
        // Then
        verify(objectMapper, never()).writeValueAsString(any());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }
    
    @Test
    void testInvalidateCache_Success() {
        // Given
        String messageContent = "테스트 메시지";
        String chatRoomTitle = "테스트방";
        
        when(redisTemplate.delete(anyString())).thenReturn(true);
        
        // When
        boolean result = cacheService.invalidateCache(messageContent, chatRoomTitle);
        
        // Then
        assertTrue(result);
        verify(redisTemplate).delete(anyString());
    }
    
    @Test
    void testInvalidateCache_CacheDisabled() {
        // Given
        ReflectionTestUtils.setField(cacheService, "cacheEnabled", false);
        
        String messageContent = "테스트 메시지";
        String chatRoomTitle = "테스트방";
        
        // When
        boolean result = cacheService.invalidateCache(messageContent, chatRoomTitle);
        
        // Then
        assertFalse(result);
        verify(redisTemplate, never()).delete(anyString());
    }
    
    @Test
    void testGetCacheStats() {
        // Given
        Set<String> mockKeys = Set.of("key1", "key2", "key3");
        when(redisTemplate.keys(anyString())).thenReturn(mockKeys);
        when(valueOperations.get("health-check")).thenReturn("ok");
        
        // When
        AnalysisCacheService.CacheStats stats = cacheService.getCacheStats();
        
        // Then
        assertTrue(stats.isEnabled());
        assertEquals(3, stats.getTotalEntries());
        assertEquals(300, stats.getTtlSeconds()); // 5분 = 300초
        assertTrue(stats.isHealthy());
    }
    
    @Test
    void testGetCacheStats_RedisUnhealthy() {
        // Given
        when(redisTemplate.keys(anyString())).thenThrow(new RuntimeException("Redis connection error"));
        
        // When
        AnalysisCacheService.CacheStats stats = cacheService.getCacheStats();
        
        // Then
        assertFalse(stats.isEnabled());
        assertEquals(0, stats.getTotalEntries());
        assertEquals(0, stats.getTtlSeconds());
        assertFalse(stats.isHealthy());
    }
    
    @Test
    void testCacheKeyGeneration_ConsistentHashing() {
        // Given
        String messageContent1 = "동일한 메시지";
        String messageContent2 = "동일한 메시지";
        String chatRoomTitle = "테스트방";
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // When
        cacheService.getCachedAnalysis(messageContent1, chatRoomTitle);
        cacheService.getCachedAnalysis(messageContent2, chatRoomTitle);
        
        // Then
        // 동일한 키로 두 번 호출되어야 함
        verify(valueOperations, times(2)).get(anyString());
    }
    
    @Test
    void testCacheKeyGeneration_DifferentContent() {
        // Given
        String messageContent1 = "첫 번째 메시지";
        String messageContent2 = "두 번째 메시지";
        String chatRoomTitle = "테스트방";
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // When
        cacheService.getCachedAnalysis(messageContent1, chatRoomTitle);
        cacheService.getCachedAnalysis(messageContent2, chatRoomTitle);
        
        // Then
        // 서로 다른 키로 호출되어야 함
        verify(valueOperations, times(2)).get(anyString());
    }
}