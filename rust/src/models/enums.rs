use serde::{Deserialize, Serialize};

// ============================================================================
// ENUMS - Matching Kotlin entity enums
// ============================================================================

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, sqlx::Type)]
#[sqlx(rename_all = "UPPERCASE")]
pub enum UserStatus {
    #[serde(rename = "PENDING")]
    Pending,
    #[serde(rename = "ACTIVE")]
    Active,
    #[serde(rename = "INACTIVE")]
    Inactive,
    #[serde(rename = "SUSPENDED")]
    Suspended,
    #[serde(rename = "APPROVED")]
    Approved,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, sqlx::Type)]
#[sqlx(rename_all = "UPPERCASE")]
pub enum UserRole {
    #[serde(rename = "ADMIN")]
    Admin,
    #[serde(rename = "STUDENT")]
    Student,
    #[serde(rename = "PARENT")]
    Parent,
    #[serde(rename = "STAFF")]
    Staff,
    #[serde(rename = "SCHOOL_ADMIN")]
    SchoolAdmin,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, sqlx::Type)]
#[sqlx(rename_all = "UPPERCASE")]
pub enum RoleType {
    #[serde(rename = "ADMIN")]
    Admin,
    #[serde(rename = "STUDENT")]
    Student,
    #[serde(rename = "PARENT")]
    Parent,
    #[serde(rename = "STAFF")]
    Staff,
    #[serde(rename = "SCHOOL_ADMIN")]
    SchoolAdmin,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, sqlx::Type)]
#[sqlx(rename_all = "UPPERCASE")]
pub enum AcademicStatus {
    #[serde(rename = "ENROLLED")]
    Enrolled,
    #[serde(rename = "GRADUATED")]
    Graduated,
    #[serde(rename = "TRANSFERRED")]
    Transferred,
    #[serde(rename = "EXPELLED")]
    Expelled,
    #[serde(rename = "SUSPENDED")]
    Suspended,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, sqlx::Type)]
#[sqlx(rename_all = "UPPERCASE")]
pub enum Gender {
    #[serde(rename = "MALE")]
    Male,
    #[serde(rename = "FEMALE")]
    Female,
    #[serde(rename = "OTHER")]
    Other,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, sqlx::Type)]
#[sqlx(rename_all = "UPPERCASE")]
pub enum InvoiceStatus {
    #[serde(rename = "DRAFT")]
    Draft,
    #[serde(rename = "SENT")]
    Sent,
    #[serde(rename = "PAID")]
    Paid,
    #[serde(rename = "OVERDUE")]
    Overdue,
    #[serde(rename = "CANCELLED")]
    Cancelled,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, sqlx::Type)]
#[sqlx(rename_all = "UPPERCASE")]
pub enum PaymentStatus {
    #[serde(rename = "PENDING")]
    Pending,
    #[serde(rename = "APPROVED")]
    Approved,
    #[serde(rename = "REJECTED")]
    Rejected,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, sqlx::Type)]
#[sqlx(rename_all = "UPPERCASE")]
pub enum AttendanceStatus {
    #[serde(rename = "PRESENT")]
    Present,
    #[serde(rename = "ABSENT")]
    Absent,
    #[serde(rename = "LATE")]
    Late,
    #[serde(rename = "EXCUSED")]
    Excused,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, sqlx::Type)]
#[sqlx(rename_all = "UPPERCASE")]
pub enum ActivityType {
    #[serde(rename = "USER_LOGIN")]
    UserLogin,
    #[serde(rename = "USER_LOGOUT")]
    UserLogout,
    #[serde(rename = "USER_CREATED")]
    UserCreated,
    #[serde(rename = "USER_UPDATED")]
    UserUpdated,
    #[serde(rename = "STUDENT_ENROLLED")]
    StudentEnrolled,
    #[serde(rename = "GRADE_SUBMITTED")]
    GradeSubmitted,
}
