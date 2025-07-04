package com.sauron.common.utils;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 문자열 관련 유틸리티 클래스
 * 다양한 문자열 처리 및 변환 기능을 제공합니다.
 */
@UtilityClass
public class StringUtils {
    
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-zA-Z0-9]");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    
    /**
     * 문자열이 null이거나 빈 문자열인지 확인
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
    
    /**
     * 문자열이 null이거나 빈 문자열이 아닌지 확인
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
    
    /**
     * 문자열이 null이거나 공백만 있는지 확인
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 문자열이 null이 아니고 공백이 아닌지 확인
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }
    
    /**
     * null 문자열을 빈 문자열로 변환
     */
    public static String nullToEmpty(String str) {
        return str == null ? "" : str;
    }
    
    /**
     * 빈 문자열을 null로 변환
     */
    public static String emptyToNull(String str) {
        return isEmpty(str) ? null : str;
    }
    
    /**
     * 문자열 앞뒤 공백 제거 (null 안전)
     */
    public static String trim(String str) {
        return str == null ? null : str.trim();
    }
    
    /**
     * 문자열 앞뒤 공백 제거 후 빈 문자열이면 null 반환
     */
    public static String trimToNull(String str) {
        String trimmed = trim(str);
        return isEmpty(trimmed) ? null : trimmed;
    }
    
    /**
     * 문자열 앞뒤 공백 제거 후 null이면 빈 문자열 반환
     */
    public static String trimToEmpty(String str) {
        return str == null ? "" : str.trim();
    }
    
    /**
     * 문자열 길이 반환 (null 안전)
     */
    public static int length(String str) {
        return str == null ? 0 : str.length();
    }
    
    /**
     * 문자열을 특정 길이로 자르기
     */
    public static String truncate(String str, int maxLength) {
        if (str == null || maxLength < 0) {
            return str;
        }
        return str.length() <= maxLength ? str : str.substring(0, maxLength);
    }
    
    /**
     * 문자열을 특정 길이로 자르고 생략 표시 추가
     */
    public static String truncateWithEllipsis(String str, int maxLength) {
        if (str == null || maxLength < 3) {
            return str;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * 첫 글자를 대문자로 변환
     */
    public static String capitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    
    /**
     * 첫 글자를 소문자로 변환
     */
    public static String uncapitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }
    
    /**
     * 카멜케이스를 스네이크케이스로 변환
     */
    public static String camelToSnake(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
    
    /**
     * 스네이크케이스를 카멜케이스로 변환
     */
    public static String snakeToCamel(String str) {
        if (isEmpty(str)) {
            return str;
        }
        String[] parts = str.split("_");
        StringBuilder result = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            result.append(capitalize(parts[i].toLowerCase()));
        }
        return result.toString();
    }
    
    /**
     * 문자열을 반복
     */
    public static String repeat(String str, int count) {
        if (str == null || count < 0) {
            return str;
        }
        return str.repeat(count);
    }
    
    /**
     * 문자열을 왼쪽으로 패딩
     */
    public static String leftPad(String str, int size, char padChar) {
        if (str == null) {
            return null;
        }
        int pads = size - str.length();
        if (pads <= 0) {
            return str;
        }
        return repeat(String.valueOf(padChar), pads) + str;
    }
    
    /**
     * 문자열을 오른쪽으로 패딩
     */
    public static String rightPad(String str, int size, char padChar) {
        if (str == null) {
            return null;
        }
        int pads = size - str.length();
        if (pads <= 0) {
            return str;
        }
        return str + repeat(String.valueOf(padChar), pads);
    }
    
    /**
     * 문자열 배열을 구분자로 결합
     */
    public static String join(String[] array, String delimiter) {
        if (array == null) {
            return null;
        }
        return String.join(delimiter, array);
    }
    
    /**
     * 컬렉션을 구분자로 결합
     */
    public static String join(Collection<String> collection, String delimiter) {
        if (collection == null) {
            return null;
        }
        return String.join(delimiter, collection);
    }
    
    /**
     * 문자열을 구분자로 분할
     */
    public static String[] split(String str, String delimiter) {
        if (str == null) {
            return null;
        }
        return str.split(Pattern.quote(delimiter));
    }
    
    /**
     * 한글 초성 추출
     */
    public static String extractKoreanInitials(String str) {
        if (isEmpty(str)) {
            return str;
        }
        
        StringBuilder result = new StringBuilder();
        for (char ch : str.toCharArray()) {
            if (ch >= '가' && ch <= '힣') {
                int unicode = ch - '가';
                int initialIndex = unicode / (21 * 28);
                char initial = (char) ('ㄱ' + initialIndex);
                result.append(initial);
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }
    
    /**
     * 문자열을 MD5 해시로 변환
     */
    public static String toMD5(String str) {
        if (str == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(str.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    /**
     * 문자열을 SHA-256 해시로 변환
     */
    public static String toSHA256(String str) {
        if (str == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(str.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * 바이트 배열을 16진수 문자열로 변환
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * 랜덤 문자열 생성
     */
    public static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        return result.toString();
    }
    
    /**
     * 문자열에서 HTML 태그 제거
     */
    public static String stripHtml(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.replaceAll("<[^>]*>", "");
    }
    
    /**
     * 문자열 정규화 (NFD 형식으로 분해 후 결합 문자 제거)
     */
    public static String normalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return Normalizer.normalize(str, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
    
    /**
     * 문자열에서 여러 공백을 하나로 압축
     */
    public static String normalizeWhitespace(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return WHITESPACE_PATTERN.matcher(str.trim()).replaceAll(" ");
    }
    
    /**
     * 문자열에서 영숫자가 아닌 문자 제거
     */
    public static String keepAlphanumericOnly(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return NON_ALPHANUMERIC_PATTERN.matcher(str).replaceAll("");
    }
    
    /**
     * 이메일 주소 마스킹 (예: t***@example.com)
     */
    public static String maskEmail(String email) {
        if (isEmpty(email) || !EMAIL_PATTERN.matcher(email).matches()) {
            return email;
        }
        
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domainPart = parts[1];
        
        if (localPart.length() <= 1) {
            return email;
        }
        
        String masked = localPart.charAt(0) + "*".repeat(localPart.length() - 1);
        return masked + "@" + domainPart;
    }
    
    /**
     * 전화번호 마스킹 (예: 010-****-5678)
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (isEmpty(phoneNumber)) {
            return phoneNumber;
        }
        
        String digitsOnly = phoneNumber.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 4) {
            return phoneNumber;
        }
        
        String prefix = digitsOnly.substring(0, 3);
        String suffix = digitsOnly.substring(digitsOnly.length() - 4);
        String middle = "*".repeat(digitsOnly.length() - 7);
        
        return prefix + "-" + middle + "-" + suffix;
    }
}