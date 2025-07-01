package com.sauron.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 토큰 생성, 검증, 파싱을 담당하는 유틸리티 클래스
 */
@Component
@Slf4j
public class JwtTokenProvider {
    
    private final SecretKey secretKey;
    private final long tokenValidityInHours;
    
    public JwtTokenProvider(
            @Value("${app.jwt.secret:sauron-default-secret-key-for-development-only-change-in-production}") String secret,
            @Value("${app.jwt.expiration-hours:24}") long tokenValidityInHours) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.tokenValidityInHours = tokenValidityInHours;
    }
    
    /**
     * 디바이스 ID를 기반으로 JWT 토큰을 생성합니다.
     */
    public String generateToken(String deviceId) {
        Instant now = Instant.now();
        Instant expiration = now.plus(tokenValidityInHours, ChronoUnit.HOURS);
        
        return Jwts.builder()
                .subject(deviceId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claim("deviceId", deviceId)
                .claim("type", "mobile")
                .signWith(secretKey)
                .compact();
    }
    
    /**
     * JWT 토큰의 유효성을 검증합니다.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT token security error: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token illegal argument: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * JWT 토큰에서 디바이스 ID를 추출합니다.
     */
    public String getDeviceIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.get("deviceId", String.class);
    }
    
    /**
     * JWT 토큰에서 Subject(사용자 식별자)를 추출합니다.
     */
    public String getSubjectFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.getSubject();
    }
    
    /**
     * JWT 토큰의 만료 시간을 확인합니다.
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
} 