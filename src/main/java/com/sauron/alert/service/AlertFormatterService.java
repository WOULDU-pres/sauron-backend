package com.sauron.alert.service;

import com.sauron.alert.entity.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 알림 포맷팅 서비스
 * Alert 엔티티를 FormattedAlert로 변환합니다.
 */
@Service
@Slf4j
public class AlertFormatterService {
    
    /**
     * Alert 엔티티를 FormattedAlert로 변환
     */
    public FormattedAlert formatAlert(Alert alert) {
        try {
            String alertId = alert.getId() != null ? alert.getId().toString() : "unknown";
            String alertType = alert.getAlertType();
            String severity = determineSeverity(alert);
            String title = generateTitle(alert);
            String message = generateMessage(alert);
            String detailedMessage = generateDetailedMessage(alert);
            Instant timestamp = alert.getCreatedAt() != null ? alert.getCreatedAt() : Instant.now();
            
            // 메타데이터에서 채팅방과 메시지 정보 추출
            String chatRoomTitle = extractChatRoomTitle(alert);
            String messageContent = extractMessageContent(alert);
            Double confidence = extractConfidence(alert);
            
            Map<String, Object> metadata = buildMetadata(alert);
            
            return new FormattedAlert(
                alertId, alertType, severity, title, message, detailedMessage,
                timestamp, chatRoomTitle, messageContent, confidence, metadata
            );
            
        } catch (Exception e) {
            log.error("Failed to format alert: {}", alert.getId(), e);
            return createFallbackFormattedAlert(alert);
        }
    }
    
    /**
     * 알림 심각도 결정
     */
    private String determineSeverity(Alert alert) {
        String alertType = alert.getAlertType();
        
        switch (alertType.toLowerCase()) {
            case "abuse":
            case "inappropriate":
                return "HIGH";
            case "advertisement":
            case "spam":
                return "MEDIUM";
            case "conflict":
                return "LOW";
            default:
                return "MEDIUM";
        }
    }
    
    /**
     * 알림 제목 생성
     */
    private String generateTitle(Alert alert) {
        String alertType = alert.getAlertType();
        
        switch (alertType.toLowerCase()) {
            case "abuse":
                return "욕설/비방 메시지 감지";
            case "advertisement":
                return "광고 메시지 감지";
            case "spam":
                return "도배 메시지 감지";
            case "inappropriate":
                return "부적절한 메시지 감지";
            case "conflict":
                return "분쟁 메시지 감지";
            default:
                return "이상 메시지 감지";
        }
    }
    
    /**
     * 알림 메시지 생성
     */
    private String generateMessage(Alert alert) {
        String chatRoom = extractChatRoomTitle(alert);
        String alertType = alert.getAlertType();
        
        return String.format("%s에서 %s이(가) 감지되었습니다.", chatRoom, getAlertTypeDescription(alertType));
    }
    
    /**
     * 상세 메시지 생성
     */
    private String generateDetailedMessage(Alert alert) {
        StringBuilder sb = new StringBuilder();
        
        String confidence = extractConfidence(alert) != null ? 
            String.format("%.1f%%", extractConfidence(alert) * 100) : "알 수 없음";
        
        sb.append("AI 분석 신뢰도: ").append(confidence).append("\n");
        sb.append("즉시 확인하여 적절한 조치를 취해주세요.\n");
        
        // 추천 조치사항 추가
        sb.append("\n권장 조치:\n");
        sb.append(getRecommendedAction(alert.getAlertType()));
        
        return sb.toString();
    }
    
    /**
     * 알림 타입 설명 반환
     */
    private String getAlertTypeDescription(String alertType) {
        switch (alertType.toLowerCase()) {
            case "abuse":
                return "욕설/비방 메시지";
            case "advertisement":
                return "광고 메시지";
            case "spam":
                return "도배 메시지";
            case "inappropriate":
                return "부적절한 메시지";
            case "conflict":
                return "분쟁 메시지";
            default:
                return "이상 메시지";
        }
    }
    
    /**
     * 권장 조치사항 반환
     */
    private String getRecommendedAction(String alertType) {
        switch (alertType.toLowerCase()) {
            case "abuse":
                return "- 해당 사용자에게 경고 또는 제재 조치\n- 필요 시 신고 처리\n- 채팅방 규칙 안내";
            case "advertisement":
                return "- 광고 메시지 삭제\n- 광고 정책 안내\n- 반복 시 사용자 제재";
            case "spam":
                return "- 도배 메시지 정리\n- 사용자 주의 조치\n- 채팅 속도 제한 고려";
            case "inappropriate":
                return "- 부적절한 내용 검토 및 삭제\n- 커뮤니티 가이드라인 안내";
            case "conflict":
                return "- 분쟁 상황 중재\n- 관련 사용자들에게 진정 요청\n- 필요 시 임시 제재";
            default:
                return "- 메시지 내용 검토\n- 필요 시 적절한 조치 수행";
        }
    }
    
    /**
     * 채팅방 제목 추출
     */
    private String extractChatRoomTitle(Alert alert) {
        // 메타데이터에서 채팅방 정보 추출 시도
        String metadata = alert.getMetadata();
        if (metadata != null && metadata.contains("chatRoom")) {
            try {
                String[] parts = metadata.split("chatRoom");
                if (parts.length > 1) {
                    return parts[1].split(",")[0].replace(":", "").replace("\"", "").trim();
                }
            } catch (Exception e) {
                log.debug("Failed to extract chatroom title from metadata: {}", metadata);
            }
        }
        return "알 수 없는 채팅방";
    }
    
    /**
     * 메시지 내용 추출
     */
    private String extractMessageContent(Alert alert) {
        // 암호화된 내용은 복호화 없이 표시할 수 없으므로 기본 메시지 반환
        String contentEncrypted = alert.getContentEncrypted();
        if (contentEncrypted != null && !contentEncrypted.trim().isEmpty()) {
            return "감지된 메시지 (암호화됨)";
        }
        return "메시지 내용 없음";
    }
    
    /**
     * 신뢰도 추출
     */
    private Double extractConfidence(Alert alert) {
        // 메타데이터에서 신뢰도 정보 추출 시도
        String metadata = alert.getMetadata();
        if (metadata != null && metadata.contains("confidence")) {
            try {
                // JSON 형태의 메타데이터에서 confidence 값 추출
                String[] parts = metadata.split("confidence");
                if (parts.length > 1) {
                    String confidencePart = parts[1].split(",")[0].replace(":", "").replace("\"", "").trim();
                    return Double.parseDouble(confidencePart);
                }
            } catch (Exception e) {
                log.debug("Failed to extract confidence from metadata: {}", metadata);
            }
        }
        return 0.5; // 기본값
    }
    
    /**
     * 메타데이터 구성
     */
    private Map<String, Object> buildMetadata(Alert alert) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("alertId", alert.getId());
        metadata.put("originalContent", alert.getContentEncrypted());
        metadata.put("createdAt", alert.getCreatedAt());
        metadata.put("status", alert.getStatus());
        metadata.put("channel", alert.getChannel());
        return metadata;
    }
    
    /**
     * 폴백 FormattedAlert 생성
     */
    private FormattedAlert createFallbackFormattedAlert(Alert alert) {
        String alertId = alert.getId() != null ? alert.getId().toString() : "unknown";
        String content = alert.getContentEncrypted() != null ? "암호화된 내용" : "알림 정보 없음";
        
        return new FormattedAlert(
            alertId,
            alert.getAlertType() != null ? alert.getAlertType() : "unknown",
            "MEDIUM",
            "알림 처리 중 오류 발생",
            content,
            "알림을 포맷팅하는 중 오류가 발생했습니다. 원본 정보를 확인해주세요.",
            Instant.now(),
            "알 수 없는 채팅방",
            content,
            0.5,
            Map.of("fallback", true, "originalAlert", alert)
        );
    }
}