package com.sauron.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Rate Limiting 서비스
 * 슬라이딩 윈도우 방식으로 요청 제한을 구현합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // Rate Limit 설정
    private static final int DEFAULT_REQUESTS_PER_MINUTE = 60;
    private static final int DEFAULT_REQUESTS_PER_HOUR = 1000;
    private static final Duration MINUTE_WINDOW = Duration.ofMinutes(1);
    private static final Duration HOUR_WINDOW = Duration.ofHours(1);
    
    /**
     * 요청이 Rate Limit을 초과했는지 확인합니다.
     * 
     * @param key 제한 대상 키 (IP 주소, 디바이스 ID 등)
     * @return 허용되면 true, 제한되면 false
     */
    public boolean isAllowed(String key) {
        return isAllowed(key, DEFAULT_REQUESTS_PER_MINUTE, MINUTE_WINDOW) &&
               isAllowed(key, DEFAULT_REQUESTS_PER_HOUR, HOUR_WINDOW);
    }
    
    /**
     * 디바이스별 Rate Limit 확인 (더 엄격한 제한)
     * 
     * @param deviceId 디바이스 ID
     * @return 허용되면 true, 제한되면 false
     */
    public boolean isAllowedForDevice(String deviceId) {
        String deviceKey = "device:" + deviceId;
        // 디바이스는 분당 30개, 시간당 500개로 제한
        return isAllowed(deviceKey, 30, MINUTE_WINDOW) &&
               isAllowed(deviceKey, 500, HOUR_WINDOW);
    }
    
    /**
     * IP별 Rate Limit 확인
     * 
     * @param ipAddress IP 주소
     * @return 허용되면 true, 제한되면 false
     */
    public boolean isAllowedForIp(String ipAddress) {
        String ipKey = "ip:" + ipAddress;
        return isAllowed(ipKey, DEFAULT_REQUESTS_PER_MINUTE, MINUTE_WINDOW) &&
               isAllowed(ipKey, DEFAULT_REQUESTS_PER_HOUR, HOUR_WINDOW);
    }
    
    /**
     * 특정 키와 윈도우에 대해 Rate Limit을 확인하고 카운트를 증가시킵니다.
     * 
     * @param key 제한 대상 키
     * @param maxRequests 최대 요청 수
     * @param window 시간 윈도우
     * @return 허용되면 true, 제한되면 false
     */
    private boolean isAllowed(String key, int maxRequests, Duration window) {
        try {
            String redisKey = "rate_limit:" + key + ":" + window.toString();
            
            // 현재 카운트 가져오기
            String currentCountStr = redisTemplate.opsForValue().get(redisKey);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
            
            // 제한 확인
            if (currentCount >= maxRequests) {
                log.warn("Rate limit exceeded for key: {}, count: {}, limit: {}", 
                        key, currentCount, maxRequests);
                return false;
            }
            
            // 카운트 증가
            Long newCount = redisTemplate.opsForValue().increment(redisKey);
            
            // 첫 번째 요청인 경우 TTL 설정
            if (newCount == 1) {
                redisTemplate.expire(redisKey, window.getSeconds(), TimeUnit.SECONDS);
            }
            
            log.debug("Rate limit check passed for key: {}, count: {}/{}", 
                     key, newCount, maxRequests);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error checking rate limit for key: " + key, e);
            // Redis 에러 시 기본적으로 허용 (가용성 우선)
            return true;
        }
    }
    
    /**
     * 특정 키의 남은 요청 수를 반환합니다.
     * 
     * @param key 제한 대상 키
     * @return 남은 요청 수
     */
    public int getRemainingRequests(String key) {
        try {
            String redisKey = "rate_limit:" + key + ":" + MINUTE_WINDOW.toString();
            String currentCountStr = redisTemplate.opsForValue().get(redisKey);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
            
            return Math.max(0, DEFAULT_REQUESTS_PER_MINUTE - currentCount);
            
        } catch (Exception e) {
            log.error("Error getting remaining requests for key: " + key, e);
            return DEFAULT_REQUESTS_PER_MINUTE;
        }
    }
    
    /**
     * 특정 키의 Rate Limit을 리셋합니다 (관리용).
     * 
     * @param key 리셋할 키
     */
    public void resetRateLimit(String key) {
        try {
            String minuteKey = "rate_limit:" + key + ":" + MINUTE_WINDOW.toString();
            String hourKey = "rate_limit:" + key + ":" + HOUR_WINDOW.toString();
            
            redisTemplate.delete(minuteKey);
            redisTemplate.delete(hourKey);
            
            log.info("Rate limit reset for key: {}", key);
            
        } catch (Exception e) {
            log.error("Error resetting rate limit for key: " + key, e);
        }
    }
    
    /**
     * Rate Limit 서비스 상태 확인
     * 
     * @return 서비스가 정상이면 true
     */
    public boolean isHealthy() {
        try {
            // Redis 연결 상태 확인
            redisTemplate.opsForValue().get("health_check");
            return true;
        } catch (Exception e) {
            log.error("Rate limit service health check failed", e);
            return false;
        }
    }
    
    /**
     * Rate Limit 통계 조회
     * 
     * @return 통계 정보
     */
    public RateLimitStatistics getStatistics() {
        try {
            // 간단한 통계 수집 (실제 구현에서는 더 정교한 메트릭 수집 필요)
            return RateLimitStatistics.builder()
                .totalViolations(0L) // TODO: 실제 메트릭 수집 구현
                .activeKeys(0L)
                .build();
        } catch (Exception e) {
            log.error("Failed to get rate limit statistics", e);
            return RateLimitStatistics.empty();
        }
    }
    
    /**
     * Rate Limit 통계 DTO
     */
    public static class RateLimitStatistics {
        private final long totalViolations;
        private final long activeKeys;
        
        private RateLimitStatistics(long totalViolations, long activeKeys) {
            this.totalViolations = totalViolations;
            this.activeKeys = activeKeys;
        }
        
        public static RateLimitStatisticsBuilder builder() {
            return new RateLimitStatisticsBuilder();
        }
        
        public static RateLimitStatistics empty() {
            return new RateLimitStatistics(0, 0);
        }
        
        // Getters
        public long getTotalViolations() { return totalViolations; }
        public long getActiveKeys() { return activeKeys; }
        
        public static class RateLimitStatisticsBuilder {
            private long totalViolations;
            private long activeKeys;
            
            public RateLimitStatisticsBuilder totalViolations(long totalViolations) {
                this.totalViolations = totalViolations;
                return this;
            }
            
            public RateLimitStatisticsBuilder activeKeys(long activeKeys) {
                this.activeKeys = activeKeys;
                return this;
            }
            
            public RateLimitStatistics build() {
                return new RateLimitStatistics(totalViolations, activeKeys);
            }
        }
    }
} 