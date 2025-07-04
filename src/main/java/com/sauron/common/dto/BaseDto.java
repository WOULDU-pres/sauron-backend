package com.sauron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 모든 DTO의 기본 클래스
 * 공통적으로 사용되는 필드들을 정의합니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseDto {
    
    /**
     * 생성 시각
     */
    private Instant createdAt;
    
    /**
     * 수정 시각
     */
    private Instant updatedAt;
    
    /**
     * 버전 정보 (낙관적 락킹)
     */
    private Long version;
    
    /**
     * 생성 시 자동으로 시각 설정
     */
    public void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    /**
     * 수정 시 자동으로 시각 설정
     */
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}