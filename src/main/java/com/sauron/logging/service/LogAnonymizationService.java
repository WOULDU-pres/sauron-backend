package com.sauron.logging.service;

import com.sauron.logging.dto.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 로그 익명화 서비스
 * 개인정보 및 민감한 정보를 익명화합니다.
 */
@Service
@Slf4j
public class LogAnonymizationService {
    
    // 전화번호 패턴
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(010|011|016|017|018|019|02|031|032|033|041|042|043|044|051|052|053|054|055|061|062|063|064)-?\\d{3,4}-?\\d{4}\\b"
    );
    
    // 이메일 패턴
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );
    
    // 카드번호 패턴
    private static final Pattern CARD_PATTERN = Pattern.compile(
        "\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"
    );
    
    // 주민등록번호 패턴
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{6}[- ]?[1-4]\\d{6}\\b"
    );
    
    // IP 주소 패턴
    private static final Pattern IP_PATTERN = Pattern.compile(
        "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    );
    
    // 비밀번호/토큰 패턴
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "(password|token|key|secret|pwd)\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE
    );
    
    // 한국 이름 패턴 (2-4글자 한글)
    private static final Pattern KOREAN_NAME_PATTERN = Pattern.compile(
        "\\b[가-힣]{2,4}\\b"
    );
    
    /**
     * 로그 엔트리 익명화
     */
    public LogEntry anonymizeLogEntry(LogEntry logEntry) {
        try {
            log.debug("Anonymizing log entry - Type: {}, Source: {}", 
                     logEntry.getLogType(), logEntry.getSource());
            
            return LogEntry.builder()
                .id(logEntry.getId())
                .logType(logEntry.getLogType())
                .source(logEntry.getSource())
                .severity(logEntry.getSeverity())
                .message(anonymizeText(logEntry.getMessage()))
                .details(anonymizeText(logEntry.getDetails()))
                .timestamp(logEntry.getTimestamp())
                .metadata(logEntry.getMetadata()) // 메타데이터는 익명화하지 않음
                .build();
                
        } catch (Exception e) {
            log.error("Failed to anonymize log entry", e);
            throw new AnonymizationException("Failed to anonymize log entry", e);
        }
    }
    
    /**
     * 텍스트 익명화
     */
    private String anonymizeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        String anonymized = text;
        
        try {
            // 1. 전화번호 익명화
            anonymized = PHONE_PATTERN.matcher(anonymized)
                .replaceAll(matcher -> anonymizePhoneNumber(matcher.group()));
            
            // 2. 이메일 익명화
            anonymized = EMAIL_PATTERN.matcher(anonymized)
                .replaceAll(matcher -> anonymizeEmail(matcher.group()));
            
            // 3. 카드번호 익명화
            anonymized = CARD_PATTERN.matcher(anonymized)
                .replaceAll("****-****-****-****");
            
            // 4. 주민등록번호 익명화
            anonymized = SSN_PATTERN.matcher(anonymized)
                .replaceAll("******-*******");
            
            // 5. IP 주소 익명화
            anonymized = IP_PATTERN.matcher(anonymized)
                .replaceAll(matcher -> anonymizeIpAddress(matcher.group()));
            
            // 6. 비밀번호/토큰 익명화
            anonymized = PASSWORD_PATTERN.matcher(anonymized)
                .replaceAll("$1=***");
            
            // 7. 한국 이름 익명화 (선택적)
            if (shouldAnonymizeNames(text)) {
                anonymized = KOREAN_NAME_PATTERN.matcher(anonymized)
                    .replaceAll(matcher -> anonymizeName(matcher.group()));
            }
            
            log.debug("Text anonymization completed - Original length: {}, Anonymized length: {}", 
                     text.length(), anonymized.length());
            
            return anonymized;
            
        } catch (Exception e) {
            log.warn("Error during text anonymization, returning original text", e);
            return text;
        }
    }
    
    /**
     * 전화번호 익명화
     */
    private String anonymizePhoneNumber(String phoneNumber) {
        // 처음 3자리와 마지막 4자리만 남기고 나머지는 *로 치환
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.length() >= 7) {
            String prefix = digits.substring(0, 3);
            String suffix = digits.substring(digits.length() - 4);
            return prefix + "-****-" + suffix;
        }
        return "***-****-****";
    }
    
    /**
     * 이메일 익명화
     */
    private String anonymizeEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 2) {
            String username = email.substring(0, atIndex);
            String domain = email.substring(atIndex);
            String anonymizedUsername = username.substring(0, 2) + "***";
            return anonymizedUsername + domain;
        }
        return "***@***.***";
    }
    
    /**
     * IP 주소 익명화
     */
    private String anonymizeIpAddress(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + "***";
        }
        return "***.***.***.**";
    }
    
    /**
     * 이름 익명화
     */
    private String anonymizeName(String name) {
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        } else if (name.length() == 3) {
            return name.charAt(0) + "*" + name.charAt(2);
        } else if (name.length() >= 4) {
            return name.charAt(0) + "**" + name.charAt(name.length() - 1);
        }
        return "***";
    }
    
    /**
     * 이름 익명화 필요 여부 결정
     */
    private boolean shouldAnonymizeNames(String text) {
        // 로그 타입이나 내용에 따라 이름 익명화 여부 결정
        // 예: USER_ACTION 로그에서는 이름을 익명화하지만, SYSTEM 로그에서는 하지 않음
        String lowerText = text.toLowerCase();
        
        // 사용자 관련 키워드가 있는 경우에만 이름 익명화
        return lowerText.contains("user") || 
               lowerText.contains("login") || 
               lowerText.contains("register") || 
               lowerText.contains("profile") ||
               lowerText.contains("사용자") ||
               lowerText.contains("로그인") ||
               lowerText.contains("회원");
    }
    
    /**
     * 익명화 품질 검증
     */
    public boolean validateAnonymization(String original, String anonymized) {
        try {
            if (original == null || anonymized == null) {
                return false;
            }
            
            // 원본에 있던 민감한 정보가 익명화된 텍스트에 그대로 남아있는지 확인
            boolean hasPhone = PHONE_PATTERN.matcher(anonymized).find();
            boolean hasEmail = EMAIL_PATTERN.matcher(anonymized).find();
            boolean hasCard = CARD_PATTERN.matcher(anonymized).find();
            boolean hasSSN = SSN_PATTERN.matcher(anonymized).find();
            
            if (hasPhone || hasEmail || hasCard || hasSSN) {
                log.warn("Anonymization validation failed - sensitive data still present");
                return false;
            }
            
            // 기본적인 텍스트 구조는 유지되어야 함
            if (anonymized.length() < original.length() * 0.3) {
                log.warn("Anonymization validation failed - text too heavily modified");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error during anonymization validation", e);
            return false;
        }
    }
    
    /**
     * 익명화 통계 정보
     */
    public AnonymizationStats getAnonymizationStats(String text) {
        if (text == null) {
            return new AnonymizationStats(0, 0, 0, 0, 0);
        }
        
        int phoneCount = countMatches(PHONE_PATTERN, text);
        int emailCount = countMatches(EMAIL_PATTERN, text);
        int cardCount = countMatches(CARD_PATTERN, text);
        int ssnCount = countMatches(SSN_PATTERN, text);
        int ipCount = countMatches(IP_PATTERN, text);
        
        return new AnonymizationStats(phoneCount, emailCount, cardCount, ssnCount, ipCount);
    }
    
    /**
     * 패턴 매칭 횟수 계산
     */
    private int countMatches(Pattern pattern, String text) {
        return (int) pattern.matcher(text).results().count();
    }
    
    /**
     * 익명화 통계 클래스
     */
    public static class AnonymizationStats {
        private final int phoneNumbers;
        private final int emails;
        private final int cardNumbers;
        private final int socialSecurityNumbers;
        private final int ipAddresses;
        
        public AnonymizationStats(int phoneNumbers, int emails, int cardNumbers, 
                                int socialSecurityNumbers, int ipAddresses) {
            this.phoneNumbers = phoneNumbers;
            this.emails = emails;
            this.cardNumbers = cardNumbers;
            this.socialSecurityNumbers = socialSecurityNumbers;
            this.ipAddresses = ipAddresses;
        }
        
        public int getPhoneNumbers() { return phoneNumbers; }
        public int getEmails() { return emails; }
        public int getCardNumbers() { return cardNumbers; }
        public int getSocialSecurityNumbers() { return socialSecurityNumbers; }
        public int getIpAddresses() { return ipAddresses; }
        
        public int getTotalSensitiveItems() {
            return phoneNumbers + emails + cardNumbers + socialSecurityNumbers + ipAddresses;
        }
        
        @Override
        public String toString() {
            return String.format("AnonymizationStats[phones=%d, emails=%d, cards=%d, ssn=%d, ips=%d, total=%d]",
                phoneNumbers, emails, cardNumbers, socialSecurityNumbers, ipAddresses, getTotalSensitiveItems());
        }
    }
    
    /**
     * 익명화 예외 클래스
     */
    public static class AnonymizationException extends RuntimeException {
        public AnonymizationException(String message) {
            super(message);
        }
        
        public AnonymizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}