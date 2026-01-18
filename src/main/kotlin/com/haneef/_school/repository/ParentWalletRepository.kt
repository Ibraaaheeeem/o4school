package com.haneef._school.repository

import com.haneef._school.entity.ParentWallet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ParentWalletRepository : JpaRepository<ParentWallet, UUID> {
    
    fun findByParentId(parentId: UUID): ParentWallet?
    
    fun findByAccountNumber(accountNumber: String): ParentWallet?
    
    fun findByCustomerCode(customerCode: String): ParentWallet?
    
    fun existsByParentId(parentId: UUID): Boolean
    
    @Query("SELECT pw FROM ParentWallet pw JOIN FETCH pw.parent p WHERE p.id = :parentId")
    fun findByParentIdWithParent(@Param("parentId") parentId: UUID): ParentWallet?
    
    fun findByParentIdAndIsActive(parentId: UUID, isActive: Boolean): ParentWallet?
    
    @Query("SELECT pw FROM ParentWallet pw WHERE pw.schoolId = :schoolId")
    fun findBySchoolId(@Param("schoolId") schoolId: UUID): List<ParentWallet>
}
