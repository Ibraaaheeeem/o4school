package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class InternalMessagingService(
    private val threadRepository: InternalMessageThreadRepository,
    private val participantRepository: InternalMessageParticipantRepository,
    private val messageRepository: InternalMessageRepository,
    private val userRepository: UserRepository,
    private val studentRepository: StudentRepository,
    private val classTeacherRepository: ClassTeacherRepository,
    private val subjectTeacherRepository: SubjectTeacherRepository,
    private val staffRepository: StaffRepository,
    private val broadcastService: BroadcastService,
    private val templateParameterResolver: TemplateParameterResolver,
    private val templateRepository: WhatsAppTemplateRepository
) {

    fun getUserThreads(userId: UUID, schoolId: UUID): List<ThreadDTO> {
        val participations = participantRepository.findByUserIdAndThreadSchoolIdOrderByThreadUpdatedAtDesc(userId, schoolId)
        
        return participations.map { p ->
            val thread = p.thread
            // Find the other participant in this thread to show their name
            val otherParticipant = participantRepository.findByThreadId(thread.id!!)
                .firstOrNull { it.user.id != userId }
            
            val otherName = otherParticipant?.user?.fullName ?: "Unknown"
            val otherRole = otherParticipant?.user?.schoolRoles?.firstOrNull { it.schoolId == schoolId && it.isActive }?.role?.name ?: "User"

            ThreadDTO(
                id = thread.id!!,
                subject = thread.subject,
                lastMessagePreview = thread.lastMessagePreview,
                updatedAt = thread.updatedAt,
                unreadCount = p.unreadCount,
                otherParticipantName = otherName,
                otherParticipantRole = otherRole
            )
        }
    }

    fun getTotalUnreadCount(userId: UUID, schoolId: UUID): Int {
        return participantRepository.countTotalUnreadForUser(userId, schoolId) ?: 0
    }

    fun getThreadMessages(threadId: UUID, userId: UUID): List<MessageDTO> {
        // Ensure user is part of thread
        val participant = participantRepository.findByThreadIdAndUserId(threadId, userId)
            ?: throw IllegalArgumentException("User is not a participant in this thread")

        // Mark as read
        if (participant.unreadCount > 0) {
            participant.unreadCount = 0
            participant.lastReadAt = LocalDateTime.now()
            participantRepository.save(participant)
        }

        val messages = messageRepository.findByThreadIdOrderByCreatedAtAsc(threadId)
        val schoolId = participant.thread.schoolId!!

        return messages.map { msg ->
            val isMine = msg.sender.id == userId
            val senderName = if (isMine) "Me" else (msg.sender.fullName ?: msg.sender.email ?: "Unknown")
            val senderRole = msg.sender.schoolRoles.firstOrNull { sr -> sr.schoolId == schoolId && sr.isActive }?.role?.name ?: "User"
            MessageDTO(
                id = msg.id!!,
                senderId = msg.sender.id!!,
                senderName = senderName,
                senderRole = senderRole,
                content = msg.content,
                createdAt = msg.createdAt,
                isMine = isMine
            )
        }
    }

    fun createThread(subject: String?, senderId: UUID, recipientId: UUID, schoolId: UUID, content: String): InternalMessageThread {
        val effectiveSubject = if (subject.isNullOrBlank()) "No Subject" else subject

        val sender = userRepository.findById(senderId).orElseThrow { IllegalArgumentException("Sender not found") }
        val recipient = userRepository.findById(recipientId).orElseThrow { IllegalArgumentException("Recipient not found") }

        // Create thread
        var thread = InternalMessageThread(subject = effectiveSubject).apply {
            this.schoolId = schoolId
            this.lastMessagePreview = content.take(100)
            this.createdAt = LocalDateTime.now()
            this.updatedAt = LocalDateTime.now()
        }
        thread = threadRepository.save(thread)

        // Create Participants
        val senderParticipant = InternalMessageParticipant(thread, sender).apply {
            this.schoolId = schoolId
            this.unreadCount = 0
            this.lastReadAt = LocalDateTime.now()
        }
        val recipientParticipant = InternalMessageParticipant(thread, recipient).apply {
            this.schoolId = schoolId
            this.unreadCount = 1
        }
        participantRepository.saveAll(listOf(senderParticipant, recipientParticipant))

        // Create initial message
        val message = InternalMessage(thread, sender, content).apply {
            this.schoolId = schoolId
        }
        messageRepository.save(message)

        return thread
    }

    fun replyToThread(threadId: UUID, senderId: UUID, content: String): InternalMessage {
        val thread = threadRepository.findById(threadId).orElseThrow { IllegalArgumentException("Thread not found") }
        val sender = userRepository.findById(senderId).orElseThrow { IllegalArgumentException("Sender not found") }

        // Verify sender is participant
        participantRepository.findByThreadIdAndUserId(threadId, senderId)
            ?: throw IllegalArgumentException("Sender is not a participant in this thread")

        val message = InternalMessage(thread, sender, content).apply {
            this.schoolId = thread.schoolId
        }
        val savedMessage = messageRepository.save(message)

        // Update thread
        thread.lastMessagePreview = content.take(100)
        thread.updatedAt = LocalDateTime.now()
        threadRepository.save(thread)

        // Update other participants' unread count
        val participants = participantRepository.findByThreadId(threadId)
        participants.forEach {
            if (it.user.id != senderId) {
                it.unreadCount++
                participantRepository.save(it)
            }
        }

        return savedMessage
    }

    fun sendInternalBroadcast(
        schoolId: UUID,
        senderId: UUID,
        subject: String?,
        content: String?,
        templateName: String?,
        recipients: List<com.haneef._school.dto.BroadcastRecipientDTO>,
        extraParams: Map<String, String> = emptyMap()
    ): Int {
        val effectiveSubject = if (subject.isNullOrBlank()) "No Subject" else subject
        val sender = userRepository.findById(senderId).orElseThrow { IllegalArgumentException("Sender not found") }
        val template = if (!templateName.isNullOrBlank()) {
            templateRepository.findByTemplateName(templateName).orElse(null)
        } else null

        var count = 0
        recipients.forEach { recipient ->
            try {
                val receiver = userRepository.findById(recipient.userId).orElse(null) ?: return@forEach
                
                // Resolve content
                val finalContent = if (template != null) {
                    val placeholders = templateParameterResolver.resolveAllParameters(receiver, schoolId, template, extraParams)
                    // Simple replacement for internal messaging (since we don't use Meta's component structure here)
                    var text = extractTemplateText(template.componentsJson)
                    placeholders.forEach { param ->
                        val key = param["parameter_name"] as? String ?: ""
                        val value = param["text"] as? String ?: ""
                        if (key.isNotEmpty()) {
                            text = text.replace("{{$key}}", value)
                        }
                    }
                    // Handle indexed placeholders too {{1}}, {{2}}...
                    placeholders.forEachIndexed { index, param ->
                        val value = param["text"] as? String ?: ""
                        text = text.replace("{{${index + 1}}}", value)
                    }
                    text
                } else {
                    content ?: ""
                }

                if (finalContent.isBlank()) return@forEach

                // Find or create thread
                // For internal broadcast, we usually create a new thread per recipient or if one exists with same subject?
                // Standard internal messaging here seems to be 1-on-1. 
                // Let's check if there's an existing 1-on-1 thread between these two.
                
                var thread = findExisting1on1Thread(senderId, receiver.id!!, schoolId, effectiveSubject)
                
                if (thread == null) {
                    thread = InternalMessageThread(subject = effectiveSubject).apply {
                        this.schoolId = schoolId
                        this.lastMessagePreview = finalContent.take(100)
                        this.createdAt = LocalDateTime.now()
                        this.updatedAt = LocalDateTime.now()
                    }
                    thread = threadRepository.save(thread)

                    val senderPart = InternalMessageParticipant(thread, sender).apply {
                        this.schoolId = schoolId
                        this.unreadCount = 0
                        this.lastReadAt = LocalDateTime.now()
                    }
                    val receiverPart = InternalMessageParticipant(thread, receiver).apply {
                        this.schoolId = schoolId
                        this.unreadCount = 1
                    }
                    participantRepository.saveAll(listOf(senderPart, receiverPart))
                } else {
                    // Update existing thread
                    thread.lastMessagePreview = finalContent.take(100)
                    thread.updatedAt = LocalDateTime.now()
                    threadRepository.save(thread)
                    
                    val receiverPart = participantRepository.findByThreadIdAndUserId(thread.id!!, receiver.id!!)
                    if (receiverPart != null) {
                        receiverPart.unreadCount++
                        participantRepository.save(receiverPart)
                    }
                }

                val message = InternalMessage(thread, sender, finalContent).apply {
                    this.schoolId = schoolId
                }
                messageRepository.save(message)
                count++
            } catch (e: Exception) {
                // Log and continue
            }
        }
        return count
    }

    private fun findExisting1on1Thread(user1Id: UUID, user2Id: UUID, schoolId: UUID, subject: String): InternalMessageThread? {
        val user1Threads = participantRepository.findByUserIdAndThreadSchoolIdOrderByThreadUpdatedAtDesc(user1Id, schoolId)
            .map { it.thread }
            .filter { it.subject == subject }
            
        return user1Threads.find { thread ->
            val participants = participantRepository.findByThreadId(thread.id!!)
            participants.size == 2 && participants.any { it.user.id == user2Id }
        }
    }

    private fun extractTemplateText(componentsJson: String?): String {
        if (componentsJson.isNullOrBlank()) return ""
        return try {
            val mapper = jacksonObjectMapper()
            val components = mapper.readValue<List<Map<String, Any>>>(componentsJson)
            val body = components.find { it["type"] == "BODY" || it["type"] == "body" }
            body?.get("text")?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getEligibleContacts(user: User, schoolId: UUID): List<ContactDTO> {
        val roles: List<RoleType> = user.schoolRoles
            .filter { it.schoolId == schoolId && it.isActive }
            .map { it.role.roleType }
        val contacts = mutableSetOf<User>()

        if (roles.contains(RoleType.PARENT)) {
            // Parent can message: Child's Class Teacher, Child's Subject Teachers, Principal
            val children: List<Student> = studentRepository.findBySchoolIdAndIsActive(schoolId, true, org.springframework.data.domain.Pageable.unpaged()).content
                .filter { s -> s.parentRelationships.any { rel -> rel.parent.user.id == user.id } }
            
            children.forEach { student ->
                student.classEnrollments.filter { it.isActive }.forEach { enrollment ->
                    val schoolClass = enrollment.schoolClass
                    // Class Teacher
                    classTeacherRepository.findBySchoolClassIdAndIsActive(schoolClass.id!!, true).firstOrNull()?.staff?.user?.let { contacts.add(it) }
                    // Subject Teachers
                    subjectTeacherRepository.findBySchoolClassIdAndIsActive(schoolClass.id!!, true).forEach { st ->
                        st.staff.user.let { contacts.add(it) }
                    }
                }
            }
            // Principals
            contacts.addAll(getUsersByRole(schoolId, RoleType.SCHOOL_ADMIN))
        }

        if (roles.contains(RoleType.STAFF) || roles.contains(RoleType.SCHOOL_ADMIN)) {
            // Staff can message: Other Staff, Principals, Parents of students in their classes
            contacts.addAll(getUsersByRole(schoolId, RoleType.STAFF))
            contacts.addAll(getUsersByRole(schoolId, RoleType.SCHOOL_ADMIN))

            val staffRecord = staffRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
            if (staffRecord != null) {
                // If they are a class teacher, get parents of students in that class
                classTeacherRepository.findByStaffIdAndIsActive(staffRecord.id!!, true).forEach { ct ->
                    val sc = ct.schoolClass
                    studentRepository.findBySchoolIdAndIsActiveAndClassId(schoolId, true, sc.id!!, org.springframework.data.domain.Pageable.unpaged()).content.forEach { student ->
                         student.parentRelationships.forEach { pr ->
                             contacts.add(pr.parent.user)
                         }
                    }
                }
                
                // If they are a subject teacher, get parents of students in those classes
                subjectTeacherRepository.findByStaffIdAndIsActive(staffRecord.id!!, true).forEach { st ->
                    studentRepository.findBySchoolIdAndIsActiveAndClassId(schoolId, true, st.schoolClass.id!!, org.springframework.data.domain.Pageable.unpaged()).content.forEach { student ->
                         student.parentRelationships.forEach { pr ->
                             contacts.add(pr.parent.user)
                         }
                    }
                }
            }
        }

        // Remove self from contacts
        contacts.removeIf { it.id == user.id }

        return contacts.map { 
            val roleLabel = it.schoolRoles.firstOrNull { sr -> sr.schoolId == schoolId && sr.isActive }?.role?.name ?: "User"
            ContactDTO(it.id!!, it.fullName ?: it.email ?: "Unknown", roleLabel) 
        }.sortedBy { it.name }
    }

    private fun getUsersByRole(schoolId: UUID, roleType: RoleType): List<User> {
        // Find users who have the specific role for the specific school
        val allUsers = userRepository.findAll() 
        return allUsers.filter { u -> u.schoolRoles.any { it.schoolId == schoolId && it.isActive && it.role.roleType == roleType } }
    }

    data class ContactDTO(
        val id: UUID,
        val name: String,
        val role: String
    )

    data class ThreadDTO(
        val id: UUID,
        val subject: String,
        val lastMessagePreview: String?,
        val updatedAt: LocalDateTime,
        val unreadCount: Int,
        val otherParticipantName: String,
        val otherParticipantRole: String
    )

    data class MessageDTO(
        val id: UUID,
        val senderId: UUID,
        val senderName: String,
        val senderRole: String,
        val content: String,
        val createdAt: LocalDateTime,
        val isMine: Boolean
    )
}
