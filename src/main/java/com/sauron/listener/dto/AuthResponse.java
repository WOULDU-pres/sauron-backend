package com.sauron.listener.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 모바일 클라이언트 인증 응답 DTO
 * JWT 토큰과 인증 정보를 포함합니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    
    /**
     * JWT 액세스 토큰
     */
    private String accessToken;
    
    /**
     * 토큰 타입 (Bearer)
     */
    private String tokenType = "Bearer";
    
    /**
     * 토큰 만료 시간 (Unix timestamp)
     */
    private Instant expiresAt;
    
    /**
     * 디바이스 ID
     */
    private String deviceId;
    
    /**
     * 성공적인 인증 응답 생성
     */
    public static AuthResponse success(String accessToken, Instant expiresAt, String deviceId) {
        return new AuthResponse(accessToken, "Bearer", expiresAt, deviceId);
    }
} 