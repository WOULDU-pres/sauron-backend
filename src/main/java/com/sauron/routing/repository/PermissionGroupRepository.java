package com.sauron.routing.repository;

import com.sauron.routing.entity.PermissionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 권한 그룹 저장소
 */
@Repository
public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, Long> {
    
    List<PermissionGroup> findByMembersContaining(Long userId);
    List<PermissionGroup> findByActive(boolean active);
}