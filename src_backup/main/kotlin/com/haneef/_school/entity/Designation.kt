package com.haneef._school.entity

import java.util.UUID

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "designations")
class Designation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    val school: School,
    
    @Column(nullable = false)
    val name: String,
    
    val description: String? = null,
    
    @Column(name = "is_active")
    val isActive: Boolean = true,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @OneToMany(mappedBy = "designation", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val permissions: MutableList<DesignationPermission> = mutableListOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Designation) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}