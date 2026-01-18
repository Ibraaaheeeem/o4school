package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "user_global_roles",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "role_id"], name = "unique_user_global_role")
    ]
)
class UserGlobalRole(
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    var role: Role,
    
    @Column(name = "assigned_at")
    var assignedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "assigned_by")
    var assignedBy: UUID? = null
) : BaseEntity() {
    
    constructor() : this(
        user = User(),
        role = Role()
    )
}
