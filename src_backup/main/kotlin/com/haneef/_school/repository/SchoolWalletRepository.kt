package com.haneef._school.repository

import com.haneef._school.entity.SchoolWallet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SchoolWalletRepository : JpaRepository<SchoolWallet, UUID> {
    fun findBySchoolId(schoolId: UUID): SchoolWallet?
    fun existsBySchoolId(schoolId: UUID): Boolean
}
