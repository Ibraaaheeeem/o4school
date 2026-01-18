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
    var gradeLevel: String? = null,
    
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