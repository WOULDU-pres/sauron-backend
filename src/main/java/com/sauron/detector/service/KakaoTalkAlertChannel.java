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
 * 카카오톡 알림 채널 구현
 * T-007-003: 카카오톡 챗봇을 통한 공지 알림 전송
 */
@Component
@Slf4j
public class KakaoTalkAlertChannel implements AlertChannel {

    private final RestTemplate restTemplate;
    
    @Value("${announcement.alert.kakaotalk.enabled:true}")
    private boolean enabled;
    
    @Value("${announcement.alert.kakaotalk.webhook.url:}")
    private String webhookUrl;
    
    @Value("${announcement.alert.kakaotalk.bot.token:}")
    private String botToken;
    
    @Value("${announcement.alert.kakaotalk.chat.room:}")
    private String chatRoomId;

    public KakaoTalkAlertChannel() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public boolean sendAlert(String alertType, String message, List<String> recipients) throws Exception {
        if (!enabled) {
            log.debug("카카오톡 알림 채널이 비활성화되어 있습니다.");
            return false;
        }
        
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            log.warn("카카오톡 웹훅 URL이 설정되지 않았습니다.");
            return false;
        }
        
        try {
            // 카카오톡 챗봇 API 요청 생성
            Map<String, Object> requestBody = createKakaoTalkRequest(alertType, message, recipients);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            if (botToken != null && !botToken.trim().isEmpty()) {
                headers.setBearerAuth(botToken);
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.debug("카카오톡 알림 전송 요청: url={}, alertType={}", webhookUrl, alertType);
            
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("카카오톡 알림 전송 성공: alertType={}, recipients={}", alertType, recipients.size());
                return true;
            } else {
                log.warn("카카오톡 알림 전송 실패: status={}, body={}", 
                        response.getStatusCode(), response.getBody());
                return false;
            }
            
        } catch (Exception e) {
            log.error("카카오톡 알림 전송 중 오류 발생: alertType={}", alertType, e);
            throw new Exception("카카오톡 알림 전송 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 카카오톡 챗봇 API 요청 생성
     */
    private Map<String, Object> createKakaoTalkRequest(String alertType, String message, List<String> recipients) {
        Map<String, Object> request = new HashMap<>();
        
        // 기본 요청 구조
        request.put("intent", Map.of(
            "name", "공지알림",
            "extra", Map.of(
                "alertType", alertType,
                "timestamp", System.currentTimeMillis()
            )
        ));
        
        // 사용자 정보 (카카오톡 챗봇에서는 userRequest로 처리)
        request.put("userRequest", Map.of(
            "timezone", "Asia/Seoul",
            "params", Map.of("ignoreMe", "true"),
            "block", Map.of(
                "id", "announcement_alert",
                "name", "공지알림"
            ),
            "utterance", message.length() > 100 ? message.substring(0, 100) + "..." : message,
            "lang", "kr",
            "user", Map.of(
                "id", "sauron_admin",
                "type", "accountId",
                "properties", Map.of()
            )
        ));
        
        // 봇 정보
        request.put("bot", Map.of(
            "id", "sauron_bot",
            "name", "Sauron 공지 감지 봇"
        ));
        
        // 액션 정보 (실제 메시지 내용)
        request.put("action", Map.of(
            "name", "sendAnnouncementAlert",
            "clientExtra", Map.of(),
            "params", Map.of(
                "message", message,
                "alertType", alertType,
                "recipients", recipients,
                "chatRoomId", chatRoomId != null ? chatRoomId : "unknown"
            ),
            "id", "announcement_alert_action",
            "detailParams", Map.of(
                "message", Map.of(
                    "origin", message,
                    "value", message,
                    "groupName", ""
                ),
                "alertType", Map.of(
                    "origin", alertType,
                    "value", alertType,
                    "groupName", ""
                )
            )
        ));
        
        return request;
    }

    @Override
    public String getChannelType() {
        return "kakaotalk";
    }

    @Override
    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.trim().isEmpty();
    }

    @Override
    public boolean isHealthy() {
        if (!isEnabled()) {
            return false;
        }
        
        try {
            // 간단한 헬스체크 요청
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            if (botToken != null && !botToken.trim().isEmpty()) {
                headers.setBearerAuth(botToken);
            }
            
            Map<String, Object> healthCheckRequest = Map.of(
                "intent", Map.of("name", "health_check"),
                "userRequest", Map.of(
                    "utterance", "health_check",
                    "user", Map.of("id", "sauron_system")
                )
            );
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(healthCheckRequest, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.debug("카카오톡 채널 헬스체크 실패: {}", e.getMessage());
            return false;
        }
    }
}