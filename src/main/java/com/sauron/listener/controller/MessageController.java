package com.sauron.listener.controller;

import com.sauron.common.queue.MessageQueueService;
import com.sauron.listener.dto.MessageRequest;
import com.sauron.listener.dto.MessageResponse;
import com.sauron.listener.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 메시지 수신 API 컨트롤러
 * React Native 앱에서 전송되는 카카오톡 알림 메시지를 처리합니다.
 */
@RestController
@RequestMapping("/v1/messages")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Messages", description = "Message reception and processing APIs")
public class MessageController {
    
    private final MessageService messageService;
    
    /**
     * 메시지 수신 및 처리
     */
    @PostMapping
    @Operation(summary = "Receive and process message", 
               description = "Process incoming KakaoTalk notification message")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Message processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<MessageResponse> receiveMessage(@Valid @RequestBody MessageRequest request) {
        log.info("Received message - ID: {}, Device: {}, ChatRoom: {}", 
                request.getMessageId(), request.getDeviceId(), request.getChatRoomTitle());
        
        MessageResponse response = messageService.processMessage(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 서비스 헬스 체크
     */
    @GetMapping("/health")
    @Operation(summary = "Service health check", description = "Check message service health status")
    public ResponseEntity<String> healthCheck() {
        boolean healthy = messageService.isHealthy();
        
        if (healthy) {
            return ResponseEntity.ok("Service is healthy");
        } else {
            return ResponseEntity.status(503).body("Service is unhealthy");
        }
    }
    
    /**
     * 큐 상태 조회
     */
    @GetMapping("/queue/status")
    @Operation(summary = "Get queue status", description = "Get current message queue status")
    public ResponseEntity<MessageQueueService.QueueStatus> getQueueStatus() {
        MessageQueueService.QueueStatus status = messageService.getQueueStatus();
        return ResponseEntity.ok(status);
    }
    
    /**
     * 큐 통계 조회 (관리용)
     */
    @GetMapping("/stats")
    @Operation(summary = "Get processing statistics", description = "Get message processing statistics")
    public ResponseEntity<String> getStats() {
        // TODO: 실제 통계 데이터 구현
        MessageQueueService.QueueStatus queueStatus = messageService.getQueueStatus();
        
        String stats = String.format(
            "Queue Status: %s\nMain Queue Size: %d\nDLQ Size: %d\nHealthy: %s",
            queueStatus.toString(),
            queueStatus.getMainQueueSize(),
            queueStatus.getDlqSize(),
            queueStatus.isHealthy() ? "Yes" : "No"
        );
        
        return ResponseEntity.ok(stats);
    }
} 