package com.sauron.detector.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 메시지 컨텍스트 DTO
 * 공고 감지를 위한 메시지 정보를 담습니다.
 */
@Data
@Builder
public class MessageContext {
    
    private String messageId;
    private String content;
    private String userId;
    private String username;
    private String chatRoomId;
    private String chatRoomTitle;
    private Instant timestamp;
    private String messageType;
    private Map<String, Object> metadata;
    
    /**
     * 메시지 길이 반환
     */
    public int getContentLength() {
        return content != null ? content.length() : 0;
    }
    
    /**
     * 특정 키워드 포함 여부 확인
     */
    public boolean containsKeyword(String keyword) {
        if (content == null || keyword == null) return false;
        return content.toLowerCase().contains(keyword.toLowerCase());
    }
    
    /**
     * 사용자 정보 유효성 확인
     */
    public boolean hasValidUser() {
        return userId != null && !userId.trim().isEmpty();
    }
    
    /**
     * 채팅방 정보 유효성 확인
     */
    public boolean hasValidChatRoom() {
        return chatRoomId != null && !chatRoomId.trim().isEmpty();
    }
    
    /**
     * 메타데이터에서 값 추출
     */
    public Object getMetadataValue(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    /**
     * 메타데이터에서 문자열 값 추출
     */
    public String getMetadataString(String key) {
        Object value = getMetadataValue(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 간단한 텍스트 표현
     */
    public String toSimpleString() {
        return String.format("MessageContext[id=%s, user=%s, chatRoom=%s, length=%d]",
            messageId, userId, chatRoomId, getContentLength());
    }
}