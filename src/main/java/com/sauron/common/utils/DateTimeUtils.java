package com.sauron.common.utils;

import lombok.experimental.UtilityClass;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

/**
 * 날짜/시간 관련 유틸리티 클래스
 * 다양한 시간대 처리 및 변환 기능을 제공합니다.
 */
@UtilityClass
public class DateTimeUtils {
    
    // 한국 시간대
    public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    
    // 일본 시간대
    public static final ZoneId TOKYO_ZONE = ZoneId.of("Asia/Tokyo");
    
    // UTC 시간대
    public static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    
    // 포맷터들
    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter ISO_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    public static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter DISPLAY_DATETIME = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
    public static final DateTimeFormatter KOREAN_DATE = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
    public static final DateTimeFormatter KOREAN_DATETIME = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분");
    
    /**
     * 현재 UTC 시각 반환
     */
    public static Instant now() {
        return Instant.now();
    }
    
    /**
     * 현재 한국 시각 반환
     */
    public static LocalDateTime nowInSeoul() {
        return LocalDateTime.now(SEOUL_ZONE);
    }
    
    /**
     * 현재 일본 시각 반환
     */
    public static LocalDateTime nowInTokyo() {
        return LocalDateTime.now(TOKYO_ZONE);
    }
    
    /**
     * UTC Instant를 한국 시간으로 변환
     */
    public static LocalDateTime toSeoulTime(Instant instant) {
        return instant.atZone(SEOUL_ZONE).toLocalDateTime();
    }
    
    /**
     * UTC Instant를 일본 시간으로 변환
     */
    public static LocalDateTime toTokyoTime(Instant instant) {
        return instant.atZone(TOKYO_ZONE).toLocalDateTime();
    }
    
    /**
     * 한국 시간을 UTC Instant로 변환
     */
    public static Instant fromSeoulTime(LocalDateTime localDateTime) {
        return localDateTime.atZone(SEOUL_ZONE).toInstant();
    }
    
    /**
     * 일본 시간을 UTC Instant로 변환
     */
    public static Instant fromTokyoTime(LocalDateTime localDateTime) {
        return localDateTime.atZone(TOKYO_ZONE).toInstant();
    }
    
    /**
     * ISO 8601 형식 문자열을 Instant로 파싱
     */
    public static Instant parseInstant(String dateTimeString) {
        try {
            return Instant.parse(dateTimeString);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date time format: " + dateTimeString, e);
        }
    }
    
    /**
     * Instant를 ISO 8601 형식 문자열로 포맷
     */
    public static String formatInstant(Instant instant) {
        return instant.toString();
    }
    
    /**
     * 한국 시간을 한국어 형식으로 포맷
     */
    public static String formatKoreanDate(LocalDateTime dateTime) {
        return dateTime.format(KOREAN_DATE);
    }
    
    /**
     * 한국 시간을 한국어 날짜시간 형식으로 포맷
     */
    public static String formatKoreanDateTime(LocalDateTime dateTime) {
        return dateTime.format(KOREAN_DATETIME);
    }
    
    /**
     * 두 시각 사이의 차이를 밀리초로 계산
     */
    public static long getDifferenceInMillis(Instant start, Instant end) {
        return ChronoUnit.MILLIS.between(start, end);
    }
    
    /**
     * 두 시각 사이의 차이를 초로 계산
     */
    public static long getDifferenceInSeconds(Instant start, Instant end) {
        return ChronoUnit.SECONDS.between(start, end);
    }
    
    /**
     * 두 시각 사이의 차이를 분으로 계산
     */
    public static long getDifferenceInMinutes(Instant start, Instant end) {
        return ChronoUnit.MINUTES.between(start, end);
    }
    
    /**
     * 두 시각 사이의 차이를 시간으로 계산
     */
    public static long getDifferenceInHours(Instant start, Instant end) {
        return ChronoUnit.HOURS.between(start, end);
    }
    
    /**
     * 두 시각 사이의 차이를 일로 계산
     */
    public static long getDifferenceInDays(Instant start, Instant end) {
        return ChronoUnit.DAYS.between(start, end);
    }
    
    /**
     * 특정 시각에서 밀리초를 더한 시각 반환
     */
    public static Instant addMillis(Instant instant, long millis) {
        return instant.plusMillis(millis);
    }
    
    /**
     * 특정 시각에서 초를 더한 시각 반환
     */
    public static Instant addSeconds(Instant instant, long seconds) {
        return instant.plusSeconds(seconds);
    }
    
    /**
     * 특정 시각에서 분을 더한 시각 반환
     */
    public static Instant addMinutes(Instant instant, long minutes) {
        return instant.plus(minutes, ChronoUnit.MINUTES);
    }
    
    /**
     * 특정 시각에서 시간을 더한 시각 반환
     */
    public static Instant addHours(Instant instant, long hours) {
        return instant.plus(hours, ChronoUnit.HOURS);
    }
    
    /**
     * 특정 시각에서 일을 더한 시각 반환
     */
    public static Instant addDays(Instant instant, long days) {
        return instant.plus(days, ChronoUnit.DAYS);
    }
    
    /**
     * 시각이 특정 범위 내에 있는지 확인
     */
    public static boolean isBetween(Instant target, Instant start, Instant end) {
        return !target.isBefore(start) && !target.isAfter(end);
    }
    
    /**
     * 오늘 날짜의 시작 시각 (00:00:00) 반환 (한국 시간 기준)
     */
    public static Instant getStartOfDay() {
        return LocalDate.now(SEOUL_ZONE)
                .atStartOfDay(SEOUL_ZONE)
                .toInstant();
    }
    
    /**
     * 오늘 날짜의 끝 시각 (23:59:59.999) 반환 (한국 시간 기준)
     */
    public static Instant getEndOfDay() {
        return LocalDate.now(SEOUL_ZONE)
                .atTime(LocalTime.MAX)
                .atZone(SEOUL_ZONE)
                .toInstant();
    }
    
    /**
     * 특정 날짜의 시작 시각 반환 (한국 시간 기준)
     */
    public static Instant getStartOfDay(LocalDate date) {
        return date.atStartOfDay(SEOUL_ZONE).toInstant();
    }
    
    /**
     * 특정 날짜의 끝 시각 반환 (한국 시간 기준)
     */
    public static Instant getEndOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX).atZone(SEOUL_ZONE).toInstant();
    }
    
    /**
     * Unix 타임스탬프를 Instant로 변환
     */
    public static Instant fromUnixTimestamp(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds);
    }
    
    /**
     * Instant를 Unix 타임스탬프로 변환
     */
    public static long toUnixTimestamp(Instant instant) {
        return instant.getEpochSecond();
    }
    
    /**
     * 시간대 이름이 유효한지 확인
     */
    public static boolean isValidTimeZone(String timeZoneId) {
        try {
            ZoneId.of(timeZoneId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 시스템 기본 시간대 반환
     */
    public static ZoneId getSystemTimeZone() {
        return ZoneId.systemDefault();
    }
}