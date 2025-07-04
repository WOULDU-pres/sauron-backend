package com.sauron.common.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * 인증 서비스
 * 사용자 권한 및 역할 검증을 담당합니다.
 */
@Service
@Slf4j
public class AuthenticationService {
    
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String MOBILE_CLIENT_ROLE = "ROLE_MOBILE_CLIENT";
    
    /**
     * 관리자 권한 확인
     */
    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        // 임시로 모든 인증된 사용자를 관리자로 처리 (개발/테스트용)
        // 실제 환경에서는 적절한 역할 확인 로직 구현 필요
        boolean hasAdminRole = authentication.getAuthorities().contains(new SimpleGrantedAuthority(ADMIN_ROLE));
        
        // 개발 환경에서는 모바일 클라이언트도 임시로 관리자 권한 부여
        if (!hasAdminRole) {
            boolean isMobileClient = authentication.getAuthorities().contains(new SimpleGrantedAuthority(MOBILE_CLIENT_ROLE));
            if (isMobileClient) {
                log.debug("Granting temporary admin privileges to mobile client: {}", authentication.getName());
                return true;
            }
        }
        
        return hasAdminRole;
    }
    
    /**
     * 모바일 클라이언트 권한 확인
     */
    public boolean isMobileClient(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().contains(new SimpleGrantedAuthority(MOBILE_CLIENT_ROLE));
    }
    
    /**
     * 인증된 사용자 ID 반환
     */
    public String getUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        
        return authentication.getName();
    }
    
    /**
     * 인증 상태 확인
     */
    public boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }
} 