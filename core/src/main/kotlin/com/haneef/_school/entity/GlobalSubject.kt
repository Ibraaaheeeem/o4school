package com.haneef._school.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "global_subjects",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["name"], name = "unique_global_subject_name"),
        UniqueConstraint(columnNames = ["code"], name = "unique_global_subject_code")
    ]
)
class GlobalSubject(
    @Column(nullable = false)
    var name: String,

    @Column(nullable = true)
    var code: String? = null,

    @Column(name = "min_grade_level", nullable = false)
    var minGradeLevel: Int = 1,

    @Column(name = "max_grade_level", nullable = false)
    var maxGradeLevel: Int = 12,

    @Column(nullable = true)
    var category: String? = null,

    @Column(name = "is_core", nullable = false)
    var isCore: Boolean = false,

    @Column(name = "is_active", nullable = false)
    override var isActive: Boolean = true

) : BaseEntity() {
    constructor() : this(name = "")
}
