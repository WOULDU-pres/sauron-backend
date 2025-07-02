package com.sauron.detector.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 화이트리스트 엔트리 엔티티
 * 공고 감지에서 제외할 대상들을 관리합니다.
 */
@Entity
@Table(name = "whitelist_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhitelistEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String type; // USER, CHATROOM, KEYWORD
    
    @Column(name = "target_id", length = 255)
    private String targetId; // 사용자 ID 또는 채팅방 ID
    
    @Column(length = 1000)
    private String pattern; // 키워드 또는 정규식 패턴
    
    @Column(name = "is_regex_pattern")
    private boolean regexPattern; // 정규식 패턴 여부
    
    @Column(nullable = false)
    private boolean active; // 활성화 상태
    
    @Column(length = 500)
    private String description; // 설명
    
    @Column(length = 100)
    private String category; // 카테고리
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * 사용자 타입인지 확인
     */
    public boolean isUserType() {
        return "USER".equalsIgnoreCase(type);
    }
    
    /**
     * 채팅방 타입인지 확인
     */
    public boolean isChatRoomType() {
        return "CHATROOM".equalsIgnoreCase(type);
    }
    
    /**
     * 키워드 타입인지 확인
     */
    public boolean isKeywordType() {
        return "KEYWORD".equalsIgnoreCase(type);
    }
    
    /**
     * 유효한 엔트리인지 확인
     */
    public boolean isValid() {
        if (type == null || type.trim().isEmpty()) return false;
        
        switch (type.toUpperCase()) {
            case "USER":
            case "CHATROOM":
                return targetId != null && !targetId.trim().isEmpty();
            case "KEYWORD":
                return pattern != null && !pattern.trim().isEmpty();
            default:
                return false;
        }
    }
    
    /**
     * 표시용 이름 반환
     */
    public String getDisplayName() {
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }
        
        switch (type.toUpperCase()) {
            case "USER":
                return "User: " + (targetId != null ? targetId : "Unknown");
            case "CHATROOM":
                return "ChatRoom: " + (targetId != null ? targetId : "Unknown");
            case "KEYWORD":
                return "Keyword: " + (pattern != null ? pattern : "Unknown");
            default:
                return "Unknown: " + id;
        }
    }
    
    /**
     * 간단한 텍스트 표현
     */
    public String toSimpleString() {
        return String.format("WhitelistEntry[id=%d, type=%s, active=%s]",
            id, type, active);
    }
}