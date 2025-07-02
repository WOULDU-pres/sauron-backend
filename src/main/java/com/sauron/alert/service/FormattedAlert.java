package com.sauron.alert.service;

import java.time.Instant;
import java.util.Map;

/**
 * 포맷된 알림 데이터
 * 채널별로 전송하기 위해 포맷팅된 알림 정보를 담습니다.
 */
public class FormattedAlert {
    
    private final String alertId;
    private final String alertType;
    private final String severity;
    private final String title;
    private final String message;
    private final String detailedMessage;
    private final Instant timestamp;
    private final String chatRoomTitle;
    private final String messageContent;
    private final Double confidence;
    private final Map<String, Object> metadata;
    
    public FormattedAlert(String alertId, String alertType, String severity, String title,
                         String message, String detailedMessage, Instant timestamp,
                         String chatRoomTitle, String messageContent, Double confidence,
                         Map<String, Object> metadata) {
        this.alertId = alertId;
        this.alertType = alertType;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.detailedMessage = detailedMessage;
        this.timestamp = timestamp;
        this.chatRoomTitle = chatRoomTitle;
        this.messageContent = messageContent;
        this.confidence = confidence;
        this.metadata = metadata;
    }
    
    // Getters
    public String getAlertId() { return alertId; }
    public String getAlertType() { return alertType; }
    public String getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getDetailedMessage() { return detailedMessage; }
    public Instant getTimestamp() { return timestamp; }
    public String getChatRoomTitle() { return chatRoomTitle; }
    public String getMessageContent() { return messageContent; }
    public Double getConfidence() { return confidence; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    /**
     * 간단한 텍스트 포맷
     */
    public String toSimpleText() {
        return String.format("[%s] %s: %s", severity, alertType, message);
    }
    
    /**
     * 상세 텍스트 포맷
     */
    public String toDetailedText() {
        StringBuilder sb = new StringBuilder();
        sb.append("🚨 ").append(title).append("\n\n");
        sb.append("📍 채팅방: ").append(chatRoomTitle).append("\n");
        sb.append("⚠️ 유형: ").append(alertType).append("\n");
        sb.append("📊 신뢰도: ").append(String.format("%.1f%%", confidence * 100)).append("\n");
        sb.append("🕐 시간: ").append(timestamp.toString()).append("\n\n");
        sb.append("💬 메시지: ").append(messageContent).append("\n\n");
        sb.append("📝 상세: ").append(detailedMessage);
        return sb.toString();
    }
    
    /**
     * HTML 포맷
     */
    public String toHtmlFormat() {
        return String.format("""
            <div style="border: 1px solid #ccc; padding: 15px; margin: 10px; border-radius: 5px;">
                <h3 style="color: #d32f2f;">🚨 %s</h3>
                <p><strong>채팅방:</strong> %s</p>
                <p><strong>유형:</strong> %s</p>
                <p><strong>신뢰도:</strong> %.1f%%</p>
                <p><strong>시간:</strong> %s</p>
                <div style="background: #f5f5f5; padding: 10px; margin: 10px 0; border-radius: 3px;">
                    <strong>메시지:</strong><br>%s
                </div>
                <p><em>%s</em></p>
            </div>
            """, title, chatRoomTitle, alertType, confidence * 100, timestamp, messageContent, detailedMessage);
    }
    
    /**
     * JSON 포맷
     */
    public String toJsonFormat() {
        return String.format("""
            {
                "alertId": "%s",
                "alertType": "%s",
                "severity": "%s",
                "title": "%s",
                "message": "%s",
                "timestamp": "%s",
                "chatRoomTitle": "%s",
                "messageContent": "%s",
                "confidence": %.3f
            }
            """, alertId, alertType, severity, title, message, timestamp, chatRoomTitle, messageContent, confidence);
    }
    
    /**
     * 마크다운 포맷
     */
    public String toMarkdownFormat() {
        return String.format("""
            # 🚨 %s
            
            **채팅방:** %s  
            **유형:** %s  
            **신뢰도:** %.1f%%  
            **시간:** %s  
            
            ## 💬 메시지
            ```
            %s
            ```
            
            ## 📝 상세 정보
            %s
            """, title, chatRoomTitle, alertType, confidence * 100, timestamp, messageContent, detailedMessage);
    }
    
    /**
     * 슬랙 포맷
     */
    public String toSlackFormat() {
        return String.format("""
            {
                "text": "%s",
                "attachments": [
                    {
                        "color": "danger",
                        "fields": [
                            {"title": "채팅방", "value": "%s", "short": true},
                            {"title": "유형", "value": "%s", "short": true},
                            {"title": "신뢰도", "value": "%.1f%%", "short": true},
                            {"title": "시간", "value": "%s", "short": true}
                        ],
                        "text": "```%s```"
                    }
                ]
            }
            """, title, chatRoomTitle, alertType, confidence * 100, timestamp, messageContent);
    }
    
    /**
     * 디스코드 포맷
     */
    public String toDiscordFormat() {
        return String.format("""
            {
                "embeds": [{
                    "title": "🚨 %s",
                    "color": 15158332,
                    "fields": [
                        {"name": "채팅방", "value": "%s", "inline": true},
                        {"name": "유형", "value": "%s", "inline": true},
                        {"name": "신뢰도", "value": "%.1f%%", "inline": true},
                        {"name": "메시지", "value": "```%s```", "inline": false}
                    ],
                    "timestamp": "%s"
                }]
            }
            """, title, chatRoomTitle, alertType, confidence * 100, messageContent, timestamp);
    }
}