package com.sauron.routing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.Set;

/**
 * 권한 그룹 엔티티
 */
@Entity
@Table(name = "permission_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionGroup {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private boolean active;
    
    @ElementCollection
    @Column(name = "allowed_alert_types")
    private Set<String> allowedAlertTypes;
    
    @Column(name = "min_severity_level")
    private String minSeverityLevel;
    
    @Column(name = "active_hours_start")
    private LocalTime activeHoursStart;
    
    @Column(name = "active_hours_end")
    private LocalTime activeHoursEnd;
    
    @ElementCollection
    @Column(name = "members")
    private Set<Long> members;
}