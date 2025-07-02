package com.sauron.listener.service;

import com.sauron.common.core.async.AsyncExecutor;
import com.sauron.common.dto.BusinessException;
import com.sauron.common.dto.ErrorCode;
import com.sauron.common.ratelimit.RateLimitException;
import com.sauron.common.ratelimit.RateLimitService;
import com.sauron.common.validation.MessageValidator;
import com.sauron.listener.dto.MessageRequest;
import com.sauron.listener.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 메시지 검증 전용 서비스
 * 메시지 유효성 검증, Rate Limiting, 중복 검사를 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageValidationService {
    
    private final MessageValidator messageValidator;
    private final RateLimitService rateLimitService;
    private final MessageRepository messageRepository;
    private final AsyncExecutor asyncExecutor;
    
    /**
     * 검증 결과 DTO
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String deviceId;
        private final String messageId;
        
        private ValidationResult(boolean valid, String errorMessage, String deviceId, String messageId) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.deviceId = deviceId;
            this.messageId = messageId;
        }
        
        public static ValidationResult success(String deviceId, String messageId) {
            return new ValidationResult(true, null, deviceId, messageId);
        }
        
        public static ValidationResult failure(String errorMessage, String deviceId, String messageId) {
            return new ValidationResult(false, errorMessage, deviceId, messageId);
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public String getDeviceId() { return deviceId; }
        public String getMessageId() { return messageId; }
    }
    
    /**
     * 메시지 전체 검증 수행 (동기)
     * 
     * @param request 메시지 요청
     * @return 검증 결과
     */
    public ValidationResult validateMessage(MessageRequest request) {
        String messageId = request.getMessageId();
        String deviceId = request.getDeviceId();
        
        try {
            log.debug("Starting validation for message: {}", messageId);
            
            // 1. Rate Limiting 확인
            validateRateLimit(request);
            
            // 2. 메시지 형식 검증
            validateMessageFormat(request);
            
            // 3. 중복 메시지 확인
            validateDuplicateMessage(request);
            
            log.debug("Validation completed successfully for message: {}", messageId);
            return ValidationResult.success(deviceId, messageId);
            
        } catch (RateLimitException e) {
            log.warn("Rate limit validation failed for message {}: {}", messageId, e.getMessage());
            return ValidationResult.failure("Rate limit exceeded: " + e.getMessage(), deviceId, messageId);
            
        } catch (IllegalArgumentException e) {
            log.warn("Format validation failed for message {}: {}", messageId, e.getMessage());
            return ValidationResult.failure("Invalid message format: " + e.getMessage(), deviceId, messageId);
            
        } catch (BusinessException e) {
            log.warn("Business validation failed for message {}: {}", messageId, e.getMessage());
            return ValidationResult.failure("Business rule violation: " + e.getMessage(), deviceId, messageId);
            
        } catch (Exception e) {
            log.error("Unexpected validation error for message {}: {}", messageId, e.getMessage(), e);
            return ValidationResult.failure("Validation error: " + e.getMessage(), deviceId, messageId);
        }
    }
    
    /**
     * 메시지 전체 검증 수행 (비동기)
     * 
     * @param request 메시지 요청
     * @return CompletableFuture<ValidationResult>
     */
    public CompletableFuture<ValidationResult> validateMessageAsync(MessageRequest request) {
        return asyncExecutor.executeWithRetry(
            () -> validateMessage(request),
            "message-validation-" + request.getMessageId(),
            2  // 최대 2회 재시도
        );
    }
    
    /**
     * Rate Limiting 확인
     */
    private void validateRateLimit(MessageRequest request) {
        String deviceId = request.getDeviceId();
        
        if (!rateLimitService.isAllowedForDevice(deviceId)) {
            int remaining = rateLimitService.getRemainingRequests("device:" + deviceId);
            throw new RateLimitException(
                "device:" + deviceId,
                remaining,
                "Device rate limit exceeded. Please slow down your requests."
            );
        }
        
        log.debug("Rate limit check passed for device: {}", deviceId);
    }
    
    /**
     * 메시지 형식 검증
     */
    private void validateMessageFormat(MessageRequest request) {
        MessageValidator.ValidationResult result = messageValidator.validate(request);
        
        if (!result.isValid()) {
            String errorMessage = "Message validation failed: " + result.getErrorMessage();
            log.warn("Format validation failed for message {}: {}", request.getMessageId(), errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        
        log.debug("Message format validation passed for ID: {}", request.getMessageId());
    }
    
    /**
     * 중복 메시지 확인
     */
    private void validateDuplicateMessage(MessageRequest request) {
        String messageId = request.getMessageId();
        
        // 메시지 ID 중복 확인
        if (messageRepository.existsByMessageId(messageId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_MESSAGE,
                "Message with ID " + messageId + " already exists");
        }
        
        log.debug("Duplicate check passed for message: {}", messageId);
    }
    
    /**
     * 검증 서비스 상태 확인
     */
    public boolean isHealthy() {
        try {
            // Rate Limit 서비스 상태 확인
            boolean rateLimitHealthy = rateLimitService.isHealthy();
            
            // 데이터베이스 연결 확인
            boolean dbHealthy = checkDatabaseHealth();
            
            if (!rateLimitHealthy) {
                log.warn("Rate limit service is unhealthy");
            }
            
            if (!dbHealthy) {
                log.warn("Database connection is unhealthy");
            }
            
            return rateLimitHealthy && dbHealthy;
            
        } catch (Exception e) {
            log.error("Validation service health check failed", e);
            return false;
        }
    }
    
    /**
     * 데이터베이스 연결 상태 확인
     */
    private boolean checkDatabaseHealth() {
        try {
            messageRepository.count();
            return true;
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return false;
        }
    }
    
    /**
     * 메시지 검증 통계 조회
     */
    public ValidationStatistics getValidationStatistics() {
        try {
            // Rate Limit 통계
            var rateLimitStats = rateLimitService.getStatistics();
            
            // 검증 성공/실패 통계 (향후 메트릭 수집 시 구현)
            return ValidationStatistics.builder()
                .totalValidations(0L) // TODO: 메트릭 수집 구현
                .successfulValidations(0L)
                .failedValidations(0L)
                .rateLimitViolations(rateLimitStats.getTotalViolations())
                .duplicateMessages(0L)
                .formatErrors(0L)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get validation statistics", e);
            return ValidationStatistics.empty();
        }
    }
    
    /**
     * 검증 통계 DTO
     */
    public static class ValidationStatistics {
        private final long totalValidations;
        private final long successfulValidations;
        private final long failedValidations;
        private final long rateLimitViolations;
        private final long duplicateMessages;
        private final long formatErrors;
        
        private ValidationStatistics(long totalValidations, long successfulValidations, 
                                   long failedValidations, long rateLimitViolations,
                                   long duplicateMessages, long formatErrors) {
            this.totalValidations = totalValidations;
            this.successfulValidations = successfulValidations;
            this.failedValidations = failedValidations;
            this.rateLimitViolations = rateLimitViolations;
            this.duplicateMessages = duplicateMessages;
            this.formatErrors = formatErrors;
        }
        
        public static ValidationStatisticsBuilder builder() {
            return new ValidationStatisticsBuilder();
        }
        
        public static ValidationStatistics empty() {
            return new ValidationStatistics(0, 0, 0, 0, 0, 0);
        }
        
        // Getters
        public long getTotalValidations() { return totalValidations; }
        public long getSuccessfulValidations() { return successfulValidations; }
        public long getFailedValidations() { return failedValidations; }
        public long getRateLimitViolations() { return rateLimitViolations; }
        public long getDuplicateMessages() { return duplicateMessages; }
        public long getFormatErrors() { return formatErrors; }
        
        public double getSuccessRate() {
            return totalValidations > 0 ? (double) successfulValidations / totalValidations : 0.0;
        }
        
        public static class ValidationStatisticsBuilder {
            private long totalValidations;
            private long successfulValidations;
            private long failedValidations;
            private long rateLimitViolations;
            private long duplicateMessages;
            private long formatErrors;
            
            public ValidationStatisticsBuilder totalValidations(long totalValidations) {
                this.totalValidations = totalValidations;
                return this;
            }
            
            public ValidationStatisticsBuilder successfulValidations(long successfulValidations) {
                this.successfulValidations = successfulValidations;
                return this;
            }
            
            public ValidationStatisticsBuilder failedValidations(long failedValidations) {
                this.failedValidations = failedValidations;
                return this;
            }
            
            public ValidationStatisticsBuilder rateLimitViolations(long rateLimitViolations) {
                this.rateLimitViolations = rateLimitViolations;
                return this;
            }
            
            public ValidationStatisticsBuilder duplicateMessages(long duplicateMessages) {
                this.duplicateMessages = duplicateMessages;
                return this;
            }
            
            public ValidationStatisticsBuilder formatErrors(long formatErrors) {
                this.formatErrors = formatErrors;
                return this;
            }
            
            public ValidationStatistics build() {
                return new ValidationStatistics(totalValidations, successfulValidations, 
                                              failedValidations, rateLimitViolations,
                                              duplicateMessages, formatErrors);
            }
        }
    }
}