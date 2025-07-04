package com.sauron.alert.controller;

import com.sauron.alert.service.AlertStreamService;
import com.sauron.common.auth.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 실시간 알림 스트림 API 컨트롤러
 * Admin Dashboard에서 SSE를 통해 실시간 알림을 수신합니다.
 */
@RestController
@RequestMapping("/v1/stream")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Alert Stream", description = "Real-time alert streaming APIs")
public class AlertStreamController {
    
    private final AlertStreamService alertStreamService;
    private final AuthenticationService authenticationService;
    
    /**
     * 실시간 알림 스트림 구독
     */
    @GetMapping(value = "/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to real-time alerts", 
               description = "Establish SSE connection for real-time alert notifications")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "SSE stream established"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required"),
        @ApiResponse(responseCode = "429", description = "Too many connections"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "bearerAuth")
    public SseEmitter subscribeToAlerts(
        Authentication authentication,
        @Parameter(description = "Connection timeout in milliseconds", example = "300000")
        @RequestParam(value = "timeout", defaultValue = "300000") Long timeout,
        @Parameter(description = "Client identifier for debugging", example = "admin-dashboard-1")
        @RequestParam(value = "clientId", required = false) String clientId
    ) {
        // 인증 및 권한 확인
        if (!authenticationService.isAdmin(authentication)) {
            throw new SecurityException("Admin privileges required for alert streaming");
        }
        
        String adminId = authentication.getName();
        String actualClientId = clientId != null ? clientId : "dashboard-" + adminId;
        
        log.info("Creating SSE connection for admin: {}, clientId: {}, timeout: {}ms", 
                adminId, actualClientId, timeout);
        
        return alertStreamService.createConnection(adminId, actualClientId, timeout);
    }
    
    /**
     * 연결 상태 조회
     */
    @GetMapping("/status")
    @Operation(summary = "Get stream connection status", 
               description = "Get current SSE connection status and statistics")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> getConnectionStatus(Authentication authentication) {
        if (!authenticationService.isAdmin(authentication)) {
            throw new SecurityException("Admin privileges required");
        }
        
        Map<String, Object> status = alertStreamService.getConnectionStatus();
        return ResponseEntity.ok(status);
    }
    
    /**
     * 테스트 알림 전송
     */
    @PostMapping("/test")
    @Operation(summary = "Send test alert", 
               description = "Send a test alert to all connected admin clients")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<String> sendTestAlert(
        Authentication authentication,
        @Parameter(description = "Test message content")
        @RequestParam(value = "message", defaultValue = "Test alert from admin dashboard") String message
    ) {
        if (!authenticationService.isAdmin(authentication)) {
            throw new SecurityException("Admin privileges required");
        }
        
        String adminId = authentication.getName();
        log.info("Admin {} sending test alert: {}", adminId, message);
        
        int sentCount = alertStreamService.sendTestAlert(adminId, message);
        
        return ResponseEntity.ok(String.format("Test alert sent to %d connected clients", sentCount));
    }
    
    /**
     * 특정 연결 종료
     */
    @DeleteMapping("/connections/{clientId}")
    @Operation(summary = "Close specific connection", 
               description = "Forcefully close a specific SSE connection")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<String> closeConnection(
        Authentication authentication,
        @PathVariable String clientId
    ) {
        if (!authenticationService.isAdmin(authentication)) {
            throw new SecurityException("Admin privileges required");
        }
        
        boolean closed = alertStreamService.closeConnection(clientId);
        
        if (closed) {
            return ResponseEntity.ok("Connection closed successfully");
        } else {
            return ResponseEntity.notFound().build();
        }
    }
} 