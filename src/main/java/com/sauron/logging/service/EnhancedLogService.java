package com.sauron.logging.service;

import com.sauron.common.core.async.AsyncExecutor;
import com.sauron.logging.dto.LogEntry;
import com.sauron.logging.dto.LogQuery;
import com.sauron.logging.dto.LogValidationResult;
import com.sauron.logging.entity.AuditLog;
import com.sauron.logging.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 향상된 로그 서비스
 * 암호화, 비식별화, 유효성 검사, 테스트 시뮬레이션을 지원합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedLogService {
    
    private final AsyncExecutor asyncExecutor;
    private final AuditLogRepository auditLogRepository;
    private final LogEncryptionService encryptionService;
    private final LogValidationService validationService;
    private final LogAnonymizationService anonymizationService;
    
    @Value("${logging.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    @Value("${logging.anonymization.enabled:true}")
    private boolean anonymizationEnabled;
    
    @Value("${logging.validation.strict:true}")
    private boolean strictValidation;
    
    @Value("${logging.async.enabled:true}")
    private boolean asyncLogging;
    
    @Value("${logging.retention.days:90}")
    private int retentionDays;
    
    /**
     * 로그 엔트리 저장 (비동기)
     */
    public CompletableFuture<LogEntry> saveLogAsync(LogEntry logEntry) {
        if (!asyncLogging) {
            return CompletableFuture.completedFuture(saveLogSync(logEntry));
        }
        
        return asyncExecutor.executeWithRetry(() -> {
            return saveLogSync(logEntry);
        }, "LogSaving", 3);
    }
    
    /**
     * 로그 엔트리 저장 (동기)
     */
    @Transactional
    public LogEntry saveLogSync(LogEntry logEntry) {
        try {
            log.debug("Processing log entry - Type: {}, Source: {}", 
                     logEntry.getLogType(), logEntry.getSource());
            
            // 1. 유효성 검사
            LogValidationResult validation = validationService.validateLogEntry(logEntry);
            if (!validation.isValid() && strictValidation) {
                throw new LogProcessingException("Log validation failed: " + validation.getErrorMessage());
            }
            
            // 2. 비식별화 처리
            LogEntry processedEntry = logEntry;
            if (anonymizationEnabled) {
                processedEntry = anonymizationService.anonymizeLogEntry(logEntry);
                log.debug("Applied anonymization to log entry");
            }
            
            // 3. 암호화 처리
            if (encryptionEnabled) {
                processedEntry = encryptionService.encryptSensitiveFields(processedEntry);
                log.debug("Applied encryption to sensitive fields");
            }
            
            // 4. 감사 로그로 변환 및 저장
            AuditLog auditLog = convertToAuditLog(processedEntry, validation);
            AuditLog saved = auditLogRepository.save(auditLog);
            
            log.info("Log entry saved successfully - ID: {}, Type: {}", 
                    saved.getId(), saved.getLogType());
            
            return convertToLogEntry(saved);
            
        } catch (Exception e) {
            log.error("Failed to save log entry - Type: {}, Source: {}", 
                     logEntry.getLogType(), logEntry.getSource(), e);
            throw new LogProcessingException("Failed to save log entry", e);
        }
    }
    
    /**
     * 로그 조회 (암호화 해제 포함)
     */
    public CompletableFuture<Page<LogEntry>> findLogsAsync(LogQuery query, Pageable pageable) {
        return asyncExecutor.executeWithTimeout(() -> {
            Page<AuditLog> auditLogs = findAuditLogs(query, pageable);
            return auditLogs.map(this::convertToLogEntry);
        }, "LogQuerying", 30000L);
    }
    
    /**
     * 감사 로그 조회
     */
    private Page<AuditLog> findAuditLogs(LogQuery query, Pageable pageable) {
        try {
            if (query.getStartDate() != null && query.getEndDate() != null) {
                return auditLogRepository.findByCreatedAtBetween(
                    query.getStartDate(), query.getEndDate(), pageable);
            }
            
            if (query.getLogType() != null) {
                return auditLogRepository.findByLogType(query.getLogType(), pageable);
            }
            
            if (query.getSource() != null) {
                return auditLogRepository.findBySource(query.getSource(), pageable);
            }
            
            if (query.getSeverity() != null) {
                return auditLogRepository.findBySeverity(query.getSeverity(), pageable);
            }
            
            return auditLogRepository.findAll(pageable);
            
        } catch (Exception e) {
            log.error("Failed to query audit logs", e);
            throw new LogProcessingException("Failed to query logs", e);
        }
    }
    
    /**
     * 로그 삭제 (보존 정책 적용)
     */
    @Transactional
    public CompletableFuture<Integer> cleanupOldLogs() {
        return asyncExecutor.executeWithTimeout(() -> {
            try {
                Instant cutoffDate = Instant.now().minusSeconds(retentionDays * 24 * 3600L);
                
                log.info("Starting log cleanup - Cutoff date: {}", cutoffDate);
                
                int deletedCount = auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
                
                log.info("Log cleanup completed - Deleted {} old log entries", deletedCount);
                
                return deletedCount;
                
            } catch (Exception e) {
                log.error("Failed to cleanup old logs", e);
                throw new LogProcessingException("Failed to cleanup old logs", e);
            }
        }, "LogCleanup", 300000L); // 5분 타임아웃
    }
    
    /**
     * 로그 통계 조회
     */
    public CompletableFuture<LogStatistics> getLogStatistics() {
        return asyncExecutor.executeWithTimeout(() -> {
            try {
                Instant today = Instant.now().minusSeconds(24 * 3600L);
                
                long totalLogs = auditLogRepository.count();
                long todayLogs = auditLogRepository.countByCreatedAtAfter(today);
                long errorLogs = auditLogRepository.countBySeverity("ERROR");
                long warningLogs = auditLogRepository.countBySeverity("WARN");
                
                List<String> topSources = auditLogRepository.findTopSourcesByCount(5);
                List<String> topLogTypes = auditLogRepository.findTopLogTypesByCount(5);
                
                return LogStatistics.builder()
                    .totalLogs(totalLogs)
                    .todayLogs(todayLogs)
                    .errorLogs(errorLogs)
                    .warningLogs(warningLogs)
                    .topSources(topSources)
                    .topLogTypes(topLogTypes)
                    .generatedAt(Instant.now())
                    .build();
                
            } catch (Exception e) {
                log.error("Failed to generate log statistics", e);
                throw new LogProcessingException("Failed to generate log statistics", e);
            }
        }, "LogStatistics", 10000L);
    }
    
    /**
     * 테스트/시뮬레이션 모드에서 로그 생성
     */
    public CompletableFuture<List<LogEntry>> generateTestLogs(int count, String logType) {
        return asyncExecutor.executeWithTimeout(() -> {
            try {
                log.info("Generating {} test logs of type: {}", count, logType);
                
                List<LogEntry> testLogs = new ArrayList<>();
                
                for (int i = 0; i < count; i++) {
                    LogEntry testLog = LogEntry.builder()
                        .logType(logType)
                        .source("TEST_SIMULATOR")
                        .severity("INFO")
                        .message("Test log message " + (i + 1))
                        .details("Generated for testing purposes")
                        .timestamp(Instant.now())
                        .metadata(Map.of(
                            "testMode", true,
                            "sequence", i + 1,
                            "totalCount", count
                        ))
                        .build();
                    
                    LogEntry saved = saveLogSync(testLog);
                    testLogs.add(saved);
                }
                
                log.info("Generated {} test logs successfully", testLogs.size());
                return testLogs;
                
            } catch (Exception e) {
                log.error("Failed to generate test logs", e);
                throw new LogProcessingException("Failed to generate test logs", e);
            }
        }, "TestLogGeneration", 60000L);
    }
    
    /**
     * 로그 무결성 검증
     */
    public CompletableFuture<LogIntegrityReport> verifyLogIntegrity() {
        return asyncExecutor.executeWithTimeout(() -> {
            try {
                log.info("Starting log integrity verification");
                
                long totalLogs = auditLogRepository.count();
                long corruptedLogs = 0;
                long decryptionFailures = 0;
                long validationFailures = 0;
                
                // 샘플링을 통한 무결성 검사 (전체의 1% 또는 최대 1000개)
                int sampleSize = Math.min(1000, (int) (totalLogs * 0.01));
                List<AuditLog> sampleLogs = auditLogRepository.findRandomSample(sampleSize);
                
                for (AuditLog auditLog : sampleLogs) {
                    try {
                        // 암호화 해제 테스트
                        if (auditLog.isEncrypted()) {
                            encryptionService.decryptSensitiveFields(auditLog);
                        }
                        
                        // 유효성 검사
                        LogEntry logEntry = convertToLogEntry(auditLog);
                        LogValidationResult validation = validationService.validateLogEntry(logEntry);
                        
                        if (!validation.isValid()) {
                            validationFailures++;
                        }
                        
                    } catch (Exception e) {
                        log.warn("Log integrity check failed for log ID: {}", auditLog.getId(), e);
                        if (e.getMessage().contains("decrypt")) {
                            decryptionFailures++;
                        } else {
                            corruptedLogs++;
                        }
                    }
                }
                
                LogIntegrityReport report = LogIntegrityReport.builder()
                    .totalLogsChecked(sampleSize)
                    .corruptedLogs(corruptedLogs)
                    .decryptionFailures(decryptionFailures)
                    .validationFailures(validationFailures)
                    .integrityPercentage((double) (sampleSize - corruptedLogs - decryptionFailures) / sampleSize * 100.0)
                    .verificationDate(Instant.now())
                    .build();
                
                log.info("Log integrity verification completed - Integrity: {:.2f}%", 
                        report.getIntegrityPercentage());
                
                return report;
                
            } catch (Exception e) {
                log.error("Failed to verify log integrity", e);
                throw new LogProcessingException("Failed to verify log integrity", e);
            }
        }, "LogIntegrityVerification", 300000L);
    }
    
    /**
     * LogEntry를 AuditLog로 변환
     */
    private AuditLog convertToAuditLog(LogEntry logEntry, LogValidationResult validation) {
        return AuditLog.builder()
            .logType(logEntry.getLogType())
            .source(logEntry.getSource())
            .severity(logEntry.getSeverity())
            .message(logEntry.getMessage())
            .details(logEntry.getDetails())
            .timestamp(logEntry.getTimestamp())
            .encrypted(encryptionEnabled)
            .anonymized(anonymizationEnabled)
            .validationStatus(validation.isValid() ? "VALID" : "INVALID")
            .validationErrors(validation.getErrorMessage())
            .processingTime(System.currentTimeMillis())
            .build();
    }
    
    /**
     * AuditLog를 LogEntry로 변환 (복호화 포함)
     */
    private LogEntry convertToLogEntry(AuditLog auditLog) {
        try {
            // 암호화된 데이터 복호화
            AuditLog decrypted = auditLog;
            if (auditLog.isEncrypted()) {
                decrypted = encryptionService.decryptSensitiveFields(auditLog);
            }
            
            return LogEntry.builder()
                .id(decrypted.getId())
                .logType(decrypted.getLogType())
                .source(decrypted.getSource())
                .severity(decrypted.getSeverity())
                .message(decrypted.getMessage())
                .details(decrypted.getDetails())
                .timestamp(decrypted.getTimestamp())
                .metadata(Map.of(
                    "encrypted", auditLog.isEncrypted(),
                    "anonymized", auditLog.isAnonymized(),
                    "validationStatus", auditLog.getValidationStatus()
                ))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to convert AuditLog to LogEntry - ID: {}", auditLog.getId(), e);
            throw new LogProcessingException("Failed to convert log entry", e);
        }
    }
    
    /**
     * 로그 설정 업데이트
     */
    public void updateConfiguration(boolean encryptionEnabled, boolean anonymizationEnabled, 
                                  boolean strictValidation, int retentionDays) {
        this.encryptionEnabled = encryptionEnabled;
        this.anonymizationEnabled = anonymizationEnabled;
        this.strictValidation = strictValidation;
        this.retentionDays = retentionDays;
        
        log.info("Log service configuration updated - Encryption: {}, Anonymization: {}, " +
                "Strict validation: {}, Retention: {} days", 
                encryptionEnabled, anonymizationEnabled, strictValidation, retentionDays);
    }
    
    /**
     * 로그 처리 예외
     */
    public static class LogProcessingException extends RuntimeException {
        public LogProcessingException(String message) {
            super(message);
        }
        
        public LogProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}