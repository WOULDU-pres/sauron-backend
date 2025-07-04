package com.sauron.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StringUtils 테스트 클래스
 */
@DisplayName("문자열 유틸리티 테스트")
class StringUtilsTest {
    
    @Test
    @DisplayName("문자열이 비어있는지 확인")
    void testIsEmpty() {
        assertThat(StringUtils.isEmpty(null)).isTrue();
        assertThat(StringUtils.isEmpty("")).isTrue();
        assertThat(StringUtils.isEmpty("   ")).isFalse();
        assertThat(StringUtils.isEmpty("Hello")).isFalse();
    }
    
    @Test
    @DisplayName("문자열이 공백인지 확인")
    void testIsBlank() {
        assertThat(StringUtils.isBlank(null)).isTrue();
        assertThat(StringUtils.isBlank("")).isTrue();
        assertThat(StringUtils.isBlank("   ")).isTrue();
        assertThat(StringUtils.isBlank("Hello")).isFalse();
        assertThat(StringUtils.isBlank("  Hello  ")).isFalse();
    }
    
    @Test
    @DisplayName("null을 빈 문자열로 변환")
    void testNullToEmpty() {
        assertThat(StringUtils.nullToEmpty(null)).isEqualTo("");
        assertThat(StringUtils.nullToEmpty("")).isEqualTo("");
        assertThat(StringUtils.nullToEmpty("Hello")).isEqualTo("Hello");
    }
    
    @Test
    @DisplayName("빈 문자열을 null로 변환")
    void testEmptyToNull() {
        assertThat(StringUtils.emptyToNull(null)).isNull();
        assertThat(StringUtils.emptyToNull("")).isNull();
        assertThat(StringUtils.emptyToNull("Hello")).isEqualTo("Hello");
    }
    
    @Test
    @DisplayName("문자열 앞뒤 공백 제거")
    void testTrim() {
        assertThat(StringUtils.trim(null)).isNull();
        assertThat(StringUtils.trim("")).isEqualTo("");
        assertThat(StringUtils.trim("  Hello  ")).isEqualTo("Hello");
        assertThat(StringUtils.trim("   ")).isEqualTo("");
    }
    
    @Test
    @DisplayName("문자열 길이 반환")
    void testLength() {
        assertThat(StringUtils.length(null)).isEqualTo(0);
        assertThat(StringUtils.length("")).isEqualTo(0);
        assertThat(StringUtils.length("Hello")).isEqualTo(5);
    }
    
    @Test
    @DisplayName("문자열 자르기")
    void testTruncate() {
        assertThat(StringUtils.truncate(null, 5)).isNull();
        assertThat(StringUtils.truncate("Hello", -1)).isEqualTo("Hello");
        assertThat(StringUtils.truncate("Hello World", 5)).isEqualTo("Hello");
        assertThat(StringUtils.truncate("Hi", 5)).isEqualTo("Hi");
    }
    
    @Test
    @DisplayName("문자열 자르기 (생략 표시 포함)")
    void testTruncateWithEllipsis() {
        assertThat(StringUtils.truncateWithEllipsis(null, 5)).isNull();
        assertThat(StringUtils.truncateWithEllipsis("Hello", 2)).isEqualTo("Hello");
        assertThat(StringUtils.truncateWithEllipsis("Hello World", 8)).isEqualTo("Hello...");
        assertThat(StringUtils.truncateWithEllipsis("Hi", 5)).isEqualTo("Hi");
    }
    
    @Test
    @DisplayName("첫 글자 대문자로 변환")
    void testCapitalize() {
        assertThat(StringUtils.capitalize(null)).isNull();
        assertThat(StringUtils.capitalize("")).isEqualTo("");
        assertThat(StringUtils.capitalize("hello")).isEqualTo("Hello");
        assertThat(StringUtils.capitalize("HELLO")).isEqualTo("HELLO");
        assertThat(StringUtils.capitalize("hELLO")).isEqualTo("HELLO");
    }
    
    @Test
    @DisplayName("첫 글자 소문자로 변환")
    void testUncapitalize() {
        assertThat(StringUtils.uncapitalize(null)).isNull();
        assertThat(StringUtils.uncapitalize("")).isEqualTo("");
        assertThat(StringUtils.uncapitalize("Hello")).isEqualTo("hello");
        assertThat(StringUtils.uncapitalize("HELLO")).isEqualTo("hELLO");
    }
    
    @ParameterizedTest
    @MethodSource("provideCamelToSnakeTestCases")
    @DisplayName("카멜케이스를 스네이크케이스로 변환")
    void testCamelToSnake(String input, String expected) {
        assertThat(StringUtils.camelToSnake(input)).isEqualTo(expected);
    }
    
    static Stream<Arguments> provideCamelToSnakeTestCases() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", ""),
            Arguments.of("hello", "hello"),
            Arguments.of("helloWorld", "hello_world"),
            Arguments.of("HelloWorld", "hello_world"),
            Arguments.of("XMLHttpRequest", "x_m_l_http_request")
        );
    }
    
    @ParameterizedTest
    @MethodSource("provideSnakeToCamelTestCases")
    @DisplayName("스네이크케이스를 카멜케이스로 변환")
    void testSnakeToCamel(String input, String expected) {
        assertThat(StringUtils.snakeToCamel(input)).isEqualTo(expected);
    }
    
    static Stream<Arguments> provideSnakeToCamelTestCases() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", ""),
            Arguments.of("hello", "hello"),
            Arguments.of("hello_world", "helloWorld"),
            Arguments.of("HELLO_WORLD", "helloWorld"),
            Arguments.of("xml_http_request", "xmlHttpRequest")
        );
    }
    
    @Test
    @DisplayName("문자열 반복")
    void testRepeat() {
        assertThat(StringUtils.repeat(null, 3)).isNull();
        assertThat(StringUtils.repeat("Hello", -1)).isEqualTo("Hello");
        assertThat(StringUtils.repeat("A", 3)).isEqualTo("AAA");
        assertThat(StringUtils.repeat("Hi", 0)).isEqualTo("");
    }
    
    @Test
    @DisplayName("문자열 왼쪽 패딩")
    void testLeftPad() {
        assertThat(StringUtils.leftPad(null, 5, '0')).isNull();
        assertThat(StringUtils.leftPad("123", 5, '0')).isEqualTo("00123");
        assertThat(StringUtils.leftPad("123456", 5, '0')).isEqualTo("123456");
    }
    
    @Test
    @DisplayName("문자열 오른쪽 패딩")
    void testRightPad() {
        assertThat(StringUtils.rightPad(null, 5, '0')).isNull();
        assertThat(StringUtils.rightPad("123", 5, '0')).isEqualTo("12300");
        assertThat(StringUtils.rightPad("123456", 5, '0')).isEqualTo("123456");
    }
    
    @Test
    @DisplayName("문자열 배열 결합")
    void testJoinArray() {
        assertThat(StringUtils.join(null, ",")).isNull();
        assertThat(StringUtils.join(new String[]{"a", "b", "c"}, ",")).isEqualTo("a,b,c");
        assertThat(StringUtils.join(new String[]{"hello"}, ",")).isEqualTo("hello");
        assertThat(StringUtils.join(new String[]{}, ",")).isEqualTo("");
    }
    
    @Test
    @DisplayName("컬렉션 결합")
    void testJoinCollection() {
        assertThat(StringUtils.join(null, ",")).isNull();
        assertThat(StringUtils.join(Arrays.asList("a", "b", "c"), ",")).isEqualTo("a,b,c");
        assertThat(StringUtils.join(Arrays.asList("hello"), ",")).isEqualTo("hello");
        assertThat(StringUtils.join(Arrays.asList(), ",")).isEqualTo("");
    }
    
    @Test
    @DisplayName("문자열 분할")
    void testSplit() {
        assertThat(StringUtils.split(null, ",")).isNull();
        assertThat(StringUtils.split("a,b,c", ",")).containsExactly("a", "b", "c");
        assertThat(StringUtils.split("hello", ",")).containsExactly("hello");
        assertThat(StringUtils.split("a|b|c", "|")).containsExactly("a", "b", "c");
    }
    
    @Test
    @DisplayName("한글 초성 추출")
    void testExtractKoreanInitials() {
        assertThat(StringUtils.extractKoreanInitials(null)).isNull();
        assertThat(StringUtils.extractKoreanInitials("")).isEqualTo("");
        assertThat(StringUtils.extractKoreanInitials("안녕하세요")).isEqualTo("ㅇㄴㅎㅅㅇ");
        assertThat(StringUtils.extractKoreanInitials("Hello 안녕")).isEqualTo("Hello ㅇㄴ");
    }
    
    @Test
    @DisplayName("MD5 해시 변환")
    void testToMD5() {
        assertThat(StringUtils.toMD5(null)).isNull();
        assertThat(StringUtils.toMD5("hello")).isEqualTo("5d41402abc4b2a76b9719d911017c592");
        assertThat(StringUtils.toMD5("")).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
    }
    
    @Test
    @DisplayName("SHA-256 해시 변환")
    void testToSHA256() {
        assertThat(StringUtils.toSHA256(null)).isNull();
        assertThat(StringUtils.toSHA256("hello"))
            .isEqualTo("2cf24dba4f21d4288094e9b2c6e88442d0d4851a8dd34ec2cb2174e2e0bd3bb9");
        assertThat(StringUtils.toSHA256(""))
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
    
    @Test
    @DisplayName("랜덤 문자열 생성")
    void testGenerateRandomString() {
        String randomString = StringUtils.generateRandomString(10);
        assertThat(randomString).hasSize(10);
        assertThat(randomString).matches("[a-zA-Z0-9]+");
        
        // 두 번 생성한 결과가 다른지 확인 (매우 낮은 확률로 같을 수 있음)
        String anotherRandomString = StringUtils.generateRandomString(10);
        assertThat(randomString).isNotEqualTo(anotherRandomString);
    }
    
    @Test
    @DisplayName("HTML 태그 제거")
    void testStripHtml() {
        assertThat(StringUtils.stripHtml(null)).isNull();
        assertThat(StringUtils.stripHtml("")).isEqualTo("");
        assertThat(StringUtils.stripHtml("Hello World")).isEqualTo("Hello World");
        assertThat(StringUtils.stripHtml("<p>Hello <b>World</b></p>")).isEqualTo("Hello World");
        assertThat(StringUtils.stripHtml("<div><span>Test</span></div>")).isEqualTo("Test");
    }
    
    @Test
    @DisplayName("공백 정규화")
    void testNormalizeWhitespace() {
        assertThat(StringUtils.normalizeWhitespace(null)).isNull();
        assertThat(StringUtils.normalizeWhitespace("")).isEqualTo("");
        assertThat(StringUtils.normalizeWhitespace("  Hello    World  ")).isEqualTo("Hello World");
        assertThat(StringUtils.normalizeWhitespace("Hello\t\nWorld")).isEqualTo("Hello World");
    }
    
    @Test
    @DisplayName("영숫자만 유지")
    void testKeepAlphanumericOnly() {
        assertThat(StringUtils.keepAlphanumericOnly(null)).isNull();
        assertThat(StringUtils.keepAlphanumericOnly("")).isEqualTo("");
        assertThat(StringUtils.keepAlphanumericOnly("Hello123")).isEqualTo("Hello123");
        assertThat(StringUtils.keepAlphanumericOnly("Hello-World_123!")).isEqualTo("HelloWorld123");
    }
    
    @Test
    @DisplayName("이메일 마스킹")
    void testMaskEmail() {
        assertThat(StringUtils.maskEmail(null)).isNull();
        assertThat(StringUtils.maskEmail("")).isEqualTo("");
        assertThat(StringUtils.maskEmail("invalid-email")).isEqualTo("invalid-email");
        assertThat(StringUtils.maskEmail("a@example.com")).isEqualTo("a@example.com");
        assertThat(StringUtils.maskEmail("test@example.com")).isEqualTo("t***@example.com");
        assertThat(StringUtils.maskEmail("user.name@domain.co.kr")).isEqualTo("u********@domain.co.kr");
    }
    
    @Test
    @DisplayName("전화번호 마스킹")
    void testMaskPhoneNumber() {
        assertThat(StringUtils.maskPhoneNumber(null)).isNull();
        assertThat(StringUtils.maskPhoneNumber("")).isEqualTo("");
        assertThat(StringUtils.maskPhoneNumber("123")).isEqualTo("123");
        assertThat(StringUtils.maskPhoneNumber("01012345678")).isEqualTo("010-***-5678");
        assertThat(StringUtils.maskPhoneNumber("010-1234-5678")).isEqualTo("010-***-5678");
        assertThat(StringUtils.maskPhoneNumber("+82-10-1234-5678")).isEqualTo("821-****-5678");
    }
}