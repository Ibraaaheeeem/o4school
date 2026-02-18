package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "students",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "school_id"], name = "unique_student_user_school"),
        UniqueConstraint(columnNames = ["student_id", "school_id"], name = "unique_student_id_school")
    ],
    indexes = [
        Index(columnList = "school_id,is_active", name = "idx_student_school_active")
    ]
)
class Student(
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    
    @Column(name = "student_id", nullable = false)
    var studentId: String,
    
    @Column(name = "admission_number")
    var admissionNumber: String? = null,
    
    @Column(name = "admission_date", nullable = false)
    var admissionDate: LocalDate,
    
    @Column(name = "graduation_date")
    var graduationDate: LocalDate? = null,
    
    @Column(name = "academic_status")
    @Enumerated(EnumType.STRING)
    var academicStatus: AcademicStatus = AcademicStatus.ENROLLED,
    
    @Column(name = "current_grade_level")
    var currentGradeLevel: String? = null,
    
    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null,
    
    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    var gender: Gender? = null,
    
    @Column(name = "previous_school")
    var previousSchool: String? = null,
    
    @Column(name = "special_needs_description")
    var specialNeedsDescription: String? = null,
    
    @Column(name = "transportation_method")
    var transportationMethod: String? = null,
    
    @Column(name = "passport_photo_url")
    var passportPhotoUrl: String? = null
) : TenantAwareEntity() {
    
    @Column(name = "is_new")
    private var _isNew: Boolean? = null
    
    var isNew: Boolean
        get() = _isNew ?: true  // Default to true if null
        set(value) { _isNew = value }
    
    @Column(name = "has_special_needs")
    private var _hasSpecialNeeds: Boolean? = null
    
    var hasSpecialNeeds: Boolean
        get() = _hasSpecialNeeds ?: false  // Default to false if null
        set(value) { _hasSpecialNeeds = value }
    
    constructor() : this(
        user = User(),
        studentId = "",
        admissionDate = LocalDate.now()
    )
    
    // Relationships
    @OneToMany(mappedBy = "student", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var classEnrollments: MutableList<StudentClass> = mutableListOf()
    
    @OneToMany(mappedBy = "student", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var parentRelationships: MutableList<ParentStudent> = mutableListOf()
    
    @OneToMany(mappedBy = "student", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var attendanceRecords: MutableList<Attendance> = mutableListOf()
    
    @OneToMany(mappedBy = "student", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var examResults: MutableList<ExamResult> = mutableListOf()
    
    @OneToMany(mappedBy = "student", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var feedbacks: MutableList<StudentFeedback> = mutableListOf()
    
    @OneToMany(mappedBy = "student", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var assessments: MutableList<Assessment> = mutableListOf()
}