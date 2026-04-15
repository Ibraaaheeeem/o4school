package com.haneef._school.service

import com.haneef._school.config.NativeDto
import com.haneef._school.repository.*
import com.haneef._school.entity.*
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.*

@Component
class SchoolDataTools(
    private val parentRepository: ParentRepository,
    private val staffRepository: StaffRepository,
    private val studentRepository: StudentRepository,
    private val financialService: FinancialService,
    private val userRepository: UserRepository,
    private val schoolRepository: SchoolRepository,
    private val schoolTimetableRepository: SchoolTimetableRepository,
    private val schoolCalendarRepository: SchoolCalendarRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val attendanceRepository: AttendanceRepository,
    private val subjectRepository: SubjectRepository,
    private val assessmentRepository: AssessmentRepository,
    private val subjectScoreRepository: SubjectScoreRepository,
    private val schoolClassRepository: SchoolClassRepository
) {

    @Tool(description = "Query parents based on complex criteria like owing/paid amounts, children details (class, age, gender, status). Always provide the schoolId.")
    fun queryParents(
        @ToolParam(description = "The criteria for filtering parents (e.g. 'owing more than 50k', 'children in SS 3', 'new students')") criteria: String,
        @ToolParam(description = "The school ID of the current school") schoolId: UUID
    ): List<RecipientInfo> {
        val allParents = parentRepository.findBySchoolIdAndIsActiveWithRelationships(schoolId, true)
        
        // Use a basic NLP-like approach to parse criteria if possible, or just filter broadly
        return allParents.filter { parent ->
            var matches = true
            
            if (criteria.contains("owing", ignoreCase = true) || criteria.contains("debt", ignoreCase = true) || criteria.contains("balance", ignoreCase = true)) {
                val balance = financialService.calculateParentBalance(parent)
                // Try to extract a number from criteria for "more than X"
                val amountMatch = Regex("""(\d+)""").find(criteria)
                if (amountMatch != null) {
                    val amount = BigDecimal(amountMatch.value)
                    if (criteria.contains("more than", ignoreCase = true) || criteria.contains(">", ignoreCase = true)) {
                        matches = matches && balance > amount
                    } else if (criteria.contains("less than", ignoreCase = true) || criteria.contains("<", ignoreCase = true)) {
                        matches = matches && balance < amount
                    } else {
                        matches = matches && balance > BigDecimal.ZERO
                    }
                } else {
                    matches = matches && balance > BigDecimal.ZERO
                }
            }
            
            if (criteria.contains("paid", ignoreCase = true)) {
                val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
                if (currentSession != null) {
                    val breakdown = financialService.getFeeBreakdown(parent, currentSession.id!!)
                    val settled = breakdown["totalSettled"] as? BigDecimal ?: BigDecimal.ZERO
                    val amountMatch = Regex("""(\d+)""").find(criteria)
                    if (amountMatch != null) {
                        val amount = BigDecimal(amountMatch.value)
                        matches = matches && settled >= amount
                    } else {
                        matches = matches && settled > BigDecimal.ZERO
                    }
                }
            }

            val classes = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)
            val mentionedClasses = classes.filter { criteria.contains(it.className, ignoreCase = true) }
            
            if (mentionedClasses.isNotEmpty() || criteria.contains("class", ignoreCase = true)) {
                val childrenClasses = parent.studentRelationships.mapNotNull { rel ->
                    rel.student.classEnrollments.find { it.isActive }?.schoolClass?.className
                }
                if (mentionedClasses.isNotEmpty()) {
                    matches = matches && childrenClasses.any { className -> mentionedClasses.any { it.className.equals(className, ignoreCase = true) } }
                } else if (criteria.contains("class", ignoreCase = true)) {
                    // Check if any of the classes mentioned in criteria match (legacy behavior if 'class' is used but no specific name found yet)
                    matches = matches && childrenClasses.any { className -> criteria.contains(className, ignoreCase = true) }
                }
            }

            if (criteria.contains("new", ignoreCase = true)) {
                matches = matches && parent.studentRelationships.filter { it.isActive }.any { it.student.isActive && it.student.isNew }
            }

            if (criteria.contains("old", ignoreCase = true) || criteria.contains("returning", ignoreCase = true)) {
                matches = matches && parent.studentRelationships.filter { it.isActive }.any { it.student.isActive && !it.student.isNew }
            }

            if (criteria.contains("gender", ignoreCase = true) || criteria.contains("boy", ignoreCase = true) || criteria.contains("girl", ignoreCase = true) || criteria.contains("male", ignoreCase = true) || criteria.contains("female", ignoreCase = true)) {
                val isBoy = criteria.contains("boy", ignoreCase = true) || criteria.contains("male", ignoreCase = true)
                val isGirl = criteria.contains("girl", ignoreCase = true) || criteria.contains("female", ignoreCase = true)
                matches = matches && parent.studentRelationships.filter { it.isActive }.any { 
                    it.student.isActive && ( (isBoy && it.student.user.gender == "MALE") || (isGirl && it.student.user.gender == "FEMALE") )
                }
            }

            if (criteria.contains("age", ignoreCase = true)) {
                // Simplified age filtering
                val ageMatch = Regex("""(\d+)""").find(criteria)
                if (ageMatch != null) {
                    val ageTarget = ageMatch.value.toInt()
                    matches = matches && parent.studentRelationships.filter { it.isActive }.any { rel ->
                        rel.student.isActive && rel.student.dateOfBirth?.let { dob ->
                            val age = LocalDate.now().year - dob.year
                            if (criteria.contains("above", ignoreCase = true) || criteria.contains("more than", ignoreCase = true)) age > ageTarget
                            else if (criteria.contains("below", ignoreCase = true) || criteria.contains("less than", ignoreCase = true)) age < ageTarget
                            else age == ageTarget
                        } ?: false
                    }
                }
            }
            
            matches
        }.map { parent ->
            RecipientInfo(
                id = parent.user.id!!,
                name = parent.user.fullName ?: "Unknown",
                phone = parent.user.phoneNumber ?: "N/A",
                balance = financialService.calculateParentBalance(parent)
            )
        }
    }

    @Tool(description = "Query staff members based on criteria like track, class, subject, role, or recruitment date. Always provide the schoolId.")
    fun queryStaff(
        @ToolParam(description = "The criteria for filtering staff (e.g. 'teach SS3', 'Mathematics teachers', 'joined after 2023')") criteria: String,
        @ToolParam(description = "The school ID of the current school") schoolId: UUID
    ): List<RecipientInfo> {
        val allStaff = staffRepository.findBySchoolIdAndIsActiveWithTeacherAssignments(schoolId, true)
        
        val filteredStaff = allStaff.filter { staff ->
            var matches = true
            
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)
            val mentionedClasses = classes.filter { criteria.contains(it.className, ignoreCase = true) }

            if (mentionedClasses.isNotEmpty() || criteria.contains("class", ignoreCase = true) || criteria.contains("teach", ignoreCase = true)) {
                val assignedClasses = (staff.classTeacherAssignments.filter { it.isActive }.map { it.schoolClass.className } + 
                                       staff.subjectTeacherAssignments.filter { it.isActive }.map { it.schoolClass.className }).distinct()
                
                if (mentionedClasses.isNotEmpty()) {
                    matches = matches && assignedClasses.any { className -> mentionedClasses.any { it.className.equals(className, ignoreCase = true) } }
                } else {
                    val classMatch = assignedClasses.any { className -> criteria.contains(className, ignoreCase = true) }
                    if (assignedClasses.isNotEmpty()) matches = matches && classMatch
                }
            }

            if (criteria.contains("subject", ignoreCase = true) || criteria.contains("teacher", ignoreCase = true)) {
                val assignedSubjects = staff.subjectTeacherAssignments.filter { it.isActive }.map { it.subject.subjectName }.distinct()
                val subjectMatch = assignedSubjects.any { subjectName -> criteria.contains(subjectName, ignoreCase = true) }
                if (assignedSubjects.isNotEmpty() && !criteria.contains("all staff", ignoreCase = true)) {
                    matches = matches && subjectMatch
                }
            }

            if (criteria.contains("track", ignoreCase = true)) {
                val assignedTracks: List<String> = (staff.classTeacherAssignments.filter { it.isActive }.mapNotNull { it.schoolClass.track?.name } + 
                                                   staff.subjectTeacherAssignments.filter { it.isActive }.mapNotNull { it.schoolClass.track?.name }).distinct()
                val trackMatch = assignedTracks.any { trackName -> criteria.contains(trackName, ignoreCase = true) }
                if (assignedTracks.isNotEmpty()) matches = matches && trackMatch
            }

            if (criteria.contains("joined", ignoreCase = true) || criteria.contains("recruited", ignoreCase = true) || criteria.contains("year", ignoreCase = true) || criteria.contains("hire", ignoreCase = true)) {
                val yearMatch = Regex("""(\d{4})""").find(criteria)
                if (yearMatch != null) {
                    val yearTarget = yearMatch.value.toInt()
                    val joinedYear = staff.hireDate.year
                    if (criteria.contains("after", ignoreCase = true)) matches = matches && joinedYear > yearTarget
                    else if (criteria.contains("before", ignoreCase = true)) matches = matches && joinedYear < yearTarget
                    else matches = matches && joinedYear == yearTarget
                }
            }

            if (criteria.contains("class teacher", ignoreCase = true)) {
                matches = matches && staff.isClassTeacher
            } else if (criteria.contains("subject teacher", ignoreCase = true)) {
                matches = matches && staff.isSubjectTeacher
            }

            if (criteria.contains("department", ignoreCase = true)) {
                staff.department?.let { dept ->
                    matches = matches && criteria.contains(dept, ignoreCase = true)
                } ?: run { if (!criteria.contains("all staff", ignoreCase = true)) matches = false }
            }

            if (criteria.isBlank() || criteria.contains("all", ignoreCase = true)) {
                matches = true
            } else {
                // If no specific tag matched but it looks like a name search
                if (matches && !criteria.contains(staff.user.fullName ?: "", ignoreCase = true)) {
                    // Fallback to name search if criteria is just a name
                    if (criteria.length > 3 && (staff.user.fullName?.contains(criteria, ignoreCase = true) == true)) {
                        matches = true
                    }
                }
            }
            
            matches
        }

        return filteredStaff.map { staff ->
            RecipientInfo(
                id = staff.user.id!!,
                name = staff.user.fullName ?: "Unknown",
                phone = staff.user.phoneNumber ?: "N/A",
                balance = BigDecimal.ZERO
            )
        }
    }

    @Tool(description = "Get detailed academic progress for a parent's children including class, attendance, subjects, and termly reports. Always provide the schoolId.")
    fun getChildAcademicDetails(
        @ToolParam(description = "The user ID of the parent") parentUserId: UUID,
        @ToolParam(description = "The school ID") schoolId: UUID
    ): String {
        val parent = parentRepository.findByUserIdAndSchoolId(parentUserId, schoolId)
            ?.takeIf { it.isActive }
            ?: return "I couldn't find your parent profile. Please contact the school office."
        
        val activeChildren = parent.studentRelationships.filter { it.isActive }.map { it.student }.filter { it.isActive }
        if (activeChildren.isEmpty()) return "You don't have any active students currently enrolled."

        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
            ?: return "The school has not set a current academic session."
        
        val currentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(schoolId, true, true).orElse(null)

        val report = StringBuilder("Academic Progress for your children:\n\n")

        activeChildren.forEach { student ->
            val enrollment = student.classEnrollments.find { it.isActive && it.schoolId == schoolId }
            val className = enrollment?.schoolClass?.className ?: "Not assigned to a class"
            val trackName = enrollment?.schoolClass?.track?.name ?: "Standard"

            report.append("👦 ${student.user.fullName}\n")
            report.append("Class: $className ($trackName Track)\n")
            
            // Attendance
            if (currentTerm != null && currentTerm.startDate != null) {
                val studentAttendance = attendanceRepository.findByStudentIdAndSchoolIdAndIsActive(student.id!!, schoolId, true)
                    .filter { it.attendanceDate!!.isAfter(currentTerm.startDate!!.minusDays(1)) && (currentTerm.endDate == null || it.attendanceDate!!.isBefore(currentTerm.endDate!!.plusDays(1))) }
                
                val termAttendance = attendanceRepository.findBySchoolClassIdAndAttendanceDateBetweenAndSchoolIdAndIsActive(
                    enrollment?.schoolClass?.id!!, currentTerm.startDate!!, currentTerm.endDate ?: LocalDate.now(), schoolId, true
                )
                val totalDays = termAttendance.distinctBy { it.attendanceDate }.size
                val presentDays = studentAttendance.count { it.status == AttendanceStatus.PRESENT }
                
                if (totalDays > 0) {
                    val percentage = (presentDays.toDouble() / totalDays.toDouble() * 100).toInt()
                    report.append("Attendance: $percentage% ($presentDays/$totalDays days present)\n")
                } else {
                    report.append("Attendance: No records for current term yet.\n")
                }
            }

            // Subjects
            val subjects = enrollment?.schoolClass?.subjectAssignments?.filter { it.isActive }?.map { it.subject.subjectName }
            if (!subjects.isNullOrEmpty()) {
                report.append("Subjects Offered: ${subjects.joinToString(", ")}\n")
            }

            // Report / Assessment
            if (currentTerm != null) {
                val assessment = assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                    student.id!!, currentSession.id!!, currentTerm.id!!, schoolId, true
                ).orElse(null)

                if (assessment != null) {
                    report.append("--- Termly Report Summary ---\n")
                    report.append("Behavioral Scores: Fluency (${assessment.fluency}/5), Handwriting (${assessment.handwriting}/5), Punctuality (${assessment.punctuality}/5)\n")
                    report.append("Teacher's Comment: ${assessment.classTeacherComment ?: "No comment yet"}\n")
                    
                    val scores = assessment.scores.filter { it.isActive }
                    if (scores.isNotEmpty()) {
                        report.append("Current Grades:\n")
                        scores.forEach { score ->
                            report.append("- ${score.subject.subjectName}: ${score.getTotalScore() ?: 0}% (${score.grade ?: "N/A"})\n")
                        }
                    }
                } else {
                    report.append("Note: Termly report for ${currentTerm.termName} is not yet available.\n")
                }
            }
            report.append("\n")
        }

        return report.toString()
    }

    @Tool(description = "Get a detailed financial summary for a parent including current term fees, payments, balances, and outstanding bills. Always provide the schoolId.")
    fun getParentFinancialSummary(
        @ToolParam(description = "The user ID of the parent") parentUserId: UUID,
        @ToolParam(description = "The school ID") schoolId: UUID
    ): String {
        val parent = parentRepository.findByUserIdAndSchoolId(parentUserId, schoolId)
            ?.takeIf { it.isActive }
            ?: return "I couldn't find your parent profile. Please contact the school office."

        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
            ?: return "The school has not set a current academic session."
        
        val currentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(schoolId, true, true).orElse(null)

        val breakdown = financialService.getFeeBreakdown(parent, currentSession.id, currentTerm?.id)
        val totalFees = breakdown["totalFees"] as? BigDecimal ?: BigDecimal.ZERO
        val totalSettled = breakdown["totalSettled"] as? BigDecimal ?: BigDecimal.ZERO
        val balance = breakdown["balance"] as? BigDecimal ?: BigDecimal.ZERO
        
        val summary = StringBuilder("Financial Summary for ${parent.user.fullName}:\n\n")
        
        if (currentTerm != null) {
            summary.append("--- Current Term: ${currentTerm.termName} ---\n")
            summary.append("Total Fees: ₦$totalFees\n")
            summary.append("Amount Paid: ₦$totalSettled\n")
            summary.append("Outstanding Balance for Term: ₦$balance\n\n")
        } else {
            summary.append("Current Session Balance: ₦$balance\n\n")
        }

        // Individual child breakdown
        val feeBreakdownList = breakdown["feeBreakdown"] as? List<Map<String, Any>>
        feeBreakdownList?.forEach { child ->
            summary.append("👦 ${child["studentName"]}\n")
            summary.append("- Total: ₦${child["total"]}\n")
            summary.append("- Paid: ₦${child["settled"]}\n")
            summary.append("- Balance: ₦${child["balance"]}\n\n")
        }

        // Global Balance (Historical)
        val globalBalance = financialService.calculateParentBalance(parent)
        if (globalBalance > balance) {
            summary.append("⚠️ Total Outstanding Debt (all time): ₦$globalBalance\n")
        }

        return summary.toString()
    }

    @Tool(description = "Get general school information like the class timetable and upcoming calendar events. Always provide the schoolId.")
    fun getSchoolInfo(
        @ToolParam(description = "The school ID") schoolId: UUID
    ): String {
        val info = StringBuilder("School Information Update:\n\n")

        // Timetable
        val today = LocalDate.now().dayOfWeek.name
        val timetable = schoolTimetableRepository.findBySchoolIdAndIsActiveOrderByDayAndTime(schoolId, true)
        
        if (timetable.isNotEmpty()) {
            info.append("📅 Today's Timetable Snippet ($today):\n")
            timetable.filter { it.dayOfWeek.name == today }.take(5).forEach { period ->
                info.append("${period.startTime} - ${period.endTime}: ${period.activityName}\n")
            }
            info.append("(Type 'full timetable' for more)\n\n")
        }

        // Calendar
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
        if (currentSession != null) {
            val upcomingEvents = schoolCalendarRepository.findUpcomingEvents(
                schoolId, currentSession.id!!, null, LocalDate.now(), org.springframework.data.domain.PageRequest.of(0, 5)
            )
            
            if (upcomingEvents.isNotEmpty()) {
                info.append("🎉 Upcoming Events:\n")
                upcomingEvents.forEach { event ->
                    info.append("- ${event.startDate}: ${event.eventName} (${event.eventType})\n")
                }
            } else {
                info.append("No upcoming events scheduled currently.\n")
            }
        }

        return info.toString()
    }

    @Tool(description = "Query students based on criteria like class, gender, status (new/returning), or age. Always provide the schoolId.")
    fun queryStudents(
        @ToolParam(description = "The criteria for filtering students (e.g. 'JSS 1 students', 'new girls', 'boys above 12')") criteria: String,
        @ToolParam(description = "The school ID of the current school") schoolId: UUID
    ): List<RecipientInfo> {
        val allStudents = studentRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        return allStudents.filter { student ->
            var matches = true
            
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)
            val mentionedClasses = classes.filter { criteria.contains(it.className, ignoreCase = true) }

            if (mentionedClasses.isNotEmpty() || criteria.contains("class", ignoreCase = true)) {
                val studentClasses = student.classEnrollments.filter { it.isActive }.map { it.schoolClass.className }
                if (mentionedClasses.isNotEmpty()) {
                    matches = matches && studentClasses.any { className -> mentionedClasses.any { it.className.equals(className, ignoreCase = true) } }
                } else {
                    val classMatch = studentClasses.any { className -> criteria.contains(className, ignoreCase = true) }
                    if (studentClasses.isNotEmpty()) matches = matches && classMatch
                }
            }

            if (criteria.contains("new", ignoreCase = true)) {
                matches = matches && student.isNew
            }

            if (criteria.contains("old", ignoreCase = true) || criteria.contains("returning", ignoreCase = true)) {
                matches = matches && !student.isNew
            }

            if (criteria.contains("gender", ignoreCase = true) || criteria.contains("boy", ignoreCase = true) || criteria.contains("girl", ignoreCase = true) || criteria.contains("male", ignoreCase = true) || criteria.contains("female", ignoreCase = true)) {
                val isBoy = criteria.contains("boy", ignoreCase = true) || criteria.contains("male", ignoreCase = true)
                val isGirl = criteria.contains("girl", ignoreCase = true) || criteria.contains("female", ignoreCase = true)
                matches = matches && ( (isBoy && student.user.gender == "MALE") || (isGirl && student.user.gender == "FEMALE") )
            }

            if (criteria.contains("age", ignoreCase = true)) {
                val ageMatch = Regex("""(\d+)""").find(criteria)
                if (ageMatch != null) {
                    val ageTarget = ageMatch.value.toInt()
                    val age = student.dateOfBirth?.let { dob -> LocalDate.now().year - dob.year } ?: 0
                    if (criteria.contains("above", ignoreCase = true) || criteria.contains("more than", ignoreCase = true)) matches = matches && age > ageTarget
                    else if (criteria.contains("below", ignoreCase = true) || criteria.contains("less than", ignoreCase = true)) matches = matches && age < ageTarget
                    else matches = matches && age == ageTarget
                }
            }
            
            matches
        }.map { student ->
            RecipientInfo(
                id = student.user.id!!,
                name = student.user.fullName ?: "Unknown",
                phone = student.user.phoneNumber ?: "N/A",
                balance = BigDecimal.ZERO
            )
        }
    }

    @NativeDto
    data class RecipientInfo(val id: UUID, val name: String, val phone: String, val balance: BigDecimal)
}
