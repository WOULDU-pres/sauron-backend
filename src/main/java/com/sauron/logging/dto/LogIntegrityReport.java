package com.sauron.logging.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 로그 무결성 보고서 DTO
 */
@Data
@Builder
public class LogIntegrityReport {
    
    private long totalLogsChecked;
    private long corruptedLogs;
    private long decryptionFailures;
    private long validationFailures;
    private double integrityPercentage;
    private Instant verificationDate;
    
    /**
     * 무결성 상태 확인
     */
    public boolean isHealthy() {
        return integrityPercentage >= 95.0; // 95% 이상이면 건강한 상태
    }
    
    /**
     * 심각한 문제 확인
     */
    public boolean hasCriticalIssues() {
        return integrityPercentage < 80.0 || // 무결성이 80% 미만
               (decryptionFailures > 0 && decryptionFailures > totalLogsChecked * 0.1); // 복호화 실패율 10% 초과
    }
    
    /**
     * 보고서 요약
     */
    public String getSummary() {
        if (isHealthy()) {
            return "Log integrity is healthy (" + String.format("%.1f%%", integrityPercentage) + ")";
        } else if (hasCriticalIssues()) {
            return "Critical log integrity issues detected (" + String.format("%.1f%%", integrityPercentage) + ")";
        } else {
            return "Log integrity requires attention (" + String.format("%.1f%%", integrityPercentage) + ")";
        }
    }
}