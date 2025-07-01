package com.sauron.filter.repository;

import com.sauron.filter.entity.WhitelistWord;
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
 * 화이트리스트 단어 레포지토리
 * 화이트리스트 단어에 대한 CRUD 및 복합 쿼리 작업을 제공합니다.
 */
@Repository
public interface WhitelistWordRepository extends JpaRepository<WhitelistWord, Long> {
    
    /**
     * 단어와 타입으로 화이트리스트 조회
     */
    Optional<WhitelistWord> findByWordAndWordType(String word, String wordType);
    
    /**
     * 단어와 타입 조합 존재 여부 확인
     */
    boolean existsByWordAndWordType(String word, String wordType);
    
    /**
     * 특정 단어 타입의 활성화된 화이트리스트 조회 (우선순위 순)
     */
    @Query("SELECT w FROM WhitelistWord w WHERE w.wordType = :wordType AND w.isActive = true ORDER BY w.priority DESC, w.createdAt DESC")
    List<WhitelistWord> findActiveByWordTypeOrderByPriority(@Param("wordType") String wordType);
    
    /**
     * 모든 활성화된 화이트리스트 조회 (우선순위 순)
     */
    @Query("SELECT w FROM WhitelistWord w WHERE w.isActive = true ORDER BY w.priority DESC, w.wordType, w.createdAt DESC")
    List<WhitelistWord> findAllActiveOrderByPriority();
    
    /**
     * 단어로 검색 (부분 일치, 대소문자 무시)
     */
    @Query("SELECT w FROM WhitelistWord w WHERE LOWER(w.word) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY w.priority DESC")
    Page<WhitelistWord> findByWordContainingIgnoreCase(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 단어 타입별 화이트리스트 조회 (페이징)
     */
    Page<WhitelistWord> findByWordTypeOrderByPriorityDescCreatedAtDesc(String wordType, Pageable pageable);
    
    /**
     * 활성화 상태별 화이트리스트 조회 (페이징)
     */
    Page<WhitelistWord> findByIsActiveOrderByPriorityDescCreatedAtDesc(Boolean isActive, Pageable pageable);
    
    /**
     * 특정 기간에 생성된 화이트리스트 조회
     */
    @Query("SELECT w FROM WhitelistWord w WHERE w.createdAt BETWEEN :startDate AND :endDate ORDER BY w.createdAt DESC")
    List<WhitelistWord> findByCreatedAtBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
    
    /**
     * 생성자별 화이트리스트 조회
     */
    List<WhitelistWord> findByCreatedByOrderByCreatedAtDesc(String createdBy);
    
    /**
     * 정규표현식 화이트리스트만 조회
     */
    @Query("SELECT w FROM WhitelistWord w WHERE w.isRegex = true AND w.isActive = true ORDER BY w.priority DESC")
    List<WhitelistWord> findActiveRegexWords();
    
    /**
     * 일반 문자열 화이트리스트만 조회
     */
    @Query("SELECT w FROM WhitelistWord w WHERE w.isRegex = false AND w.isActive = true ORDER BY w.priority DESC")
    List<WhitelistWord> findActiveNonRegexWords();
    
    /**
     * 최근 업데이트된 화이트리스트 조회
     */
    @Query("SELECT w FROM WhitelistWord w WHERE w.updatedAt >= :since ORDER BY w.updatedAt DESC")
    List<WhitelistWord> findRecentlyUpdated(@Param("since") Instant since);
    
    /**
     * 우선순위가 특정 값 이상인 화이트리스트 조회
     */
    @Query("SELECT w FROM WhitelistWord w WHERE w.priority >= :minPriority AND w.isActive = true ORDER BY w.priority DESC")
    List<WhitelistWord> findByPriorityGreaterThanEqualAndActive(@Param("minPriority") Integer minPriority);
    
    /**
     * 단어 타입별 통계 조회
     */
    @Query("SELECT w.wordType, COUNT(w), " +
           "SUM(CASE WHEN w.isActive = true THEN 1 ELSE 0 END), " +
           "AVG(w.priority) " +
           "FROM WhitelistWord w " +
           "GROUP BY w.wordType")
    List<Object[]> getWordTypeStatistics();
    
    /**
     * 생성자별 통계 조회
     */
    @Query("SELECT w.createdBy, COUNT(w), " +
           "SUM(CASE WHEN w.isActive = true THEN 1 ELSE 0 END) " +
           "FROM WhitelistWord w " +
           "WHERE w.createdBy IS NOT NULL " +
           "GROUP BY w.createdBy")
    List<Object[]> getCreatorStatistics();
    
    /**
     * 복합 검색 (단어, 타입, 활성화 상태)
     */
    @Query("SELECT w FROM WhitelistWord w WHERE " +
           "(:word IS NULL OR LOWER(w.word) LIKE LOWER(CONCAT('%', :word, '%'))) AND " +
           "(:wordType IS NULL OR w.wordType = :wordType) AND " +
           "(:isActive IS NULL OR w.isActive = :isActive) " +
           "ORDER BY w.priority DESC, w.createdAt DESC")
    Page<WhitelistWord> searchWhitelistWords(@Param("word") String word,
                                            @Param("wordType") String wordType,
                                            @Param("isActive") Boolean isActive,
                                            Pageable pageable);
    
    /**
     * 활성화된 화이트리스트 개수 조회
     */
    @Query("SELECT COUNT(w) FROM WhitelistWord w WHERE w.isActive = true")
    long countActiveWords();
    
    /**
     * 단어 타입별 활성화된 화이트리스트 개수 조회
     */
    @Query("SELECT COUNT(w) FROM WhitelistWord w WHERE w.wordType = :wordType AND w.isActive = true")
    long countActiveWordsByType(@Param("wordType") String wordType);
} 