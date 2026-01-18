package com.haneef._school.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "education_tracks",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["name", "school_id"], name = "unique_track_school")
    ],
    indexes = [
        Index(columnList = "school_id", name = "idx_track_school")
    ]
)
class EducationTrack(
    @Column(nullable = false)
    var name: String,
    
    var description: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(name = "")
    
    // Relationships
    @OneToMany(mappedBy = "track", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var departments: MutableList<Department> = mutableListOf()
    
    @OneToMany(mappedBy = "track", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var classes: MutableList<SchoolClass> = mutableListOf()
    

}