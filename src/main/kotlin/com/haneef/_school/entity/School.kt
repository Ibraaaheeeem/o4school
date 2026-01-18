package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "schools")
class School(
    @Column(nullable = false)
    var name: String? = "",
    
    @Column(nullable = false, unique = true)
    var slug: String? = "",
    
    @Column(nullable = false)
    var email: String? = "",
    
    @Column(nullable = false)
    var phone: String? = "",
    
    var website: String? = null,
    
    // Address fields
    @Column(name = "address_line1", nullable = false)
    var addressLine1: String? = "",
    
    @Column(name = "address_line2")
    var addressLine2: String? = null,
    
    @Column(nullable = false)
    var city: String? = "",
    
    @Column(nullable = false)
    var state: String? = "",
    
    @Column(name = "postal_code", nullable = false)
    var postalCode: String? = "",
    
    @Column(nullable = false)
    var country: String = "Nigeria",
    
    // Domain configuration
    var subdomain: String? = null,
    
    @Column(name = "custom_domain")
    var customDomain: String? = null,
    
    @Column(name = "ssl_enabled")
    var sslEnabled: Boolean = false,
    
    // Status and settings
    var status: String = "pending", // pending, active, suspended, inactive
    var timezone: String = "Africa/Lagos",
    var currency: String = "NGN",
    var language: String = "en",
    
    // Academic settings
    @Column(name = "academic_year_start")
    var academicYearStart: String = "09-01", // MM-DD format
    
    @Column(name = "academic_year_end")
    var academicYearEnd: String = "07-31", // MM-DD format
    
    @Column(name = "current_academic_year")
    var currentAcademicYear: String? = null, // 2023-2024
    
    // Administrator information
    @Column(name = "admin_user_id")
    var adminUserId: UUID? = null,
    
    @Column(name = "admin_name", nullable = false)
    var adminName: String? = "",
    
    @Column(name = "admin_email", nullable = false)
    var adminEmail: String? = "",
    
    @Column(name = "admin_phone", nullable = false)
    var adminPhone: String? = "",
    
    // Branding
    @Column(name = "logo_url")
    var logoUrl: String? = null,
    
    @Column(name = "banner_url")
    var bannerUrl: String? = null,
    
    @Column(name = "primary_color")
    var primaryColor: String = "#007bff",
    
    @Column(name = "secondary_color")
    var secondaryColor: String = "#6c757d",
    
    @Column(name = "school_motto")
    var schoolMotto: String? = null,
    
    @Column(name = "admission_prefix", unique = true)
    var admissionPrefix: String? = null
) : BaseEntity() {
}