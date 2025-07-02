package com.sauron.logging.repository;

import com.sauron.logging.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 감사 로그 저장소
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    /**
     * 날짜 범위로 로그 조회
     */
    Page<AuditLog> findByCreatedAtBetween(Instant startDate, Instant endDate, Pageable pageable);
    
    /**
     * 로그 타입별 조회
     */
    Page<AuditLog> findByLogType(String logType, Pageable pageable);
    
    /**
     * 소스별 조회
     */
    Page<AuditLog> findBySource(String source, Pageable pageable);
    
    /**
     * 심각도별 조회
     */
    Page<AuditLog> findBySeverity(String severity, Pageable pageable);
    
    /**
     * 특정 날짜 이후 로그 수 조회
     */
    long countByCreatedAtAfter(Instant date);
    
    /**
     * 심각도별 로그 수 조회
     */
    long countBySeverity(String severity);
    
    /**
     * 오래된 로그 삭제
     */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    int deleteByCreatedAtBefore(@Param("cutoffDate") Instant cutoffDate);
    
    /**
     * 상위 소스 목록 조회
     */
    @Query("SELECT a.source FROM AuditLog a GROUP BY a.source ORDER BY COUNT(a) DESC")
    List<String> findTopSourcesByCount(@Param("limit") int limit);
    
    /**
     * 상위 로그 타입 목록 조회
     */
    @Query("SELECT a.logType FROM AuditLog a GROUP BY a.logType ORDER BY COUNT(a) DESC")
    List<String> findTopLogTypesByCount(@Param("limit") int limit);
    
    /**
     * 랜덤 샘플 조회 (무결성 검사용)
     */
    @Query(value = "SELECT * FROM audit_logs ORDER BY RANDOM() LIMIT :sampleSize", nativeQuery = true)
    List<AuditLog> findRandomSample(@Param("sampleSize") int sampleSize);
    
    /**
     * 암호화된 로그 수 조회
     */
    long countByEncrypted(boolean encrypted);
    
    /**
     * 익명화된 로그 수 조회
     */
    long countByAnonymized(boolean anonymized);
    
    /**
     * 유효성 검사 상태별 로그 수 조회
     */
    long countByValidationStatus(String validationStatus);
    
    /**
     * 특정 기간 내 오류 로그 조회
     */
    @Query("SELECT a FROM AuditLog a WHERE a.severity IN ('ERROR', 'FATAL') AND a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    List<AuditLog> findErrorLogsByDateRange(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
    
    /**
     * 키워드로 메시지 검색
     */
    @Query("SELECT a FROM AuditLog a WHERE LOWER(a.message) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.details) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<AuditLog> findByMessageKeyword(@Param("keyword") String keyword, Pageable pageable);
}