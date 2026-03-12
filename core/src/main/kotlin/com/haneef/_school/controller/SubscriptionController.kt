package com.haneef._school.controller

import com.haneef._school.entity.User
import com.haneef._school.repository.ServiceUsageLogRepository
import com.haneef._school.service.SubscriptionService
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@RequestMapping("/admin/school-setup/subscriptions")
class SubscriptionController(
    private val subscriptionService: SubscriptionService,
    private val usageLogRepository: ServiceUsageLogRepository,
    @org.springframework.beans.factory.annotation.Value("\${paystack.public.key:}") private val paystackPublicKey: String,
    @org.springframework.beans.factory.annotation.Value("\${squad.public.key:}") private val squadPublicKey: String,
    @org.springframework.beans.factory.annotation.Value("\${WHATSAPP_SUB_RATE:500}") private val whatsappSubRate: Long,
    @org.springframework.beans.factory.annotation.Value("\${SMS_SUB_RATE:5}") private val smsSubRate: Long,
    @org.springframework.beans.factory.annotation.Value("\${AI_SUB_RATE:10}") private val aiSubRate: Long
) {

    @GetMapping
    fun viewSubscriptions(
        authentication: org.springframework.security.core.Authentication,
        session: jakarta.servlet.http.HttpSession,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        model: Model
    ): String {
        // Find user and their school context
        val userDetails = authentication.principal as com.haneef._school.service.CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? java.util.UUID
            ?: return "redirect:/admin/school-setup/school-details"

        // Fetch their current subscription status and balances
        val subscription = subscriptionService.getSubscription(selectedSchoolId)
        model.addAttribute("subscription", subscription)
        
        // Calculate dynamic properties
        val activeStudents = subscriptionService.getActiveStudentCount(selectedSchoolId)
        val renewalFee = subscriptionService.calculateRenewalFee(selectedSchoolId)
        model.addAttribute("activeStudents", activeStudents)
        model.addAttribute("renewalFee", renewalFee)
        model.addAttribute("paystackPublicKey", paystackPublicKey)
        model.addAttribute("squadPublicKey", squadPublicKey)
        model.addAttribute("whatsappSubRate", whatsappSubRate)
        model.addAttribute("smsSubRate", smsSubRate)
        model.addAttribute("aiSubRate", aiSubRate)

        // Fetch their paginated usage logs so admins can track staff
        val pageable = PageRequest.of(page, size)
        val usageLogs = usageLogRepository.findBySchoolIdOrderByTimestampDesc(selectedSchoolId, pageable)
        model.addAttribute("usageLogs", usageLogs)
        model.addAttribute("currentPage", page)
        model.addAttribute("totalPages", usageLogs.totalPages)

        return "admin/school-setup/subscriptions"
    }

    @org.springframework.web.bind.annotation.PostMapping("/fee-collection")
    fun updateFeeCollection(
        authentication: org.springframework.security.core.Authentication,
        session: jakarta.servlet.http.HttpSession,
        @RequestParam(required = false, defaultValue = "false") active: Boolean,
        @RequestParam(required = false) accountNumber: String?,
        @RequestParam(required = false) bankName: String?,
        @RequestParam(required = false, defaultValue = "false") termsAccepted: Boolean,
        redirectAttributes: org.springframework.web.servlet.mvc.support.RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? java.util.UUID
            ?: return "redirect:/admin/school-setup/school-details"

        if (active) {
            if (accountNumber.isNullOrBlank() || bankName.isNullOrBlank() || !termsAccepted) {
                redirectAttributes.addFlashAttribute("error", "Bank Name, Account Number, and accepted Terms are required to enable Fee Collection.")
                return "redirect:/admin/school-setup/subscriptions"
            }
        }

        try {
            subscriptionService.updateFeeCollectionSettings(selectedSchoolId, accountNumber, bankName, termsAccepted, active)
            redirectAttributes.addFlashAttribute("success", if (active) "Fee Collection enabled successfully!" else "Fee Collection disabled.")
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error updating settings: ${e.message}")
        }
        
        return "redirect:/admin/school-setup/subscriptions"
    }

    @org.springframework.web.bind.annotation.PostMapping("/process-payment")
    fun processPayment(
        session: jakarta.servlet.http.HttpSession,
        @RequestParam reference: String,
        @RequestParam type: String,
        @RequestParam units: Int,
        @RequestParam amount: Long,
        @RequestParam(required = false) redirectUrl: String?,
        redirectAttributes: org.springframework.web.servlet.mvc.support.RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? java.util.UUID
            ?: return "redirect:/admin/school-setup/school-details"

        try {
            when (type) {
                "WHATSAPP_SUB_ORDER" -> subscriptionService.topUpTokens(selectedSchoolId, com.haneef._school.entity.ServiceFeature.WHATSAPP_MESSAGING, units)
                "SMS_SUB_ORDER" -> subscriptionService.topUpTokens(selectedSchoolId, com.haneef._school.entity.ServiceFeature.SMS_MESSAGING, units)
                "AI_SUB_ORDER" -> subscriptionService.topUpTokens(selectedSchoolId, com.haneef._school.entity.ServiceFeature.AI_TOKENS, units)
                "MAIN_SUB_ORDER" -> subscriptionService.renewSubscription(selectedSchoolId, units)
                else -> throw IllegalArgumentException("Unknown order type: $type")
            }
            redirectAttributes.addFlashAttribute("success", "Payment successful! Your account has been updated.")
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error processing payment: ${e.message}")
        }
        
        return if (!redirectUrl.isNullOrBlank() && redirectUrl.startsWith("/")) {
            "redirect:$redirectUrl"
        } else {
            "redirect:/admin/school-setup/subscriptions"
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/renew")
    fun renewSubscription(
        session: jakarta.servlet.http.HttpSession,
        @RequestParam(defaultValue = "1") years: Int,
        redirectAttributes: org.springframework.web.servlet.mvc.support.RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? java.util.UUID
            ?: return "redirect:/admin/school-setup/school-details"

        try {
            subscriptionService.renewSubscription(selectedSchoolId, years)
            redirectAttributes.addFlashAttribute("success", "Subscription renewed successfully for $years year(s)!")
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error renewing subscription: ${e.message}")
        }
        
        return "redirect:/admin/school-setup/subscriptions"
    }
}
