package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.repository.PaystackParentWalletRepository
import com.haneef._school.repository.SquadParentWalletRepository
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals

class WalletAsyncServiceTest {

    private val paystackRepo = mockk<PaystackParentWalletRepository>(relaxed = true)
    private val squadRepo = mockk<SquadParentWalletRepository>(relaxed = true)
    private val paystackService = mockk<PaystackService>(relaxed = true)
    private val squadService = mockk<SquadService>(relaxed = true)

    private lateinit var service: WalletAsyncService

    @BeforeEach
    fun setup() {
        service = WalletAsyncService(paystackRepo, paystackService, squadRepo, squadService)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `generatePaystackAccount - success updates wallet`() {
        val walletId = UUID.randomUUID()
        val user = User(phoneNumber = "08010000000", email = "u@example.com")
        val parent = Parent(user = user)
        val wallet = PaystackParentWallet(parent = parent, customerCode = "CUST123")

        every { paystackRepo.findById(walletId) } returns Optional.of(wallet)

        val bank = BankData(name = "Wema", id = 1, slug = "wema")
        val accountData = AccountData(
            bank = bank,
            accountName = "Test Account",
            accountNumber = "1234567890",
            assigned = true,
            currency = "NGN",
            active = true,
            id = 999L,
            createdAt = LocalDateTime.now().toString(),
            updatedAt = LocalDateTime.now().toString(),
            assignment = null,
            customer = null
        )

        val resp = PaystackAccountResponse(status = true, message = "ok", data = accountData)
        every { paystackService.createDedicatedAccount("CUST123", any()) } returns resp

        val slot = slot<PaystackParentWallet>()
        every { paystackRepo.save(capture(slot)) } returns wallet

        service.generatePaystackAccount(walletId, "wema-bank")

        verify { paystackRepo.findById(walletId) }
        verify { paystackService.createDedicatedAccount("CUST123", "wema-bank") }
        verify { paystackRepo.save(any()) }

        assertEquals("1234567890", slot.captured.accountNumber)
        assertEquals("Test Account", slot.captured.accountName)
        assertEquals("Wema", slot.captured.bankName)
        assertEquals(999L, slot.captured.paystackAccountId)
    }

    @Test
    fun `generatePaystackAccount - failure does not save`() {
        val walletId = UUID.randomUUID()
        val wallet = PaystackParentWallet()
        every { paystackRepo.findById(walletId) } returns Optional.of(wallet)
        every { paystackService.createDedicatedAccount(any(), any()) } returns null

        service.generatePaystackAccount(walletId, "wema-bank")

        verify { paystackRepo.findById(walletId) }
        verify { paystackService.createDedicatedAccount(any(), "wema-bank") }
        verify(exactly = 0) { paystackRepo.save(any()) }
    }

    @Test
    fun `generateSquadAccount - success updates wallet`() {
        val walletId = UUID.randomUUID()
        val user = User(phoneNumber = "+2348010000000", email = "p@example.com", firstName = "John", lastName = "Doe")
        val parent = Parent(user = user)
        val wallet = SquadParentWallet(parent = parent)

        every { squadRepo.findById(walletId) } returns Optional.of(wallet)

        val data = SquadAccountData(
            firstName = "John",
            lastName = "Doe",
            accountNumber = "4000123456",
            bankName = "Squad Bank",
            bankCode = "001",
            customerIdentifier = "p@example.com",
            currency = "NGN"
        )
        val resp = SquadAccountResponse(success = true, message = "ok", data = data)
        every { squadService.createVirtualAccount(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns resp

        val slot = slot<SquadParentWallet>()
        every { squadRepo.save(capture(slot)) } returns wallet

        service.generateSquadAccount(walletId, "12345678901", "01/01/1980", "1", "Somewhere")

        verify { squadRepo.findById(walletId) }
        verify { squadService.createVirtualAccount(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify { squadRepo.save(any()) }

        assertEquals("4000123456", slot.captured.accountNumber)
        assertEquals("John Doe", slot.captured.accountName)
        assertEquals("Squad Bank", slot.captured.bankName)
        assertEquals(true, slot.captured.isActive)
    }

    @Test
    fun `generateSquadAccount - missing phone deletes wallet`() {
        val walletId = UUID.randomUUID()
        val user = User(phoneNumber = null, email = "p@example.com")
        val parent = Parent(user = user)
        val wallet = SquadParentWallet(parent = parent)

        every { squadRepo.findById(walletId) } returns Optional.of(wallet)
        every { squadRepo.delete(wallet) } just Runs

        service.generateSquadAccount(walletId, "", "", "", "")

        verify { squadRepo.findById(walletId) }
        verify { squadRepo.delete(wallet) }
    }

    @Test
    fun `generateSquadAccount - api failure deletes wallet`() {
        val walletId = UUID.randomUUID()
        val user = User(phoneNumber = "+2348010000000", email = "p@example.com")
        val parent = Parent(user = user)
        val wallet = SquadParentWallet(parent = parent)

        every { squadRepo.findById(walletId) } returns Optional.of(wallet)
        every { squadService.createVirtualAccount(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns null
        every { squadRepo.delete(wallet) } just Runs

        service.generateSquadAccount(walletId, "x", "x", "x", "x")

        verify { squadRepo.findById(walletId) }
        verify { squadService.createVirtualAccount(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify { squadRepo.delete(wallet) }
    }
}
