package com.haneef._school.entity

import jakarta.persistence.*

enum class UserStatus {
    PENDING, ACTIVE, INACTIVE, SUSPENDED
}

enum class UserRole {
    ADMIN, STUDENT, PARENT, STAFF, SCHOOL_ADMIN
}

enum class RoleType {
    ADMIN, STUDENT, PARENT, STAFF, SCHOOL_ADMIN
}

enum class AcademicStatus {
    ENROLLED, GRADUATED, TRANSFERRED, EXPELLED, SUSPENDED
}

enum class InvoiceStatus {
    DRAFT, SENT, PAID, OVERDUE, CANCELLED
}

enum class PaymentStatus {
    PENDING, APPROVED, REJECTED
}

@Entity
@Table(name = "roles")
class Role(
    @Column(nullable = false)
    var name: String,
    
    @Column(name = "role_type", nullable = false)
    @Enumerated(EnumType.STRING)
    var roleType: RoleType,
    
    var description: String? = null,
    
    @Column(name = "is_system_role")
    var isSystemRole: Boolean = false
) : BaseEntity() {
    
    constructor() : this(name = "", roleType = RoleType.STAFF)
}