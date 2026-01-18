package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "staff",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "school_id"], name = "unique_staff_user_school"),
        UniqueConstraint(columnNames = ["staff_id", "school_id"], name = "unique_staff_id_school")
    ],
    indexes = [
        Index(columnList = "school_id,department,is_active", name = "idx_staff_school_dept"),
        Index(columnList = "school_id,designation,is_active", name = "idx_staff_school_designation")
    ]
)
class Staff(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    
    @Column(name = "staff_id", nullable = false)
    var staffId: String,
    
    @Column(name = "employee_number")
    var employeeNumber: String? = null,
    
    @Column(nullable = false)
    var designation: String = "Teacher", // Teacher, Cleaner, Gateman, etc.
    
    @Column(name = "hire_date", nullable = false)
    var hireDate: LocalDate,
    
    @Column(name = "termination_date")
    var terminationDate: LocalDate? = null,
    
    @Column(name = "employment_status")
    var employmentStatus: String = "active",
    
    @Column(name = "employment_type")
    var employmentType: String = "full_time",
    
    @Column(name = "highest_degree")
    var highestDegree: String? = null,
    
    var department: String? = null,
    
    @Column(name = "is_class_teacher")
    var isClassTeacher: Boolean = false,
    
    @Column(name = "is_subject_teacher")
    var isSubjectTeacher: Boolean = false,
    
    // Account details
    @Column(name = "bank_name")
    var bankName: String? = null,
    
    @Column(name = "account_name")
    var accountName: String? = null,
    
    @Column(name = "account_number")
    var accountNumber: String? = null,
    
    @Column(name = "monthly_deduction")
    var monthlyDeduction: Double = 0.0,
    
    @Column(name = "class_teacher_for")
    var classTeacherFor: UUID? = null,
    
    @Column(name = "years_of_experience")
    var yearsOfExperience: Int = 0
) : TenantAwareEntity() {
    
    constructor() : this(
        user = User(),
        staffId = "",
        hireDate = LocalDate.now()
    )
    
    // Relationships
    @OneToMany(mappedBy = "staff", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var subjectAssignments: MutableList<ClassSubject> = mutableListOf()
    
    @OneToMany(mappedBy = "staff", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var attendanceRecords: MutableList<Attendance> = mutableListOf()
    
    @OneToMany(mappedBy = "staff", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var exams: MutableList<Exam> = mutableListOf()
    
    @OneToMany(mappedBy = "gradedByStaff", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var gradedResults: MutableList<ExamResult> = mutableListOf()
    
    @OneToMany(mappedBy = "staff", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var studentFeedbacks: MutableList<StudentFeedback> = mutableListOf()
    
    // Teacher assignments
    @OneToMany(mappedBy = "staff", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var classTeacherAssignments: MutableSet<ClassTeacher> = mutableSetOf()
    
    @OneToMany(mappedBy = "staff", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var subjectTeacherAssignments: MutableSet<SubjectTeacher> = mutableSetOf()
}