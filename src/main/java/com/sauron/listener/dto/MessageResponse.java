package com.sauron.listener.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 메시지 수신 API 응답 DTO
 * 처리 결과와 메타데이터를 포함합니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    
    /**
     * 처리된 메시지의 서버 측 ID
     */
    private Long id;
    
    /**
     * 원본 메시지 ID (요청에서 전달된 값)
     */
    private String messageId;
    
    /**
     * 처리 상태 (RECEIVED, QUEUED, PROCESSING, FAILED)
     */
    private String status;
    
    /**
     * 큐 전송 여부
     */
    private boolean queuedForAnalysis;
    
    /**
     * 서버 처리 시각
     */
    private Instant processedAt;
    
    /**
     * 응답 메시지
     */
    private String message;
    
    /**
     * 성공적인 응답을 생성하는 정적 팩토리 메서드
     */
    public static MessageResponse success(Long id, String messageId, boolean queued) {
        return new MessageResponse(
            id,
            messageId,
            "RECEIVED",
            queued,
            Instant.now(),
            "Message received and processed successfully"
        );
    }
    
    /**
     * 실패 응답을 생성하는 정적 팩토리 메서드
     */
    public static MessageResponse failure(String messageId, String errorMessage) {
        return new MessageResponse(
            null,
            messageId,
            "FAILED",
            false,
            Instant.now(),
            errorMessage
        );
    }
} 