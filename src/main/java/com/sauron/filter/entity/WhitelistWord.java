package com.sauron.filter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 화이트리스트 단어 엔티티
 * 정상 메시지로 분류되어야 하는 단어나 패턴을 저장합니다.
 */
@Entity
@Table(name = "whitelist_words", indexes = {
    @Index(name = "idx_whitelist_words_word", columnList = "word"),
    @Index(name = "idx_whitelist_words_type", columnList = "word_type"),
    @Index(name = "idx_whitelist_words_active", columnList = "is_active"),
    @Index(name = "idx_whitelist_words_priority", columnList = "priority")
}, uniqueConstraints = {
    @UniqueConstraint(name = "unique_whitelist_word_type", columnNames = {"word", "word_type"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhitelistWord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 화이트리스트에 포함될 단어 또는 패턴
     */
    @Column(name = "word", nullable = false, length = 255)
    private String word;
    
    /**
     * 단어 타입 (GENERAL, SENDER, CONTENT_PATTERN)
     */
    @Column(name = "word_type", nullable = false, length = 50)
    @Builder.Default
    private String wordType = "GENERAL";
    
    /**
     * 단어에 대한 설명
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * 정규표현식 여부
     */
    @Column(name = "is_regex", nullable = false)
    @Builder.Default
    private Boolean isRegex = false;
    
    /**
     * 대소문자 구분 여부
     */
    @Column(name = "is_case_sensitive", nullable = false)
    @Builder.Default
    private Boolean isCaseSensitive = false;
    
    /**
     * 우선순위 (높을수록 먼저 적용)
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;
    
    /**
     * 생성자 정보
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    /**
     * 생성 시각
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    /**
     * 마지막 업데이트 시각
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    /**
     * 활성화 상태
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    /**
     * 엔티티 업데이트 전 자동 호출
     */
    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }
    
    /**
     * 단어 타입 열거형
     */
    public enum WordType {
        GENERAL("일반 단어"),
        SENDER("발신자 관련"),
        CONTENT_PATTERN("내용 패턴");
        
        private final String description;
        
        WordType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 단어가 주어진 텍스트와 매치되는지 확인
     */
    public boolean matches(String text) {
        if (text == null || !isActive) {
            return false;
        }
        
        String targetText = isCaseSensitive ? text : text.toLowerCase();
        String matchWord = isCaseSensitive ? word : word.toLowerCase();
        
        if (isRegex) {
            try {
                return targetText.matches(matchWord);
            } catch (Exception e) {
                // 정규표현식 오류 시 일반 문자열 매칭으로 fallback
                return targetText.contains(matchWord);
            }
        } else {
            return targetText.contains(matchWord);
        }
    }
    
    /**
     * 활성화된 화이트리스트인지 확인
     */
    public boolean isActiveWhitelist() {
        return isActive != null && isActive;
    }
} 