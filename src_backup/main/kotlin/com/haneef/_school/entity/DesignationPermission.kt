package com.haneef._school.entity

import java.util.UUID

import jakarta.persistence.*

@Entity
@Table(name = "designation_permissions")
class DesignationPermission(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id", nullable = false)
    val designation: Designation,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    val permission: Permission,
    
    @Column(name = "can_read")
    val canRead: Boolean = false,
    
    @Column(name = "can_write")
    val canWrite: Boolean = false,
    
    @Column(name = "can_delete")
    val canDelete: Boolean = false,
    
    @Column(name = "can_approve")
    val canApprove: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DesignationPermission) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}