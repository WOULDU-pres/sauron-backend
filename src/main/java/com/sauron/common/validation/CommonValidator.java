package com.sauron.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 공통 검증 유틸리티 클래스
 * 다양한 계층에서 재사용 가능한 검증 로직을 제공합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommonValidator {
    
    private final Validator validator;
    
    // 정규 표현식 패턴들
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    
    private static final Pattern UUID_PATTERN = 
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", 
        Pattern.CASE_INSENSITIVE);
    
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("^\\+?[1-9]\\d{1,14}$");
    
    private static final Pattern KOREAN_PATTERN = 
        Pattern.compile("^[가-힣\\s]+$");
    
    private static final Pattern ALPHANUMERIC_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9]+$");
    
    /**
     * Bean Validation을 사용한 객체 검증
     * 
     * @param object 검증할 객체
     * @return 검증 결과 리스트
     */
    public <T> ValidationResult validateObject(T object) {
        if (object == null) {
            return ValidationResult.failure("Object cannot be null");
        }
        
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        
        if (violations.isEmpty()) {
            return ValidationResult.success();
        }
        
        List<String> errors = violations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toList());
        
        return ValidationResult.failure(errors);
    }
    
    /**
     * 이메일 형식 검증
     */
    public boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * UUID 형식 검증
     */
    public boolean isValidUUID(String uuid) {
        return uuid != null && UUID_PATTERN.matcher(uuid).matches();
    }
    
    /**
     * 전화번호 형식 검증
     */
    public boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }
    
    /**
     * 한글 문자열 검증
     */
    public boolean isKoreanText(String text) {
        return text != null && KOREAN_PATTERN.matcher(text).matches();
    }
    
    /**
     * 영숫자 문자열 검증
     */
    public boolean isAlphanumeric(String text) {
        return text != null && ALPHANUMERIC_PATTERN.matcher(text).matches();
    }
    
    /**
     * 문자열 길이 범위 검증
     */
    public boolean isValidLength(String text, int min, int max) {
        if (text == null) {
            return min == 0;
        }
        int length = text.length();
        return length >= min && length <= max;
    }
    
    /**
     * 숫자 범위 검증
     */
    public boolean isValidRange(Number value, Number min, Number max) {
        if (value == null) {
            return false;
        }
        double val = value.doubleValue();
        double minVal = min.doubleValue();
        double maxVal = max.doubleValue();
        
        return val >= minVal && val <= maxVal;
    }
    
    /**
     * URL 형식 검증
     */
    public boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        try {
            new java.net.URI(url).toURL();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 빈 문자열이 아닌지 검증 (null, 빈 문자열, 공백만 있는 문자열 모두 false)
     */
    public boolean isNotBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }
    
    /**
     * 컬렉션이 비어있지 않은지 검증
     */
    public boolean isNotEmpty(java.util.Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }
    
    /**
     * 배열이 비어있지 않은지 검증
     */
    public boolean isNotEmpty(Object[] array) {
        return array != null && array.length > 0;
    }
    
    /**
     * 비밀번호 강도 검증
     * 최소 8자, 대소문자, 숫자, 특수문자 포함
     */
    public boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> "!@#$%^&*()_+-=[]{}|;:,.<>?".indexOf(ch) >= 0);
        
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
    
    /**
     * IP 주소 형식 검증
     */
    public boolean isValidIpAddress(String ip) {
        if (ip == null) {
            return false;
        }
        
        try {
            java.net.InetAddress.getByName(ip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * JSON 형식 검증
     */
    public boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}