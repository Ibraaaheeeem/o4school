package com.haneef._school.repository

import com.haneef._school.entity.PaystackParentWallet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PaystackParentWalletRepository : JpaRepository<PaystackParentWallet, UUID> {
    
    fun findByParentId(parentId: UUID): PaystackParentWallet?
    
    fun findByAccountNumber(accountNumber: String): PaystackParentWallet?
    
    fun findByCustomerCode(customerCode: String): PaystackParentWallet?
    
    fun existsByParentId(parentId: UUID): Boolean
    
    @Query("SELECT pw FROM PaystackParentWallet pw JOIN FETCH pw.parent p WHERE p.id = :parentId")
    fun findByParentIdWithParent(@Param("parentId") parentId: UUID): PaystackParentWallet?
    
    fun findByParentIdAndIsActive(parentId: UUID, isActive: Boolean): PaystackParentWallet?
    
    @Query("SELECT pw FROM PaystackParentWallet pw WHERE pw.schoolId = :schoolId")
    fun findBySchoolId(@Param("schoolId") schoolId: UUID): List<PaystackParentWallet>
}
