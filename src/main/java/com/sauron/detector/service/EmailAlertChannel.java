package com.sauron.detector.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 이메일 알림 채널 구현 (Mock 구현)
 * T-007-003: 이메일을 통한 공지 알림 전송 (폴백 채널)
 * 
 * NOTE: 실제 JavaMail 구현은 spring-boot-starter-mail 의존성 추가 후 활성화
 */
@Component
@Slf4j
public class EmailAlertChannel implements AlertChannel {
    
    @Value("${announcement.alert.email.enabled:false}")
    private boolean enabled;
    
    @Value("${announcement.alert.email.from:noreply@sauron.local}")
    private String fromEmail;
    
    @Value("${announcement.alert.email.subject.prefix:[Sauron 공지 알림]}")
    private String subjectPrefix;

    @Override
    public boolean sendAlert(String alertType, String message, List<String> recipients) throws Exception {
        if (!enabled) {
            log.debug("이메일 알림 채널이 비활성화되어 있습니다.");
            return false;
        }
        
        if (recipients == null || recipients.isEmpty()) {
            log.warn("이메일 수신자가 설정되지 않았습니다.");
            return false;
        }
        
        try {
            // Mock 구현 - 실제 메일 전송 대신 로깅
            String subject = generateSubject(alertType);
            String emailContent = formatEmailMessage(alertType, message);
            
            log.info("이메일 알림 Mock 전송: to={}, subject={}, alertType={}", 
                    recipients, subject, alertType);
            log.debug("이메일 내용: {}", emailContent);
            
            // 실제 구현 시 JavaMailSender 사용:
            // SimpleMailMessage mailMessage = new SimpleMailMessage();
            // mailMessage.setFrom(fromEmail);
            // mailMessage.setTo(recipients.toArray(new String[0]));
            // mailMessage.setSubject(subject);
            // mailMessage.setText(emailContent);
            // mailSender.send(mailMessage);
            
            log.info("이메일 알림 Mock 전송 완료: recipients={}", recipients.size());
            return true;
            
        } catch (Exception e) {
            log.error("이메일 알림 전송 중 오류 발생: alertType={}", alertType, e);
            throw new Exception("이메일 알림 전송 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 이메일 제목 생성
     */
    private String generateSubject(String alertType) {
        String priority = switch (alertType) {
            case "ANNOUNCEMENT_HIGH_PRIORITY" -> " [긴급]";
            case "ANNOUNCEMENT_MEDIUM_PRIORITY" -> " [보통]";
            case "ANNOUNCEMENT_TIME_VIOLATION" -> " [시간외]";
            default -> "";
        };
        
        return subjectPrefix + priority + " 공지/이벤트 감지";
    }

    /**
     * 이메일 메시지 포맷팅
     */
    private String formatEmailMessage(String alertType, String message) {
        StringBuilder formatted = new StringBuilder();
        
        formatted.append("Sauron 공지/이벤트 감지 시스템에서 새로운 공지를 감지했습니다.\n\n");
        
        // 알림 정보
        formatted.append("=== 알림 정보 ===\n");
        formatted.append("알림 타입: ").append(getAlertTypeDescription(alertType)).append("\n");
        formatted.append("감지 시간: ").append(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(java.time.LocalDateTime.now())
        ).append("\n\n");
        
        // 메시지 내용
        formatted.append("=== 감지된 내용 ===\n");
        formatted.append(message);
        
        // 추가 안내
        formatted.append("\n\n=== 처리 안내 ===\n");
        if (alertType.contains("HIGH_PRIORITY")) {
            formatted.append("⚠️ 높은 우선순위의 공지입니다. 즉시 확인하시기 바랍니다.\n");
        } else if (alertType.contains("TIME_VIOLATION")) {
            formatted.append("🌙 업무시간 외에 감지된 공지입니다. 긴급 사안일 수 있으니 확인 바랍니다.\n");
        } else {
            formatted.append("📋 새로운 공지/이벤트가 감지되었습니다. 확인 후 필요한 조치를 취해주세요.\n");
        }
        
        // 시스템 정보
        formatted.append("\n=== 시스템 정보 ===\n");
        formatted.append("발송 시스템: Sauron 공지 감지 시스템\n");
        formatted.append("발송 시간: ").append(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(java.time.LocalDateTime.now())
        ).append("\n");
        formatted.append("이 메일은 자동으로 발송된 메일입니다.\n");
        
        return formatted.toString();
    }

    /**
     * 알림 타입 설명 반환
     */
    private String getAlertTypeDescription(String alertType) {
        return switch (alertType) {
            case "ANNOUNCEMENT_HIGH_PRIORITY" -> "높은 우선순위 공지";
            case "ANNOUNCEMENT_MEDIUM_PRIORITY" -> "보통 우선순위 공지";
            case "ANNOUNCEMENT_LOW_PRIORITY" -> "낮은 우선순위 공지";
            case "ANNOUNCEMENT_TIME_VIOLATION" -> "업무시간 외 공지";
            default -> "일반 공지";
        };
    }

    @Override
    public String getChannelType() {
        return "email";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isHealthy() {
        if (!isEnabled()) {
            return false;
        }
        
        try {
            // Mock 구현에서는 항상 healthy
            return true;
            
        } catch (Exception e) {
            log.debug("이메일 채널 헬스체크 실패: {}", e.getMessage());
            return false;
        }
    }
}