package com.sauron.logging.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 로그 조회 쿼리 DTO
 * 로그 검색 및 필터링 조건을 정의합니다.
 */
@Data
@Builder
public class LogQuery {
    
    private String logType;
    private String source;
    private String severity;
    private Instant startDate;
    private Instant endDate;
    private List<String> severityLevels;
    private List<String> sources;
    private List<String> logTypes;
    private String messageKeyword;
    private Boolean includeEncrypted;
    private Boolean includeAnonymized;
    
    /**
     * 기본 쿼리 생성
     */
    public static LogQuery defaultQuery() {
        return LogQuery.builder()
            .includeEncrypted(true)
            .includeAnonymized(true)
            .build();
    }
    
    /**
     * 날짜 범위 쿼리 생성
     */
    public static LogQuery dateRangeQuery(Instant startDate, Instant endDate) {
        return LogQuery.builder()
            .startDate(startDate)
            .endDate(endDate)
            .includeEncrypted(true)
            .includeAnonymized(true)
            .build();
    }
    
    /**
     * 로그 타입별 쿼리 생성
     */
    public static LogQuery logTypeQuery(String logType) {
        return LogQuery.builder()
            .logType(logType)
            .includeEncrypted(true)
            .includeAnonymized(true)
            .build();
    }
    
    /**
     * 심각도별 쿼리 생성
     */
    public static LogQuery severityQuery(String severity) {
        return LogQuery.builder()
            .severity(severity)
            .includeEncrypted(true)
            .includeAnonymized(true)
            .build();
    }
    
    /**
     * 소스별 쿼리 생성
     */
    public static LogQuery sourceQuery(String source) {
        return LogQuery.builder()
            .source(source)
            .includeEncrypted(true)
            .includeAnonymized(true)
            .build();
    }
    
    /**
     * 유효성 검사
     */
    public boolean isValid() {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 필터 조건 존재 여부
     */
    public boolean hasFilters() {
        return logType != null || source != null || severity != null ||
               startDate != null || endDate != null ||
               (severityLevels != null && !severityLevels.isEmpty()) ||
               (sources != null && !sources.isEmpty()) ||
               (logTypes != null && !logTypes.isEmpty()) ||
               messageKeyword != null;
    }
    
    /**
     * 쿼리 설명 생성
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder("LogQuery[");
        
        if (logType != null) sb.append("type=").append(logType).append(", ");
        if (source != null) sb.append("source=").append(source).append(", ");
        if (severity != null) sb.append("severity=").append(severity).append(", ");
        if (startDate != null) sb.append("from=").append(startDate).append(", ");
        if (endDate != null) sb.append("to=").append(endDate).append(", ");
        if (messageKeyword != null) sb.append("keyword=").append(messageKeyword).append(", ");
        
        if (sb.length() > 9) {
            sb.setLength(sb.length() - 2); // 마지막 ", " 제거
        }
        
        sb.append("]");
        return sb.toString();
    }
}