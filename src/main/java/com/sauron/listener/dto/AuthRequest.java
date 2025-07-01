package com.sauron.listener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모바일 클라이언트 인증 요청 DTO
 * 디바이스 등록 및 JWT 토큰 발급을 위한 요청 정보
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    
    /**
     * 디바이스 고유 식별자
     */
    @NotBlank(message = "Device ID cannot be blank")
    @Size(max = 100, message = "Device ID cannot exceed 100 characters")
    private String deviceId;
    
    /**
     * 디바이스 이름 (선택사항)
     */
    @Size(max = 200, message = "Device name cannot exceed 200 characters")
    private String deviceName;
    
    /**
     * 애플리케이션 버전
     */
    @Size(max = 50, message = "App version cannot exceed 50 characters")
    private String appVersion;
} 