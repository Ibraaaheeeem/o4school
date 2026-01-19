package com.haneef._school.config

import com.haneef._school.service.CustomUserDetailsService
import com.haneef._school.service.UserSchoolRoleService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.RegexRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val customUserDetailsService: CustomUserDetailsService,
    private val userSchoolRoleService: UserSchoolRoleService,
    private val customAuthenticationFailureHandler: CustomAuthenticationFailureHandler,
    private val customAuthenticationEntryPoint: CustomAuthenticationEntryPoint
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf ->
                csrf.ignoringRequestMatchers("/paystack/webhooks", "/h2-console/**")
            }
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/", "/login", "/register", "/activate-account", "/forgot-password", "/auth/**", "/error", "/css/**", "/js/**", "/images/**", "/paystack/webhooks", "/favicon.ico").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                    .requestMatchers("/actuator/**").hasRole("SYSTEM_ADMIN")
                    .requestMatchers("/h2-console/**").hasRole("SYSTEM_ADMIN")
                    .requestMatchers("/404", "/public/**", "/api/public/**").permitAll() // Allow public pages and APIs
                    .requestMatchers("/{slug:[a-zA-Z0-9-]+}").permitAll() // Allow school landing pages by slug
                    .requestMatchers("/select-school", "/select-role").authenticated()
                    .requestMatchers("/system-admin/**").hasRole("SYSTEM_ADMIN")
                    .requestMatchers("/admin/**").hasAnyRole("SYSTEM_ADMIN", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF", "TEACHER")
                    .requestMatchers("/staff/**").hasAnyRole("SYSTEM_ADMIN", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF", "TEACHER")
                    .requestMatchers("/parent/**").hasAnyRole("SYSTEM_ADMIN", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF", "PARENT")
                    .requestMatchers("/student/**").hasAnyRole("SYSTEM_ADMIN", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF", "PARENT", "STUDENT")
                    .anyRequest().authenticated()
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(customAuthenticationEntryPoint)
            }
            .authenticationProvider(authenticationProvider())
            .formLogin { form ->
                form
                    .loginPage("/auth/login")
                    .loginProcessingUrl("/auth/login")
                    .usernameParameter("combinedUsername")
                    .passwordParameter("password")
                    .successHandler(CustomAuthenticationSuccessHandler(userSchoolRoleService))
                    .failureHandler(customAuthenticationFailureHandler)
                    .permitAll()
            }
            .requestCache { cache ->
                val requestCache = org.springframework.security.web.savedrequest.HttpSessionRequestCache()
                val authPaths = listOf("/login", "/register", "/activate-account", "/forgot-password", "/auth/.*", "/favicon.ico")
                val matchers = authPaths.map { RegexRequestMatcher(it, null) }.toMutableList<org.springframework.security.web.util.matcher.RequestMatcher>()
                matchers.add(RequestHeaderRequestMatcher("HX-Request"))
                requestCache.setRequestMatcher(NegatedRequestMatcher(OrRequestMatcher(matchers)))
                cache.requestCache(requestCache)
            }
            .logout { logout ->
                logout
                    .logoutUrl("/auth/logout")
                    .logoutSuccessUrl("/")
                    .permitAll()
            }
            .headers { headers ->
                headers.frameOptions { it.sameOrigin() }
                headers.contentSecurityPolicy { csp ->
                    csp.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdnjs.cloudflare.com https://cdn.quilljs.com https://unpkg.com; style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.quilljs.com https://fonts.googleapis.com; font-src 'self' https://cdnjs.cloudflare.com https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'")
                }
                headers.httpStrictTransportSecurity { hsts ->
                    hsts.includeSubDomains(true)
                    hsts.maxAgeInSeconds(31536000)
                }
            }
        
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun authenticationProvider(): AuthenticationProvider {
        val authProvider = DaoAuthenticationProvider()
        authProvider.setUserDetailsService(customUserDetailsService)
        authProvider.setPasswordEncoder(passwordEncoder())
        return authProvider
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager {
        return config.authenticationManager
    }
}