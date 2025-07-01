package com.sauron.filter.repository;

import com.sauron.filter.entity.ExceptionWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 예외 단어 레포지토리
 * 예외 단어에 대한 CRUD 및 복합 쿼리 작업을 제공합니다.
 */
@Repository
public interface ExceptionWordRepository extends JpaRepository<ExceptionWord, Long> {
    
    /**
     * 단어, 타입, 적용 범위로 예외 단어 조회
     */
    Optional<ExceptionWord> findByWordAndWordTypeAndExceptionScope(String word, String wordType, String exceptionScope);
    
    /**
     * 단어, 타입, 적용 범위 조합 존재 여부 확인
     */
    boolean existsByWordAndWordTypeAndExceptionScope(String word, String wordType, String exceptionScope);
    
    /**
     * 특정 예외 범위의 활성화된 예외 단어 조회 (우선순위 순)
     */
    @Query("SELECT e FROM ExceptionWord e WHERE e.exceptionScope = :scope AND e.isActive = true ORDER BY e.priority DESC, e.createdAt DESC")
    List<ExceptionWord> findActiveByScopeOrderByPriority(@Param("scope") String scope);
    
    /**
     * 모든 활성화된 예외 단어 조회 (우선순위 순)
     */
    @Query("SELECT e FROM ExceptionWord e WHERE e.isActive = true ORDER BY e.priority DESC, e.exceptionScope, e.wordType, e.createdAt DESC")
    List<ExceptionWord> findAllActiveOrderByPriority();
    
    /**
     * 특정 감지 타입에 적용 가능한 예외 단어 조회 (ALL 포함)
     */
    @Query("SELECT e FROM ExceptionWord e WHERE (e.exceptionScope = 'ALL' OR e.exceptionScope = :detectedType) AND e.isActive = true ORDER BY e.priority DESC")
    List<ExceptionWord> findApplicableToDetectedType(@Param("detectedType") String detectedType);
    
    /**
     * 단어로 검색 (부분 일치, 대소문자 무시)
     */
    @Query("SELECT e FROM ExceptionWord e WHERE LOWER(e.word) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY e.priority DESC")
    Page<ExceptionWord> findByWordContainingIgnoreCase(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 단어 타입별 예외 단어 조회 (페이징)
     */
    Page<ExceptionWord> findByWordTypeOrderByPriorityDescCreatedAtDesc(String wordType, Pageable pageable);
    
    /**
     * 예외 범위별 예외 단어 조회 (페이징)
     */
    Page<ExceptionWord> findByExceptionScopeOrderByPriorityDescCreatedAtDesc(String exceptionScope, Pageable pageable);
    
    /**
     * 활성화 상태별 예외 단어 조회 (페이징)
     */
    Page<ExceptionWord> findByIsActiveOrderByPriorityDescCreatedAtDesc(Boolean isActive, Pageable pageable);
    
    /**
     * 특정 기간에 생성된 예외 단어 조회
     */
    @Query("SELECT e FROM ExceptionWord e WHERE e.createdAt BETWEEN :startDate AND :endDate ORDER BY e.createdAt DESC")
    List<ExceptionWord> findByCreatedAtBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
    
    /**
     * 생성자별 예외 단어 조회
     */
    List<ExceptionWord> findByCreatedByOrderByCreatedAtDesc(String createdBy);
    
    /**
     * 정규표현식 예외 단어만 조회
     */
    @Query("SELECT e FROM ExceptionWord e WHERE e.isRegex = true AND e.isActive = true ORDER BY e.priority DESC")
    List<ExceptionWord> findActiveRegexWords();
    
    /**
     * 일반 문자열 예외 단어만 조회
     */
    @Query("SELECT e FROM ExceptionWord e WHERE e.isRegex = false AND e.isActive = true ORDER BY e.priority DESC")
    List<ExceptionWord> findActiveNonRegexWords();
    
    /**
     * 최근 업데이트된 예외 단어 조회
     */
    @Query("SELECT e FROM ExceptionWord e WHERE e.updatedAt >= :since ORDER BY e.updatedAt DESC")
    List<ExceptionWord> findRecentlyUpdated(@Param("since") Instant since);
    
    /**
     * 우선순위가 특정 값 이상인 예외 단어 조회
     */
    @Query("SELECT e FROM ExceptionWord e WHERE e.priority >= :minPriority AND e.isActive = true ORDER BY e.priority DESC")
    List<ExceptionWord> findByPriorityGreaterThanEqualAndActive(@Param("minPriority") Integer minPriority);
    
    /**
     * 예외 범위별 통계 조회
     */
    @Query("SELECT e.exceptionScope, COUNT(e), " +
           "SUM(CASE WHEN e.isActive = true THEN 1 ELSE 0 END), " +
           "AVG(e.priority) " +
           "FROM ExceptionWord e " +
           "GROUP BY e.exceptionScope")
    List<Object[]> getExceptionScopeStatistics();
    
    /**
     * 단어 타입별 통계 조회
     */
    @Query("SELECT e.wordType, COUNT(e), " +
           "SUM(CASE WHEN e.isActive = true THEN 1 ELSE 0 END), " +
           "AVG(e.priority) " +
           "FROM ExceptionWord e " +
           "GROUP BY e.wordType")
    List<Object[]> getWordTypeStatistics();
    
    /**
     * 생성자별 통계 조회
     */
    @Query("SELECT e.createdBy, COUNT(e), " +
           "SUM(CASE WHEN e.isActive = true THEN 1 ELSE 0 END) " +
           "FROM ExceptionWord e " +
           "WHERE e.createdBy IS NOT NULL " +
           "GROUP BY e.createdBy")
    List<Object[]> getCreatorStatistics();
    
    /**
     * 복합 검색 (단어, 타입, 범위, 활성화 상태)
     */
    @Query("SELECT e FROM ExceptionWord e WHERE " +
           "(:word IS NULL OR LOWER(e.word) LIKE LOWER(CONCAT('%', :word, '%'))) AND " +
           "(:wordType IS NULL OR e.wordType = :wordType) AND " +
           "(:exceptionScope IS NULL OR e.exceptionScope = :exceptionScope) AND " +
           "(:isActive IS NULL OR e.isActive = :isActive) " +
           "ORDER BY e.priority DESC, e.createdAt DESC")
    Page<ExceptionWord> searchExceptionWords(@Param("word") String word,
                                           @Param("wordType") String wordType,
                                           @Param("exceptionScope") String exceptionScope,
                                           @Param("isActive") Boolean isActive,
                                           Pageable pageable);
    
    /**
     * 활성화된 예외 단어 개수 조회
     */
    @Query("SELECT COUNT(e) FROM ExceptionWord e WHERE e.isActive = true")
    long countActiveWords();
    
    /**
     * 예외 범위별 활성화된 예외 단어 개수 조회
     */
    @Query("SELECT COUNT(e) FROM ExceptionWord e WHERE e.exceptionScope = :scope AND e.isActive = true")
    long countActiveWordsByScope(@Param("scope") String scope);
    
    /**
     * 단어 타입별 활성화된 예외 단어 개수 조회
     */
    @Query("SELECT COUNT(e) FROM ExceptionWord e WHERE e.wordType = :wordType AND e.isActive = true")
    long countActiveWordsByType(@Param("wordType") String wordType);
} 