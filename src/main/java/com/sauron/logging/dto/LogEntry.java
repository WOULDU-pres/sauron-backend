package com.sauron.logging.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 로그 엔트리 DTO
 * 로그 데이터의 전송 및 처리를 위한 데이터 구조
 */
@Data
@Builder(toBuilder = true)
public class LogEntry {
    
    private Long id;
    private String logType;
    private String source;
    private String severity;
    private String message;
    private String details;
    private Instant timestamp;
    private Map<String, Object> metadata;
    
    /**
     * 로그 유효성 확인
     */
    public boolean isValid() {
        return logType != null && !logType.trim().isEmpty() &&
               source != null && !source.trim().isEmpty() &&
               severity != null && !severity.trim().isEmpty() &&
               message != null && !message.trim().isEmpty();
    }
    
    /**
     * 민감한 데이터 포함 여부 확인
     */
    public boolean containsSensitiveData() {
        if (message == null && details == null) {
            return false;
        }
        
        String content = (message + " " + (details != null ? details : "")).toLowerCase();
        
        // 민감한 정보 패턴 확인
        return content.contains("password") || 
               content.contains("token") || 
               content.contains("key") ||
               content.contains("secret") ||
               content.matches(".*\\d{4}-\\d{4}-\\d{4}-\\d{4}.*") || // 카드번호 패턴
               content.matches(".*\\d{3}-\\d{4}-\\d{4}.*"); // 전화번호 패턴
    }
    
    /**
     * 메타데이터에서 값 추출
     */
    public Object getMetadataValue(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    /**
     * 메타데이터에서 문자열 값 추출
     */
    public String getMetadataString(String key) {
        Object value = getMetadataValue(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 로그 레벨 우선순위 반환
     */
    public int getSeverityLevel() {
        switch (severity.toUpperCase()) {
            case "TRACE": return 0;
            case "DEBUG": return 1;
            case "INFO": return 2;
            case "WARN": return 3;
            case "ERROR": return 4;
            case "FATAL": return 5;
            default: return 2; // INFO 기본값
        }
    }
    
    /**
     * 간단한 텍스트 표현
     */
    public String toSimpleString() {
        return String.format("LogEntry[id=%d, type=%s, severity=%s, source=%s]",
            id, logType, severity, source);
    }
    
    /**
     * 상세 텍스트 표현
     */
    public String toDetailedString() {
        return String.format("LogEntry[id=%d, type=%s, severity=%s, source=%s, timestamp=%s, message=%s]",
            id, logType, severity, source, timestamp, 
            message != null && message.length() > 50 ? message.substring(0, 47) + "..." : message);
    }
    
    /**
     * 익명화된 버전 생성
     */
    public LogEntry createAnonymizedVersion() {
        return this.toBuilder()
            .message(anonymizeMessage(message))
            .details(anonymizeMessage(details))
            .build();
    }
    
    /**
     * 메시지 익명화
     */
    private String anonymizeMessage(String text) {
        if (text == null) return null;
        
        return text
            .replaceAll("\\b\\d{3}-\\d{4}-\\d{4}\\b", "XXX-XXXX-XXXX") // 전화번호
            .replaceAll("\\b\\d{4}-\\d{4}-\\d{4}-\\d{4}\\b", "XXXX-XXXX-XXXX-XXXX") // 카드번호
            .replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "***@***.***") // 이메일
            .replaceAll("(password|token|key|secret)\\s*[=:]\\s*\\S+", "$1=***"); // 키-값 쌍
    }
}