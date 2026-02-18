package com.haneef._school.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "departments",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["name", "school_id", "track_id"], name = "unique_dept_school_track")
    ],
    indexes = [
        Index(columnList = "school_id,track_id", name = "idx_dept_school_track")
    ]
)
class Department(
    @Column(nullable = false)
    var name: String,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    var track: EducationTrack? = null,
    
    var description: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(name = "")
    
    // Relationships
    @OneToMany(mappedBy = "department", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var classes: MutableList<SchoolClass> = mutableListOf()
}