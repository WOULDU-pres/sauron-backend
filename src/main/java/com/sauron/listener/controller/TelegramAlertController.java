package com.sauron.listener.controller;

import com.sauron.common.external.telegram.TelegramNotificationService;
import com.sauron.listener.dto.TelegramTestRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 텔레그램 알림 관리 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
@Validated
@Tag(name = "Telegram Alert API", description = "텔레그램 알림 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class TelegramAlertController {
    
    private final TelegramNotificationService telegramNotificationService;
    
    /**
     * 텔레그램 서비스 상태 확인
     */
    @GetMapping("/health")
    @Operation(summary = "텔레그램 서비스 상태 확인", description = "텔레그램 봇 연결 상태 및 서비스 가용성 확인")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        try {
            CompletableFuture<Boolean> healthCheck = telegramNotificationService.checkServiceHealth();
            Boolean isHealthy = healthCheck.get();
            
            Map<String, Object> response = Map.of(
                "status", isHealthy ? "healthy" : "unhealthy",
                "service", "telegram",
                "timestamp", java.time.Instant.now().toString()
            );
            
            if (isHealthy) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(503).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error checking Telegram service health", e);
            
            Map<String, Object> errorResponse = Map.of(
                "status", "error",
                "service", "telegram",
                "error", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            );
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 테스트 알림 전송
     */
    @PostMapping("/test")
    @Operation(summary = "테스트 알림 전송", description = "지정된 채팅방으로 테스트 알림 메시지 전송")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> sendTestAlert(@Valid @RequestBody TelegramTestRequest request) {
        try {
            log.info("Sending test alert to chat: {}", request.getChatId());
            
            CompletableFuture<Boolean> sendResult = telegramNotificationService.sendTestAlert(request.getChatId());
            Boolean success = sendResult.get();
            
            Map<String, Object> response = Map.of(
                "success", success,
                "chatId", request.getChatId(),
                "message", success ? "테스트 알림이 성공적으로 전송되었습니다." : "테스트 알림 전송에 실패했습니다.",
                "timestamp", java.time.Instant.now().toString()
            );
            
            if (success) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error sending test alert to chat: {}", request.getChatId(), e);
            
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "chatId", request.getChatId(),
                "error", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            );
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 알림 설정 정보 조회
     */
    @GetMapping("/settings")
    @Operation(summary = "알림 설정 조회", description = "현재 텔레그램 알림 설정 정보 조회")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getAlertSettings() {
        // TODO: 실제 설정 관리 엔티티 구현 후 연동
        Map<String, Object> settings = Map.of(
            "enabled", true,
            "supportedTypes", java.util.List.of("spam", "advertisement", "abuse", "inappropriate", "conflict"),
            "minConfidence", 0.7,
            "throttleMinutes", 5,
            "maxAlertsPerHour", 100
        );
        
        return ResponseEntity.ok(settings);
    }
}