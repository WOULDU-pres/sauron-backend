package com.sauron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 표준화된 API 응답 DTO
 * 모든 성공적인 API 응답에 대해 일관된 구조를 제공합니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    
    /**
     * 성공 여부
     */
    @Builder.Default
    private boolean success = true;
    
    /**
     * 응답 데이터
     */
    private T data;
    
    /**
     * 응답 메시지
     */
    private String message;
    
    /**
     * 응답 시각
     */
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    /**
     * 요청 ID (추적용)
     */
    private String requestId;
    
    /**
     * 성공 응답 생성 헬퍼 메서드
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message("Operation completed successfully")
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 성공 응답 생성 헬퍼 메서드 (메시지 포함)
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 빈 성공 응답 생성 헬퍼 메서드
     */
    public static ApiResponse<Void> success() {
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Operation completed successfully")
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 빈 성공 응답 생성 헬퍼 메서드 (메시지 포함)
     */
    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}