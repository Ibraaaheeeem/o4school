package com.haneef._school.repository

import com.haneef._school.entity.SquadParentWallet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SquadParentWalletRepository : JpaRepository<SquadParentWallet, UUID> {
    
    fun findByParentId(parentId: UUID): SquadParentWallet?
    
    fun findByAccountNumber(accountNumber: String): SquadParentWallet?
    
    fun findByCustomerIdentifier(customerIdentifier: String): SquadParentWallet?
    
    fun existsByParentId(parentId: UUID): Boolean
    
    @Query("SELECT sw FROM SquadParentWallet sw JOIN FETCH sw.parent p WHERE p.id = :parentId")
    fun findByParentIdWithParent(@Param("parentId") parentId: UUID): SquadParentWallet?
    
    fun findByParentIdAndIsActive(parentId: UUID, isActive: Boolean): SquadParentWallet?
    
    @Query("SELECT sw FROM SquadParentWallet sw WHERE sw.schoolId = :schoolId")
    fun findBySchoolId(@Param("schoolId") schoolId: UUID): List<SquadParentWallet>
}
