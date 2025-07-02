package com.sauron.alert.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * 콘솔 채널 어댑터
 * 콘솔/로그를 통한 알림 출력을 담당합니다.
 */
@Component
@Slf4j
public class ConsoleChannelAdapter implements AlertChannelAdapter {
    
    @Value("${console.alert.enabled:true}")
    private boolean enabled;
    
    @Value("${console.alert.log-level:INFO}")
    private String logLevel;
    
    private Instant lastSuccessTime;
    private int consecutiveFailures = 0;
    
    // 모든 알림 타입 지원
    private final Set<String> supportedAlertTypes = Set.of(
        "abuse", "advertisement", "spam", "inappropriate", "conflict", "normal", "unknown"
    );
    
    @Override
    public String getChannelName() {
        return "console";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public boolean isHealthy() {
        // 콘솔은 항상 건강한 상태로 간주
        return true;
    }
    
    @Override
    public boolean supportsAlertType(String alertType) {
        return supportedAlertTypes.contains(alertType.toLowerCase());
    }
    
    @Override
    public boolean supportsHighPriority() {
        return true; // 콘솔은 모든 우선순위 지원
    }
    
    @Override
    public boolean supportsFallback() {
        return true; // 콘솔은 폴백 지원
    }
    
    @Override
    public void sendAlert(FormattedAlert alert) throws Exception {
        try {
            String formattedMessage = formatConsoleMessage(alert);
            
            // 로그 레벨에 따른 출력
            switch (logLevel.toUpperCase()) {
                case "ERROR":
                    log.error("ALERT: {}", formattedMessage);
                    break;
                case "WARN":
                    log.warn("ALERT: {}", formattedMessage);
                    break;
                case "INFO":
                default:
                    log.info("ALERT: {}", formattedMessage);
                    break;
            }
            
            // 콘솔에도 직접 출력
            System.out.println("=== SAURON ALERT ===");
            System.out.println(formattedMessage);
            System.out.println("==================");
            
            updateSuccessMetrics();
            
        } catch (Exception e) {
            updateFailureMetrics();
            throw new Exception("Console alert failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void sendHighPriorityAlert(FormattedAlert alert) throws Exception {
        try {
            String formattedMessage = formatHighPriorityMessage(alert);
            
            // 고우선순위는 항상 ERROR 레벨로 출력
            log.error("HIGH PRIORITY ALERT: {}", formattedMessage);
            
            // 콘솔에 강조 표시
            System.out.println("🚨🚨🚨 HIGH PRIORITY ALERT 🚨🚨🚨");
            System.out.println(formattedMessage);
            System.out.println("🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨");
            
            updateSuccessMetrics();
            
        } catch (Exception e) {
            updateFailureMetrics();
            throw new Exception("High priority console alert failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void sendFallbackAlert(FormattedAlert alert) throws Exception {
        try {
            String formattedMessage = formatFallbackMessage(alert);
            
            log.warn("FALLBACK ALERT: {}", formattedMessage);
            
            System.out.println("🔄 FALLBACK ALERT (Primary channels failed) 🔄");
            System.out.println(formattedMessage);
            System.out.println("🔄🔄🔄🔄🔄🔄🔄🔄🔄🔄🔄🔄🔄🔄🔄🔄🔄🔄🔄");
            
            updateSuccessMetrics();
            
        } catch (Exception e) {
            updateFailureMetrics();
            throw new Exception("Fallback console alert failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Instant getLastSuccessTime() {
        return lastSuccessTime;
    }
    
    @Override
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
    
    @Override
    public void updateConfiguration(Map<String, Object> config) {
        if (config.containsKey("enabled")) {
            this.enabled = (Boolean) config.get("enabled");
        }
        if (config.containsKey("logLevel")) {
            this.logLevel = (String) config.get("logLevel");
        }
        
        log.info("Console channel configuration updated - enabled: {}, logLevel: {}", enabled, logLevel);
    }
    
    /**
     * 일반 콘솔 메시지 포맷팅
     */
    private String formatConsoleMessage(FormattedAlert alert) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("\n");
        sb.append("Alert ID: ").append(alert.getAlertId()).append("\n");
        sb.append("Type: ").append(alert.getAlertType()).append("\n");
        sb.append("Severity: ").append(alert.getSeverity()).append("\n");
        sb.append("Title: ").append(alert.getTitle()).append("\n");
        sb.append("Chat Room: ").append(alert.getChatRoomTitle()).append("\n");
        sb.append("Confidence: ").append(String.format("%.1f%%", alert.getConfidence() * 100)).append("\n");
        sb.append("Timestamp: ").append(formatTimestamp(alert.getTimestamp())).append("\n");
        sb.append("Message: ").append(truncateForConsole(alert.getMessageContent())).append("\n");
        sb.append("Details: ").append(alert.getDetailedMessage()).append("\n");
        
        return sb.toString();
    }
    
    /**
     * 고우선순위 메시지 포맷팅
     */
    private String formatHighPriorityMessage(FormattedAlert alert) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("\n");
        sb.append("🚨 URGENT: ").append(alert.getTitle()).append("\n");
        sb.append("Alert ID: ").append(alert.getAlertId()).append("\n");
        sb.append("Type: ").append(alert.getAlertType()).append(" (HIGH PRIORITY)").append("\n");
        sb.append("Chat Room: ").append(alert.getChatRoomTitle()).append("\n");
        sb.append("Confidence: ").append(String.format("%.1f%%", alert.getConfidence() * 100)).append("\n");
        sb.append("Timestamp: ").append(formatTimestamp(alert.getTimestamp())).append("\n");
        sb.append("Message: ").append(truncateForConsole(alert.getMessageContent())).append("\n");
        sb.append("⚡ IMMEDIATE ACTION REQUIRED! ⚡\n");
        
        return sb.toString();
    }
    
    /**
     * 폴백 메시지 포맷팅
     */
    private String formatFallbackMessage(FormattedAlert alert) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("\n");
        sb.append("🔄 FALLBACK: ").append(alert.getTitle()).append("\n");
        sb.append("Alert ID: ").append(alert.getAlertId()).append("\n");
        sb.append("Type: ").append(alert.getAlertType()).append(" (FALLBACK)").append("\n");
        sb.append("Chat Room: ").append(alert.getChatRoomTitle()).append("\n");
        sb.append("Timestamp: ").append(formatTimestamp(alert.getTimestamp())).append("\n");
        sb.append("Message: ").append(truncateForConsole(alert.getMessageContent())).append("\n");
        sb.append("Note: Primary alert channels failed, using console fallback\n");
        
        return sb.toString();
    }
    
    /**
     * 타임스탬프 포맷팅
     */
    private String formatTimestamp(Instant timestamp) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(timestamp.atZone(java.time.ZoneId.systemDefault()));
    }
    
    /**
     * 콘솔용 메시지 길이 제한
     */
    private String truncateForConsole(String message) {
        if (message == null) return "";
        if (message.length() <= 200) return message;
        return message.substring(0, 197) + "...";
    }
    
    /**
     * 성공 메트릭 업데이트
     */
    private void updateSuccessMetrics() {
        lastSuccessTime = Instant.now();
        consecutiveFailures = 0;
    }
    
    /**
     * 실패 메트릭 업데이트
     */
    private void updateFailureMetrics() {
        consecutiveFailures++;
    }
}