package com.sauron.detector.repository;

import com.sauron.detector.entity.WhitelistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 화이트리스트 저장소
 */
@Repository
public interface WhitelistRepository extends JpaRepository<WhitelistEntry, Long> {
    
    /**
     * 타입별 화이트리스트 조회
     */
    List<WhitelistEntry> findByType(String type);
    
    /**
     * 타입과 대상 ID로 화이트리스트 조회
     */
    List<WhitelistEntry> findByTypeAndTargetId(String type, String targetId);
    
    /**
     * 활성 상태별 화이트리스트 조회
     */
    List<WhitelistEntry> findByActive(boolean active);
    
    /**
     * 타입과 활성 상태로 화이트리스트 조회
     */
    List<WhitelistEntry> findByTypeAndActive(String type, boolean active);
    
    /**
     * 패턴으로 키워드 화이트리스트 조회
     */
    @Query("SELECT w FROM WhitelistEntry w WHERE w.type = 'KEYWORD' AND w.pattern LIKE %:pattern% AND w.active = true")
    List<WhitelistEntry> findActiveKeywordsByPattern(@Param("pattern") String pattern);
    
    /**
     * 사용자 ID로 활성 화이트리스트 확인
     */
    @Query("SELECT COUNT(w) > 0 FROM WhitelistEntry w WHERE w.type = 'USER' AND w.targetId = :userId AND w.active = true")
    boolean existsActiveUserWhitelist(@Param("userId") String userId);
    
    /**
     * 채팅방 ID로 활성 화이트리스트 확인
     */
    @Query("SELECT COUNT(w) > 0 FROM WhitelistEntry w WHERE w.type = 'CHATROOM' AND w.targetId = :chatRoomId AND w.active = true")
    boolean existsActiveChatRoomWhitelist(@Param("chatRoomId") String chatRoomId);
    
    /**
     * 카테고리별 화이트리스트 조회
     */
    List<WhitelistEntry> findByCategoryAndActive(String category, boolean active);
    
    /**
     * 생성자별 화이트리스트 조회
     */
    List<WhitelistEntry> findByCreatedByAndActive(String createdBy, boolean active);
}