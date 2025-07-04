package com.sauron.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauron.alert.entity.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 실시간 알림 스트림 서비스
 * SSE 연결 관리 및 알림 브로드캐스팅을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertStreamService {
    
    private final ObjectMapper objectMapper;
    
    // SSE 연결 관리
    private final Map<String, SseEmitter> connections = new ConcurrentHashMap<>();
    private final Map<String, String> clientToAdmin = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    
    // 연결 제한
    private static final int MAX_CONNECTIONS = 10;
    private static final long DEFAULT_TIMEOUT = 5 * 60 * 1000L; // 5분
    
    /**
     * SSE 연결 생성
     */
    public SseEmitter createConnection(String adminId, String clientId, Long timeout) {
        // 연결 수 제한 확인
        if (connectionCount.get() >= MAX_CONNECTIONS) {
            throw new RuntimeException("Maximum number of connections reached");
        }
        
        // 기존 연결이 있으면 정리
        if (connections.containsKey(clientId)) {
            closeConnection(clientId);
        }
        
        // 새 SSE 연결 생성
        SseEmitter emitter = new SseEmitter(timeout != null ? timeout : DEFAULT_TIMEOUT);
        
        // 연결 저장
        connections.put(clientId, emitter);
        clientToAdmin.put(clientId, adminId);
        connectionCount.incrementAndGet();
        
        log.info("New SSE connection created - Admin: {}, Client: {}, Total connections: {}", 
                adminId, clientId, connectionCount.get());
        
        // 이벤트 핸들러 설정
        emitter.onCompletion(() -> {
            cleanupConnection(clientId);
            log.info("SSE connection completed - Client: {}", clientId);
        });
        
        emitter.onTimeout(() -> {
            cleanupConnection(clientId);
            log.info("SSE connection timed out - Client: {}", clientId);
        });
        
        emitter.onError((e) -> {
            cleanupConnection(clientId);
            log.warn("SSE connection error - Client: {}, Error: {}", clientId, e.getMessage());
        });
        
        // 연결 성공 메시지 전송
        try {
            sendToClient(clientId, "connection", Map.of(
                "status", "connected",
                "clientId", clientId,
                "timestamp", Instant.now().toString()
            ));
        } catch (Exception e) {
            log.error("Failed to send connection confirmation - Client: {}", clientId, e);
            cleanupConnection(clientId);
            throw new RuntimeException("Failed to establish SSE connection");
        }
        
        return emitter;
    }
    
    /**
     * 특정 클라이언트에게 메시지 전송
     */
    public void sendToClient(String clientId, String eventType, Object data) {
        SseEmitter emitter = connections.get(clientId);
        if (emitter == null) {
            log.debug("No connection found for client: {}", clientId);
            return;
        }
        
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                .id(Instant.now().toEpochMilli() + "-" + clientId)
                .name(eventType)
                .data(objectMapper.writeValueAsString(data));
            
            emitter.send(event);
            log.debug("Message sent to client {} - Event: {}", clientId, eventType);
            
        } catch (IOException e) {
            log.warn("Failed to send message to client: {} - {}", clientId, e.getMessage());
            cleanupConnection(clientId);
        } catch (Exception e) {
            log.error("Unexpected error sending message to client: {}", clientId, e);
            cleanupConnection(clientId);
        }
    }
    
    /**
     * 모든 연결된 클라이언트에게 알림 브로드캐스트
     */
    public void broadcastAlert(Alert alert) {
        if (connections.isEmpty()) {
            log.debug("No connected clients to broadcast alert: {}", alert.getId());
            return;
        }
        
        Map<String, Object> alertData = Map.of(
            "id", alert.getId() != null ? alert.getId().toString() : "unknown",
            "type", alert.getAlertType() != null ? alert.getAlertType() : "UNKNOWN",
            "severity", "MEDIUM", // Default severity since not in entity
            "title", "Alert Notification", // Default title
            "message", alert.getContentEncrypted() != null ? "[Alert Content]" : "New alert detected",
            "timestamp", alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : Instant.now().toString(),
            "channel", alert.getChannel() != null ? alert.getChannel() : "system",
            "status", alert.getStatus() != null ? alert.getStatus() : "UNKNOWN"
        );
        
        int sentCount = 0;
        for (String clientId : connections.keySet()) {
            try {
                sendToClient(clientId, "alert", alertData);
                sentCount++;
            } catch (Exception e) {
                log.warn("Failed to broadcast alert to client: {}", clientId, e);
            }
        }
        
        log.info("Alert broadcasted to {} clients - Alert ID: {}", sentCount, alert.getId());
    }
    
    /**
     * 테스트 알림 전송
     */
    public int sendTestAlert(String adminId, String message) {
        Map<String, Object> testData = Map.of(
            "id", "test-" + Instant.now().toEpochMilli(),
            "type", "test",
            "severity", "INFO",
            "title", "Test Alert",
            "message", message,
            "timestamp", Instant.now().toString(),
            "chatRoom", "System Test",
            "sender", adminId
        );
        
        int sentCount = 0;
        for (String clientId : connections.keySet()) {
            try {
                sendToClient(clientId, "alert", testData);
                sentCount++;
            } catch (Exception e) {
                log.warn("Failed to send test alert to client: {}", clientId, e);
            }
        }
        
        log.info("Test alert sent to {} clients by admin: {}", sentCount, adminId);
        return sentCount;
    }
    
    /**
     * 특정 연결 종료
     */
    public boolean closeConnection(String clientId) {
        SseEmitter emitter = connections.get(clientId);
        if (emitter == null) {
            return false;
        }
        
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("Error completing SSE emitter for client: {}", clientId, e);
        }
        
        cleanupConnection(clientId);
        return true;
    }
    
    /**
     * 연결 정리
     */
    private void cleanupConnection(String clientId) {
        connections.remove(clientId);
        String adminId = clientToAdmin.remove(clientId);
        connectionCount.decrementAndGet();
        
        log.debug("Connection cleaned up - Client: {}, Admin: {}, Remaining: {}", 
                clientId, adminId, connectionCount.get());
    }
    
    /**
     * 연결 상태 조회
     */
    public Map<String, Object> getConnectionStatus() {
        return Map.of(
            "totalConnections", connectionCount.get(),
            "maxConnections", MAX_CONNECTIONS,
            "connectedClients", connections.keySet(),
            "adminConnections", clientToAdmin,
            "defaultTimeout", DEFAULT_TIMEOUT,
            "timestamp", Instant.now().toString()
        );
    }
    
    /**
     * 모든 연결 종료
     */
    public void closeAllConnections() {
        log.info("Closing all SSE connections - Total: {}", connectionCount.get());
        
        for (Map.Entry<String, SseEmitter> entry : connections.entrySet()) {
            try {
                entry.getValue().complete();
            } catch (Exception e) {
                log.warn("Error closing connection: {}", entry.getKey(), e);
            }
        }
        
        connections.clear();
        clientToAdmin.clear();
        connectionCount.set(0);
        
        log.info("All SSE connections closed");
    }
    
    /**
     * 서비스 상태 확인
     */
    public boolean isHealthy() {
        try {
            // 기본적인 상태 확인
            int connections = connectionCount.get();
            boolean withinLimits = connections <= MAX_CONNECTIONS;
            
            log.debug("AlertStreamService health check - Connections: {}/{}, Healthy: {}", 
                    connections, MAX_CONNECTIONS, withinLimits);
            
            return withinLimits;
        } catch (Exception e) {
            log.error("AlertStreamService health check failed", e);
            return false;
        }
    }
} 