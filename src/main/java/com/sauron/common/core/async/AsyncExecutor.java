package com.sauron.common.core.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * 표준 비동기 실행기
 * 공통 비동기 처리 패턴을 제공합니다.
 */
@Slf4j
@Component
public class AsyncExecutor {
    
    /**
     * 재시도 로직을 포함한 비동기 실행
     * 
     * @param operation 실행할 작업
     * @param operationName 작업 이름 (로깅용)
     * @param maxRetries 최대 재시도 횟수
     * @return CompletableFuture 결과
     */
    public <T> CompletableFuture<T> executeWithRetry(
            Supplier<T> operation,
            String operationName,
            int maxRetries) {
        
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;
            
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    log.debug("Executing {} - attempt {}/{}", operationName, attempt, maxRetries);
                    T result = operation.get();
                    
                    if (attempt > 1) {
                        log.info("Operation {} succeeded on attempt {}/{}", operationName, attempt, maxRetries);
                    }
                    
                    return result;
                    
                } catch (Exception e) {
                    lastException = e;
                    log.warn("Operation {} failed on attempt {}/{}: {}", 
                            operationName, attempt, maxRetries, e.getMessage());
                    
                    if (attempt < maxRetries) {
                        try {
                            // 지수 백오프 적용
                            long delay = (long) Math.pow(2, attempt - 1) * 1000;
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new CompletionException("Operation interrupted", ie);
                        }
                    }
                }
            }
            
            log.error("Operation {} failed after {} attempts", operationName, maxRetries);
            throw new CompletionException("Operation failed after " + maxRetries + " attempts", lastException);
        });
    }
    
    /**
     * 폴백을 포함한 비동기 실행
     * 
     * @param primaryOperation 주 작업
     * @param fallbackOperation 폴백 작업
     * @param operationName 작업 이름 (로깅용)
     * @return CompletableFuture 결과
     */
    public <T> CompletableFuture<T> executeWithFallback(
            Supplier<T> primaryOperation,
            Supplier<T> fallbackOperation,
            String operationName) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing primary operation: {}", operationName);
                return primaryOperation.get();
                
            } catch (Exception e) {
                log.warn("Primary operation {} failed: {}, executing fallback", operationName, e.getMessage());
                
                try {
                    T fallbackResult = fallbackOperation.get();
                    log.info("Fallback operation {} succeeded", operationName);
                    return fallbackResult;
                    
                } catch (Exception fe) {
                    log.error("Both primary and fallback operations failed for {}", operationName, fe);
                    throw new CompletionException("Both primary and fallback operations failed", fe);
                }
            }
        });
    }
    
    /**
     * 타임아웃을 포함한 비동기 실행
     * 
     * @param operation 실행할 작업
     * @param operationName 작업 이름 (로깅용)
     * @param timeoutMs 타임아웃 (밀리초)
     * @return CompletableFuture 결과
     */
    public <T> CompletableFuture<T> executeWithTimeout(
            Supplier<T> operation,
            String operationName,
            long timeoutMs) {
        
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            log.debug("Executing operation with timeout: {} ({}ms)", operationName, timeoutMs);
            return operation.get();
        });
        
        return future.orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        if (throwable instanceof java.util.concurrent.TimeoutException) {
                            log.warn("Operation {} timed out after {}ms", operationName, timeoutMs);
                        } else {
                            log.error("Operation {} failed: {}", operationName, throwable.getMessage());
                        }
                    } else {
                        log.debug("Operation {} completed successfully", operationName);
                    }
                });
    }
    
    /**
     * 재시도와 폴백을 모두 포함한 비동기 실행
     * 
     * @param primaryOperation 주 작업
     * @param fallbackOperation 폴백 작업
     * @param operationName 작업 이름 (로깅용)
     * @param maxRetries 최대 재시도 횟수
     * @return CompletableFuture 결과
     */
    public <T> CompletableFuture<T> executeWithRetryAndFallback(
            Supplier<T> primaryOperation,
            Supplier<T> fallbackOperation,
            String operationName,
            int maxRetries) {
        
        return executeWithRetry(primaryOperation, operationName + " (primary)", maxRetries)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.warn("Primary operation {} failed after retries, executing fallback", operationName);
                        try {
                            return fallbackOperation.get();
                        } catch (Exception fe) {
                            log.error("Fallback operation {} also failed", operationName, fe);
                            throw new CompletionException("Both primary and fallback operations failed", fe);
                        }
                    }
                    return result;
                });
    }
}