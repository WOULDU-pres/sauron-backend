package com.sauron.routing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * 관리자 사용자 엔티티
 */
@Entity
@Table(name = "admin_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUser {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String role;
    
    @Column(nullable = false)
    private boolean active;
    
    @Column(name = "notification_enabled")
    private boolean notificationEnabled;
    
    @Column(name = "receive_high_priority_only")
    private boolean receiveHighPriorityOnly;
    
    @Column(name = "availability_status")
    private String availabilityStatus;
    
    @Column(name = "min_severity_level")
    private String minSeverityLevel;
    
    @ElementCollection
    @Column(name = "allowed_alert_types")
    private Set<String> allowedAlertTypes;
    
    @Column(name = "last_active_at")
    private Instant lastActiveAt;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}