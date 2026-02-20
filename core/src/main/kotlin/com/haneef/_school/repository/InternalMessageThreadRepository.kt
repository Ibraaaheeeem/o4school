package com.haneef._school.repository

import com.haneef._school.entity.InternalMessageThread
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface InternalMessageThreadRepository : JpaRepository<InternalMessageThread, UUID> {
    fun findBySchoolIdOrderByUpdatedAtDesc(schoolId: UUID): List<InternalMessageThread>
}
