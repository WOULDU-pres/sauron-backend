package com.sauron.common.ratelimit;

/**
 * Rate Limit 초과 시 발생하는 커스텀 예외
 */
public class RateLimitException extends RuntimeException {
    
    private final String key;
    private final int remainingRequests;
    
    public RateLimitException(String key, int remainingRequests) {
        super("Rate limit exceeded for: " + key);
        this.key = key;
        this.remainingRequests = remainingRequests;
    }
    
    public RateLimitException(String key, int remainingRequests, String message) {
        super(message);
        this.key = key;
        this.remainingRequests = remainingRequests;
    }
    
    public String getKey() {
        return key;
    }
    
    public int getRemainingRequests() {
        return remainingRequests;
    }
} 