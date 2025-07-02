package com.sauron.alert.service;

import com.sauron.common.external.telegram.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 텔레그램 채널 어댑터
 * 텔레그램을 통한 알림 전송을 담당합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramChannelAdapter implements AlertChannelAdapter {
    
    private final TelegramNotificationService telegramService;
    
    @Value("${telegram.alert.enabled:true}")
    private boolean enabled;
    
    @Value("${telegram.alert.high-priority-chat-id:}")
    private String highPriorityChatId;
    
    @Value("${telegram.alert.fallback-chat-id:}")
    private String fallbackChatId;
    
    private Instant lastSuccessTime;
    private int consecutiveFailures = 0;
    
    // 지원하는 알림 타입들
    private final Set<String> supportedAlertTypes = Set.of(
        "abuse", "advertisement", "spam", "inappropriate", "conflict", "normal"
    );
    
    @Override
    public String getChannelName() {
        return "telegram";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public boolean isHealthy() {
        // 최근 10분 내에 성공했거나, 연속 실패가 5회 미만이면 건강한 상태
        return lastSuccessTime != null && 
               Duration.between(lastSuccessTime, Instant.now()).toMinutes() < 10 ||
               consecutiveFailures < 5;
    }
    
    @Override
    public boolean supportsAlertType(String alertType) {
        return supportedAlertTypes.contains(alertType.toLowerCase());
    }
    
    @Override
    public boolean supportsHighPriority() {
        return highPriorityChatId != null && !highPriorityChatId.trim().isEmpty();
    }
    
    @Override
    public boolean supportsFallback() {
        return fallbackChatId != null && !fallbackChatId.trim().isEmpty();
    }
    
    @Override
    public void sendAlert(FormattedAlert alert) throws Exception {
        try {
            log.debug("Sending telegram alert - ID: {}, Type: {}", alert.getAlertId(), alert.getAlertType());
            
            String message = formatTelegramMessage(alert);
            telegramService.sendAlert(message);
            
            updateSuccessMetrics();
            log.info("Telegram alert sent successfully - ID: {}", alert.getAlertId());
            
        } catch (Exception e) {
            updateFailureMetrics();
            log.error("Failed to send telegram alert - ID: {}", alert.getAlertId(), e);
            throw new Exception("Telegram alert failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void sendHighPriorityAlert(FormattedAlert alert) throws Exception {
        if (!supportsHighPriority()) {
            throw new UnsupportedOperationException("High priority alerts not configured for Telegram");
        }
        
        try {
            log.warn("Sending HIGH PRIORITY telegram alert - ID: {}, Type: {}", 
                    alert.getAlertId(), alert.getAlertType());
            
            String message = formatHighPriorityMessage(alert);
            telegramService.sendHighPriorityAlert(message, highPriorityChatId);
            
            updateSuccessMetrics();
            log.info("High priority telegram alert sent - ID: {}", alert.getAlertId());
            
        } catch (Exception e) {
            updateFailureMetrics();
            log.error("Failed to send high priority telegram alert - ID: {}", alert.getAlertId(), e);
            throw new Exception("High priority telegram alert failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void sendFallbackAlert(FormattedAlert alert) throws Exception {
        if (!supportsFallback()) {
            throw new UnsupportedOperationException("Fallback alerts not configured for Telegram");
        }
        
        try {
            log.info("Sending FALLBACK telegram alert - ID: {}, Type: {}", 
                    alert.getAlertId(), alert.getAlertType());
            
            String message = formatFallbackMessage(alert);
            telegramService.sendFallbackAlert(message, fallbackChatId);
            
            updateSuccessMetrics();
            log.info("Fallback telegram alert sent - ID: {}", alert.getAlertId());
            
        } catch (Exception e) {
            updateFailureMetrics();
            log.error("Failed to send fallback telegram alert - ID: {}", alert.getAlertId(), e);
            throw new Exception("Fallback telegram alert failed: " + e.getMessage(), e);
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
        if (config.containsKey("highPriorityChatId")) {
            this.highPriorityChatId = (String) config.get("highPriorityChatId");
        }
        if (config.containsKey("fallbackChatId")) {
            this.fallbackChatId = (String) config.get("fallbackChatId");
        }
        
        log.info("Telegram channel configuration updated - enabled: {}", enabled);
    }
    
    /**
     * 일반 텔레그램 메시지 포맷팅
     */
    private String formatTelegramMessage(FormattedAlert alert) {
        StringBuilder sb = new StringBuilder();
        
        // 이모지로 심각도 표시
        String severityEmoji = getSeverityEmoji(alert.getSeverity());
        sb.append(severityEmoji).append(" **").append(alert.getTitle()).append("**\n\n");
        
        sb.append("📍 **채팅방:** ").append(escapeMarkdown(alert.getChatRoomTitle())).append("\n");
        sb.append("⚠️ **유형:** ").append(alert.getAlertType()).append("\n");
        sb.append("📊 **신뢰도:** ").append(String.format("%.1f%%", alert.getConfidence() * 100)).append("\n");
        sb.append("🕐 **시간:** ").append(alert.getTimestamp().toString()).append("\n\n");
        
        sb.append("💬 **메시지:**\n");
        sb.append("```\n");
        sb.append(truncateMessage(alert.getMessageContent(), 1000));
        sb.append("\n```\n\n");
        
        sb.append("📝 ").append(alert.getDetailedMessage());
        
        return sb.toString();
    }
    
    /**
     * 고우선순위 메시지 포맷팅
     */
    private String formatHighPriorityMessage(FormattedAlert alert) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("🚨🚨🚨 **긴급 알림** 🚨🚨🚨\n\n");
        sb.append("**").append(alert.getTitle()).append("**\n\n");
        
        sb.append("📍 **채팅방:** ").append(escapeMarkdown(alert.getChatRoomTitle())).append("\n");
        sb.append("⚠️ **유형:** ").append(alert.getAlertType()).append("\n");
        sb.append("📊 **신뢰도:** ").append(String.format("%.1f%%", alert.getConfidence() * 100)).append("\n\n");
        
        sb.append("💬 **메시지:**\n");
        sb.append("```\n");
        sb.append(truncateMessage(alert.getMessageContent(), 800));
        sb.append("\n```\n\n");
        
        sb.append("⚡ **즉시 조치가 필요합니다!**");
        
        return sb.toString();
    }
    
    /**
     * 폴백 메시지 포맷팅
     */
    private String formatFallbackMessage(FormattedAlert alert) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("🔄 **폴백 알림** (주 채널 실패)\n\n");
        sb.append(alert.getTitle()).append("\n\n");
        
        sb.append("채팅방: ").append(alert.getChatRoomTitle()).append("\n");
        sb.append("유형: ").append(alert.getAlertType()).append("\n");
        sb.append("시간: ").append(alert.getTimestamp().toString()).append("\n\n");
        
        sb.append("메시지: ").append(truncateMessage(alert.getMessageContent(), 500));
        
        return sb.toString();
    }
    
    /**
     * 심각도별 이모지 반환
     */
    private String getSeverityEmoji(String severity) {
        switch (severity.toUpperCase()) {
            case "HIGH":
                return "🔴";
            case "MEDIUM":
                return "🟡";
            case "LOW":
                return "🟢";
            default:
                return "⚪";
        }
    }
    
    /**
     * 마크다운 특수문자 이스케이프
     */
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("*", "\\*")
                   .replace("_", "\\_")
                   .replace("`", "\\`")
                   .replace("[", "\\[")
                   .replace("]", "\\]");
    }
    
    /**
     * 메시지 길이 제한
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength - 3) + "...";
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