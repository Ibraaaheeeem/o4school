package com.haneef._school.controller

import java.util.UUID

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.http.HttpSession
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Controller
@RequestMapping("/admin/academic")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'STAFF', 'TEACHER')")
class AcademicController(
    private val academicSessionRepository: AcademicSessionRepository,
    private val schoolCalendarRepository: SchoolCalendarRepository,
    private val schoolTimetableRepository: SchoolTimetableRepository,
    private val termRepository: TermRepository,
    private val authorizationService: com.haneef._school.service.AuthorizationService
) {

    private val logger = org.slf4j.LoggerFactory.getLogger(AcademicController::class.java)

    @GetMapping
    fun academicHome(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        // Get current academic session
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
        val allSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        
        // Get upcoming calendar events
        val upcomingEvents = schoolCalendarRepository.findBySchoolIdAndDateRange(
            selectedSchoolId, true, LocalDate.now(), LocalDate.now().plusMonths(3)
        ).take(5)

        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("currentSession", currentSession)
        model.addAttribute("allSessions", allSessions)
        model.addAttribute("upcomingEvents", upcomingEvents)

        return "admin/academic/home"
    }

    // Academic Session Management
    @GetMapping("/sessions")
    fun sessionsList(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)

        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("academicSessions", academicSessions)

        return "admin/academic/sessions"
    }

    @GetMapping("/sessions/new/modal")
    fun getNewSessionModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("academicSession", AcademicSession())
        model.addAttribute("isEdit", false)
        
        return "admin/academic/session-modal"
    }

    @GetMapping("/sessions/{id}/modal")
    fun getEditSessionModal(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )
            
        // Use secure validation
        val academicSession = authorizationService.validateAndGetAcademicSession(id, selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("academicSession", academicSession)
        model.addAttribute("isEdit", true)
        
        return "admin/academic/session-modal"
    }

    @PostMapping("/sessions/save-htmx")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveSessionHtmx(
        @RequestParam(required = false) id: UUID?,
        @RequestParam sessionName: String,
        @RequestParam sessionYear: String,
        @RequestParam startDate: String,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) isCurrentSession: Boolean = false,
        @RequestParam(required = false) notes: String?,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            if (id != null) {
                // Update existing session
                val existingSession = academicSessionRepository.findById(id).orElseThrow()
                
                if (existingSession.schoolId != selectedSchoolId) {
                    model.addAttribute("error", "Unauthorized access to academic session")
                    return "fragments/error :: error-message"
                }
                
                existingSession.apply {
                    this.sessionName = sessionName
                    this.sessionYear = sessionYear
                    this.startDate = LocalDate.parse(startDate)
                    this.endDate = if (endDate.isNullOrBlank()) null else LocalDate.parse(endDate)
                    this.isCurrentSession = isCurrentSession
                    this.notes = notes
                }
                
                // If setting as current session, unset others
                if (isCurrentSession) {
                    val otherSessions = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                    otherSessions?.let {
                        it.isCurrentSession = false
                        academicSessionRepository.save(it)
                    }
                }
                
                academicSessionRepository.save(existingSession)
                model.addAttribute("message", "Academic session updated successfully!")
            } else {
                // Create new session
                val newSession = AcademicSession(
                    sessionName = sessionName,
                    sessionYear = sessionYear,
                    startDate = LocalDate.parse(startDate)
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.endDate = if (endDate.isNullOrBlank()) null else LocalDate.parse(endDate)
                    this.isCurrentSession = isCurrentSession
                    this.notes = notes
                    this.isActive = true
                }
                
                // If setting as current session, unset others
                if (isCurrentSession) {
                    val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                    currentSession?.let {
                        it.isCurrentSession = false
                        academicSessionRepository.save(it)
                    }
                }
                
                academicSessionRepository.save(newSession)
                model.addAttribute("message", "Academic session created successfully!")
            }

            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error saving academic session: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    // Term Management
    @GetMapping("/sessions/{sessionId}/terms")
    fun sessionTerms(@PathVariable sessionId: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val academicSession = academicSessionRepository.findById(sessionId).orElseThrow { RuntimeException("Session not found") }
        
        if (academicSession.schoolId != selectedSchoolId) {
            throw RuntimeException("Unauthorized access to academic session")
        }
        
        val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(sessionId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("academicSession", academicSession)
        model.addAttribute("terms", terms)
        
        return "admin/academic/terms"
    }

    @GetMapping("/sessions/{sessionId}/terms/new/modal")
    fun getNewTermModal(@PathVariable sessionId: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val academicSession = academicSessionRepository.findById(sessionId).orElseThrow { RuntimeException("Session not found") }
        
        if (academicSession.schoolId != selectedSchoolId) {
            throw RuntimeException("Unauthorized access to academic session")
        }
        
        val newTerm = Term().apply {
            this.academicSession = academicSession
        }
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("term", newTerm)
        model.addAttribute("academicSession", academicSession)
        model.addAttribute("isEdit", false)
        
        // Check for existing open-ended current term
        val schoolCurrentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(selectedSchoolId, true, true)
        if (schoolCurrentTerm.isPresent && schoolCurrentTerm.get().endDate == null) {
             model.addAttribute("existingOpenTerm", schoolCurrentTerm.get())
        }
        
        return "admin/academic/term-modal"
    }

    @GetMapping("/terms/{id}/modal")
    fun getEditTermModal(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val term = termRepository.findById(id).orElseThrow { RuntimeException("Term not found") }
        
        if (term.schoolId != selectedSchoolId) {
            throw RuntimeException("Unauthorized access to term")
        }
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("term", term)
        model.addAttribute("academicSession", term.academicSession)
        model.addAttribute("isEdit", true)
        
        // Check for existing open-ended current term (excluding self)
        val schoolCurrentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(selectedSchoolId, true, true)
        if (schoolCurrentTerm.isPresent && schoolCurrentTerm.get().id != id && schoolCurrentTerm.get().endDate == null) {
             model.addAttribute("existingOpenTerm", schoolCurrentTerm.get())
        }
        
        return "admin/academic/term-modal"
    }

    @PostMapping("/terms/save-htmx")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveTermHtmx(
        @RequestParam(required = false) id: UUID?,
        @RequestParam sessionId: UUID,
        @RequestParam termName: String,
        @RequestParam startDate: String,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) isCurrentTerm: Boolean = false,
        @RequestParam(required = false) description: String?,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val academicSession = academicSessionRepository.findById(sessionId).orElseThrow()
            
            if (academicSession.schoolId != selectedSchoolId) {
                model.addAttribute("error", "Unauthorized access to academic session")
                return "fragments/error :: error-message"
            }
            
            val startDateObj = LocalDate.parse(startDate)
            val endDateObj = if (endDate.isNullOrBlank()) null else LocalDate.parse(endDate)
            
            // Validation: Only current term can have open end date
            if (endDateObj == null && !isCurrentTerm) {
                 model.addAttribute("error", "Only the current term can have an open end date")
                 return "fragments/error :: error-message"
            }
            
            // Validate date range
            if (endDateObj != null && (startDateObj.isAfter(endDateObj) || startDateObj.equals(endDateObj))) {
                model.addAttribute("error", "Start date must be before end date")
                return "fragments/error :: error-message"
            }
            
            // Validate against session dates
            if (startDateObj.isBefore(academicSession.startDate)) {
                 model.addAttribute("error", "Term start date cannot be before the academic session start date (${academicSession.startDate})")
                 return "fragments/error :: error-message"
            }
            
            if (academicSession.endDate != null) {
                if (endDateObj != null && endDateObj.isAfter(academicSession.endDate)) {
                     model.addAttribute("error", "Term end date cannot be after the academic session end date (${academicSession.endDate})")
                     return "fragments/error :: error-message"
                }
                if (startDateObj.isAfter(academicSession.endDate)) {
                     model.addAttribute("error", "Term start date cannot be after the academic session end date (${academicSession.endDate})")
                     return "fragments/error :: error-message"
                }
            }
            
            // Check for duplicate term name (active or inactive)
            val existingByNameOpt = termRepository.findByAcademicSessionIdAndTermName(sessionId, termName)
            var termToResurrect: Term? = null

            if (existingByNameOpt.isPresent) {
                val existing = existingByNameOpt.get()
                if (id != null) {
                    // Editing an existing term
                    if (existing.id != id) {
                        model.addAttribute("error", "A term with this name already exists in this session")
                        return "fragments/error :: error-message"
                    }
                } else {
                    // Creating a new term
                    if (existing.isActive) {
                        model.addAttribute("error", "A term with this name already exists in this session")
                        return "fragments/error :: error-message"
                    }
                    // It's inactive, so we will resurrect it
                    termToResurrect = existing
                }
            }
            
            // Overlap Check
            val sessionTerms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(sessionId, true)
            for (t in sessionTerms) {
                if (t.id == id) continue // Skip self
                
                val tStart = t.startDate
                val tEnd = t.endDate
                
                // Case 1: Existing term is open-ended
                if (tEnd == null) {
                     // If new term starts after existing open-ended term starts, it's an overlap (unless we close the existing one, which is handled in current term logic)
                     // But strictly speaking for overlap check:
                     if (startDateObj >= tStart) {
                         // This is only allowed if we are making the new term CURRENT, which will close the old one.
                         // If NOT making it current, this is an illegal overlap.
                         if (!isCurrentTerm) {
                             model.addAttribute("error", "Term overlaps with existing open-ended term '${t.termName}' (Starts ${tStart}). You must close that term first.")
                             return "fragments/error :: error-message"
                         }
                     }
                     // If new term ends after existing starts (and new term starts before existing starts - i.e. completely before), no overlap.
                     // But if new term is open ended, and starts before existing starts, it overlaps.
                     if (endDateObj == null && startDateObj < tStart) {
                          // New open ended term starts before existing open ended term.
                          // This effectively means they overlap forever.
                          model.addAttribute("error", "Cannot create an open-ended term that starts before another open-ended term '${t.termName}'.")
                          return "fragments/error :: error-message"
                     }
                } else {
                    // Case 2: Existing term has fixed range [tStart, tEnd]
                    
                    // If new term is open-ended
                    if (endDateObj == null) {
                        // Starts within existing range
                        if (startDateObj >= tStart && startDateObj <= tEnd) {
                             model.addAttribute("error", "Term start date falls within existing term '${t.termName}' (${tStart} - ${tEnd})")
                             return "fragments/error :: error-message"
                        }
                        // Starts before, so it definitely overlaps since it has no end
                        if (startDateObj < tStart) {
                             model.addAttribute("error", "Open-ended term starting at ${startDateObj} will overlap with existing term '${t.termName}' (${tStart} - ${tEnd})")
                             return "fragments/error :: error-message"
                        }
                    } else {
                        // New term has fixed range [startDateObj, endDateObj]
                        
                        // Check if new start date is inside existing range
                        if (startDateObj >= tStart && startDateObj <= tEnd) {
                             model.addAttribute("error", "Term start date falls within existing term '${t.termName}' (${tStart} - ${tEnd})")
                             return "fragments/error :: error-message"
                        }
                        
                        // Check if new end date is inside existing range
                        if (endDateObj >= tStart && endDateObj <= tEnd) {
                             model.addAttribute("error", "Term end date falls within existing term '${t.termName}' (${tStart} - ${tEnd})")
                             return "fragments/error :: error-message"
                        }
                        
                        // Check if new term completely encloses existing term
                        if (startDateObj <= tStart && endDateObj >= tEnd) {
                             model.addAttribute("error", "Term dates enclose existing term '${t.termName}' (${tStart} - ${tEnd})")
                             return "fragments/error :: error-message"
                        }
                    }
                }
            }
            
            // Current Term Logic
            if (isCurrentTerm) {
                // Check if there is another current term for the SCHOOL
                val schoolCurrentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(selectedSchoolId, true, true)
                
                if (schoolCurrentTerm.isPresent) {
                    val otherCurrent = schoolCurrentTerm.get()
                    if (otherCurrent.id != id) {
                        // There is another current term.
                        
                        // User requirement: "outgoing current term should be ended by entering the end date"
                        // New requirement: Automatically close previous term
                        if (otherCurrent.endDate == null) {
                             val autoEndDate = startDateObj.minusDays(1)
                             
                             // Validate that autoEndDate is valid (e.g. after start date of previous term)
                             if (autoEndDate < otherCurrent.startDate) {
                                 model.addAttribute("error", "Cannot automatically close previous term '${otherCurrent.termName}'. The calculated end date ($autoEndDate) would be before its start date (${otherCurrent.startDate}). Please adjust the start date of the new term.")
                                 return "fragments/error :: error-message"
                             }
                             
                             otherCurrent.endDate = autoEndDate
                             otherCurrent.isCurrentTerm = false
                             termRepository.save(otherCurrent)
                        } else {
                            // Check that new current term starts AFTER the old one ends
                            if (startDateObj <= otherCurrent.endDate) {
                                  model.addAttribute("error", "New current term must start after the previous current term '${otherCurrent.termName}' ends (${otherCurrent.endDate}).")
                                  return "fragments/error :: error-message"
                            }
                            
                            // Unset previous current term
                            otherCurrent.isCurrentTerm = false
                            termRepository.save(otherCurrent)
                        }
                    }
                }
            }
            
            if (id != null) {
                // Update existing term
                val existingTerm = termRepository.findById(id).orElseThrow()
                
                if (existingTerm.schoolId != selectedSchoolId) {
                    model.addAttribute("error", "Unauthorized access to term")
                    return "fragments/error :: error-message"
                }
                
                existingTerm.apply {
                    this.termName = termName
                    this.startDate = startDateObj
                    this.endDate = endDateObj
                    this.isCurrentTerm = isCurrentTerm
                    this.description = description
                }
                
                termRepository.save(existingTerm)
                model.addAttribute("message", "Term updated successfully!")
            } else if (termToResurrect != null) {
                // Resurrect inactive term
                val existingTerm = termToResurrect
                
                if (existingTerm.schoolId != selectedSchoolId) {
                    model.addAttribute("error", "Unauthorized access to term")
                    return "fragments/error :: error-message"
                }
                
                existingTerm.apply {
                    this.termName = termName
                    this.startDate = startDateObj
                    this.endDate = endDateObj
                    this.isCurrentTerm = isCurrentTerm
                    this.description = description
                    this.isActive = true
                    this.status = "planned"
                }
                
                termRepository.save(existingTerm)
                model.addAttribute("message", "Term restored and updated successfully!")
            } else {
                // Create new term
                val newTerm = Term(
                    academicSession = academicSession,
                    termName = termName,
                    startDate = startDateObj,
                    endDate = endDateObj
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.isCurrentTerm = isCurrentTerm
                    this.description = description
                    this.isActive = true
                    this.status = "planned"
                }
                
                termRepository.save(newTerm)
                model.addAttribute("message", "Term created successfully!")
            }

            // Fetch updated terms list for OOB update
            val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(sessionId, true)
            model.addAttribute("terms", terms)
            model.addAttribute("academicSession", academicSession)

            return "admin/academic/term-success"
        } catch (e: Exception) {
            logger.error("Error saving term", e)
            model.addAttribute("error", "Error saving term: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/terms/delete/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun deleteTerm(@PathVariable id: UUID, session: HttpSession, model: Model): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        try {
            val term = termRepository.findById(id).orElseThrow()
            
            if (term.schoolId != selectedSchoolId) {
                model.addAttribute("error", "Unauthorized access to term")
                return "fragments/error :: error-message"
            }
            
            term.isActive = false
            termRepository.save(term)
            model.addAttribute("message", "Term deleted successfully!")
            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error deleting term: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    // Calendar Management
    @GetMapping("/calendar")
    fun calendarList(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val calendarEvents = schoolCalendarRepository.findBySchoolIdAndIsActiveWithSession(selectedSchoolId, true)

        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("calendarEvents", calendarEvents)
        model.addAttribute("eventTypes", CalendarEventType.values())

        return "admin/academic/calendar"
    }

    @GetMapping("/calendar/new/modal")
    fun getNewCalendarModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
        
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("calendarEvent", SchoolCalendar())
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("eventTypes", CalendarEventType.values())
        model.addAttribute("isEdit", false)
        
        return "admin/academic/calendar-modal"
    }

    @GetMapping("/calendar/{id}/modal")
    fun getEditCalendarModal(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
        
        val calendarEvent = schoolCalendarRepository.findById(id).orElseThrow { RuntimeException("Calendar event not found") }
        
        if (calendarEvent.schoolId != selectedSchoolId) {
            throw RuntimeException("Unauthorized access to calendar event")
        }
        
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("calendarEvent", calendarEvent)
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("eventTypes", CalendarEventType.values())
        model.addAttribute("isEdit", true)
        
        return "admin/academic/calendar-modal"
    }

    @PostMapping("/calendar/save-htmx")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveCalendarHtmx(
        @RequestParam(required = false) id: UUID?,
        @RequestParam sessionId: UUID,
        @RequestParam eventName: String,
        @RequestParam eventType: CalendarEventType,
        @RequestParam startDate: String,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) description: String?,
        @RequestParam(required = false) isHoliday: Boolean = false,
        @RequestParam(required = false) isExamPeriod: Boolean = false,
        @RequestParam(required = false) color: String = "#3b82f6",
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val academicSession = academicSessionRepository.findById(sessionId).orElseThrow()
            
            if (id != null) {
                // Update existing event
                val existingEvent = schoolCalendarRepository.findById(id).orElseThrow()
                
                if (existingEvent.schoolId != selectedSchoolId) {
                    model.addAttribute("error", "Unauthorized access to calendar event")
                    return "fragments/error :: error-message"
                }
                
                existingEvent.apply {
                    this.session = academicSession
                    this.eventName = eventName
                    this.eventType = eventType
                    this.startDate = LocalDate.parse(startDate)
                    this.endDate = if (endDate.isNullOrBlank()) null else LocalDate.parse(endDate)
                    this.description = description
                    this.isHoliday = isHoliday
                    this.isExamPeriod = isExamPeriod
                    this.color = color
                }
                schoolCalendarRepository.save(existingEvent)
                model.addAttribute("message", "Calendar event updated successfully!")
            } else {
                // Create new event
                val newEvent = SchoolCalendar(
                    session = academicSession,
                    eventName = eventName,
                    eventType = eventType,
                    startDate = LocalDate.parse(startDate)
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.endDate = if (endDate.isNullOrBlank()) null else LocalDate.parse(endDate)
                    this.description = description
                    this.isHoliday = isHoliday
                    this.isExamPeriod = isExamPeriod
                    this.color = color
                    this.isActive = true
                }
                schoolCalendarRepository.save(newEvent)
                model.addAttribute("message", "Calendar event created successfully!")
            }

            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error saving calendar event: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    // Timetable Management
    @GetMapping("/timetable")
    fun timetableList(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val timetableEntries = schoolTimetableRepository.findBySchoolIdAndIsActiveOrderByDayAndTime(selectedSchoolId, true)
        val timetableByDay = timetableEntries.groupBy { it.dayOfWeek }

        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("timetableByDay", timetableByDay)
        model.addAttribute("daysOfWeek", DayOfWeek.values())
        model.addAttribute("activityTypes", TimetableActivityType.values())

        return "admin/academic/timetable"
    }

    @GetMapping("/timetable/new/modal")
    fun getNewTimetableModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("timetableEntry", SchoolTimetable())
        model.addAttribute("daysOfWeek", DayOfWeek.values())
        model.addAttribute("activityTypes", TimetableActivityType.values())
        model.addAttribute("isEdit", false)
        
        return "admin/academic/timetable-modal"
    }

    @GetMapping("/timetable/{id}/modal")
    fun getEditTimetableModal(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val timetableEntry = schoolTimetableRepository.findById(id).orElseThrow { RuntimeException("Timetable entry not found") }
        
        if (timetableEntry.schoolId != selectedSchoolId) {
            throw RuntimeException("Unauthorized access to timetable entry")
        }
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("timetableEntry", timetableEntry)
        model.addAttribute("daysOfWeek", DayOfWeek.values())
        model.addAttribute("activityTypes", TimetableActivityType.values())
        model.addAttribute("isEdit", true)
        
        return "admin/academic/timetable-modal"
    }

    @PostMapping("/timetable/save-htmx")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveTimetableHtmx(
        @RequestParam(required = false) id: UUID?,
        @RequestParam(required = false) dayOfWeek: DayOfWeek?,
        @RequestParam(required = false) daysOfWeek: List<DayOfWeek>?,
        @RequestParam startTime: String,
        @RequestParam endTime: String,
        @RequestParam activityName: String,
        @RequestParam(required = false) description: String?,
        @RequestParam activityType: TimetableActivityType,
        @RequestParam(required = false) isBreak: Boolean = false,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val startTimeObj = LocalTime.parse(startTime)
            val endTimeObj = LocalTime.parse(endTime)
            
            // Validate time range
            if (startTimeObj.isAfter(endTimeObj) || startTimeObj.equals(endTimeObj)) {
                model.addAttribute("error", "Start time must be before end time")
                return "fragments/error :: error-message"
            }
            
            if (id != null) {
                // Update existing entry (single day only)
                if (dayOfWeek == null) {
                    model.addAttribute("error", "Day of week is required for editing")
                    return "fragments/error :: error-message"
                }
                
                val existingEntry = schoolTimetableRepository.findById(id).orElseThrow()
                
                if (existingEntry.schoolId != selectedSchoolId) {
                    model.addAttribute("error", "Unauthorized access to timetable entry")
                    return "fragments/error :: error-message"
                }
                
                // Check for overlapping time slots (excluding current entry)
                val overlapping = schoolTimetableRepository.findOverlappingTimeSlots(
                    selectedSchoolId, dayOfWeek, startTimeObj, endTimeObj, true
                ).filter { it.id != id }
                
                if (overlapping.isNotEmpty()) {
                    model.addAttribute("error", "Time slot overlaps with existing entry: ${overlapping.first().activityName}")
                    return "fragments/error :: error-message"
                }
                
                existingEntry.apply {
                    this.dayOfWeek = dayOfWeek
                    this.startTime = startTimeObj
                    this.endTime = endTimeObj
                    this.activityName = activityName
                    this.description = description
                    this.activityType = activityType
                    this.isBreak = isBreak
                }
                schoolTimetableRepository.save(existingEntry)
                model.addAttribute("message", "Timetable entry updated successfully!")
            } else {
                // Create new entries (can be multiple days)
                val targetDays = daysOfWeek ?: listOfNotNull(dayOfWeek)
                
                if (targetDays.isEmpty()) {
                    model.addAttribute("error", "At least one day must be selected")
                    return "fragments/error :: error-message"
                }
                
                // Check for overlapping time slots on all selected days
                val overlappingDays = mutableListOf<String>()
                targetDays.forEach { day ->
                    val overlapping = schoolTimetableRepository.findOverlappingTimeSlots(
                        selectedSchoolId, day, startTimeObj, endTimeObj, true
                    )
                    if (overlapping.isNotEmpty()) {
                        overlappingDays.add("$day (${overlapping.first().activityName})")
                    }
                }
                
                if (overlappingDays.isNotEmpty()) {
                    model.addAttribute("error", "Time slot overlaps with existing entries on: ${overlappingDays.joinToString(", ")}")
                    return "fragments/error :: error-message"
                }
                
                // Create or Resurrect entries for all selected days
                targetDays.forEach { day ->
                    // Check for existing entry (active or inactive) to handle unique constraint
                    val existingEntryOpt = schoolTimetableRepository.findBySchoolIdAndDayOfWeekAndStartTime(
                        selectedSchoolId, day, startTimeObj
                    )
                    
                    if (existingEntryOpt.isPresent) {
                        val existing = existingEntryOpt.get()
                        // We already checked for overlaps with ACTIVE entries above.
                        // If we are here and it is active, it means it has the EXACT SAME start time.
                        // The overlap check might have caught it, but let's be safe.
                        if (existing.isActive) {
                             // This should ideally be caught by overlap check, but strictly speaking:
                             // Overlap check finds: start < end AND end > start.
                             // If existing is 8:00-9:00 and new is 8:00-9:00, it overlaps.
                             // So we shouldn't reach here if it's active.
                             // But if we do, we update it? No, user intended to create NEW.
                             // But we can't create new because of unique constraint.
                             // So we update the existing one.
                             existing.apply {
                                this.endTime = endTimeObj
                                this.activityName = activityName
                                this.description = description
                                this.activityType = activityType
                                this.isBreak = isBreak
                             }
                             schoolTimetableRepository.save(existing)
                        } else {
                            // Resurrect inactive entry
                            existing.apply {
                                this.endTime = endTimeObj
                                this.activityName = activityName
                                this.description = description
                                this.activityType = activityType
                                this.isBreak = isBreak
                                this.isActive = true
                            }
                            schoolTimetableRepository.save(existing)
                        }
                    } else {
                        // Create brand new entry
                        val newEntry = SchoolTimetable(
                            dayOfWeek = day,
                            startTime = startTimeObj,
                            endTime = endTimeObj,
                            activityName = activityName
                        ).apply {
                            this.schoolId = selectedSchoolId
                            this.description = description
                            this.activityType = activityType
                            this.isBreak = isBreak
                            this.isActive = true
                        }
                        schoolTimetableRepository.save(newEntry)
                    }
                }
                
                val dayNames = targetDays.joinToString(", ")
                val message = if (targetDays.size == 1) {
                    "Timetable entry created successfully!"
                } else {
                    "Timetable entries created for $dayNames successfully!"
                }
                model.addAttribute("message", message)
            }

            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error saving timetable entry: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/calendar/delete/{id}")
    fun deleteCalendarEvent(@PathVariable id: UUID, session: HttpSession, model: Model): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        try {
            val event = schoolCalendarRepository.findById(id).orElseThrow()
            
            if (event.schoolId != selectedSchoolId) {
                model.addAttribute("error", "Unauthorized access to calendar event")
                return "fragments/error :: error-message"
            }
            
            event.isActive = false
            schoolCalendarRepository.save(event)
            model.addAttribute("message", "Calendar event deleted successfully!")
            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error deleting calendar event: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/timetable/delete/{id}")
    fun deleteTimetableEntry(@PathVariable id: UUID, session: HttpSession, model: Model): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        try {
            val entry = schoolTimetableRepository.findById(id).orElseThrow()
            
            if (entry.schoolId != selectedSchoolId) {
                model.addAttribute("error", "Unauthorized access to timetable entry")
                return "fragments/error :: error-message"
            }
            
            entry.isActive = false
            schoolTimetableRepository.save(entry)
            model.addAttribute("message", "Timetable entry deleted successfully!")
            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error deleting timetable entry: ${e.message}")
            return "fragments/error :: error-message"
        }
    }
}