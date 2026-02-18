package com.haneef._school

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@org.springframework.context.annotation.ImportRuntimeHints(com.haneef._school.config.GlobalRuntimeHints::class)
class Application

fun main(args: Array<String>) {
	runApplication<Application>(*args)
}
