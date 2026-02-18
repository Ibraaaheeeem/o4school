package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "user_school_roles",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "school_id", "role_id"], name = "unique_user_school_role")
    ],
    indexes = [
        Index(columnList = "user_id,school_id,is_active", name = "idx_user_school_active")
    ]
)
class UserSchoolRole(
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    
    @Column(name = "school_id", nullable = false)
    override var schoolId: UUID?,
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    var role: Role,
    
    @Column(name = "assigned_by")
    var assignedBy: UUID? = null,
    
    @Column(name = "assigned_at")
    var assignedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null,
    
    @Column(name = "is_primary")
    var isPrimary: Boolean = false
) : TenantAwareEntity() {
    
    constructor() : this(
        user = User(),
        schoolId = null,
        role = Role()
    )
    
    init {
        this.schoolId = schoolId
    }
}