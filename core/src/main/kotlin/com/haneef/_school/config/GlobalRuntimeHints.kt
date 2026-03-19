package com.haneef._school.config

import org.slf4j.LoggerFactory
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import jakarta.persistence.Entity

import java.util.*
import org.hibernate.collection.spi.PersistentBag
import org.hibernate.collection.spi.PersistentSet
import org.hibernate.collection.spi.PersistentSortedSet
import org.hibernate.collection.spi.PersistentList

class GlobalRuntimeHints : RuntimeHintsRegistrar {

    private val logger = LoggerFactory.getLogger(GlobalRuntimeHints::class.java)

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val cl = classLoader ?: javaClass.classLoader

        // 1. Register JPA model classes (@Entity, @Embeddable, @MappedSuperclass) and DTOs (@NativeDto)
        val scanner = ClassPathScanningCandidateComponentProvider(false)

        // Include JPA entity types
        scanner.addIncludeFilter(AnnotationTypeFilter(Entity::class.java))
        tryAddAnnotationFilter(scanner, "jakarta.persistence.Embeddable")
        tryAddAnnotationFilter(scanner, "jakarta.persistence.MappedSuperclass")

        // Include classes marked for native reflection
        scanner.addIncludeFilter(AnnotationTypeFilter(NativeDto::class.java))

        val candidates = scanner.findCandidateComponents("com.haneef._school")

        candidates.forEach { beanDefinition ->
            val name = beanDefinition.beanClassName ?: return@forEach
            try {
                val clazz = Class.forName(name, false, cl)
                hints.reflection().registerType(
                    clazz,
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INTROSPECT_PUBLIC_METHODS,
                    MemberCategory.INTROSPECT_PUBLIC_CONSTRUCTORS
                )
            } catch (e: Exception) {
                logger.debug("Skipping reflection hint for '{}': {}", name, e.message)
            }
        }

        // 2. Register standard Java Collections & Math
        val javaUtils = listOf(
            ArrayList::class.java, LinkedList::class.java,
            HashMap::class.java, LinkedHashMap::class.java,
            HashSet::class.java, LinkedHashSet::class.java,
            List::class.java, Set::class.java, Map::class.java,
            java.lang.Math::class.java
        )
        javaUtils.forEach { 
            hints.reflection().registerType(it, MemberCategory.INVOKE_PUBLIC_METHODS) 
        }

        // 3. Register Thymeleaf Utilities
        listOf(
            "org.thymeleaf.expression.Lists",
            "org.thymeleaf.expression.Strings",
            "org.thymeleaf.expression.Numbers",
            "org.thymeleaf.expression.Dates",
            "org.thymeleaf.expression.Arrays",
            "org.thymeleaf.engine.IterationStatusVar",
            "org.thymeleaf.expression.Booleans",
            "org.thymeleaf.expression.Objects",
            "org.thymeleaf.expression.Aggregates",
            "org.thymeleaf.expression.Messages",
            "org.thymeleaf.expression.Ids",
            "org.thymeleaf.expression.Temporals"
        ).forEach { name ->
            try {
                hints.reflection().registerType(Class.forName(name, false, cl), MemberCategory.INVOKE_PUBLIC_METHODS)
            } catch (e: Exception) {
                logger.debug("Skipping Thymeleaf hint for '{}': {}", name, e.message)
            }
        }

        // 4. Fix for Kotlin Collections
        listOf("kotlin.collections.EmptyList", "kotlin.collections.EmptyMap").forEach { name ->
            try {
                hints.reflection().registerType(Class.forName(name, false, cl), MemberCategory.INVOKE_PUBLIC_METHODS)
            } catch (e: Exception) {
                logger.debug("Skipping Kotlin collection hint for '{}': {}", name, e.message)
            }
        }

        // 5. Register Spring Data Page/Slice/PageImpl
        listOf(
            "org.springframework.data.domain.PageImpl",
            "org.springframework.data.domain.Page",
            "org.springframework.data.domain.Slice",
            "org.springframework.data.domain.Chunk"
        ).forEach { name ->
            try {
                hints.reflection().registerType(Class.forName(name, false, cl), MemberCategory.INVOKE_PUBLIC_METHODS)
            } catch (e: Exception) {
                logger.debug("Skipping Spring Data hint for '{}': {}", name, e.message)
            }
        }

        // 6. Register java.util.Collections$UnmodifiableRandomAccessList
        try {
            hints.reflection().registerType(
                Class.forName("java.util.Collections\$UnmodifiableRandomAccessList", false, cl),
                MemberCategory.INVOKE_PUBLIC_METHODS
            )
        } catch (e: ClassNotFoundException) {
            logger.debug("Skipping UnmodifiableRandomAccessList hint: {}", e.message)
        }

        // 7. Register Hibernate Collections (PersistentBag, etc.)
        val hibernateCollections = listOf(
            PersistentBag::class.java,
            PersistentSet::class.java,
            PersistentSortedSet::class.java,
            PersistentList::class.java
        )

        hibernateCollections.forEach { 
            hints.reflection().registerType(it, MemberCategory.INVOKE_PUBLIC_METHODS) 
        }

        // 8. Catch-All Strategy for Proxies
        listOf(
            "org.hibernate.proxy.HibernateProxy",
            "org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor"
        ).forEach { name ->
            try {
                hints.reflection().registerType(Class.forName(name, false, cl), MemberCategory.INVOKE_PUBLIC_METHODS)
            } catch (e: Exception) {
                logger.debug("Skipping Hibernate proxy hint for '{}': {}", name, e.message)
            }
        }
        // 9. Register AWT and ImageIO for Invoice Generation
        val awtClasses = listOf(
            java.awt.Color::class.java,
            java.awt.Font::class.java,
            java.awt.RenderingHints::class.java,
            java.awt.image.BufferedImage::class.java,
            javax.imageio.ImageIO::class.java
        )
        
        awtClasses.forEach {
            try {
                hints.reflection().registerType(it, 
                    MemberCategory.INVOKE_PUBLIC_METHODS, 
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.DECLARED_FIELDS)
            } catch (e: Exception) {}
        }

        // 10. Register PostgreSQL Dialect and Hibernate internals for Native
        listOf(
            "org.hibernate.dialect.PostgreSQLDialect",
            "org.hibernate.dialect.DatabaseVersion",
            "com.haneef._school.config.GlobalRuntimeHints"
        ).forEach { name ->
            try {
                hints.reflection().registerType(
                    Class.forName(name, false, cl),
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.DECLARED_FIELDS
                )
            } catch (e: Exception) {
                logger.debug("Skipping Hibernate internals hint for '{}': {}", name, e.message)
            }
        }

        // 11. Register types for Serialization (Native fix)
        // This is necessary because Hibernate serializes certain query parameters/caching data
        val serializableTypes = listOf(
            UUID::class.java,
            ArrayList::class.java,
            LinkedList::class.java,
            HashMap::class.java,
            LinkedHashMap::class.java,
            HashSet::class.java,
            java.lang.Long::class.java,
            java.lang.Integer::class.java,
            java.lang.Double::class.java,
            java.lang.Boolean::class.java,
            java.lang.String::class.java,
            java.time.LocalDateTime::class.java,
            java.time.LocalDate::class.java
        )
        
        serializableTypes.forEach {
            hints.serialization().registerType(it)
        }

        // 12. Register Flyway Internals for Native Image
        listOf(
            "org.flywaydb.core.internal.database.postgresql.PostgreSQLDatabaseType",
            "org.flywaydb.core.internal.database.postgresql.PostgreSQLConnection",
            "org.flywaydb.core.api.configuration.FluentConfiguration",
            "org.flywaydb.core.Flyway",
            "org.flywaydb.database.postgresql.PostgreSQLDatabaseType",
            "org.flywaydb.core.internal.configuration.extensions.DeployScriptFilenameConfigurationExtension"
        ).forEach { name ->
            try {
                hints.reflection().registerType(
                    Class.forName(name, false, cl),
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.DECLARED_FIELDS
                )
            } catch (e: Exception) {
                logger.debug("Skipping Flyway hint for '{}': {}", name, e.message)
            }
        }
        // 13. Register AI DTOs explicitly (nested classes picked up via @NativeDto at class-scan time;
        //     listed here as a safety net for inner-class JVM names using '$')
        listOf(
            "com.haneef._school.service.SchoolDataTools\$RecipientInfo",
            "com.haneef._school.controller.NaturalLanguageQueryController\$RecipientListResponse",
            "com.haneef._school.controller.NaturalLanguageQueryController\$QueryRequest"
        ).forEach { name ->
            try {
                hints.reflection().registerType(
                    Class.forName(name, false, cl),
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.DECLARED_FIELDS
                )
            } catch (e: Exception) {
                logger.debug("Skipping AI DTO hint for '{}': {}", name, e.message)
            }
        }

        // 14. Register Google GenAI library types (Fix for Native Image deserialization)
        listOf(
            "com.google.genai.types.PartMediaResolution",
            "com.google.genai.types.PartMediaResolution\$Builder",
            "com.google.genai.types.GenerateContentConfig",
            "com.google.genai.types.GenerateContentConfig\$Builder",
            "com.google.genai.types.GenerationConfig",
            "com.google.genai.types.GenerationConfig\$Builder",
            "com.google.genai.types.Content",
            "com.google.genai.types.Content\$Builder",
            "com.google.genai.types.Part",
            "com.google.genai.types.Part\$Builder"
        ).forEach { name ->
            try {
                hints.reflection().registerType(
                    Class.forName(name, false, cl),
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.INTROSPECT_PUBLIC_METHODS,
                    MemberCategory.INTROSPECT_PUBLIC_CONSTRUCTORS
                )
            } catch (e: Exception) {
                logger.debug("Skipping Google GenAI hint for '{}': {}", name, e.message)
            }
        }
    }

    private fun tryAddAnnotationFilter(
        scanner: ClassPathScanningCandidateComponentProvider,
        annotationClassName: String
    ) {
        try {
            val loadedClass = Class.forName(annotationClassName)
            if (!loadedClass.isAnnotation) return
            @Suppress("UNCHECKED_CAST")
            val annotationType = loadedClass as Class<Annotation>
            scanner.addIncludeFilter(AnnotationTypeFilter(annotationType))
        } catch (_: ClassNotFoundException) {
            // Annotation not present on classpath; skip silently
        }
    }
}
