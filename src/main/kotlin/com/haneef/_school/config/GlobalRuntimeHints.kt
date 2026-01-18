package com.haneef._school.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.core.type.filter.AssignableTypeFilter
import jakarta.persistence.Entity

import java.util.*
import org.hibernate.collection.spi.PersistentBag
import org.hibernate.collection.spi.PersistentSet
import org.hibernate.collection.spi.PersistentSortedSet
import org.hibernate.collection.spi.PersistentList

class GlobalRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        
        // 1. Register ALL your Entities and DTOs automatically
        // useDefaultFilters = false to disable default @Component scanning
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        
        // Include Entities
        scanner.addIncludeFilter(AnnotationTypeFilter(Entity::class.java))
        
        // Include everything in the package (DTOs, etc)
        // Any::class.java corresponds to java.lang.Object, so this matches all classes
        scanner.addIncludeFilter(AssignableTypeFilter(Any::class.java)) 
        
        // This scans everything under com.haneef._school
        val candidates = scanner.findCandidateComponents("com.haneef._school")
        
        candidates.forEach { beanDefinition ->
            try {
                val clazz = Class.forName(beanDefinition.beanClassName)
                hints.reflection().registerType(clazz, 
                    MemberCategory.INVOKE_PUBLIC_METHODS, 
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
            } catch (e: Exception) {
                // Ignore classes that can't be loaded
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
        // Using fully qualified names where necessary to avoid ambiguity
        // Register Thymeleaf Utilities
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Lists"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Strings"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Numbers"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Dates"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Arrays"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.engine.IterationStatusVar"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Booleans"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Objects"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Aggregates"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Messages"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Ids"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        try { hints.reflection().registerType(Class.forName("org.thymeleaf.expression.Temporals"), MemberCategory.INVOKE_PUBLIC_METHODS) } catch(e: Exception) {}
        
        // 4. Fix for Kotlin Collections
        listOf("kotlin.collections.EmptyList", "kotlin.collections.EmptyMap").forEach {
            try {
                hints.reflection().registerType(Class.forName(it), MemberCategory.INVOKE_PUBLIC_METHODS)
            } catch (e: Exception) {}
        }
        
        // 5. Register Spring Data Page/Slice/PageImpl
        listOf(
            "org.springframework.data.domain.PageImpl",
            "org.springframework.data.domain.Page",
            "org.springframework.data.domain.Slice",
            "org.springframework.data.domain.Chunk"
        ).forEach {
             try {
                hints.reflection().registerType(Class.forName(it), MemberCategory.INVOKE_PUBLIC_METHODS)
            } catch (e: Exception) {}
        }
        
        // 6. Register java.util.Collections$UnmodifiableRandomAccessList
        try {
             hints.reflection().registerType(Class.forName("java.util.Collections\$UnmodifiableRandomAccessList"),
                MemberCategory.INVOKE_PUBLIC_METHODS)
        } catch (e: ClassNotFoundException) {
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
        val extraClasses = listOf(
            "org.hibernate.proxy.HibernateProxy",
            "org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor" 
        )

        extraClasses.forEach { className ->
            try {
                hints.reflection().registerType(Class.forName(className), MemberCategory.INVOKE_PUBLIC_METHODS)
            } catch (e: Exception) {
                // Class not on classpath, skip it
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
    }
}
