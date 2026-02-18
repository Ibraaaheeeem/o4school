package com.haneef._school.entity

import java.util.UUID

import jakarta.persistence.*

@Entity
@Table(
    name = "classes",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["class_name", "school_id"], name = "unique_class_school_year")
    ],
    indexes = [
        Index(columnList = "school_id,grade_level", name = "idx_class_school_grade"),
        Index(columnList = "school_id,department_id", name = "idx_class_school_dept")
    ]
)
class SchoolClass(
    @Column(name = "class_name", nullable = false)
    var className: String,
    
    @Column(name = "class_code")
    var classCode: String? = null,
    
    @Column(name = "grade_level")
    var gradeLevel: Int? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    var department: Department? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    var track: EducationTrack? = null,
    
    var term: String? = null,
    
    @Column(name = "max_capacity")
    var maxCapacity: Int = 30,
    
    @Column(name = "current_enrollment")
    var currentEnrollment: Int = 0,
    
    @Column(name = "classroom_location")
    var classroomLocation: String? = null,
    
    @Column(name = "class_staff_id")
    var classStaffId: UUID? = null,

    @Column(name = "scoring_scheme", columnDefinition = "TEXT")
    var scoringScheme: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        className = ""
    )
    
    enum class GradeLevel(val value: Int, val displayName: String) {
        KINDERGARTEN(-3, "Kindergarten"),
        NURSERY_1(-2, "Nursery 1"),
        NURSERY_2(-1, "Nursery 2"),
        NURSERY_3(0, "Nursery 3"),
        PRIMARY_1(1, "Primary 1"),
        PRIMARY_2(2, "Primary 2"),
        PRIMARY_3(3, "Primary 3"),
        PRIMARY_4(4, "Primary 4"),
        PRIMARY_5(5, "Primary 5"),
        PRIMARY_6(6, "Primary 6"),
        JSS_1(7, "JSS 1"),
        JSS_2(8, "JSS 2"),
        JSS_3(9, "JSS 3"),
        SSS_1(10, "SSS 1"),
        SSS_2(11, "SSS 2"),
        SSS_3(12, "SSS 3");

        companion object {
            fun fromValue(value: Int): GradeLevel? = values().find { it.value == value }
            
            fun fromClassName(className: String): Int? {
                val name = className.trim()
                return values().find { 
                    name.equals(it.displayName, ignoreCase = true) || 
                    name.contains(it.displayName, ignoreCase = true) 
                }?.value ?: when {
                    // Fallback heuristics if exact match fails but patterns exist
                    name.contains("Primary", ignoreCase = true) -> {
                         name.filter { it.isDigit() }.toIntOrNull()
                    }
                    name.contains("JSS", ignoreCase = true) -> {
                         name.filter { it.isDigit() }.toIntOrNull()?.let { it + 6 }
                    }
                    name.contains("SSS", ignoreCase = true) -> {
                         name.filter { it.isDigit() }.toIntOrNull()?.let { it + 9 }
                    }
                    else -> null
                }
            }
        }
    }
    
    val gradeLevelDisplayName: String
        get() = gradeLevel?.let { GradeLevel.fromValue(it)?.displayName } ?: ""
    
    // Relationships
    @OneToMany(mappedBy = "schoolClass", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var subjectAssignments: MutableList<ClassSubject> = mutableListOf()
    
    @OneToMany(mappedBy = "schoolClass", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var studentEnrollments: MutableList<StudentClass> = mutableListOf()
    
    @OneToMany(mappedBy = "schoolClass", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var attendanceRecords: MutableList<Attendance> = mutableListOf()
    
    @OneToMany(mappedBy = "schoolClass", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var exams: MutableList<Exam> = mutableListOf()
}