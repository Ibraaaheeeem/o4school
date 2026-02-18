package com.haneef._school.repository

import com.haneef._school.entity.SchoolBankAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SchoolBankAccountRepository : JpaRepository<SchoolBankAccount, UUID> {
    fun findBySchoolId(schoolId: UUID): SchoolBankAccount?
}
