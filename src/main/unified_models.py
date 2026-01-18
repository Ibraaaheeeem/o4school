"""
Unified Database Models for Multi-School Management System
Using shared database with school_id tenant isolation for cost efficiency.
"""
from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import Column, String, Integer, Boolean, DateTime, Date, Text, JSON, ForeignKey, Index, UniqueConstraint, Float
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship, backref
from datetime import datetime, date
from enum import Enum
import uuid

# Initialize SQLAlchemy
db = SQLAlchemy()

# ============================================================================
# BASE MODELS
# ============================================================================

class BaseModel(db.Model):
    """Base model with common fields for all entities."""
    __abstract__ = True
    
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    
    def to_dict(self):
        """Convert model instance to dictionary."""
        return {
            column.name: getattr(self, column.name)
            for column in self.__table__.columns
        }
    
    def update(self, **kwargs):
        """Update model instance with provided kwargs."""
        for key, value in kwargs.items():
            if hasattr(self, key):
                setattr(self, key, value)
        self.updated_at = datetime.utcnow()


class TenantAwareModel(BaseModel):
    """Base model for tenant-aware entities with automatic school_id filtering."""
    __abstract__ = True
    
    school_id = Column(UUID(as_uuid=True), nullable=False, index=True)
    
    @classmethod
    def query_for_school(cls, school_id):
        """Query entities for a specific school."""
        return cls.query.filter_by(school_id=school_id, is_active=True)
    
    @classmethod
    def get_by_id_and_school(cls, entity_id, school_id):
        """Get entity by ID and school."""
        return cls.query.filter_by(id=entity_id, school_id=school_id, is_active=True).first()


# ============================================================================
# ENUMS
# ============================================================================

class UserStatus(Enum):
    PENDING = "pending"
    ACTIVE = "active"
    INACTIVE = "inactive"
    SUSPENDED = "suspended"


class RoleType(Enum):
    ADMIN = "admin"
    STUDENT = "student"
    PARENT = "parent"
    STAFF = "staff"


class AcademicStatus(Enum):
    ENROLLED = "enrolled"
    GRADUATED = "graduated"
    TRANSFERRED = "transferred"
    EXPELLED = "expelled"
    SUSPENDED = "suspended"


class InvoiceStatus(Enum):
    DRAFT = "draft"
    SENT = "sent"
    PAID = "paid"
    OVERDUE = "overdue"
    CANCELLED = "cancelled"


class PaymentStatus(Enum):
    PENDING = "pending"
    APPROVED = "approved"
    REJECTED = "rejected"


class AttendanceStatus(Enum):
    PRESENT = "present"
    ABSENT = "absent"
    LATE = "late"
    EXCUSED = "excused"
# ============================================================================
# CORE SYSTEM MODELS (Shared across all schools)
# ============================================================================

class School(BaseModel):
    """School model for tenant management - shared table."""
    __tablename__ = 'schools'
    
    # Basic Information
    name = Column(String(255), nullable=False)
    slug = Column(String(100), unique=True, nullable=False, index=True)
    
    # Contact Information
    email = Column(String(255), nullable=False)
    phone = Column(String(20), nullable=False)
    website = Column(String(255), nullable=True)
    address_line1 = Column(String(255), nullable=False)
    address_line2 = Column(String(255), nullable=True)
    city = Column(String(100), nullable=False)
    state = Column(String(100), nullable=False)
    postal_code = Column(String(20), nullable=False)
    country = Column(String(100), nullable=False, default='Nigeria')
    
    # Domain Configuration
    subdomain = Column(String(100), unique=True, nullable=True, index=True)
    custom_domain = Column(String(255), unique=True, nullable=True, index=True)
    ssl_enabled = Column(Boolean, default=False)
    
    # Status and Settings
    status = Column(String(20), default='pending')  # pending, active, suspended, inactive
    timezone = Column(String(50), default='Africa/Lagos')
    currency = Column(String(10), default='NGN')
    language = Column(String(10), default='en')
    
    # Academic Settings
    academic_year_start = Column(String(10), default='09-01')  # MM-DD format
    academic_year_end = Column(String(10), default='07-31')    # MM-DD format
    current_academic_year = Column(String(10), nullable=True)  # 2023-2024
    
    # Administrator Information
    admin_user_id = Column(UUID(as_uuid=True), nullable=True)
    admin_name = Column(String(255), nullable=False)
    admin_email = Column(String(255), nullable=False)
    admin_phone = Column(String(20), nullable=False)
    
    # Branding and Customization
    logo_url = Column(String(500), nullable=True)
    banner_url = Column(String(500), nullable=True)
    primary_color = Column(String(7), default='#007bff')
    secondary_color = Column(String(7), default='#6c757d')
    school_motto = Column(String(500), nullable=True)
    
    # Feature Flags
    features_enabled = Column(JSON, default=dict)
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'name': self.name,
            'slug': self.slug,
            'email': self.email,
            'phone': self.phone,
            'website': self.website,
            'address': {
                'line1': self.address_line1,
                'line2': self.address_line2,
                'city': self.city,
                'state': self.state,
                'postal_code': self.postal_code,
                'country': self.country
            },
            'domain': {
                'subdomain': self.subdomain,
                'custom_domain': self.custom_domain,
                'ssl_enabled': self.ssl_enabled
            },
            'status': self.status,
            'settings': {
                'timezone': self.timezone,
                'currency': self.currency,
                'language': self.language,
                'academic_year_start': self.academic_year_start,
                'academic_year_end': self.academic_year_end,
                'current_academic_year': self.current_academic_year
            },
            'branding': {
                'logo_url': self.logo_url,
                'banner_url': self.banner_url,
                'primary_color': self.primary_color,
                'secondary_color': self.secondary_color,
                'motto': self.school_motto
            },
            'features_enabled': self.features_enabled or {},
            'admin': {
                'user_id': str(self.admin_user_id) if self.admin_user_id else None,
                'name': self.admin_name,
                'email': self.admin_email,
                'phone': self.admin_phone
            },
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None
        }


class User(BaseModel):
    """User model for authentication and basic profile - shared table."""
    __tablename__ = 'users'
    
    # Authentication
    phone_number = Column(String(20), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=True)  # Set after activation
    
    # Basic Profile
    email = Column(String(255), nullable=True, index=True)
    first_name = Column(String(100), nullable=True)
    last_name = Column(String(100), nullable=True)
    middle_name = Column(String(100), nullable=True)
    date_of_birth = Column(Date, nullable=True)
    gender = Column(String(10), nullable=True)
    profile_picture_url = Column(String(500), nullable=True)
    
    # Address
    address_line1 = Column(String(255), nullable=True)
    address_line2 = Column(String(255), nullable=True)
    city = Column(String(100), nullable=True)
    state = Column(String(100), nullable=True)
    postal_code = Column(String(20), nullable=True)
    country = Column(String(100), default='Nigeria')
    
    # Status
    status = Column(String(20), default=UserStatus.PENDING.value, nullable=False)
    is_verified = Column(Boolean, default=False)
    verification_status = Column(String(20), default='unverified')  # unverified, verified
    approval_status = Column(String(20), default='pending')  # pending, approved, rejected
    verified_at = Column(DateTime, nullable=True)
    approved_at = Column(DateTime, nullable=True)
    approved_by = Column(UUID(as_uuid=True), nullable=True)
    last_login_at = Column(DateTime, nullable=True)
    
    # Email Verification (for teachers and staff)
    email_verified = Column(Boolean, default=False)
    email_verification_token = Column(String(255), nullable=True)
    email_verification_expires = Column(DateTime, nullable=True)
    otp_code = Column(String(10), nullable=True)
    otp_expires = Column(DateTime, nullable=True)
    last_otp_sent = Column(DateTime, nullable=True)
    otp_attempts = Column(Integer, default=0)
    
    # Metadata
    user_metadata = Column(JSON, default=dict)
    
    @property
    def full_name(self):
        """Get user's full name."""
        names = [self.first_name, self.middle_name, self.last_name]
        return ' '.join(filter(None, names))
    
    def get_schools(self):
        """Get all schools this user belongs to."""
        return [role.school_id for role in self.school_roles if role.is_active]
    
    def to_dict(self, include_sensitive=False):
        return {
            'id': str(self.id),
            'phoneNumber': self.phone_number,
            'phone_number': self.phone_number if include_sensitive else None,
            'email': self.email,
            'first_name': self.first_name,
            'last_name': self.last_name,
            'middle_name': self.middle_name,
            'full_name': self.full_name,
            'profile': {
                'firstName': self.first_name,
                'lastName': self.last_name,
                'email': self.email,
                'avatar': self.profile_picture_url
            },
            'date_of_birth': self.date_of_birth.isoformat() if self.date_of_birth else None,
            'gender': self.gender,
            'profile_picture_url': self.profile_picture_url,
            'address': {
                'line1': self.address_line1,
                'line2': self.address_line2,
                'city': self.city,
                'state': self.state,
                'postal_code': self.postal_code,
                'country': self.country
            } if self.address_line1 else None,
            'status': self.status,
            'is_verified': self.is_verified,
            'last_login_at': self.last_login_at.isoformat() if self.last_login_at else None,
            'metadata': self.user_metadata or {},
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
            'schools': []  # Placeholder for frontend compatibility
        }


class Role(BaseModel):
    """Role model for RBAC - shared table."""
    __tablename__ = 'roles'
    
    name = Column(String(100), nullable=False)
    role_type = Column(String(20), nullable=False)  # admin, teacher, student, parent, staff
    description = Column(Text, nullable=True)
    permissions = Column(JSON, default=list)
    is_system_role = Column(Boolean, default=False)
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'name': self.name,
            'role_type': self.role_type,
            'description': self.description,
            'permissions': self.permissions or [],
            'is_system_role': self.is_system_role,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }
# ============================================================================
# TENANT-AWARE MODELS (School-specific data)
# ============================================================================

class UserSchoolRole(TenantAwareModel):
    """User-School-Role relationship - tenant-aware."""
    __tablename__ = 'user_school_roles'
    
    user_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False)
    role_id = Column(UUID(as_uuid=True), ForeignKey('roles.id'), nullable=False)
    
    # Assignment metadata
    assigned_by = Column(UUID(as_uuid=True), nullable=True)
    assigned_at = Column(DateTime, default=datetime.utcnow)
    expires_at = Column(DateTime, nullable=True)
    is_primary = Column(Boolean, default=False)
    
    # Role-specific data
    role_data = Column(JSON, default=dict)
    
    # Relationships
    user = relationship('User', backref='school_roles')
    role = relationship('Role')
    
    __table_args__ = (
        UniqueConstraint('user_id', 'school_id', 'role_id', name='unique_user_school_role'),
        Index('idx_user_school_active', 'user_id', 'school_id', 'is_active'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'user_id': str(self.user_id),
            'school_id': str(self.school_id),
            'role_id': str(self.role_id),
            'role': self.role.to_dict() if self.role else None,
            'assigned_by': str(self.assigned_by) if self.assigned_by else None,
            'assigned_at': self.assigned_at.isoformat() if self.assigned_at else None,
            'expires_at': self.expires_at.isoformat() if self.expires_at else None,
            'is_primary': self.is_primary,
            'role_data': self.role_data or {},
            'is_active': self.is_active
        }


class Student(TenantAwareModel):
    """Student model - tenant-aware."""
    __tablename__ = 'students'
    
    user_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False)
    
    # Student identification
    student_id = Column(String(50), nullable=False, index=True)
    admission_number = Column(String(50), nullable=True)
    
    # Academic information
    admission_date = Column(Date, nullable=False)
    graduation_date = Column(Date, nullable=True)
    academic_status = Column(String(20), default=AcademicStatus.ENROLLED.value)
    current_grade_level = Column(String(20), nullable=True)
    
    # Additional information
    previous_school = Column(String(255), nullable=True)
    has_special_needs = Column(Boolean, default=False)
    special_needs_description = Column(Text, nullable=True)
    transportation_method = Column(String(50), nullable=True)
    
    # Relationships
    user = relationship('User', backref='student_profiles')
    
    __table_args__ = (
        UniqueConstraint('user_id', 'school_id', name='unique_student_user_school'),
        UniqueConstraint('student_id', 'school_id', name='unique_student_id_school'),
        Index('idx_student_school_active', 'school_id', 'is_active'),
    )
    
    def get_current_classes(self, academic_year=None):
        """Get current class enrollments for this student"""
        # Import here to avoid circular imports
        from shared.models.unified_models import StudentClasses
        query = StudentClasses.query.filter_by(
            student_id=self.id,
            school_id=self.school_id,
            is_active=True
        )
        if academic_year:
            query = query.filter_by(academic_year=academic_year)
        return query.all()
    
    def get_primary_class(self, academic_year=None):
        """Get the primary class for this student (first active enrollment)"""
        enrollments = self.get_current_classes(academic_year)
        return enrollments[0] if enrollments else None
    
    def to_dict(self, include_classes=False, academic_year=None, include_user=True):
        result = {
            'id': str(self.id),
            'user_id': str(self.user_id),
            'school_id': str(self.school_id),
            'student_id': self.student_id,
            'admission_number': self.admission_number,
            'admission_date': self.admission_date.isoformat() if self.admission_date else None,
            'graduation_date': self.graduation_date.isoformat() if self.graduation_date else None,
            'academic_status': self.academic_status,
            'current_grade_level': self.current_grade_level,
            'previous_school': self.previous_school,
            'has_special_needs': self.has_special_needs,
            'special_needs_description': self.special_needs_description,
            'transportation_method': self.transportation_method,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }
        
        # Include user information if requested
        if include_user and self.user:
            result['user'] = {
                'first_name': self.user.first_name,
                'last_name': self.user.last_name,
                'middle_name': self.user.middle_name,
                'email': self.user.email,
                'phone_number': self.user.phone_number,
                'date_of_birth': self.user.date_of_birth.isoformat() if self.user.date_of_birth else None,
                'gender': self.user.gender
            }
            # Also add direct access for backward compatibility
            result['first_name'] = self.user.first_name
            result['last_name'] = self.user.last_name
            result['full_name'] = self.user.full_name
            result['email'] = self.user.email
        
        # Include class information if requested
        if include_classes:
            try:
                enrollments = self.get_current_classes(academic_year)
                result['class_enrollments'] = [enrollment.to_dict() for enrollment in enrollments]
                
                # For backward compatibility, include primary class info
                primary_enrollment = enrollments[0] if enrollments else None
                if primary_enrollment:
                    result['class_id'] = str(primary_enrollment.class_id)
                    result['primary_class'] = primary_enrollment.to_dict()
                else:
                    result['class_id'] = None
                    result['primary_class'] = None
            except:
                result['class_enrollments'] = []
                result['class_id'] = None
                result['primary_class'] = None
        
        return result


class Parent(TenantAwareModel):
    """Parent model - tenant-aware."""
    __tablename__ = 'parents'
    
    user_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False)
    
    # Parent information
    relationship_type = Column(String(20), nullable=False)  # father, mother, guardian
    is_primary_contact = Column(Boolean, default=False)
    is_emergency_contact = Column(Boolean, default=True)
    is_financially_responsible = Column(Boolean, default=True)
    
    # Communication preferences
    receive_academic_updates = Column(Boolean, default=True)
    receive_financial_updates = Column(Boolean, default=True)
    receive_disciplinary_updates = Column(Boolean, default=True)
    
    # Relationships
    user = relationship('User', backref='parent_profiles')
    
    __table_args__ = (
        UniqueConstraint('user_id', 'school_id', name='unique_parent_user_school'),
        Index('idx_parent_school_active', 'school_id', 'is_active'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'user_id': str(self.user_id),
            'school_id': str(self.school_id),
            'relationship_type': self.relationship_type,
            'is_primary_contact': self.is_primary_contact,
            'is_emergency_contact': self.is_emergency_contact,
            'is_financially_responsible': self.is_financially_responsible,
            'communication_preferences': {
                'receive_academic_updates': self.receive_academic_updates,
                'receive_financial_updates': self.receive_financial_updates,
                'receive_disciplinary_updates': self.receive_disciplinary_updates
            },
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class ParentStudent(TenantAwareModel):
    """Parent-Student relationship - tenant-aware."""
    __tablename__ = 'parent_student_relationships'
    
    parent_id = Column(UUID(as_uuid=True), ForeignKey('parents.id'), nullable=False)
    student_id = Column(UUID(as_uuid=True), ForeignKey('students.id'), nullable=False)
    relationship_type = Column(String(50), nullable=False)  # biological, adoptive, guardian, etc.
    
    # Relationships
    parent = relationship('Parent', backref='student_relationships')
    student = relationship('Student', backref='parent_relationships')
    
    __table_args__ = (
        UniqueConstraint('parent_id', 'student_id', 'school_id', name='unique_parent_student_school'),
        Index('idx_parent_student_school', 'school_id', 'parent_id', 'student_id'),
    )


class Staff(TenantAwareModel):
    """Staff model - tenant-aware (includes teachers, cleaners, gatemen, etc.)."""
    __tablename__ = 'staff'
    
    user_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False)
    
    # Staff identification
    staff_id = Column(String(50), nullable=False, index=True)
    employee_number = Column(String(50), nullable=True)
    
    # Staff designation
    designation = Column(String(50), nullable=False, default='Teacher')  # Teacher, Cleaner, Gateman, etc.
    
    # Employment
    hire_date = Column(Date, nullable=False)
    termination_date = Column(Date, nullable=True)
    employment_status = Column(String(20), default='active')
    employment_type = Column(String(20), default='full_time')
    
    # Academic qualifications (mainly for teachers)
    highest_degree = Column(String(100), nullable=True)
    certifications = Column(JSON, default=list)
    
    # Teaching information (for teachers only)
    subjects_taught = Column(JSON, default=list)
    grade_levels_taught = Column(JSON, default=list)
    department = Column(String(100), nullable=True)
    is_class_teacher = Column(Boolean, default=False)
    is_subject_teacher = Column(Boolean, default=False)
    
    # Account details
    bank_name = Column(String(100), nullable=True)
    account_name = Column(String(100), nullable=True)
    account_number = Column(String(10), nullable=True)
    monthly_deduction = Column(Float, default=0.0)
    
    # Class teacher assignment (if applicable)
    class_teacher_for = Column(UUID(as_uuid=True), ForeignKey('classes.id'), nullable=True)
    
    # Experience
    years_of_experience = Column(Integer, default=0)
    
    # Relationships
    user = relationship('User', backref='staff_profiles')
    
    __table_args__ = (
        UniqueConstraint('user_id', 'school_id', name='unique_staff_user_school'),
        UniqueConstraint('staff_id', 'school_id', name='unique_staff_id_school'),
        Index('idx_staff_school_dept', 'school_id', 'department', 'is_active'),
        Index('idx_staff_school_designation', 'school_id', 'designation', 'is_active'),
    )
    
    def to_dict(self):
        result = {
            'id': str(self.id),
            'user_id': str(self.user_id),
            'school_id': str(self.school_id),
            'staff_id': self.staff_id,
            'employee_number': self.employee_number,
            'designation': self.designation,
            'hire_date': self.hire_date.isoformat() if self.hire_date else None,
            'termination_date': self.termination_date.isoformat() if self.termination_date else None,
            'employment_status': self.employment_status,
            'employment_type': self.employment_type,
            'highest_degree': self.highest_degree,
            'certifications': self.certifications or [],
            'subjects_taught': self.subjects_taught or [],
            'grade_levels_taught': self.grade_levels_taught or [],
            'department': self.department,
            'track_id': None,  # TODO: Derive from department lookup
            'is_subject_teacher': self.is_subject_teacher,
            'is_class_teacher': self.is_class_teacher,
            'class_teacher_for': str(self.class_teacher_for) if self.class_teacher_for else None,
            'years_of_experience': self.years_of_experience,
            'bank_name': self.bank_name,
            'account_name': self.account_name,
            'account_number': self.account_number,
            'monthly_deduction': self.monthly_deduction,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }

        # Include user information
        if self.user:
            result['first_name'] = self.user.first_name
            result['last_name'] = self.user.last_name
            result['middle_name'] = self.user.middle_name
            result['full_name'] = self.user.full_name
            result['email'] = self.user.email
            result['phone_number'] = self.user.phone_number
            
        return result

# Keep Teacher as an alias for backward compatibility
Teacher = Staff


class EducationTrack(TenantAwareModel):
    """Education Track model - represents different educational approaches (e.g., Conventional, Islamic, Montessori)."""
    __tablename__ = 'education_tracks'
    
    name = Column(String(100), nullable=False)
    description = Column(Text, nullable=True)
    
    __table_args__ = (
        UniqueConstraint('name', 'school_id', name='unique_track_school'),
        Index('idx_track_school', 'school_id'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'name': self.name,
            'description': self.description,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class Department(TenantAwareModel):
    """Department model - represents educational levels within a track (e.g., Nursery, Primary, Secondary)."""
    __tablename__ = 'departments'
    
    name = Column(String(100), nullable=False)
    track_id = Column(UUID(as_uuid=True), ForeignKey('education_tracks.id'), nullable=True)
    description = Column(Text, nullable=True)
    
    # Relationships
    track = relationship('EducationTrack', foreign_keys=[track_id])
    
    __table_args__ = (
        UniqueConstraint('name', 'school_id', 'track_id', name='unique_dept_school_track'),
        Index('idx_dept_school_track', 'school_id', 'track_id'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'name': self.name,
            'track_id': str(self.track_id) if self.track_id else None,
            'description': self.description,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class Class(TenantAwareModel):
    """Class/Grade model - tenant-aware."""
    __tablename__ = 'classes'
    
    # Class identification
    class_name = Column(String(100), nullable=False)
    class_code = Column(String(20), nullable=True)
    grade_level = Column(String(20), nullable=True)  # Made nullable for backward compatibility
    
    # Hierarchical structure
    department_id = Column(UUID(as_uuid=True), ForeignKey('departments.id'), nullable=True)
    track_id = Column(UUID(as_uuid=True), ForeignKey('education_tracks.id'), nullable=True)
    
    # Academic information
    academic_year = Column(String(10), nullable=False)
    term = Column(String(10), nullable=True)
    
    # Capacity and enrollment
    max_capacity = Column(Integer, default=30)
    current_enrollment = Column(Integer, default=0)
    classroom_location = Column(String(100), nullable=True)
    
    # Class teacher
    class_staff_id = Column(UUID(as_uuid=True), nullable=True)
    
    # Relationships
    department = relationship('Department', foreign_keys=[department_id])
    track = relationship('EducationTrack', foreign_keys=[track_id])
    
    # Backward compatibility property
    @property
    def class_teacher_id(self):
        return self.class_staff_id
    
    @class_teacher_id.setter
    def class_teacher_id(self, value):
        self.class_staff_id = value
    
    __table_args__ = (
        UniqueConstraint('class_name', 'school_id', 'academic_year', name='unique_class_school_year'),
        Index('idx_class_school_grade', 'school_id', 'grade_level', 'academic_year'),
        Index('idx_class_school_dept', 'school_id', 'department_id'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'name': self.class_name,
            'class_name': self.class_name,
            'class_code': self.class_code,
            'code': self.class_code,
            'grade_level': self.grade_level,
            'department_id': str(self.department_id) if self.department_id else None,
            'track_id': str(self.track_id) if self.track_id else None,
            'academic_year': self.academic_year,
            'term': self.term,
            'max_capacity': self.max_capacity,
            'capacity': self.max_capacity,
            'current_enrollment': self.current_enrollment,
            'classroom_location': self.classroom_location,
            'location': self.classroom_location,
            'room_number': self.classroom_location,
            'class_staff_id': str(self.class_staff_id) if self.class_staff_id else None,
            'class_teacher_id': str(self.class_staff_id) if self.class_staff_id else None,  # Backward compatibility
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class Subject(TenantAwareModel):
    """Subject model - tenant-aware. Subjects are created once per school and can be offered by multiple classes."""
    __tablename__ = 'subjects'
    
    # Subject identification
    subject_name = Column(String(100), nullable=False)
    subject_code = Column(String(20), nullable=True)
    
    # Subject details
    description = Column(Text, nullable=True)
    is_core = Column(Boolean, default=True)  # Core vs Elective
    credit_hours = Column(Integer, default=1)
    
    # Subject category (for organization)
    category = Column(String(50), nullable=True)  # e.g., 'Science', 'Arts', 'Languages', 'Technical'
    
    # Academic information
    academic_year = Column(String(10), nullable=False)
    
    __table_args__ = (
        UniqueConstraint('subject_name', 'school_id', 'academic_year', name='unique_subject_school_year'),
        Index('idx_subject_school_category', 'school_id', 'category'),
        Index('idx_subject_school_core', 'school_id', 'is_core'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'name': self.subject_name,
            'subject_name': self.subject_name,
            'subject_code': self.subject_code,
            'code': self.subject_code,
            'description': self.description,
            'is_core': self.is_core,
            'credit_hours': self.credit_hours,
            'category': self.category,
            'academic_year': self.academic_year,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class ClassSubject(TenantAwareModel):
    """Class-Subject relationship - tenant-aware. Links subjects to classes."""
    __tablename__ = 'class_subjects'
    
    class_id = Column(UUID(as_uuid=True), ForeignKey('classes.id'), nullable=False)
    subject_id = Column(UUID(as_uuid=True), ForeignKey('subjects.id'), nullable=False)
    
    # Assignment metadata
    assigned_by = Column(UUID(as_uuid=True), nullable=True)
    assigned_at = Column(DateTime, default=datetime.utcnow)
    
    # Teaching assignment (optional)
    staff_id = Column(UUID(as_uuid=True), ForeignKey('staff.id'), nullable=True)
    
    # Relationships
    class_obj = relationship('Class', backref='subject_assignments')
    subject = relationship('Subject', backref='class_assignments')
    staff = relationship('Staff', backref='subject_assignments')
    
    # Keep teacher as alias for backward compatibility
    @property
    def teacher_id(self):
        return self.staff_id
    
    @teacher_id.setter
    def teacher_id(self, value):
        self.staff_id = value
    
    @property
    def teacher(self):
        return self.staff
    
    __table_args__ = (
        UniqueConstraint('class_id', 'subject_id', 'school_id', name='unique_class_subject_school'),
        Index('idx_class_subject_school', 'school_id', 'class_id', 'subject_id'),
        Index('idx_class_subject_staff', 'staff_id', 'school_id'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'class_id': str(self.class_id),
            'subject_id': str(self.subject_id),
            'staff_id': str(self.staff_id) if self.staff_id else None,
            'teacher_id': str(self.staff_id) if self.staff_id else None,  # Backward compatibility
            'assigned_by': str(self.assigned_by) if self.assigned_by else None,
            'assigned_at': self.assigned_at.isoformat() if self.assigned_at else None,
            'class': self.class_obj.to_dict() if self.class_obj else None,
            'subject': self.subject.to_dict() if self.subject else None,
            'staff': self.staff.to_dict() if self.staff else None,
            'teacher': self.staff.to_dict() if self.staff else None,  # Backward compatibility
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class StudentClasses(TenantAwareModel):
    """Student-Class relationship with track support - tenant-aware. Links students to classes using admission numbers."""
    __tablename__ = 'student_classes'
    
    student_id = Column(UUID(as_uuid=True), ForeignKey('students.id'), nullable=False)
    class_id = Column(UUID(as_uuid=True), ForeignKey('classes.id'), nullable=False)
    track_id = Column(UUID(as_uuid=True), ForeignKey('education_tracks.id'), nullable=False)
    
    # Student identification
    admission_number = Column(String(50), nullable=False, index=True)
    
    # Academic information
    academic_year = Column(String(10), nullable=False)
    term = Column(String(10), default='First Term')
    enrollment_date = Column(Date, default=date.today)
    
    # Relationships
    student = relationship('Student', backref='class_enrollments')
    class_obj = relationship('Class', backref='student_enrollments')
    track = relationship('EducationTrack', backref='student_enrollments')
    
    __table_args__ = (
        UniqueConstraint('student_id', 'track_id', 'academic_year', 'school_id', name='unique_student_track_year'),
        UniqueConstraint('admission_number', 'track_id', 'academic_year', 'school_id', name='unique_admission_track_year'),
        Index('idx_student_classes_school', 'school_id', 'is_active'),
        Index('idx_student_classes_student', 'student_id', 'is_active'),
        Index('idx_student_classes_track', 'track_id', 'is_active'),
        Index('idx_student_classes_admission', 'admission_number', 'school_id'),
        Index('idx_student_classes_class', 'class_id', 'is_active'),
        Index('idx_student_classes_year', 'academic_year', 'school_id'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'student_id': str(self.student_id),
            'class_id': str(self.class_id),
            'track_id': str(self.track_id),
            'admission_number': self.admission_number,
            'academic_year': self.academic_year,
            'term': self.term,
            'enrollment_date': self.enrollment_date.isoformat() if self.enrollment_date else None,
            'student': self.student.to_dict() if self.student else None,
            'class': self.class_obj.to_dict() if self.class_obj else None,
            'track': self.track.to_dict() if self.track else None,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None
        }


# ============================================================================
# ACADEMIC MODELS
# ============================================================================

class Attendance(TenantAwareModel):
    """Attendance tracking - tenant-aware."""
    __tablename__ = 'attendance'
    
    student_id = Column(UUID(as_uuid=True), ForeignKey('students.id'), nullable=False)
    class_id = Column(UUID(as_uuid=True), ForeignKey('classes.id'), nullable=False)
    staff_id = Column(UUID(as_uuid=True), ForeignKey('staff.id'), nullable=False)
    
    # Attendance details
    attendance_date = Column(Date, nullable=False)
    status = Column(String(20), nullable=False, default=AttendanceStatus.PRESENT.value)
    arrival_time = Column(DateTime, nullable=True)
    departure_time = Column(DateTime, nullable=True)
    notes = Column(Text, nullable=True)
    
    # Relationships
    student = relationship('Student')
    class_obj = relationship('Class')
    staff = relationship('Staff')
    
    # Backward compatibility
    @property
    def teacher_id(self):
        return self.staff_id
    
    @teacher_id.setter
    def teacher_id(self, value):
        self.staff_id = value
    
    @property
    def teacher(self):
        return self.staff
    
    __table_args__ = (
        UniqueConstraint('student_id', 'class_id', 'attendance_date', 'school_id', name='unique_student_attendance'),
        Index('idx_attendance_school_date', 'school_id', 'attendance_date'),
        Index('idx_attendance_class_date', 'class_id', 'attendance_date'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'student_id': str(self.student_id),
            'class_id': str(self.class_id),
            'staff_id': str(self.staff_id),
            'teacher_id': str(self.staff_id),  # Backward compatibility
            'attendance_date': self.attendance_date.isoformat() if self.attendance_date else None,
            'status': self.status,
            'arrival_time': self.arrival_time.isoformat() if self.arrival_time else None,
            'departure_time': self.departure_time.isoformat() if self.departure_time else None,
            'notes': self.notes,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class Exam(TenantAwareModel):
    """Exam/Assessment model - tenant-aware."""
    __tablename__ = 'exams'
    
    # Exam details
    exam_name = Column(String(255), nullable=False)
    subject = Column(String(100), nullable=False)
    class_id = Column(UUID(as_uuid=True), ForeignKey('classes.id'), nullable=False)
    staff_id = Column(UUID(as_uuid=True), ForeignKey('staff.id'), nullable=False)
    
    # Exam metadata
    exam_date = Column(Date, nullable=False)
    duration_minutes = Column(Integer, nullable=True)
    total_marks = Column(Integer, nullable=False)
    passing_marks = Column(Integer, nullable=True)
    
    # Exam type and settings
    exam_type = Column(String(50), nullable=False)  # quiz, test, midterm, final, assignment
    term = Column(String(10), nullable=True)
    academic_year = Column(String(10), nullable=False)
    
    # Relationships
    class_obj = relationship('Class')
    staff = relationship('Staff')
    
    # Backward compatibility
    @property
    def teacher_id(self):
        return self.staff_id
    
    @teacher_id.setter
    def teacher_id(self, value):
        self.staff_id = value
    
    @property
    def teacher(self):
        return self.staff
    
    __table_args__ = (
        Index('idx_exam_school_class', 'school_id', 'class_id', 'exam_date'),
        Index('idx_exam_school_subject', 'school_id', 'subject', 'academic_year'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'exam_name': self.exam_name,
            'subject': self.subject,
            'class_id': str(self.class_id),
            'staff_id': str(self.staff_id),
            'teacher_id': str(self.staff_id),  # Backward compatibility
            'exam_date': self.exam_date.isoformat() if self.exam_date else None,
            'duration_minutes': self.duration_minutes,
            'total_marks': self.total_marks,
            'passing_marks': self.passing_marks,
            'exam_type': self.exam_type,
            'term': self.term,
            'academic_year': self.academic_year,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class ExamResult(TenantAwareModel):
    """Exam results - tenant-aware."""
    __tablename__ = 'exam_results'
    
    exam_id = Column(UUID(as_uuid=True), ForeignKey('exams.id'), nullable=False)
    student_id = Column(UUID(as_uuid=True), ForeignKey('students.id'), nullable=False)
    
    # Results
    marks_obtained = Column(Integer, nullable=False)
    grade = Column(String(5), nullable=True)  # A+, A, B+, B, C, D, F
    percentage = Column(Integer, nullable=True)
    position = Column(Integer, nullable=True)  # Position in class
    
    # Metadata
    graded_by = Column(UUID(as_uuid=True), ForeignKey('staff.id'), nullable=False)
    graded_at = Column(DateTime, default=datetime.utcnow)
    remarks = Column(Text, nullable=True)
    
    # Relationships
    exam = relationship('Exam')
    student = relationship('Student')
    graded_by_staff = relationship('Staff')
    
    # Backward compatibility
    @property
    def graded_by_teacher(self):
        return self.graded_by_staff
    
    __table_args__ = (
        UniqueConstraint('exam_id', 'student_id', 'school_id', name='unique_exam_student_result'),
        Index('idx_result_school_exam', 'school_id', 'exam_id'),
        Index('idx_result_student_school', 'student_id', 'school_id'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'exam_id': str(self.exam_id),
            'student_id': str(self.student_id),
            'marks_obtained': self.marks_obtained,
            'grade': self.grade,
            'percentage': self.percentage,
            'position': self.position,
            'graded_by': str(self.graded_by),
            'graded_at': self.graded_at.isoformat() if self.graded_at else None,
            'remarks': self.remarks,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class StudentFeedback(TenantAwareModel):
    """Student feedback - tenant-aware."""
    __tablename__ = 'student_feedback'
    
    student_id = Column(UUID(as_uuid=True), ForeignKey('students.id'), nullable=False)
    staff_id = Column(UUID(as_uuid=True), ForeignKey('staff.id'), nullable=False)
    
    # Feedback details
    feedback_type = Column(String(50), nullable=False)  # academic, behavioral, general
    subject = Column(String(100), nullable=True)
    content = Column(Text, nullable=False)
    rating = Column(String(20), nullable=True)  # excellent, good, satisfactory, needs_improvement
    
    # Metadata
    feedback_date = Column(Date, default=date.today)
    term = Column(String(10), nullable=True)
    academic_year = Column(String(10), nullable=False)
    
    # Relationships
    student = relationship('Student')
    staff = relationship('Staff')
    
    # Backward compatibility
    @property
    def teacher_id(self):
        return self.staff_id
    
    @teacher_id.setter
    def teacher_id(self, value):
        self.staff_id = value
    
    @property
    def teacher(self):
        return self.staff
    
    __table_args__ = (
        Index('idx_feedback_school_student', 'school_id', 'student_id', 'feedback_date'),
        Index('idx_feedback_school_staff', 'school_id', 'staff_id', 'feedback_date'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'student_id': str(self.student_id),
            'staff_id': str(self.staff_id),
            'teacher_id': str(self.staff_id),  # Backward compatibility
            'feedback_type': self.feedback_type,
            'subject': self.subject,
            'content': self.content,
            'rating': self.rating,
            'feedback_date': self.feedback_date.isoformat() if self.feedback_date else None,
            'term': self.term,
            'academic_year': self.academic_year,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


# ============================================================================
# ASSESSMENT MODELS (Comprehensive Student Assessment System)
# ============================================================================

class Assessment(TenantAwareModel):
    """Student Assessment model - comprehensive term-based assessment including behavioral traits."""
    __tablename__ = 'assessments'
    
    # Student reference - using admission_number for compatibility
    admission_number = Column(String(50), nullable=False, index=True)
    student_id = Column(UUID(as_uuid=True), ForeignKey('students.id'), nullable=True)  # Optional FK for flexibility
    
    # Academic period
    session = Column(String(10), nullable=False)  # e.g., "2024/2025"
    term = Column(String(15), nullable=False)  # e.g., "First Term", "Second Term"
    
    # Attendance
    attendance = Column(Integer, default=0)
    
    # Behavioral assessments (scored 1-5 or similar scale)
    fluency = Column(Integer, default=0)
    handwriting = Column(Integer, default=0)
    game = Column(Integer, default=0)
    initiative = Column(Integer, default=0)
    critical_thinking = Column(Integer, default=0)
    punctuality = Column(Integer, default=0)
    attentiveness = Column(Integer, default=0)
    neatness = Column(Integer, default=0)
    self_discipline = Column(Integer, default=0)
    politeness = Column(Integer, default=0)
    
    # Comments
    class_teacher_comment = Column(Text, nullable=True)
    head_teacher_comment = Column(Text, nullable=True)
    
    # Relationships
    student = relationship('Student', backref='assessments', foreign_keys=[student_id])
    scores = relationship('SubjectScore', back_populates='assessment', cascade='all, delete-orphan')
    
    __table_args__ = (
        UniqueConstraint('school_id', 'admission_number', 'session', 'term', name='uq_assessment_school_student_term'),
        Index('idx_assessment_school_session', 'school_id', 'session', 'term'),
        Index('idx_assessment_admission', 'admission_number'),
    )
    
    def __repr__(self):
        return f"<Assessment {self.admission_number} - {self.session} {self.term}>"
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'admission_number': self.admission_number,
            'student_id': str(self.student_id) if self.student_id else None,
            'session': self.session,
            'term': self.term,
            'attendance': self.attendance,
            'fluency': self.fluency,
            'handwriting': self.handwriting,
            'game': self.game,
            'initiative': self.initiative,
            'critical_thinking': self.critical_thinking,
            'punctuality': self.punctuality,
            'attentiveness': self.attentiveness,
            'neatness': self.neatness,
            'self_discipline': self.self_discipline,
            'politeness': self.politeness,
            'class_teacher_comment': self.class_teacher_comment,
            'head_teacher_comment': self.head_teacher_comment,
            'scores': [score.to_dict() for score in self.scores] if self.scores else [],
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None
        }


class SubjectScore(TenantAwareModel):
    """Subject Score model - individual subject scores within an assessment."""
    __tablename__ = 'subject_scores'
    
    # Assessment reference
    assessment_id = Column(UUID(as_uuid=True), ForeignKey('assessments.id'), nullable=False)
    
    # Subject reference
    class_subject_id = Column(UUID(as_uuid=True), ForeignKey('class_subjects.id'), nullable=False)
    subject_id = Column(UUID(as_uuid=True), ForeignKey('subjects.id'), nullable=True)  # Direct subject reference for flexibility
    
    # Score components
    first_ca = Column(Float, default=0.0)  # First Continuous Assessment
    second_ca = Column(Float, default=0.0)  # Second Continuous Assessment
    exam = Column(Float, default=0.0)  # Exam score
    
    # Calculated fields
    total_score = Column(Float, default=0.0)  # Sum of CA + Exam
    grade = Column(String(5), nullable=True)  # A+, A, B+, B, C+, C, D, F
    position = Column(Integer, nullable=True)  # Position in class for this subject
    
    # Additional fields
    remarks = Column(String(255), nullable=True)
    
    # Relationships
    assessment = relationship('Assessment', back_populates='scores')
    class_subject = relationship('ClassSubject')
    subject = relationship('Subject', foreign_keys=[subject_id])
    
    __table_args__ = (
        UniqueConstraint('assessment_id', 'class_subject_id', name='uq_subject_score_assessment_subject'),
        Index('idx_subject_score_assessment', 'assessment_id'),
        Index('idx_subject_score_class_subject', 'class_subject_id'),
        Index('idx_subject_score_school', 'school_id'),
    )
    
    def __repr__(self):
        return f"<SubjectScore ClassSubject:{self.class_subject_id} Assessment:{self.assessment_id}>"
    
    def calculate_total_and_grade(self):
        """Calculate total score and assign grade."""
        self.total_score = self.first_ca + self.second_ca + self.exam
        
        # Grade calculation (adjust thresholds as needed)
        if self.total_score >= 90:
            self.grade = 'A+'
        elif self.total_score >= 80:
            self.grade = 'A'
        elif self.total_score >= 70:
            self.grade = 'B+'
        elif self.total_score >= 60:
            self.grade = 'B'
        elif self.total_score >= 50:
            self.grade = 'C+'
        elif self.total_score >= 40:
            self.grade = 'C'
        elif self.total_score >= 30:
            self.grade = 'D'
        else:
            self.grade = 'F'
    
    def to_dict(self):
        # Calculate total score and grade on-the-fly
        total = self.first_ca + self.second_ca + self.exam
        
        # Calculate grade based on total score
        if total >= 90:
            grade = 'A+'
        elif total >= 70:
            grade = 'A'
        elif total >= 60:
            grade = 'B'
        elif total >= 50:
            grade = 'C'
        elif total >= 45:
            grade = 'D'
        elif total >= 40:
            grade = 'E'
        else:
            grade = 'F'
        
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'assessment_id': str(self.assessment_id),
            'class_subject_id': str(self.class_subject_id),
            'subject_id': str(self.subject_id) if self.subject_id else None,
            'first_ca': self.first_ca,
            'second_ca': self.second_ca,
            'exam': self.exam,
            'total_score': total,
            'grade': grade,
            'position': self.position,
            'remarks': self.remarks,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None
        }


# ============================================================================
# FINANCIAL MODELS
# ============================================================================

class FeeStructure(TenantAwareModel):
    """Fee structure - tenant-aware."""
    __tablename__ = 'fee_structures'
    
    # Fee details
    fee_name = Column(String(255), nullable=False)
    fee_type = Column(String(50), nullable=False)  # tuition, transport, activity, etc.
    amount = Column(Integer, nullable=False)  # Amount in smallest currency unit (kobo for NGN)
    
    # Applicability
    grade_levels = Column(JSON, default=list)  # Which grades this fee applies to
    is_mandatory = Column(Boolean, default=True)
    is_recurring = Column(Boolean, default=True)
    
    # Timing
    due_date = Column(Date, nullable=True)
    academic_year = Column(String(10), nullable=False)
    term = Column(String(10), nullable=True)
    
    __table_args__ = (
        Index('idx_fee_school_year', 'school_id', 'academic_year'),
        Index('idx_fee_school_type', 'school_id', 'fee_type'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'fee_name': self.fee_name,
            'fee_type': self.fee_type,
            'amount': self.amount,
            'grade_levels': self.grade_levels or [],
            'is_mandatory': self.is_mandatory,
            'is_recurring': self.is_recurring,
            'due_date': self.due_date.isoformat() if self.due_date else None,
            'academic_year': self.academic_year,
            'term': self.term,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class Invoice(TenantAwareModel):
    """Invoice model - tenant-aware."""
    __tablename__ = 'invoices'
    
    student_id = Column(UUID(as_uuid=True), ForeignKey('students.id'), nullable=False)
    parent_id = Column(UUID(as_uuid=True), ForeignKey('parents.id'), nullable=False)
    
    # Invoice details
    invoice_number = Column(String(50), nullable=False, unique=True)
    total_amount = Column(Integer, nullable=False)  # Amount in smallest currency unit
    amount_paid = Column(Integer, default=0)
    balance_due = Column(Integer, nullable=False)
    
    # Dates
    issue_date = Column(Date, default=date.today)
    due_date = Column(Date, nullable=False)
    
    # Status and metadata
    status = Column(String(20), default=InvoiceStatus.DRAFT.value)
    term = Column(String(10), nullable=True)
    academic_year = Column(String(10), nullable=False)
    notes = Column(Text, nullable=True)
    
    # Relationships
    student = relationship('Student')
    parent = relationship('Parent')
    
    __table_args__ = (
        Index('idx_invoice_school_student', 'school_id', 'student_id'),
        Index('idx_invoice_school_parent', 'school_id', 'parent_id'),
        Index('idx_invoice_school_status', 'school_id', 'status', 'due_date'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'student_id': str(self.student_id),
            'parent_id': str(self.parent_id),
            'invoice_number': self.invoice_number,
            'total_amount': self.total_amount,
            'amount_paid': self.amount_paid,
            'balance_due': self.balance_due,
            'issue_date': self.issue_date.isoformat() if self.issue_date else None,
            'due_date': self.due_date.isoformat() if self.due_date else None,
            'status': self.status,
            'term': self.term,
            'academic_year': self.academic_year,
            'notes': self.notes,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class InvoiceItem(TenantAwareModel):
    """Invoice line items - tenant-aware."""
    __tablename__ = 'invoice_items'
    
    invoice_id = Column(UUID(as_uuid=True), ForeignKey('invoices.id'), nullable=False)
    fee_structure_id = Column(UUID(as_uuid=True), ForeignKey('fee_structures.id'), nullable=True)
    
    # Item details
    description = Column(String(255), nullable=False)
    quantity = Column(Integer, default=1)
    unit_amount = Column(Integer, nullable=False)
    total_amount = Column(Integer, nullable=False)
    
    # Relationships
    invoice = relationship('Invoice', backref='items')
    fee_structure = relationship('FeeStructure')
    
    __table_args__ = (
        Index('idx_invoice_item_school', 'school_id', 'invoice_id'),
    )


class PaymentNotification(TenantAwareModel):
    """Payment notifications from parents - tenant-aware."""
    __tablename__ = 'payment_notifications'
    
    invoice_id = Column(UUID(as_uuid=True), ForeignKey('invoices.id'), nullable=False)
    parent_id = Column(UUID(as_uuid=True), ForeignKey('parents.id'), nullable=False)
    
    # Payment details
    amount = Column(Integer, nullable=False)
    payment_method = Column(String(50), nullable=False)
    payment_reference = Column(String(100), nullable=True)
    proof_of_payment_url = Column(String(500), nullable=True)
    notes = Column(Text, nullable=True)
    
    # Status and review
    status = Column(String(20), default=PaymentStatus.PENDING.value)
    reviewed_by = Column(UUID(as_uuid=True), nullable=True)
    reviewed_at = Column(DateTime, nullable=True)
    review_notes = Column(Text, nullable=True)
    
    # Relationships
    invoice = relationship('Invoice')
    parent = relationship('Parent')
    
    __table_args__ = (
        Index('idx_payment_school_status', 'school_id', 'status', 'created_at'),
        Index('idx_payment_school_parent', 'school_id', 'parent_id'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'invoice_id': str(self.invoice_id),
            'parent_id': str(self.parent_id),
            'amount': self.amount,
            'payment_method': self.payment_method,
            'payment_reference': self.payment_reference,
            'proof_of_payment_url': self.proof_of_payment_url,
            'notes': self.notes,
            'status': self.status,
            'reviewed_by': str(self.reviewed_by) if self.reviewed_by else None,
            'reviewed_at': self.reviewed_at.isoformat() if self.reviewed_at else None,
            'review_notes': self.review_notes,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }
# ============================================================================
# COMMUNICATION MODELS
# ============================================================================

class MessageThread(TenantAwareModel):
    """Message thread for conversations - tenant-aware."""
    __tablename__ = 'message_threads'
    
    # Thread details
    subject = Column(String(255), nullable=False)
    thread_type = Column(String(50), default='individual')  # individual, group, announcement
    
    # Participants (stored as JSON array of user IDs)
    participants = Column(JSON, default=list)
    
    # Thread metadata
    last_message_at = Column(DateTime, nullable=True)
    message_count = Column(Integer, default=0)
    
    __table_args__ = (
        Index('idx_thread_school_type', 'school_id', 'thread_type', 'last_message_at'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'subject': self.subject,
            'thread_type': self.thread_type,
            'participants': self.participants or [],
            'last_message_at': self.last_message_at.isoformat() if self.last_message_at else None,
            'message_count': self.message_count,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class Message(TenantAwareModel):
    """Individual messages - tenant-aware."""
    __tablename__ = 'messages'
    
    thread_id = Column(UUID(as_uuid=True), ForeignKey('message_threads.id'), nullable=False)
    sender_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False)
    
    # Message content
    content = Column(Text, nullable=False)
    message_type = Column(String(50), default='text')  # text, announcement, urgent
    attachments = Column(JSON, default=list)  # File attachments
    
    # Message metadata
    sent_at = Column(DateTime, default=datetime.utcnow)
    priority = Column(String(20), default='normal')  # low, normal, high, urgent
    
    # Relationships
    thread = relationship('MessageThread', backref='messages')
    sender = relationship('User')
    
    __table_args__ = (
        Index('idx_message_school_thread', 'school_id', 'thread_id', 'sent_at'),
        Index('idx_message_school_sender', 'school_id', 'sender_id', 'sent_at'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'thread_id': str(self.thread_id),
            'sender_id': str(self.sender_id),
            'content': self.content,
            'message_type': self.message_type,
            'attachments': self.attachments or [],
            'sent_at': self.sent_at.isoformat() if self.sent_at else None,
            'priority': self.priority,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class MessageRecipient(TenantAwareModel):
    """Message read status tracking - tenant-aware."""
    __tablename__ = 'message_recipients'
    
    message_id = Column(UUID(as_uuid=True), ForeignKey('messages.id'), nullable=False)
    recipient_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False)
    
    # Read status
    is_read = Column(Boolean, default=False)
    read_at = Column(DateTime, nullable=True)
    
    # Relationships
    message = relationship('Message')
    recipient = relationship('User')
    
    __table_args__ = (
        UniqueConstraint('message_id', 'recipient_id', 'school_id', name='unique_message_recipient'),
        Index('idx_recipient_school_user', 'school_id', 'recipient_id', 'is_read'),
    )


class Notification(TenantAwareModel):
    """System notifications - tenant-aware."""
    __tablename__ = 'notifications'
    
    # Notification details
    title = Column(String(255), nullable=False)
    content = Column(Text, nullable=False)
    notification_type = Column(String(50), nullable=False)  # academic, financial, system, etc.
    
    # Recipients (JSON array of user IDs or roles)
    recipients = Column(JSON, default=list)
    
    # Notification metadata
    priority = Column(String(20), default='normal')
    expires_at = Column(DateTime, nullable=True)
    
    __table_args__ = (
        Index('idx_notification_school_type', 'school_id', 'notification_type', 'created_at'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'title': self.title,
            'content': self.content,
            'notification_type': self.notification_type,
            'recipients': self.recipients or [],
            'priority': self.priority,
            'expires_at': self.expires_at.isoformat() if self.expires_at else None,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


# ============================================================================
# EXAMINATION MODELS
# ============================================================================

class Examination(TenantAwareModel):
    """Examination model - represents a specific exam instance (e.g., CA 1 for Math in JSS 1)."""
    __tablename__ = 'examinations'
    
    # Exam details
    title = Column(String(255), nullable=False)  # e.g., "First Term CA 1 2024/2025"
    exam_type = Column(String(50), nullable=False)  # CA 1, CA 2, Final Examination
    
    # Context
    subject_id = Column(UUID(as_uuid=True), ForeignKey('subjects.id'), nullable=False)
    class_id = Column(UUID(as_uuid=True), ForeignKey('classes.id'), nullable=False)
    term = Column(String(20), nullable=False)  # First Term, Second Term, Third Term
    session = Column(String(20), nullable=False)  # 2024/2025
    
    # Metadata
    created_by = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False)
    is_published = Column(Boolean, default=False)
    start_time = Column(DateTime, nullable=True)
    end_time = Column(DateTime, nullable=True)
    duration_minutes = Column(Integer, default=60)
    total_marks = Column(Integer, nullable=True)  # Total marks for the examination
    
    # Relationships
    subject = relationship('Subject')
    class_obj = relationship('Class')
    creator = relationship('User')
    questions = relationship('Question', backref='examination', cascade='all, delete-orphan')
    
    __table_args__ = (
        Index('idx_exam_school_context', 'school_id', 'class_id', 'subject_id', 'term', 'session'),
    )
    
    def to_dict(self):
        # Get track and department names through class relationship
        track_name = None
        department_name = None
        if self.class_obj:
            if hasattr(self.class_obj, 'department') and self.class_obj.department:
                department_name = self.class_obj.department.name
                if hasattr(self.class_obj.department, 'track') and self.class_obj.department.track:
                    track_name = self.class_obj.department.track.name
        
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'title': self.title,
            'exam_type': self.exam_type,
            'subject_id': str(self.subject_id),
            'subject_name': self.subject.subject_name if self.subject else None,
            'class_id': str(self.class_id),
            'class_name': self.class_obj.class_name if self.class_obj else None,
            'track_name': track_name,
            'department_name': department_name,
            'term': self.term,
            'session': self.session,
            'created_by': str(self.created_by),
            'is_published': self.is_published,
            'start_time': self.start_time.isoformat() + 'Z' if self.start_time else None,
            'end_time': self.end_time.isoformat() + 'Z' if self.end_time else None,
            'duration_minutes': self.duration_minutes,
            'total_marks': self.total_marks,
            'question_count': len(self.questions),
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class Question(TenantAwareModel):
    """Question model - individual questions for an examination."""
    __tablename__ = 'questions'
    
    examination_id = Column(UUID(as_uuid=True), ForeignKey('examinations.id'), nullable=False)
    
    # Content
    instruction = Column(Text, nullable=True)
    question_text = Column(Text, nullable=False)
    question_image_url = Column(String(500), nullable=True)
    
    # Options
    option_a = Column(Text, nullable=False)
    option_b = Column(Text, nullable=False)
    option_c = Column(Text, nullable=True)
    option_d = Column(Text, nullable=True)
    option_e = Column(Text, nullable=True)
    
    # Answer
    correct_answer = Column(String(1), nullable=False)  # A, B, C, D, E
    marks = Column(Float, default=1.0)
    
    __table_args__ = (
        Index('idx_question_exam', 'examination_id'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'examination_id': str(self.examination_id),
            'instruction': self.instruction,
            'question_text': self.question_text,
            'question_image_url': self.question_image_url,
            'options': {
                'A': self.option_a,
                'B': self.option_b,
                'C': self.option_c,
                'D': self.option_d,
                'E': self.option_e
            },
            'correct_answer': self.correct_answer,
            'marks': self.marks,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class ExaminationSubmission(TenantAwareModel):
    """Examination Submission model - tracks student submissions for online exams."""
    __tablename__ = 'examination_submissions'
    
    examination_id = Column(UUID(as_uuid=True), ForeignKey('examinations.id'), nullable=False)
    student_id = Column(UUID(as_uuid=True), ForeignKey('students.id'), nullable=False)
    status = Column(String(20), default='in_progress')  # in_progress, submitted, graded
    score = Column(Float, nullable=True)
    attempt_count = Column(Integer, default=1)
    started_at = Column(DateTime, nullable=True)  # When student began the exam
    submitted_at = Column(DateTime, nullable=True)
    
    # Relationships
    examination = relationship('Examination')
    student = relationship('Student')
    
    __table_args__ = (
        Index('idx_submission_exam_student', 'examination_id', 'student_id'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'examination_id': str(self.examination_id),
            'student_id': str(self.student_id),
            'status': self.status,
            'score': self.score,
            'started_at': self.started_at.isoformat() if self.started_at else None,
            'submitted_at': self.submitted_at.isoformat() if self.submitted_at else None,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


# ============================================================================
# CALENDAR MODELS (Academic Sessions and Terms)
# ============================================================================

class AcademicSession(TenantAwareModel):
    """Academic Session model - represents an academic year/session for a school."""
    __tablename__ = 'academic_sessions'
    
    # Session identification
    session_name = Column(String(100), nullable=False)
    session_year = Column(String(20), nullable=False)
    
    # Session dates
    start_date = Column(Date, nullable=False)
    end_date = Column(Date, nullable=True)  # Optional for current sessions
    
    # Session status
    is_current_session = Column(Boolean, default=False)
    status = Column(String(20), default='active')  # active, completed, planned
    notes = Column(Text, nullable=True)
    
    # Relationships
    terms = relationship('SchoolCalendar', backref='session', cascade='all, delete-orphan')
    
    __table_args__ = (
        UniqueConstraint('school_id', 'session_year', name='unique_school_session_year'),
        Index('idx_academic_sessions_school_id', 'school_id'),
        Index('idx_academic_sessions_year', 'session_year'),
        Index('idx_academic_sessions_current', 'is_current_session'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'session_name': self.session_name,
            'session_year': self.session_year,
            'start_date': self.start_date.isoformat() if self.start_date else None,
            'end_date': self.end_date.isoformat() if self.end_date else None,
            'is_current_session': self.is_current_session,
            'status': self.status,
            'notes': self.notes,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None
        }


class SchoolCalendar(TenantAwareModel):
    """School Calendar model - represents terms within academic sessions."""
    __tablename__ = 'school_calendar'
    
    # Session relationship
    session_id = Column(UUID(as_uuid=True), ForeignKey('academic_sessions.id'), nullable=True)
    
    # Term identification
    academic_year = Column(String(10), nullable=False)  # For backward compatibility
    term_number = Column(Integer, nullable=False)
    term_name = Column(String(100), nullable=False)
    
    # Term dates
    term_start_date = Column(Date, nullable=False)
    term_end_date = Column(Date, nullable=True)  # Optional for current terms
    
    # Holiday dates
    holiday_start_date = Column(Date, nullable=True)
    holiday_end_date = Column(Date, nullable=True)
    
    # Term status
    is_current_term = Column(Boolean, default=False)
    notes = Column(Text, nullable=True)
    
    __table_args__ = (
        UniqueConstraint('session_id', 'term_number', name='unique_session_term'),
        Index('idx_school_calendar_school_id', 'school_id'),
        Index('idx_school_calendar_session_id', 'session_id'),
        Index('idx_school_calendar_academic_year', 'academic_year'),
        Index('idx_school_calendar_current_term', 'is_current_term'),
        Index('idx_school_calendar_dates', 'term_start_date', 'term_end_date'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'session_id': str(self.session_id) if self.session_id else None,
            'academic_year': self.academic_year,
            'term_number': self.term_number,
            'term_name': self.term_name,
            'term_start_date': self.term_start_date.isoformat() if self.term_start_date else None,
            'term_end_date': self.term_end_date.isoformat() if self.term_end_date else None,
            'holiday_start_date': self.holiday_start_date.isoformat() if self.holiday_start_date else None,
            'holiday_end_date': self.holiday_end_date.isoformat() if self.holiday_end_date else None,
            'is_current_term': self.is_current_term,
            'notes': self.notes,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None
        }


# ============================================================================
# AUTHENTICATION MODELS (Shared)
# ============================================================================

class UserSession(BaseModel):
    """User session tracking - shared table."""
    __tablename__ = 'user_sessions'
    
    user_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False)
    school_id = Column(UUID(as_uuid=True), nullable=True)  # Current school context
    role_id = Column(UUID(as_uuid=True), nullable=True)    # Current role context
    
    # Session details
    session_token = Column(String(255), unique=True, nullable=False, index=True)
    refresh_token = Column(String(255), unique=True, nullable=True, index=True)
    
    # Session metadata
    ip_address = Column(String(45), nullable=True)
    user_agent = Column(Text, nullable=True)
    expires_at = Column(DateTime, nullable=False)
    last_activity_at = Column(DateTime, default=datetime.utcnow)
    
    # Relationships
    user = relationship('User', backref='sessions')
    
    def is_expired(self):
        return datetime.utcnow() > self.expires_at
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'user_id': str(self.user_id),
            'school_id': str(self.school_id) if self.school_id else None,
            'role_id': str(self.role_id) if self.role_id else None,
            'expires_at': self.expires_at.isoformat() if self.expires_at else None,
            'last_activity_at': self.last_activity_at.isoformat() if self.last_activity_at else None,
            'is_expired': self.is_expired(),
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class ActivationCode(BaseModel):
    """Phone activation codes - shared table."""
    __tablename__ = 'activation_codes'
    
    phone_number = Column(String(20), nullable=False, index=True)
    code = Column(String(10), nullable=False)
    
    # Code metadata
    expires_at = Column(DateTime, nullable=False)
    is_used = Column(Boolean, default=False)
    attempts = Column(Integer, default=0)
    
    def is_expired(self):
        return datetime.utcnow() > self.expires_at
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'phone_number': self.phone_number,
            'expires_at': self.expires_at.isoformat() if self.expires_at else None,
            'is_used': self.is_used,
            'attempts': self.attempts,
            'is_expired': self.is_expired(),
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


# ============================================================================
# TIMETABLE MODELS
# ============================================================================

class SchoolTimetable(TenantAwareModel):
    """General school timetable for fixed activities (resumption, assembly, breaks, etc.)"""
    __tablename__ = 'school_timetables'
    
    day_of_week = Column(String(10), nullable=False)  # Monday, Tuesday, etc.
    activity_type = Column(String(20), nullable=False)  # resumption, assembly, break, prayer, sports, lesson_period
    start_time = Column(String(8), nullable=False)  # HH:MM format (e.g., "08:00")
    end_time = Column(String(8), nullable=False)  # HH:MM format (e.g., "09:00")
    title = Column(String(100), nullable=False)
    description = Column(Text, nullable=True)
    
    __table_args__ = (
        Index('idx_school_timetable_day', 'school_id', 'day_of_week'),
        Index('idx_school_timetable_activity', 'school_id', 'activity_type'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'day_of_week': self.day_of_week,
            'activity_type': self.activity_type,
            'start_time': self.start_time,
            'end_time': self.end_time,
            'title': self.title,
            'description': self.description,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class ClassTimetable(TenantAwareModel):
    """Class-specific lesson timetable"""
    __tablename__ = 'class_timetables'
    
    class_id = Column(UUID(as_uuid=True), ForeignKey('classes.id'), nullable=False)
    subject_id = Column(UUID(as_uuid=True), ForeignKey('subjects.id'), nullable=False)
    teacher_id = Column(UUID(as_uuid=True), ForeignKey('staff.id'), nullable=True)  # staff table
    day_of_week = Column(String(10), nullable=False)
    start_time = Column(String(8), nullable=False)  # HH:MM format
    end_time = Column(String(8), nullable=False)  # HH:MM format
    room_number = Column(String(20), nullable=True)
    
    # Relationships
    class_obj = relationship('Class', foreign_keys=[class_id])
    subject = relationship('Subject', foreign_keys=[subject_id])
    teacher = relationship('Staff', foreign_keys=[teacher_id])
    
    __table_args__ = (
        Index('idx_class_timetable_class', 'school_id', 'class_id', 'day_of_week'),
        Index('idx_class_timetable_teacher', 'school_id', 'teacher_id'),
    )
    
    def to_dict(self):
        return {
            'id': str(self.id),
            'school_id': str(self.school_id),
            'class_id': str(self.class_id),
            'subject_id': str(self.subject_id),
            'teacher_id': str(self.teacher_id) if self.teacher_id else None,
            'day_of_week': self.day_of_week,
            'start_time': self.start_time,
            'end_time': self.end_time,
            'room_number': self.room_number,
            'subject_name': self.subject.subject_name if self.subject else None,
            'teacher_name': self.teacher.user.full_name if self.teacher and self.teacher.user else None,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

def init_database(app):
    """Initialize database with app context."""
    db.init_app(app)
    return db


def create_all_tables(app):
    """Create all tables."""
    with app.app_context():
        db.create_all()


def setup_tenant_middleware(app):
    """Setup tenant context middleware for automatic school_id filtering."""
    
    @app.before_request
    def set_tenant_context():
        """Set tenant context for the current request."""
        from flask import request, g
        
        # Get school_id from various sources
        school_id = None
        
        # 1. Try header first
        school_id = request.headers.get('X-School-ID')
        
        # 2. Try JWT token payload (if available)
        if not school_id and hasattr(g, 'current_user_session'):
            school_id = g.current_user_session.get('school_id')
        
        # 3. Try domain resolution (for tenant service)
        if not school_id:
            host = request.headers.get('Host', '')
            # This would resolve school from domain
            # school = resolve_school_from_domain(host)
            # school_id = school.id if school else None
        
        # Set the tenant context
        g.current_school_id = school_id
        
        # Set PostgreSQL session variable for RLS
        if school_id:
            try:
                db.session.execute(f"SET app.current_school_id = '{school_id}'")
            except Exception:
                pass  # Ignore if not using PostgreSQL RLS


def setup_row_level_security():
    """Setup PostgreSQL Row Level Security policies."""
    policies = [
        # Enable RLS on tenant-aware tables
        "ALTER TABLE students ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE parents ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE teachers ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE classes ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE attendance ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE exams ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE exam_results ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE student_feedback ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE fee_structures ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE invoice_items ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE payment_notifications ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE message_threads ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE messages ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE message_recipients ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE user_school_roles ENABLE ROW LEVEL SECURITY;",
        "ALTER TABLE parent_student_relationships ENABLE ROW LEVEL SECURITY;",
        
        # Create policies for automatic school_id filtering
        """CREATE POLICY tenant_isolation_students ON students
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_parents ON parents
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_teachers ON teachers
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_classes ON classes
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_attendance ON attendance
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_exams ON exams
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_exam_results ON exam_results
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_student_feedback ON student_feedback
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_fee_structures ON fee_structures
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_invoices ON invoices
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_invoice_items ON invoice_items
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_payment_notifications ON payment_notifications
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_message_threads ON message_threads
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_messages ON messages
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_message_recipients ON message_recipients
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_notifications ON notifications
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_user_school_roles ON user_school_roles
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
           
        """CREATE POLICY tenant_isolation_parent_student_relationships ON parent_student_relationships
           FOR ALL TO application_role
           USING (school_id = current_setting('app.current_school_id')::UUID);""",
    ]
    
    return policies


# Export all models for easy importing
__all__ = [
    'db', 'BaseModel', 'TenantAwareModel',
    'School', 'User', 'Role', 'UserSchoolRole',
    'Student', 'Parent', 'ParentStudent', 'Staff', 'Teacher', 'Class',
    'EducationTrack', 'Department', 'Subject', 'ClassSubject', 'StudentClasses',
    'Attendance', 'Exam', 'ExamResult', 'StudentFeedback',
    'Assessment', 'SubjectScore',
    'FeeStructure', 'Invoice', 'InvoiceItem', 'PaymentNotification',
    'MessageThread', 'Message', 'MessageRecipient', 'Notification',
    'AcademicSession', 'SchoolCalendar',
    'SchoolTimetable', 'ClassTimetable',
    'UserSession', 'ActivationCode',
    'init_database', 'create_all_tables', 'setup_tenant_middleware', 'setup_row_level_security'
]