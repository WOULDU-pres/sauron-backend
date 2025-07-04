package com.sauron.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiResponse 테스트 클래스
 */
@DisplayName("API 응답 DTO 테스트")
class ApiResponseTest {
    
    @Test
    @DisplayName("성공 응답 생성 - 데이터 포함")
    void testSuccessWithData() {
        // Given
        String testData = "test data";
        
        // When
        ApiResponse<String> response = ApiResponse.success(testData);
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(testData);
        assertThat(response.getMessage()).isEqualTo("Operation completed successfully");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getTimestamp()).isBefore(Instant.now().plusSeconds(1));
    }
    
    @Test
    @DisplayName("성공 응답 생성 - 데이터와 메시지 포함")
    void testSuccessWithDataAndMessage() {
        // Given
        String testData = "test data";
        String customMessage = "Custom success message";
        
        // When
        ApiResponse<String> response = ApiResponse.success(testData, customMessage);
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(testData);
        assertThat(response.getMessage()).isEqualTo(customMessage);
        assertThat(response.getTimestamp()).isNotNull();
    }
    
    @Test
    @DisplayName("성공 응답 생성 - 데이터 없음")
    void testSuccessWithoutData() {
        // When
        ApiResponse<Void> response = ApiResponse.success();
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("Operation completed successfully");
        assertThat(response.getTimestamp()).isNotNull();
    }
    
    @Test
    @DisplayName("성공 응답 생성 - 메시지만 포함")
    void testSuccessWithMessageOnly() {
        // Given
        String customMessage = "Custom success message";
        
        // When
        ApiResponse<Void> response = ApiResponse.success(customMessage);
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo(customMessage);
        assertThat(response.getTimestamp()).isNotNull();
    }
    
    @Test
    @DisplayName("Builder 패턴으로 응답 생성")
    void testBuilderPattern() {
        // Given
        String testData = "test data";
        String requestId = "req-123";
        Instant fixedTime = Instant.parse("2023-01-01T00:00:00Z");
        
        // When
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .data(testData)
                .message("Custom message")
                .timestamp(fixedTime)
                .requestId(requestId)
                .build();
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(testData);
        assertThat(response.getMessage()).isEqualTo("Custom message");
        assertThat(response.getTimestamp()).isEqualTo(fixedTime);
        assertThat(response.getRequestId()).isEqualTo(requestId);
    }
    
    @Test
    @DisplayName("실패 응답 생성")
    void testFailureResponse() {
        // Given
        String errorMessage = "Something went wrong";
        
        // When
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(false)
                .message(errorMessage)
                .build();
        
        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo(errorMessage);
        assertThat(response.getTimestamp()).isNotNull();
    }
    
    @Test
    @DisplayName("기본값 테스트")
    void testDefaultValues() {
        // When
        ApiResponse<String> response = ApiResponse.<String>builder().build();
        
        // Then
        assertThat(response.isSuccess()).isTrue(); // 기본값
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getTimestamp()).isNotNull(); // 기본값으로 현재 시간 설정
        assertThat(response.getRequestId()).isNull();
    }
}