package com.sauron.logging.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 감사 로그 엔티티
 * 암호화, 비식별화된 로그 데이터를 저장합니다.
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "log_type", nullable = false, length = 50)
    private String logType;
    
    @Column(nullable = false, length = 100)
    private String source;
    
    @Column(nullable = false, length = 20)
    private String severity;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @Column(columnDefinition = "TEXT")
    private String details;
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "is_encrypted")
    private boolean encrypted;
    
    @Column(name = "is_anonymized")
    private boolean anonymized;
    
    @Column(name = "validation_status", length = 20)
    private String validationStatus;
    
    @Column(name = "validation_errors", columnDefinition = "TEXT")
    private String validationErrors;
    
    @Column(name = "processing_time")
    private Long processingTime;
    
    @Column(name = "checksum", length = 64)
    private String checksum;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (timestamp == null) {
            timestamp = createdAt;
        }
    }
    
    /**
     * 민감한 데이터 포함 여부 확인
     */
    public boolean containsSensitiveData() {
        if (!encrypted && (message != null || details != null)) {
            String content = (message + " " + (details != null ? details : "")).toLowerCase();
            return content.contains("password") || 
                   content.contains("token") || 
                   content.contains("key") ||
                   content.contains("secret");
        }
        return false;
    }
    
    /**
     * 로그 심각도 레벨 반환
     */
    public int getSeverityLevel() {
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
     * 유효성 검사 통과 여부
     */
    public boolean isValidationPassed() {
        return "VALID".equals(validationStatus);
    }
    
    /**
     * 간단한 텍스트 표현
     */
    public String toSimpleString() {
        return String.format("AuditLog[id=%d, type=%s, severity=%s, encrypted=%s]",
            id, logType, severity, encrypted);
    }
}