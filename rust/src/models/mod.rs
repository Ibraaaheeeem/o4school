// Re-export all model modules and their types

pub mod academic;
pub mod assessments;
pub mod auth;
pub mod community;
pub mod enums;
pub mod finance;
pub mod messaging;
pub mod organizations;
pub mod requests;
pub mod system;
pub mod users;

// Re-export commonly used types from auth module
pub use auth::{
    AuthNextRoute,
    SignUpRequest, SignUpResponse,
    SignInRequest, SignInResponse,
    ActivationRequest, ActivationResponse,
    VerifyEmailRequest, VerifyEmailResponse,
    ForgotPasswordRequest, ForgotPasswordResponse,
    ResetPasswordRequest, ResetPasswordResponse,
    LogoutRequest, LogoutResponse,
    SendOtpRequest, SendOtpResponse,
    VerifyOtpRequest, VerifyOtpResponse,
    CreateRoleUserRequest, CreateStudentInfo, CreateUserInfo, CreateStudentClassInfo,
    CreateParentInfo, CreateParentWithUserRequest, CreateParentStudentInfo,
    CreateStaffInfo, CreateStaffWithUserRequest, CreateClassTeacherInfo, CreateSubjectTeacherInfo,
    UpdateStudentClassesRequest, UpdateParentStudentsRequest,
    UpdateClassTeacherRequest, UpdateSubjectTeacherRequest,
    UserRoleInfo, UserSchoolWithRoles, RefreshTokenRequest, RefreshTokenResponse,
    AuthErrorResponse,
};

// Re-export user types
pub use users::{User, UserSchoolRole, Role, Permission, UserGlobalRole, DesignationPermission};

// Re-export academic types
pub use academic::{
    SchoolClass, Subject, ClassSubject, SchoolSubject, SchoolSubjectFilter,
    AcademicSession, Term, StudentClass, ClassTeacher, SubjectTeacher,
    SchoolSubjectResponse, LinkedClassResponse,
    CreateAcademicSessionRequest, UpdateAcademicSessionRequest,
    CreateTermStudentTransitionRequest, CreateTermRequest, UpdateTermRequest,
    TermStudentTransitionAction,
    ScoringComponent, ScoringScheme, CreateScoringSchemeRequest, UpdateScoringSchemeRequest,
};

// Re-export organization types
pub use organizations::{
    School, Department, Designation, SchoolCalendar, SchoolTimetable, EducationTrack, SubjectMapping,
    CreateCalendarEventRequest, UpdateCalendarEventRequest,
    CreateSchoolTimetableRequest, UpdateSchoolTimetableRequest, TimetableSuccessResponse,
    UpdateSchoolDataRequest, CreateEducationTrackRequest, CreateDepartmentRequest, CreateClassRequest,
    SaveSchoolSubjectsRequest, LinkSubjectClassesRequest, LinkSchoolSubjectClassesRequest,
    InitializeDefaultStructureRequest, InitializeDefaultStructureResponse,
    AcademicStructureResponse, AcademicTrackNode, DepartmentNode, ClassNode,
};

// Re-export community types
pub use community::people::{
    Student, Parent, Staff, PaginatedResponse, Pagination,
    StaffListResponse, ParentListResponse, StudentListResponse, ParentLinkedStudent,
    StaffClassAssignmentResponse, StaffSubjectAssignmentResponse, 
    StudentClassAssignmentResponse, StudentDetailResponse,
};

// Re-export assessment types  
pub use assessments::{
    Examination, Assessment, SubjectScore, ExamResult, Question, Attendance, ExaminationSubmission, StudentFeedback,
    CreateExaminationRequest, UpdateExaminationRequest,
    CreateAssessmentRequest, UpdateAssessmentRequest,
    SaveSubjectScoreItem, SaveSubjectScoresRequest,
    ClassAssessmentContextResponse,
};

// Re-export finance types
pub use finance::{Invoice, Bill, ClassFeeItem, FeeItem, Settlement, StudentOptionalFee};

// Re-export request/response types
pub use requests::{HealthResponse, CreateTenantRequest, CreateSchoolRequest};