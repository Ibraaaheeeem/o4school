package com.haneef._school.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.haneef._school.entity.SchoolBankAccount
import com.haneef._school.repository.SchoolBankAccountRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.util.Optional
import java.util.UUID

class PaystackRecipientServiceTest {

    private val repository = mockk<SchoolBankAccountRepository>(relaxed = true)
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `createTransferRecipient returns existing recipient code without calling paystack`() {
        val service = newService(secretKey = "sk_test_123")
        val bankAccountId = UUID.randomUUID()
        val account = SchoolBankAccount().apply {
            recipientCode = "RCP_EXISTING"
        }

        every { repository.findById(bankAccountId) } returns Optional.of(account)

        val result = service.createTransferRecipient(bankAccountId)

        assertTrue(result.isSuccess)
        assertEquals("RCP_EXISTING", result.getOrNull())
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `createTransferRecipient saves recipient code when paystack returns success`() {
        val service = newService(secretKey = "sk_test_123")
        val bankAccountId = UUID.randomUUID()
        val account = SchoolBankAccount().apply {
            accountName = "My School"
            accountNumber = "0123456789"
            bankCode = "058"
            recipientCode = null
        }
        every { repository.findById(bankAccountId) } returns Optional.of(account)
        every { repository.save(any()) } answers { firstArg() }

        val server = serverFor(service)
        server.expect(requestTo("https://api.paystack.co/transferrecipient"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": true,
                      "message": "Recipient created",
                      "data": { "recipient_code": "RCP_12345" }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val result = service.createTransferRecipient(bankAccountId)

        assertTrue(result.isSuccess)
        assertEquals("RCP_12345", result.getOrNull())
        assertEquals("RCP_12345", account.recipientCode)
        verify(exactly = 1) { repository.save(account) }
        server.verify()
    }

    @Test
    fun `createTransferRecipient returns failure when bank code is missing`() {
        val service = newService(secretKey = "sk_test_123")
        val bankAccountId = UUID.randomUUID()
        val account = SchoolBankAccount().apply {
            accountName = "My School"
            accountNumber = "0123456789"
            bankCode = null
        }
        every { repository.findById(bankAccountId) } returns Optional.of(account)

        val result = service.createTransferRecipient(bankAccountId)

        assertFalse(result.isSuccess)
        assertEquals("Bank code is required", result.exceptionOrNull()?.message)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `verifyBankAccount returns account name on success`() {
        val service = newService(secretKey = "sk_test_123")
        val server = serverFor(service)

        server.expect(requestTo(containsString("/bank/resolve?")))
            .andExpect(requestTo(containsString("account_number=0123456789")))
            .andExpect(requestTo(containsString("bank_code=058")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": true,
                      "message": "Account resolved",
                      "data": { "account_name": "Haneef School" }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val result = service.verifyBankAccount("0123456789", "058")

        assertTrue(result.isSuccess)
        assertEquals("Haneef School", result.getOrNull())
        server.verify()
    }

    @Test
    fun `verifyBankAccount fails fast when secret key is blank`() {
        val service = newService(secretKey = "")

        val result = service.verifyBankAccount("0123456789", "058")

        assertFalse(result.isSuccess)
        assertEquals("Paystack secret key is not configured", result.exceptionOrNull()?.message)
    }

    @Test
    fun `createTransferRecipient returns failure when paystack response body is empty`() {
        val service = newService(secretKey = "sk_test_123")
        val bankAccountId = UUID.randomUUID()
        val account = SchoolBankAccount().apply {
            accountName = "My School"
            accountNumber = "0123456789"
            bankCode = "058"
            recipientCode = null
        }
        every { repository.findById(bankAccountId) } returns Optional.of(account)

        val server = serverFor(service)
        server.expect(requestTo("https://api.paystack.co/transferrecipient"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        val result = service.createTransferRecipient(bankAccountId)

        assertFalse(result.isSuccess)
        assertEquals("Empty response body from Paystack", result.exceptionOrNull()?.message)
        verify(exactly = 0) { repository.save(any()) }
        server.verify()
    }

    @Test
    fun `verifyBankAccount returns failure when paystack responds with client error`() {
        val service = newService(secretKey = "sk_test_123")
        val server = serverFor(service)

        server.expect(requestTo(containsString("/bank/resolve?")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withBadRequest().body("{" + "\"status\":false,\"message\":\"Invalid account details\"" + "}"))

        val result = service.verifyBankAccount("0123456789", "058")

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() != null)
        server.verify()
    }

    private fun newService(secretKey: String): PaystackRecipientService {
        return PaystackRecipientService(
            schoolBankAccountRepository = repository,
            paystackSecretKey = secretKey,
            paystackBaseUrl = "https://api.paystack.co",
            objectMapper = objectMapper
        )
    }

    private fun serverFor(service: PaystackRecipientService): MockRestServiceServer {
        val field = PaystackRecipientService::class.java.getDeclaredField("restTemplate")
        field.isAccessible = true
        val restTemplate = field.get(service) as RestTemplate
        return MockRestServiceServer.bindTo(restTemplate).build()
    }
}
