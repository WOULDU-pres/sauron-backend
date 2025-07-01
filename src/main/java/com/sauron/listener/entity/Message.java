package com.sauron.listener.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 메시지 엔티티
 * 카카오톡 오픈채팅에서 수집된 메시지 정보를 저장합니다.
 */
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_messages_device_id", columnList = "device_id"),
    @Index(name = "idx_messages_chat_room_id", columnList = "chat_room_id"),
    @Index(name = "idx_messages_created_at", columnList = "created_at"),
    @Index(name = "idx_messages_detection_status", columnList = "detection_status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 클라이언트에서 생성한 고유 메시지 ID
     */
    @Column(name = "message_id", nullable = false, unique = true, length = 100)
    private String messageId;
    
    /**
     * 디바이스 ID (익명화된 형태)
     */
    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;
    
    /**
     * 채팅방 ID (해시된 형태)
     */
    @Column(name = "chat_room_id", length = 100)
    private String chatRoomId;
    
    /**
     * 채팅방 제목 (익명화된 형태)
     */
    @Column(name = "chat_room_title", length = 200)
    private String chatRoomTitle;
    
    /**
     * 발신자 정보 (해시된 형태)
     */
    @Column(name = "sender_hash", length = 100)
    private String senderHash;
    
    /**
     * 메시지 내용 (암호화된 형태)
     */
    @Column(name = "content_encrypted", columnDefinition = "TEXT")
    private String contentEncrypted;
    
    /**
     * 메시지 내용 해시 (중복 검출용)
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;
    
    /**
     * 메시지 우선순위
     */
    @Column(name = "priority", length = 20)
    @Builder.Default
    private String priority = "normal";
    
    /**
     * AI 분석 결과 (정상/도배/광고/욕설/분쟁 등)
     */
    @Column(name = "detected_type", length = 50)
    private String detectedType;
    
    /**
     * AI 분석 신뢰도 (0.0 ~ 1.0)
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;
    
    /**
     * 감지 상태 (PENDING/PROCESSING/COMPLETED/FAILED)
     */
    @Column(name = "detection_status", length = 20)
    @Builder.Default
    private String detectionStatus = "PENDING";
    
    /**
     * 처리 결과 메타데이터 (JSON 형태)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    /**
     * 메시지 수신 시각
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    /**
     * AI 분석 완료 시각
     */
    @Column(name = "analyzed_at")
    private Instant analyzedAt;
    
    /**
     * 마지막 업데이트 시각
     */
    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    /**
     * 엔티티 업데이트 전 자동 호출
     */
    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }
    
    /**
     * 메시지가 이상 메시지인지 확인
     */
    public boolean isAbnormal() {
        return detectedType != null && 
               !detectedType.equalsIgnoreCase("normal") && 
               !detectedType.equalsIgnoreCase("unknown");
    }
    
    /**
     * AI 분석이 완료되었는지 확인
     */
    public boolean isAnalysisCompleted() {
        return "COMPLETED".equalsIgnoreCase(detectionStatus);
    }
    
    /**
     * AI 분석이 실패했는지 확인
     */
    public boolean isAnalysisFailed() {
        return "FAILED".equalsIgnoreCase(detectionStatus);
    }
} 