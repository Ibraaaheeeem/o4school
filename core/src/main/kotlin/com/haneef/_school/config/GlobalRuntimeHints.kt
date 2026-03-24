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

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalRuntimeHints::class.java)
        private val COMMON_REFLECTION_CATEGORIES = arrayOf(
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INTROSPECT_PUBLIC_METHODS,
            MemberCategory.INTROSPECT_PUBLIC_CONSTRUCTORS
        )
    }

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val cl = classLoader ?: javaClass.classLoader

        registerJpaAndNativeDtos(hints, cl)
        registerJavaCollectionsAndMath(hints)
        registerThymeleafUtilities(hints, cl)
        registerKotlinCollections(hints, cl)
        registerSpringDataTypes(hints, cl)
        registerUnmodifiableRandomAccessList(hints, cl)
        registerHibernateCollections(hints)
        registerHibernateProxyHints(hints, cl)
        registerAwtAndImageIo(hints)
        registerHibernateAndDialectInternals(hints, cl)
        registerSerializableTypes(hints)
        registerFlywayInternals(hints, cl)
        registerAiDtoSafetyNet(hints, cl)
        registerGoogleGenAiTypes(hints, cl)
    }

    private fun registerJpaAndNativeDtos(hints: RuntimeHints, cl: ClassLoader) {
        val scanner = ClassPathScanningCandidateComponentProvider(false)

        scanner.addIncludeFilter(AnnotationTypeFilter(Entity::class.java))
        tryAddAnnotationFilter(scanner, "jakarta.persistence.Embeddable")
        tryAddAnnotationFilter(scanner, "jakarta.persistence.MappedSuperclass")
        scanner.addIncludeFilter(AnnotationTypeFilter(NativeDto::class.java))

        scanner.findCandidateComponents("com.haneef._school").forEach { beanDefinition ->
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
            } catch (e: ClassNotFoundException) {
                logger.debug("Skipping reflection hint for '{}': {}", name, e.message)
            } catch (e: LinkageError) {
                logger.debug("Skipping reflection hint for '{}' due to linkage error: {}", name, e.message)
            }
        }
    }

    private fun registerJavaCollectionsAndMath(hints: RuntimeHints) {
        val javaUtils = listOf(
            ArrayList::class.java, LinkedList::class.java,
            HashMap::class.java, LinkedHashMap::class.java,
            HashSet::class.java, LinkedHashSet::class.java,
            java.lang.Math::class.java
        )
        javaUtils.forEach { registerReflectionType(hints, it, MemberCategory.INVOKE_PUBLIC_METHODS) }
    }

    private fun registerThymeleafUtilities(hints: RuntimeHints, cl: ClassLoader) {
        registerReflectionTypesByName(
            hints = hints,
            cl = cl,
            names = listOf(
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
            ),
            context = "Thymeleaf",
            categories = arrayOf(MemberCategory.INVOKE_PUBLIC_METHODS)
        )
    }

    private fun registerKotlinCollections(hints: RuntimeHints, cl: ClassLoader) {
        registerReflectionTypesByName(
            hints = hints,
            cl = cl,
            names = listOf("kotlin.collections.EmptyList", "kotlin.collections.EmptyMap"),
            context = "Kotlin collection",
            categories = arrayOf(MemberCategory.INVOKE_PUBLIC_METHODS)
        )
    }

    private fun registerSpringDataTypes(hints: RuntimeHints, cl: ClassLoader) {
        registerReflectionTypesByName(
            hints = hints,
            cl = cl,
            names = listOf(
            "org.springframework.data.domain.PageImpl",
            "org.springframework.data.domain.Page",
            "org.springframework.data.domain.Slice",
            "org.springframework.data.domain.Chunk"
            ),
            context = "Spring Data",
            categories = arrayOf(MemberCategory.INVOKE_PUBLIC_METHODS)
        )
    }

    private fun registerUnmodifiableRandomAccessList(hints: RuntimeHints, cl: ClassLoader) {
        registerReflectionTypeByName(
            hints = hints,
            cl = cl,
            name = "java.util.Collections\$UnmodifiableRandomAccessList",
            context = "UnmodifiableRandomAccessList",
            categories = arrayOf(MemberCategory.INVOKE_PUBLIC_METHODS)
        )
    }

    private fun registerHibernateCollections(hints: RuntimeHints) {
        val hibernateCollections = listOf(
            PersistentBag::class.java,
            PersistentSet::class.java,
            PersistentSortedSet::class.java,
            PersistentList::class.java
        )

        hibernateCollections.forEach { registerReflectionType(hints, it, MemberCategory.INVOKE_PUBLIC_METHODS) }
    }

    private fun registerHibernateProxyHints(hints: RuntimeHints, cl: ClassLoader) {
        registerReflectionTypesByName(
            hints = hints,
            cl = cl,
            names = listOf(
            "org.hibernate.proxy.HibernateProxy",
            "org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor"
            ),
            context = "Hibernate proxy",
            categories = arrayOf(MemberCategory.INVOKE_PUBLIC_METHODS)
        )

        try {
            val hibernateProxy = Class.forName("org.hibernate.proxy.HibernateProxy", false, cl)
            if (hibernateProxy.isInterface) {
                hints.proxies().registerJdkProxy(hibernateProxy, java.io.Serializable::class.java)
            }
        } catch (e: ClassNotFoundException) {
            logger.debug("Skipping Hibernate proxy JDK-proxy hint: {}", e.message)
        } catch (e: LinkageError) {
            logger.debug("Skipping Hibernate proxy JDK-proxy hint due to linkage error: {}", e.message)
        }
    }

    private fun registerAwtAndImageIo(hints: RuntimeHints) {
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
            } catch (e: Exception) {
                logger.debug("Skipping AWT/ImageIO hint for '{}': {}", it.name, e.message)
            }
        }
    }

    private fun registerHibernateAndDialectInternals(hints: RuntimeHints, cl: ClassLoader) {
        registerReflectionTypesByName(
            hints = hints,
            cl = cl,
            names = listOf(
            "org.hibernate.dialect.PostgreSQLDialect",
            "org.hibernate.dialect.DatabaseVersion"
            ),
            context = "Hibernate internals",
            categories = COMMON_REFLECTION_CATEGORIES
        )
    }

    private fun registerSerializableTypes(hints: RuntimeHints) {
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
    }

    private fun registerFlywayInternals(hints: RuntimeHints, cl: ClassLoader) {
        registerReflectionTypesByName(
            hints = hints,
            cl = cl,
            names = listOf(
            "org.flywaydb.core.internal.database.postgresql.PostgreSQLDatabaseType",
            "org.flywaydb.core.internal.database.postgresql.PostgreSQLConnection",
            "org.flywaydb.core.api.configuration.FluentConfiguration",
            "org.flywaydb.core.Flyway",
            "org.flywaydb.database.postgresql.PostgreSQLDatabaseType",
            "org.flywaydb.core.internal.configuration.extensions.DeployScriptFilenameConfigurationExtension"
            ),
            context = "Flyway",
            categories = COMMON_REFLECTION_CATEGORIES
        )
    }

    private fun registerAiDtoSafetyNet(hints: RuntimeHints, cl: ClassLoader) {
        registerReflectionTypesByName(
            hints = hints,
            cl = cl,
            names = listOf(
            "com.haneef._school.service.SchoolDataTools\$RecipientInfo",
            "com.haneef._school.controller.NaturalLanguageQueryController\$RecipientListResponse",
            "com.haneef._school.controller.NaturalLanguageQueryController\$QueryRequest"
            ),
            context = "AI DTO",
            categories = COMMON_REFLECTION_CATEGORIES
        )
    }

    private fun registerGoogleGenAiTypes(hints: RuntimeHints, cl: ClassLoader) {
        registerReflectionTypesByName(
            hints = hints,
            cl = cl,
            names = listOf(
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
            ),
            context = "Google GenAI",
            categories = COMMON_REFLECTION_CATEGORIES
        )
    }

    private fun registerReflectionType(
        hints: RuntimeHints,
        type: Class<*>,
        vararg categories: MemberCategory
    ) {
        hints.reflection().registerType(type, *categories)
    }

    private fun registerReflectionTypesByName(
        hints: RuntimeHints,
        cl: ClassLoader,
        names: List<String>,
        context: String,
        categories: Array<MemberCategory>
    ) {
        names.forEach { name ->
            registerReflectionTypeByName(hints, cl, name, context, categories)
        }
    }

    private fun registerReflectionTypeByName(
        hints: RuntimeHints,
        cl: ClassLoader,
        name: String,
        context: String,
        categories: Array<MemberCategory>
    ) {
        try {
            hints.reflection().registerType(Class.forName(name, false, cl), *categories)
        } catch (e: ClassNotFoundException) {
            logger.debug("Skipping {} hint for '{}': {}", context, name, e.message)
        } catch (e: LinkageError) {
            logger.debug("Skipping {} hint for '{}' due to linkage error: {}", context, name, e.message)
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
