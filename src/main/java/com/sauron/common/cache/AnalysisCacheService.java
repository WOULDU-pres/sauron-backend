package com.sauron.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauron.common.external.GeminiWorkerClient.AnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;

/**
 * 분석 결과 캐시 서비스
 * Redis를 사용하여 Gemini 분석 결과를 TTL 기반으로 캐싱합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisCacheService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${gemini.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${gemini.cache.ttl:300s}")
    private Duration cacheTtl;
    
    private static final String CACHE_KEY_PREFIX = "sauron:analysis:";
    private static final String HASH_ALGORITHM = "SHA-256";
    
    /**
     * 분석 결과를 캐시에서 조회합니다.
     * 
     * @param messageContent 메시지 내용
     * @param chatRoomTitle 채팅방 제목
     * @return 캐시된 분석 결과 (없으면 Optional.empty())
     */
    public Optional<AnalysisResult> getCachedAnalysis(String messageContent, String chatRoomTitle) {
        if (!cacheEnabled) {
            return Optional.empty();
        }
        
        try {
            String cacheKey = generateCacheKey(messageContent, chatRoomTitle);
            String cachedResult = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedResult != null) {
                AnalysisResult result = objectMapper.readValue(cachedResult, AnalysisResult.class);
                log.debug("Cache hit for message hash: {}", cacheKey);
                return Optional.of(result);
            }
            
            log.debug("Cache miss for message hash: {}", cacheKey);
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Failed to retrieve cached analysis", e);
            return Optional.empty();
        }
    }
    
    /**
     * 분석 결과를 캐시에 저장합니다.
     * 
     * @param messageContent 메시지 내용
     * @param chatRoomTitle 채팅방 제목
     * @param analysisResult 분석 결과
     */
    public void cacheAnalysis(String messageContent, String chatRoomTitle, AnalysisResult analysisResult) {
        if (!cacheEnabled) {
            return;
        }
        
        try {
            String cacheKey = generateCacheKey(messageContent, chatRoomTitle);
            String serializedResult = objectMapper.writeValueAsString(analysisResult);
            
            redisTemplate.opsForValue().set(cacheKey, serializedResult, cacheTtl);
            
            log.debug("Cached analysis result for message hash: {}, TTL: {}", cacheKey, cacheTtl);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize analysis result for caching", e);
        } catch (Exception e) {
            log.error("Failed to cache analysis result", e);
        }
    }
    
    /**
     * 캐시 통계 정보를 반환합니다.
     * 
     * @return 캐시 통계
     */
    public CacheStats getCacheStats() {
        try {
            // Redis에서 해당 패턴의 키 개수 조회
            var keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
            long totalEntries = keys != null ? keys.size() : 0;
            
            return new CacheStats(
                cacheEnabled,
                totalEntries,
                cacheTtl.getSeconds(),
                isRedisHealthy()
            );
            
        } catch (Exception e) {
            log.error("Failed to get cache stats", e);
            return new CacheStats(false, 0, 0, false);
        }
    }
    
    /**
     * 특정 메시지의 캐시를 무효화합니다.
     * 
     * @param messageContent 메시지 내용
     * @param chatRoomTitle 채팅방 제목
     * @return 삭제 성공 여부
     */
    public boolean invalidateCache(String messageContent, String chatRoomTitle) {
        if (!cacheEnabled) {
            return false;
        }
        
        try {
            String cacheKey = generateCacheKey(messageContent, chatRoomTitle);
            Boolean deleted = redisTemplate.delete(cacheKey);
            
            log.debug("Cache invalidated for message hash: {}, deleted: {}", cacheKey, deleted);
            return Boolean.TRUE.equals(deleted);
            
        } catch (Exception e) {
            log.error("Failed to invalidate cache", e);
            return false;
        }
    }
    
    /**
     * 메시지 내용과 채팅방 제목으로 캐시 키를 생성합니다.
     */
    private String generateCacheKey(String messageContent, String chatRoomTitle) {
        try {
            String combinedContent = messageContent + "|" + (chatRoomTitle != null ? chatRoomTitle : "");
            
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(combinedContent.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return CACHE_KEY_PREFIX + hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            log.error("Hash algorithm not available: {}", HASH_ALGORITHM, e);
            // 폴백으로 단순 문자열 조합 사용
            return CACHE_KEY_PREFIX + messageContent.hashCode() + "_" + 
                   (chatRoomTitle != null ? chatRoomTitle.hashCode() : 0);
        }
    }
    
    /**
     * Redis 연결 상태를 확인합니다.
     */
    private boolean isRedisHealthy() {
        try {
            redisTemplate.opsForValue().get("health-check");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 캐시 통계 정보를 담는 클래스
     */
    public static class CacheStats {
        private final boolean enabled;
        private final long totalEntries;
        private final long ttlSeconds;
        private final boolean healthy;
        
        public CacheStats(boolean enabled, long totalEntries, long ttlSeconds, boolean healthy) {
            this.enabled = enabled;
            this.totalEntries = totalEntries;
            this.ttlSeconds = ttlSeconds;
            this.healthy = healthy;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public long getTotalEntries() {
            return totalEntries;
        }
        
        public long getTtlSeconds() {
            return ttlSeconds;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{enabled=%s, entries=%d, ttl=%ds, healthy=%s}", 
                               enabled, totalEntries, ttlSeconds, healthy);
        }
    }
}