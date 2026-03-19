package com.haneef._school.config

/**
 * Marker annotation for classes that should be registered for reflection and
 * introspection in GraalVM Native Image builds.
 *
 * Apply this annotation to DTO classes that are serialized/deserialized by Jackson,
 * used with Spring AI entity mapping, or otherwise require reflective access at runtime.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class NativeDto
