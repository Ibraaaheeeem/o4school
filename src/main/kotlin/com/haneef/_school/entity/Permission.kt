package com.haneef._school.entity

import java.util.UUID

import jakarta.persistence.*

@Entity
@Table(name = "permissions")
data class Permission(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    
    @Column(nullable = false, unique = true)
    val name: String,
    
    val description: String? = null,
    
    @Column(nullable = false)
    val module: String, // e.g., "STUDENT_MANAGEMENT", "ACADEMIC", "FINANCE", etc.
    
    @Column(name = "is_active")
    val isActive: Boolean = true
)

// Common permissions enum for reference
enum class SystemPermission(val permissionName: String, val module: String, val description: String) {
    // Student Management
    VIEW_STUDENTS("VIEW_STUDENTS", "STUDENT_MANAGEMENT", "View student information"),
    CREATE_STUDENTS("CREATE_STUDENTS", "STUDENT_MANAGEMENT", "Create new student records"),
    EDIT_STUDENTS("EDIT_STUDENTS", "STUDENT_MANAGEMENT", "Edit student information"),
    DELETE_STUDENTS("DELETE_STUDENTS", "STUDENT_MANAGEMENT", "Delete student records"),
    
    // Staff Management
    VIEW_STAFF("VIEW_STAFF", "STAFF_MANAGEMENT", "View staff information"),
    CREATE_STAFF("CREATE_STAFF", "STAFF_MANAGEMENT", "Create new staff records"),
    EDIT_STAFF("EDIT_STAFF", "STAFF_MANAGEMENT", "Edit staff information"),
    DELETE_STAFF("DELETE_STAFF", "STAFF_MANAGEMENT", "Delete staff records"),
    
    // Academic Management
    VIEW_CLASSES("VIEW_CLASSES", "ACADEMIC", "View class information"),
    MANAGE_CLASSES("MANAGE_CLASSES", "ACADEMIC", "Create and manage classes"),
    VIEW_SUBJECTS("VIEW_SUBJECTS", "ACADEMIC", "View subject information"),
    MANAGE_SUBJECTS("MANAGE_SUBJECTS", "ACADEMIC", "Create and manage subjects"),
    VIEW_GRADES("VIEW_GRADES", "ACADEMIC", "View student grades"),
    MANAGE_GRADES("MANAGE_GRADES", "ACADEMIC", "Create and manage grades"),
    
    // Financial Management
    VIEW_FEES("VIEW_FEES", "FINANCE", "View fee information"),
    MANAGE_FEES("MANAGE_FEES", "FINANCE", "Create and manage fees"),
    VIEW_PAYMENTS("VIEW_PAYMENTS", "FINANCE", "View payment records"),
    PROCESS_PAYMENTS("PROCESS_PAYMENTS", "FINANCE", "Process fee payments"),
    
    // Reports
    VIEW_REPORTS("VIEW_REPORTS", "REPORTS", "View system reports"),
    GENERATE_REPORTS("GENERATE_REPORTS", "REPORTS", "Generate custom reports"),
    
    // System Administration
    MANAGE_SCHOOL_SETTINGS("MANAGE_SCHOOL_SETTINGS", "ADMINISTRATION", "Manage school settings"),
    MANAGE_USERS("MANAGE_USERS", "ADMINISTRATION", "Manage user accounts"),
    MANAGE_PERMISSIONS("MANAGE_PERMISSIONS", "ADMINISTRATION", "Manage user permissions"),
    VIEW_AUDIT_LOGS("VIEW_AUDIT_LOGS", "ADMINISTRATION", "View system audit logs")
}