package com.haneef.school.data.models

import com.google.gson.annotations.SerializedName

data class DashboardResponse(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("school_id")
    val schoolId: String,
    @SerializedName("school_name")
    val schoolName: String,
    @SerializedName("role")
    val role: String,
    @SerializedName("admin_overview")
    val adminOverview: AdminOverview? = null,
    @SerializedName("staff_overview")
    val staffOverview: StaffOverview? = null,
    @SerializedName("parent_overview")
    val parentOverview: ParentOverview? = null,
    @SerializedName("student_overview")
    val studentOverview: StudentOverview? = null,
    @SerializedName("financial_health")
    val financialHealth: FinancialHealth,
    @SerializedName("critical_alerts")
    val criticalAlerts: List<CriticalAlert> = emptyList(),
    @SerializedName("upcoming_events")
    val upcomingEvents: List<CalendarEventSummary> = emptyList()
)

// Keep these role-specific overview models minimal/extendable until backend schema is finalized.
data class AdminOverview(
    @SerializedName("total_students")
    val totalStudents: Int? = null,
    @SerializedName("total_staff")
    val totalStaff: Int? = null,
    @SerializedName("active_staff")
    val activeStaff: Int? = null,
    @SerializedName("total_parents")
    val totalParents: Int? = null,
    @SerializedName("active_sessions")
    val activeSessions: Int? = null,
    @SerializedName("pending_activations")
    val pendingActivations: Int? = null,
    @SerializedName("total_fee_items")
    val totalFeeItems: Int? = null,
    @SerializedName("total_settlements")
    val totalSettlements: Double? = null,
    @SerializedName("attendance_percent")
    val attendancePercent: Double? = null
)

data class StaffOverview(
    @SerializedName("classes_count")
    val classesCount: Int? = null,
    @SerializedName("pending_tasks")
    val pendingTasks: Int? = null
)

data class ParentOverview(
    @SerializedName("children_count")
    val childrenCount: Int? = null,
    @SerializedName("due_payments")
    val duePayments: Double? = null
)

data class StudentOverview(
    @SerializedName("current_gpa")
    val currentGpa: Double? = null,
    @SerializedName("attendance_percent")
    val attendancePercent: Double? = null
)

data class FinancialHealth(
    @SerializedName("total_outstanding_fees")
    val totalOutstandingFees: Double? = null,
    @SerializedName("collection_rate_percent")
    val collectionRatePercent: Double? = null,
    @SerializedName("monthly_revenue")
    val monthlyRevenue: Double? = null,
    @SerializedName("monthly_expense")
    val monthlyExpense: Double? = null,
    @SerializedName("net_cash_flow")
    val netCashFlow: Double? = null,
    @SerializedName("last_updated")
    val lastUpdated: String
)

data class CriticalAlert(
    @SerializedName("alert_type")
    val alertType: String,
    @SerializedName("severity")
    val severity: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("affected_count")
    val affectedCount: Long? = null,
    @SerializedName("action_required")
    val actionRequired: String,
    @SerializedName("created_at")
    val createdAt: String
)

data class CalendarEventSummary(
    @SerializedName("event_id")
    val eventId: String? = null,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("start_at")
    val startAt: String? = null,
    @SerializedName("end_at")
    val endAt: String? = null,
    @SerializedName("location")
    val location: String? = null
)

data class EnrollmentAnalytics(
    @SerializedName("period")
    val period: String,
    @SerializedName("total_enrolled")
    val totalEnrolled: Int,
    @SerializedName("new_enrollments")
    val newEnrollments: Int,
    @SerializedName("withdrawals")
    val withdrawals: Int
)