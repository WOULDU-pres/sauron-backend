package com.sauron.listener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 모바일 앱에서 전송하는 메시지 요청 DTO
 * 카카오톡 알림에서 추출된 메시지 정보를 포함합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {
    
    /**
     * 메시지 고유 식별자 (모바일에서 생성)
     */
    @NotBlank(message = "Message ID cannot be blank")
    @Size(max = 100, message = "Message ID cannot exceed 100 characters")
    private String messageId;
    
    /**
     * 채팅방 제목
     */
    @NotBlank(message = "Chat room title cannot be blank")
    @Size(max = 200, message = "Chat room title cannot exceed 200 characters")
    private String chatRoomTitle;
    
    /**
     * 발신자 이름 (익명화된 해시값)
     */
    @NotBlank(message = "Sender hash cannot be blank")
    @Size(max = 64, message = "Sender hash cannot exceed 64 characters")
    private String senderHash;
    
    /**
     * 메시지 내용 (암호화된 상태)
     */
    @NotBlank(message = "Message content cannot be blank")
    @Size(max = 2000, message = "Message content cannot exceed 2000 characters")
    private String messageContent;
    
    /**
     * 메시지 수신 시각 (ISO 8601 형식)
     */
    @NotNull(message = "Received at timestamp is required")
    private Instant receivedAt;
    
    /**
     * 알림 패키지명 (카카오톡 검증용)
     */
    @NotBlank(message = "Package name cannot be blank")
    @Size(max = 100, message = "Package name cannot exceed 100 characters")
    private String packageName;
    
    /**
     * 디바이스 식별자 (익명화된 해시값)
     */
    @NotBlank(message = "Device ID cannot be blank")
    @Size(max = 64, message = "Device ID cannot exceed 64 characters")
    private String deviceId;
    
    /**
     * 메시지 우선순위 (high, normal, low)
     */
    @NotBlank(message = "Priority cannot be blank")
    private String priority;
} 