package com.sauron.common.validation;

import com.sauron.listener.dto.MessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 메시지 요청에 대한 비즈니스 로직 검증을 수행하는 컴포넌트
 * Jakarta Validation과 함께 사용되어 추가적인 검증을 제공합니다.
 */
@Component
@Slf4j
public class MessageValidator {
    
    // 허용되는 패키지 이름 패턴
    private static final Pattern PACKAGE_NAME_PATTERN = 
        Pattern.compile("^[a-z]+(\\.[a-z][a-z0-9_]*)*$");
    
    // 메시지 최대 길이
    private static final int MAX_MESSAGE_LENGTH = 5000;
    
    // 디바이스 ID 패턴 (영문자, 숫자, 하이픈만 허용)
    private static final Pattern DEVICE_ID_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9\\-_]+$");
    
    /**
     * 메시지 요청의 비즈니스 로직 검증을 수행합니다.
     * 
     * @param request 검증할 메시지 요청
     * @return 검증 결과 객체
     */
    public ValidationResult validate(MessageRequest request) {
        List<String> errors = new ArrayList<>();
        
        // 1. 타임스탬프 검증 (과거 24시간 이내)
        if (!isValidTimestamp(request.getReceivedAt())) {
            errors.add("Received timestamp must be within the last 24 hours");
        }
        
        // 2. 패키지 이름 검증
        if (!isValidPackageName(request.getPackageName())) {
            errors.add("Invalid package name format");
        }
        
        // 3. 메시지 내용 검증
        if (!isValidMessageContent(request.getMessageContent())) {
            errors.add("Message content contains invalid characters or exceeds length limit");
        }
        
        // 4. 디바이스 ID 검증
        if (!isValidDeviceId(request.getDeviceId())) {
            errors.add("Device ID contains invalid characters");
        }
        
        // 5. 우선순위 검증
        if (!isValidPriority(request.getPriority())) {
            errors.add("Priority must be one of: low, normal, high, urgent");
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 타임스탬프가 유효한지 검증합니다 (24시간 이내).
     */
    private boolean isValidTimestamp(Instant receivedAt) {
        if (receivedAt == null) {
            return false;
        }
        
        Instant now = Instant.now();
        Instant twentyFourHoursAgo = now.minus(24, ChronoUnit.HOURS);
        Instant oneHourFromNow = now.plus(1, ChronoUnit.HOURS); // 클라이언트 시간 오차 고려
        
        return receivedAt.isAfter(twentyFourHoursAgo) && receivedAt.isBefore(oneHourFromNow);
    }
    
    /**
     * 패키지 이름이 유효한지 검증합니다.
     */
    private boolean isValidPackageName(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        
        // 카카오톡 관련 패키지만 허용
        return packageName.startsWith("com.kakao") || 
               packageName.equals("com.android.systemui");
    }
    
    /**
     * 메시지 내용이 유효한지 검증합니다.
     */
    private boolean isValidMessageContent(String messageContent) {
        if (messageContent == null) {
            return false;
        }
        
        // 길이 검증
        if (messageContent.length() > MAX_MESSAGE_LENGTH) {
            return false;
        }
        
        // 제어 문자 제거 (탭, 개행 제외)
        String cleanContent = messageContent.replaceAll("[\\p{Cntrl}&&[^\\t\\n\\r]]", "");
        return cleanContent.length() > 0;
    }
    
    /**
     * 디바이스 ID가 유효한지 검증합니다.
     */
    private boolean isValidDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return false;
        }
        
        return DEVICE_ID_PATTERN.matcher(deviceId).matches();
    }
    
    /**
     * 우선순위가 유효한지 검증합니다.
     */
    private boolean isValidPriority(String priority) {
        if (priority == null) {
            return true; // 선택사항
        }
        
        return priority.matches("^(low|normal|high|urgent)$");
    }
    
    /**
     * 검증 결과를 담는 내부 클래스
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        
        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }
} 