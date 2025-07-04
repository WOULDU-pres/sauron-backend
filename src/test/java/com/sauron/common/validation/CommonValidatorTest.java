package com.sauron.common.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CommonValidator 테스트 클래스
 */
@DisplayName("공통 검증 유틸리티 테스트")
class CommonValidatorTest {
    
    private CommonValidator commonValidator;
    
    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        commonValidator = new CommonValidator(validator);
    }
    
    @Test
    @DisplayName("이메일 형식 검증 - 유효한 이메일")
    void testValidEmail() {
        // Given
        String validEmail = "test@example.com";
        
        // When
        boolean result = commonValidator.isValidEmail(validEmail);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "user@domain.co.kr",
        "user.name@domain.com",
        "user+tag@domain.org",
        "123@domain.net"
    })
    @DisplayName("이메일 형식 검증 - 다양한 유효한 이메일들")
    void testValidEmails(String email) {
        assertThat(commonValidator.isValidEmail(email)).isTrue();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "invalid-email",
        "@domain.com",
        "user@",
        "user@domain",
        "",
        " "
    })
    @DisplayName("이메일 형식 검증 - 무효한 이메일들")
    void testInvalidEmails(String email) {
        assertThat(commonValidator.isValidEmail(email)).isFalse();
    }
    
    @Test
    @DisplayName("이메일 형식 검증 - null 입력")
    void testNullEmail() {
        assertThat(commonValidator.isValidEmail(null)).isFalse();
    }
    
    @Test
    @DisplayName("UUID 형식 검증 - 유효한 UUID")
    void testValidUUID() {
        // Given
        String validUuid = "550e8400-e29b-41d4-a716-446655440000";
        
        // When
        boolean result = commonValidator.isValidUUID(validUuid);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "invalid-uuid",
        "550e8400-e29b-41d4-a716",
        "550e8400-e29b-41d4-a716-44665544000g",
        "",
        " "
    })
    @DisplayName("UUID 형식 검증 - 무효한 UUID들")
    void testInvalidUUIDs(String uuid) {
        assertThat(commonValidator.isValidUUID(uuid)).isFalse();
    }
    
    @Test
    @DisplayName("전화번호 형식 검증 - 유효한 전화번호")
    void testValidPhoneNumber() {
        // Given
        String validPhone = "+821012345678";
        
        // When
        boolean result = commonValidator.isValidPhone(validPhone);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "01012345678",
        "+821012345678",
        "+14155552671"
    })
    @DisplayName("전화번호 형식 검증 - 다양한 유효한 전화번호들")
    void testValidPhoneNumbers(String phone) {
        assertThat(commonValidator.isValidPhone(phone)).isTrue();
    }
    
    @Test
    @DisplayName("한글 문자열 검증")
    void testKoreanText() {
        // Given
        String koreanText = "안녕하세요 반갑습니다";
        String nonKoreanText = "Hello World 123";
        
        // When & Then
        assertThat(commonValidator.isKoreanText(koreanText)).isTrue();
        assertThat(commonValidator.isKoreanText(nonKoreanText)).isFalse();
    }
    
    @Test
    @DisplayName("영숫자 문자열 검증")
    void testAlphanumeric() {
        // Given
        String alphanumeric = "Test123";
        String nonAlphanumeric = "Test-123!";
        
        // When & Then
        assertThat(commonValidator.isAlphanumeric(alphanumeric)).isTrue();
        assertThat(commonValidator.isAlphanumeric(nonAlphanumeric)).isFalse();
    }
    
    @Test
    @DisplayName("문자열 길이 범위 검증")
    void testValidLength() {
        // Given
        String text = "Hello";
        
        // When & Then
        assertThat(commonValidator.isValidLength(text, 3, 10)).isTrue();
        assertThat(commonValidator.isValidLength(text, 6, 10)).isFalse();
        assertThat(commonValidator.isValidLength(text, 1, 4)).isFalse();
        assertThat(commonValidator.isValidLength(null, 0, 10)).isTrue();
        assertThat(commonValidator.isValidLength(null, 1, 10)).isFalse();
    }
    
    @Test
    @DisplayName("숫자 범위 검증")
    void testValidRange() {
        // When & Then
        assertThat(commonValidator.isValidRange(5, 1, 10)).isTrue();
        assertThat(commonValidator.isValidRange(0, 1, 10)).isFalse();
        assertThat(commonValidator.isValidRange(15, 1, 10)).isFalse();
        assertThat(commonValidator.isValidRange(null, 1, 10)).isFalse();
    }
    
    @Test
    @DisplayName("URL 형식 검증")
    void testValidUrl() {
        // When & Then
        assertThat(commonValidator.isValidUrl("https://www.example.com")).isTrue();
        assertThat(commonValidator.isValidUrl("http://localhost:8080")).isTrue();
        assertThat(commonValidator.isValidUrl("ftp://files.example.com")).isTrue();
        assertThat(commonValidator.isValidUrl("invalid-url")).isFalse();
        assertThat(commonValidator.isValidUrl(null)).isFalse();
        assertThat(commonValidator.isValidUrl("")).isFalse();
    }
    
    @Test
    @DisplayName("빈 문자열이 아닌지 검증")
    void testNotBlank() {
        // When & Then
        assertThat(commonValidator.isNotBlank("Hello")).isTrue();
        assertThat(commonValidator.isNotBlank("  World  ")).isTrue();
        assertThat(commonValidator.isNotBlank("")).isFalse();
        assertThat(commonValidator.isNotBlank("   ")).isFalse();
        assertThat(commonValidator.isNotBlank(null)).isFalse();
    }
    
    @Test
    @DisplayName("비밀번호 강도 검증")
    void testStrongPassword() {
        // When & Then
        assertThat(commonValidator.isStrongPassword("Password123!")).isTrue();
        assertThat(commonValidator.isStrongPassword("password123!")).isFalse(); // 대문자 없음
        assertThat(commonValidator.isStrongPassword("PASSWORD123!")).isFalse(); // 소문자 없음
        assertThat(commonValidator.isStrongPassword("Password!")).isFalse(); // 숫자 없음
        assertThat(commonValidator.isStrongPassword("Password123")).isFalse(); // 특수문자 없음
        assertThat(commonValidator.isStrongPassword("Pass1!")).isFalse(); // 8자 미만
        assertThat(commonValidator.isStrongPassword(null)).isFalse();
    }
    
    @Test
    @DisplayName("IP 주소 형식 검증")
    void testValidIpAddress() {
        // When & Then
        assertThat(commonValidator.isValidIpAddress("192.168.1.1")).isTrue();
        assertThat(commonValidator.isValidIpAddress("127.0.0.1")).isTrue();
        assertThat(commonValidator.isValidIpAddress("::1")).isTrue(); // IPv6
        assertThat(commonValidator.isValidIpAddress("invalid-ip")).isFalse();
        assertThat(commonValidator.isValidIpAddress(null)).isFalse();
    }
    
    @Test
    @DisplayName("JSON 형식 검증")
    void testValidJson() {
        // When & Then
        assertThat(commonValidator.isValidJson("{\"key\": \"value\"}")).isTrue();
        assertThat(commonValidator.isValidJson("[1, 2, 3]")).isTrue();
        assertThat(commonValidator.isValidJson("\"simple string\"")).isTrue();
        assertThat(commonValidator.isValidJson("{invalid json}")).isFalse();
        assertThat(commonValidator.isValidJson(null)).isFalse();
        assertThat(commonValidator.isValidJson("")).isFalse();
    }
}