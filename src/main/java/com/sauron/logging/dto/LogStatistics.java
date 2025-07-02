package com.sauron.logging.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 로그 통계 DTO
 */
@Data
@Builder
public class LogStatistics {
    
    private long totalLogs;
    private long todayLogs;
    private long errorLogs;
    private long warningLogs;
    private List<String> topSources;
    private List<String> topLogTypes;
    private Instant generatedAt;
    
    /**
     * 오류율 계산
     */
    public double getErrorRate() {
        return totalLogs > 0 ? (double) errorLogs / totalLogs * 100.0 : 0.0;
    }
    
    /**
     * 경고율 계산
     */
    public double getWarningRate() {
        return totalLogs > 0 ? (double) warningLogs / totalLogs * 100.0 : 0.0;
    }
    
    /**
     * 오늘 로그 비율 계산
     */
    public double getTodayLogRate() {
        return totalLogs > 0 ? (double) todayLogs / totalLogs * 100.0 : 0.0;
    }
}