package com.sauron.routing.repository;

import com.sauron.routing.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 관리자 사용자 저장소
 */
@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    
    List<AdminUser> findByActive(boolean active);
    List<AdminUser> findByRoleAndActive(String role, boolean active);
    List<AdminUser> findByIdIn(List<Long> ids);
    int countByActive(boolean active);
}