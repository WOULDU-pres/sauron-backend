package com.sauron.listener.repository;

import com.sauron.listener.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 메시지 데이터 액세스 레포지토리
 * 메시지 엔티티에 대한 CRUD 및 복잡한 쿼리 작업을 제공합니다.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    
    /**
     * 메시지 ID로 메시지 조회
     */
    Optional<Message> findByMessageId(String messageId);
    
    /**
     * 메시지 ID 존재 여부 확인
     */
    boolean existsByMessageId(String messageId);
    
    /**
     * 콘텐츠 해시로 중복 메시지 확인
     */
    boolean existsByContentHash(String contentHash);
    
    /**
     * 디바이스별 메시지 조회 (페이징)
     */
    Page<Message> findByDeviceIdOrderByCreatedAtDesc(String deviceId, Pageable pageable);
    
    /**
     * 채팅방별 메시지 조회 (페이징)
     */
    Page<Message> findByChatRoomIdOrderByCreatedAtDesc(String chatRoomId, Pageable pageable);
    
    /**
     * 감지 상태별 메시지 조회
     */
    List<Message> findByDetectionStatus(String detectionStatus);
    
    /**
     * 감지 타입별 메시지 조회 (페이징)
     */
    Page<Message> findByDetectedTypeOrderByCreatedAtDesc(String detectedType, Pageable pageable);
    
    /**
     * 특정 기간 내 메시지 조회
     */
    @Query("SELECT m FROM Message m WHERE m.createdAt BETWEEN :startTime AND :endTime ORDER BY m.createdAt DESC")
    List<Message> findByCreatedAtBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
    
    /**
     * 이상 메시지 조회 (정상/unknown 제외)
     */
    @Query("SELECT m FROM Message m WHERE m.detectedType IS NOT NULL " +
           "AND m.detectedType NOT IN ('normal', 'unknown') " +
           "ORDER BY m.createdAt DESC")
    Page<Message> findAbnormalMessages(Pageable pageable);
    
    /**
     * 특정 디바이스의 최근 메시지 조회
     */
    @Query("SELECT m FROM Message m WHERE m.deviceId = :deviceId " +
           "AND m.createdAt >= :since ORDER BY m.createdAt DESC")
    List<Message> findRecentMessagesByDevice(@Param("deviceId") String deviceId, @Param("since") Instant since);
    
    /**
     * 처리 대기 중인 메시지 조회 (오래된 순)
     */
    @Query("SELECT m FROM Message m WHERE m.detectionStatus = 'PENDING' " +
           "ORDER BY m.createdAt ASC")
    List<Message> findPendingMessagesOrderByCreatedAt();
    
    /**
     * 분석 실패한 메시지 조회
     */
    @Query("SELECT m FROM Message m WHERE m.detectionStatus = 'FAILED' " +
           "ORDER BY m.createdAt DESC")
    List<Message> findFailedAnalysisMessages();
    
    /**
     * 특정 신뢰도 이상의 이상 메시지 조회
     */
    @Query("SELECT m FROM Message m WHERE m.detectedType IS NOT NULL " +
           "AND m.detectedType NOT IN ('normal', 'unknown') " +
           "AND m.confidenceScore >= :minConfidence " +
           "ORDER BY m.confidenceScore DESC, m.createdAt DESC")
    Page<Message> findHighConfidenceAbnormalMessages(@Param("minConfidence") Double minConfidence, Pageable pageable);
    
    /**
     * 디바이스별 메시지 통계 조회
     */
    @Query("SELECT m.deviceId, COUNT(m), " +
           "SUM(CASE WHEN m.detectedType NOT IN ('normal', 'unknown') THEN 1 ELSE 0 END) " +
           "FROM Message m WHERE m.createdAt >= :since " +
           "GROUP BY m.deviceId")
    List<Object[]> getDeviceMessageStatistics(@Param("since") Instant since);
    
    /**
     * 채팅방별 이상 메시지 통계 조회
     */
    @Query("SELECT m.chatRoomId, m.chatRoomTitle, COUNT(m), " +
           "AVG(CASE WHEN m.confidenceScore IS NOT NULL THEN m.confidenceScore ELSE 0 END) " +
           "FROM Message m WHERE m.detectedType IS NOT NULL " +
           "AND m.detectedType NOT IN ('normal', 'unknown') " +
           "AND m.createdAt >= :since " +
           "GROUP BY m.chatRoomId, m.chatRoomTitle " +
           "ORDER BY COUNT(m) DESC")
    List<Object[]> getChatRoomAbnormalStatistics(@Param("since") Instant since);
    
    /**
     * 메시지 상태 일괄 업데이트
     */
    @Modifying
    @Query("UPDATE Message m SET m.detectionStatus = :newStatus, m.updatedAt = :updatedAt " +
           "WHERE m.detectionStatus = :currentStatus")
    int updateMessageStatus(@Param("currentStatus") String currentStatus, 
                           @Param("newStatus") String newStatus, 
                           @Param("updatedAt") Instant updatedAt);
    
    /**
     * 분석 결과 업데이트
     */
    @Modifying
    @Query("UPDATE Message m SET m.detectedType = :detectedType, " +
           "m.confidenceScore = :confidenceScore, " +
           "m.detectionStatus = :detectionStatus, " +
           "m.analyzedAt = :analyzedAt, " +
           "m.updatedAt = :updatedAt " +
           "WHERE m.messageId = :messageId")
    int updateAnalysisResult(@Param("messageId") String messageId,
                            @Param("detectedType") String detectedType,
                            @Param("confidenceScore") Double confidenceScore,
                            @Param("detectionStatus") String detectionStatus,
                            @Param("analyzedAt") Instant analyzedAt,
                            @Param("updatedAt") Instant updatedAt);
    
    /**
     * 오래된 메시지 삭제 (데이터 정리용)
     */
    @Modifying
    @Query("DELETE FROM Message m WHERE m.createdAt < :cutoffTime")
    int deleteOldMessages(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * 특정 기간의 메시지 개수 조회
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.createdAt BETWEEN :startTime AND :endTime")
    long countMessagesBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
    
    /**
     * 특정 기간의 이상 메시지 개수 조회
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.createdAt BETWEEN :startTime AND :endTime " +
           "AND m.detectedType IS NOT NULL AND m.detectedType NOT IN ('normal', 'unknown')")
    long countAbnormalMessagesBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
} 