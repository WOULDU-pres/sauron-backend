package com.sauron.logging.service;

import com.sauron.logging.dto.LogEntry;
import com.sauron.logging.dto.LogValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 로그 유효성 검사 서비스
 */
@Service
@Slf4j
public class LogValidationService {
    
    @Value("${logging.validation.max-message-length:10000}")
    private int maxMessageLength;
    
    @Value("${logging.validation.max-details-length:50000}")
    private int maxDetailsLength;
    
    @Value("${logging.validation.future-timestamp-tolerance-minutes:5}")
    private int futureTimestampToleranceMinutes;
    
    // 허용된 로그 타입
    private static final Set<String> VALID_LOG_TYPES = Set.of(
        "APPLICATION", "SECURITY", "AUDIT", "ERROR", "ACCESS", 
        "PERFORMANCE", "SYSTEM", "USER_ACTION", "API_CALL", "MESSAGE_PROCESSING"
    );
    
    // 허용된 심각도 레벨
    private static final Set<String> VALID_SEVERITY_LEVELS = Set.of(
        "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"
    );
    
    // 금지된 패턴 (보안상 위험한 내용)
    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
        "<script", "javascript:", "onclick", "onerror", "eval(", 
        "document.cookie", "localStorage", "sessionStorage"
    );
    
    /**
     * 로그 엔트리 유효성 검사
     */
    public LogValidationResult validateLogEntry(LogEntry logEntry) {
        List<String> errors = new ArrayList<>();
        
        try {
            // 1. 필수 필드 검사
            validateRequiredFields(logEntry, errors);
            
            // 2. 필드 길이 검사
            validateFieldLengths(logEntry, errors);
            
            // 3. 형식 유효성 검사
            validateFormats(logEntry, errors);
            
            // 4. 타임스탬프 유효성 검사
            validateTimestamp(logEntry, errors);
            
            // 5. 보안 검사
            validateSecurity(logEntry, errors);
            
            // 6. 비즈니스 규칙 검사
            validateBusinessRules(logEntry, errors);
            
            if (errors.isEmpty()) {
                log.debug("Log entry validation passed - Type: {}, Source: {}", 
                         logEntry.getLogType(), logEntry.getSource());
                return LogValidationResult.success();
            } else {
                log.warn("Log entry validation failed - Errors: {}", errors);
                return LogValidationResult.failure(errors);
            }
            
        } catch (Exception e) {
            log.error("Error during log validation", e);
            return LogValidationResult.failure("Validation error: " + e.getMessage());
        }
    }
    
    /**
     * 필수 필드 검사
     */
    private void validateRequiredFields(LogEntry logEntry, List<String> errors) {
        if (logEntry.getLogType() == null || logEntry.getLogType().trim().isEmpty()) {
            errors.add("Log type is required");
        }
        
        if (logEntry.getSource() == null || logEntry.getSource().trim().isEmpty()) {
            errors.add("Source is required");
        }
        
        if (logEntry.getSeverity() == null || logEntry.getSeverity().trim().isEmpty()) {
            errors.add("Severity is required");
        }
        
        if (logEntry.getMessage() == null || logEntry.getMessage().trim().isEmpty()) {
            errors.add("Message is required");
        }
        
        if (logEntry.getTimestamp() == null) {
            errors.add("Timestamp is required");
        }
    }
    
    /**
     * 필드 길이 검사
     */
    private void validateFieldLengths(LogEntry logEntry, List<String> errors) {
        if (logEntry.getMessage() != null && logEntry.getMessage().length() > maxMessageLength) {
            errors.add("Message length exceeds maximum allowed (" + maxMessageLength + " characters)");
        }
        
        if (logEntry.getDetails() != null && logEntry.getDetails().length() > maxDetailsLength) {
            errors.add("Details length exceeds maximum allowed (" + maxDetailsLength + " characters)");
        }
        
        if (logEntry.getLogType() != null && logEntry.getLogType().length() > 50) {
            errors.add("Log type length exceeds maximum allowed (50 characters)");
        }
        
        if (logEntry.getSource() != null && logEntry.getSource().length() > 100) {
            errors.add("Source length exceeds maximum allowed (100 characters)");
        }
        
        if (logEntry.getSeverity() != null && logEntry.getSeverity().length() > 20) {
            errors.add("Severity length exceeds maximum allowed (20 characters)");
        }
    }
    
    /**
     * 형식 유효성 검사
     */
    private void validateFormats(LogEntry logEntry, List<String> errors) {
        // 로그 타입 검사
        if (logEntry.getLogType() != null && 
            !VALID_LOG_TYPES.contains(logEntry.getLogType().toUpperCase())) {
            errors.add("Invalid log type: " + logEntry.getLogType() + 
                      ". Valid types: " + VALID_LOG_TYPES);
        }
        
        // 심각도 레벨 검사
        if (logEntry.getSeverity() != null && 
            !VALID_SEVERITY_LEVELS.contains(logEntry.getSeverity().toUpperCase())) {
            errors.add("Invalid severity level: " + logEntry.getSeverity() + 
                      ". Valid levels: " + VALID_SEVERITY_LEVELS);
        }
        
        // 소스 형식 검사 (영문, 숫자, 언더스코어, 하이픈만 허용)
        if (logEntry.getSource() != null && 
            !logEntry.getSource().matches("^[a-zA-Z0-9_-]+$")) {
            errors.add("Source contains invalid characters. Only letters, numbers, underscore, and hyphen are allowed");
        }
    }
    
    /**
     * 타임스탬프 유효성 검사
     */
    private void validateTimestamp(LogEntry logEntry, List<String> errors) {
        if (logEntry.getTimestamp() != null) {
            Instant now = Instant.now();
            
            // 미래 시간 허용 범위 확인
            if (logEntry.getTimestamp().isAfter(now.plus(futureTimestampToleranceMinutes, ChronoUnit.MINUTES))) {
                errors.add("Timestamp is too far in the future (tolerance: " + 
                          futureTimestampToleranceMinutes + " minutes)");
            }
            
            // 과거 시간 확인 (1년 이전은 경고)
            if (logEntry.getTimestamp().isBefore(now.minus(365, ChronoUnit.DAYS))) {
                errors.add("Timestamp is more than 1 year old, which may indicate a clock synchronization issue");
            }
        }
    }
    
    /**
     * 보안 검사
     */
    private void validateSecurity(LogEntry logEntry, List<String> errors) {
        // 메시지에서 위험한 패턴 검사
        if (logEntry.getMessage() != null) {
            String message = logEntry.getMessage().toLowerCase();
            for (String pattern : FORBIDDEN_PATTERNS) {
                if (message.contains(pattern)) {
                    errors.add("Message contains potentially dangerous pattern: " + pattern);
                }
            }
        }
        
        // 상세 내용에서 위험한 패턴 검사
        if (logEntry.getDetails() != null) {
            String details = logEntry.getDetails().toLowerCase();
            for (String pattern : FORBIDDEN_PATTERNS) {
                if (details.contains(pattern)) {
                    errors.add("Details contain potentially dangerous pattern: " + pattern);
                }
            }
        }
        
        // SQL 인젝션 패턴 검사
        String[] sqlPatterns = {"drop table", "delete from", "insert into", "update set", "union select"};
        String combinedContent = (logEntry.getMessage() + " " + 
                                (logEntry.getDetails() != null ? logEntry.getDetails() : "")).toLowerCase();
        
        for (String sqlPattern : sqlPatterns) {
            if (combinedContent.contains(sqlPattern)) {
                errors.add("Content contains potentially dangerous SQL pattern: " + sqlPattern);
            }
        }
    }
    
    /**
     * 비즈니스 규칙 검사
     */
    private void validateBusinessRules(LogEntry logEntry, List<String> errors) {
        // ERROR나 FATAL 레벨에는 상세 내용이 있어야 함
        if (logEntry.getSeverity() != null && 
            ("ERROR".equals(logEntry.getSeverity().toUpperCase()) || 
             "FATAL".equals(logEntry.getSeverity().toUpperCase()))) {
            if (logEntry.getDetails() == null || logEntry.getDetails().trim().isEmpty()) {
                errors.add("ERROR and FATAL level logs must include details");
            }
        }
        
        // SECURITY 타입 로그는 WARN 이상이어야 함
        if ("SECURITY".equals(logEntry.getLogType()) && logEntry.getSeverity() != null) {
            int severityLevel = getSeverityLevel(logEntry.getSeverity());
            if (severityLevel < 3) { // WARN 이하
                errors.add("SECURITY type logs must have severity level WARN or higher");
            }
        }
        
        // 메시지와 상세 내용이 동일한 경우 경고
        if (logEntry.getMessage() != null && logEntry.getDetails() != null &&
            logEntry.getMessage().equals(logEntry.getDetails())) {
            errors.add("Message and details should not be identical");
        }
    }
    
    /**
     * 심각도 레벨 숫자 반환
     */
    private int getSeverityLevel(String severity) {
        switch (severity.toUpperCase()) {
            case "TRACE": return 0;
            case "DEBUG": return 1;
            case "INFO": return 2;
            case "WARN": return 3;
            case "ERROR": return 4;
            case "FATAL": return 5;
            default: return 2;
        }
    }
    
    /**
     * 유효성 검사 설정 업데이트
     */
    public void updateValidationConfig(int maxMessageLength, int maxDetailsLength, 
                                     int futureTimestampToleranceMinutes) {
        this.maxMessageLength = maxMessageLength;
        this.maxDetailsLength = maxDetailsLength;
        this.futureTimestampToleranceMinutes = futureTimestampToleranceMinutes;
        
        log.info("Validation configuration updated - Max message: {}, Max details: {}, Timestamp tolerance: {} min", 
                maxMessageLength, maxDetailsLength, futureTimestampToleranceMinutes);
    }
}