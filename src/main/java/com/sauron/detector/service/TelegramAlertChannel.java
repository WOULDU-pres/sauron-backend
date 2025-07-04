package com.sauron.detector.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 텔레그램 알림 채널 구현
 * T-007-003: 텔레그램 봇을 통한 공지 알림 전송
 */
@Component
@Slf4j
public class TelegramAlertChannel implements AlertChannel {

    private final RestTemplate restTemplate;
    
    @Value("${announcement.alert.telegram.enabled:false}")
    private boolean enabled;
    
    @Value("${announcement.alert.telegram.bot.token:}")
    private String botToken;
    
    @Value("${announcement.alert.telegram.chat.id:}")
    private String chatId;

    public TelegramAlertChannel() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public boolean sendAlert(String alertType, String message, List<String> recipients) throws Exception {
        if (!enabled) {
            log.debug("텔레그램 알림 채널이 비활성화되어 있습니다.");
            return false;
        }
        
        if (botToken == null || botToken.trim().isEmpty()) {
            log.warn("텔레그램 봇 토큰이 설정되지 않았습니다.");
            return false;
        }
        
        if (chatId == null || chatId.trim().isEmpty()) {
            log.warn("텔레그램 채팅 ID가 설정되지 않았습니다.");
            return false;
        }
        
        try {
            String telegramApiUrl = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);
            
            // 텔레그램 메시지 포맷팅
            String formattedMessage = formatTelegramMessage(alertType, message);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("text", formattedMessage);
            requestBody.put("parse_mode", "Markdown");
            requestBody.put("disable_web_page_preview", true);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.debug("텔레그램 알림 전송 요청: chatId={}, alertType={}", chatId, alertType);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(telegramApiUrl, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && 
                response.getBody() != null && 
                Boolean.TRUE.equals(response.getBody().get("ok"))) {
                
                log.info("텔레그램 알림 전송 성공: alertType={}, chatId={}", alertType, chatId);
                return true;
            } else {
                log.warn("텔레그램 알림 전송 실패: status={}, body={}", 
                        response.getStatusCode(), response.getBody());
                return false;
            }
            
        } catch (Exception e) {
            log.error("텔레그램 알림 전송 중 오류 발생: alertType={}", alertType, e);
            throw new Exception("텔레그램 알림 전송 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 텔레그램 메시지 포맷팅 (Markdown 형식)
     */
    private String formatTelegramMessage(String alertType, String message) {
        StringBuilder formatted = new StringBuilder();
        
        // 알림 타입에 따른 이모지 및 헤더
        String emoji = getAlertEmoji(alertType);
        formatted.append(emoji).append(" *공지/이벤트 감지 알림*\n\n");
        
        // 우선순위 표시
        String priority = getPriorityText(alertType);
        if (priority != null) {
            formatted.append("🏷️ *우선순위:* ").append(priority).append("\n");
        }
        
        // 현재 시간
        String currentTime = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .format(java.time.LocalDateTime.now());
        formatted.append("🕒 *감지 시간:* ").append(currentTime).append("\n\n");
        
        // 메시지 내용을 텔레그램 형식으로 변환
        String telegramMessage = convertToTelegramFormat(message);
        formatted.append(telegramMessage);
        
        // 알림 타입별 추가 안내
        if (alertType.contains("HIGH_PRIORITY")) {
            formatted.append("\n\n⚠️ *즉시 확인이 필요한 높은 우선순위 공지입니다.*");
        } else if (alertType.contains("TIME_VIOLATION")) {
            formatted.append("\n\n🌙 *업무시간 외 공지가 감지되었습니다. 긴급 사안일 수 있습니다.*");
        }
        
        return formatted.toString();
    }

    /**
     * 알림 타입별 이모지 반환
     */
    private String getAlertEmoji(String alertType) {
        return switch (alertType) {
            case "ANNOUNCEMENT_HIGH_PRIORITY" -> "🚨";
            case "ANNOUNCEMENT_MEDIUM_PRIORITY" -> "⚠️";
            case "ANNOUNCEMENT_TIME_VIOLATION" -> "🌙";
            default -> "🔔";
        };
    }

    /**
     * 우선순위 텍스트 반환
     */
    private String getPriorityText(String alertType) {
        return switch (alertType) {
            case "ANNOUNCEMENT_HIGH_PRIORITY" -> "높음";
            case "ANNOUNCEMENT_MEDIUM_PRIORITY" -> "보통";
            case "ANNOUNCEMENT_LOW_PRIORITY" -> "낮음";
            case "ANNOUNCEMENT_TIME_VIOLATION" -> "시간외";
            default -> null;
        };
    }

    /**
     * 일반 메시지를 텔레그램 Markdown 형식으로 변환
     */
    private String convertToTelegramFormat(String message) {
        if (message == null) return "";
        
        // 기본적인 형식 변환
        String converted = message
            .replace("📊 신뢰도:", "*📊 신뢰도:*")
            .replace("🕒 감지 시간:", "*🕒 감지 시간:*")
            .replace("💬 채팅방:", "*💬 채팅방:*")
            .replace("👤 발송자:", "*👤 발송자:*")
            .replace("🎯 감지 패턴:", "*🎯 감지 패턴:*")
            .replace("🔤 키워드:", "*🔤 키워드:*")
            .replace("⏰ 시간 표현:", "*⏰시간 표현:*")
            .replace("📝 메시지 내용:", "*📝 메시지 내용:*");
        
        // 텔레그램 특수문자 이스케이핑
        converted = converted
            .replace("_", "\\_")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("(", "\\(")
            .replace(")", "\\)");
        
        return converted;
    }

    @Override
    public String getChannelType() {
        return "telegram";
    }

    @Override
    public boolean isEnabled() {
        return enabled && 
               botToken != null && !botToken.trim().isEmpty() &&
               chatId != null && !chatId.trim().isEmpty();
    }

    @Override
    public boolean isHealthy() {
        if (!isEnabled()) {
            return false;
        }
        
        try {
            String telegramApiUrl = String.format("https://api.telegram.org/bot%s/getMe", botToken);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                telegramApiUrl, HttpMethod.GET, request, Map.class);
            
            return response.getStatusCode().is2xxSuccessful() && 
                   response.getBody() != null && 
                   Boolean.TRUE.equals(response.getBody().get("ok"));
            
        } catch (Exception e) {
            log.debug("텔레그램 채널 헬스체크 실패: {}", e.getMessage());
            return false;
        }
    }
}