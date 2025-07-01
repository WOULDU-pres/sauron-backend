package com.sauron.listener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 텔레그램 테스트 알림 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "텔레그램 테스트 알림 요청")
public class TelegramTestRequest {
    
    /**
     * 텔레그램 채팅 ID
     */
    @NotBlank(message = "채팅 ID는 필수입니다")
    @Pattern(regexp = "^-?\\d+$", message = "올바른 텔레그램 채팅 ID 형식이 아닙니다")
    @Schema(description = "텔레그램 채팅 ID", example = "12345678", required = true)
    private String chatId;
    
    /**
     * 테스트 메시지 (선택사항)
     */
    @Schema(description = "사용자 정의 테스트 메시지", example = "시스템 테스트")
    private String customMessage;
}