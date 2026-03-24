package com.haneef._school.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.haneef._school.dto.AiQuestionRequest
import com.haneef._school.dto.AiQuestionResponse
import com.haneef._school.dto.GeneratedQuestionDto
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
class AiService(
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AiService::class.java)
        private const val PROVIDER_GEMINI = "gemini"
        private const val PROVIDER_DEEPSEEK = "deepseek"
        private const val REQUEST_TIMEOUT_SECONDS = 60L
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    @Value("\${ai.provider:gemini}")
    private lateinit var provider: String

    @Value("\${google.gemini.api.key:}")
    private lateinit var geminiApiKey: String

    @Value("\${deepseek.api.key:}")
    private lateinit var deepseekApiKey: String

    @Value("\${deepseek.model:deepseek-reasoner}")
    private lateinit var deepseekModel: String

    private val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
    private val deepseekUrl = "https://api.deepseek.com/chat/completions"

    @PostConstruct
    fun validateConfiguration() {
        if (provider.isBlank()) {
            logger.warn("AI provider is blank; defaulting to {}", PROVIDER_GEMINI)
        }
    }

    fun generateQuestions(request: AiQuestionRequest): List<GeneratedQuestionDto> {
        return when (normalizedProvider()) {
            PROVIDER_DEEPSEEK -> generateWithDeepSeek(request)
            PROVIDER_GEMINI -> generateWithGemini(request)
            else -> {
                logger.warn("Unsupported AI provider '{}'; defaulting to {}", provider, PROVIDER_GEMINI)
                generateWithGemini(request)
            }
        }
    }

    private fun generateWithGemini(request: AiQuestionRequest): List<GeneratedQuestionDto> {
        if (geminiApiKey.isBlank()) {
            logger.error("Gemini API key is not configured")
            throw IllegalStateException("Gemini AI is currently unavailable (API key missing)")
        }

        val prompt = buildPrompt(request)
        val requestBody = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to prompt)
                    )
                )
            )
        )

        val jsonRequest = objectMapper.writeValueAsString(requestBody)
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("${geminiUrl}?key=${geminiApiKey}"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
            .build()

        try {
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error("Gemini API returned status {}", response.statusCode())
                throw IllegalStateException("Failed to generate questions from Gemini")
            }

            val responseJson = objectMapper.readTree(response.body())
            val generatedText = responseJson
                .path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText()

            require(generatedText.isNotBlank()) { "Gemini returned an empty response" }

            return parseAiResponse(generatedText)
        } catch (e: Exception) {
            logger.error("Error calling Gemini API", e)
            throw IllegalStateException("Error generating questions with Gemini", e)
        }
    }

    private fun generateWithDeepSeek(request: AiQuestionRequest): List<GeneratedQuestionDto> {
        if (deepseekApiKey.isBlank()) {
            logger.error("DeepSeek API key is not configured")
            throw IllegalStateException("DeepSeek AI is currently unavailable (API key missing)")
        }

        val prompt = buildPrompt(request)
        val requestBody = mapOf(
            "model" to deepseekModel,
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You are an expert academic examiner. You output raw JSON only, no markdown formatting."),
                mapOf("role" to "user", "content" to prompt)
            ),
            "stream" to false,
            "response_format" to mapOf("type" to "json_object")
        )

        val jsonRequest = objectMapper.writeValueAsString(requestBody)
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(deepseekUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $deepseekApiKey")
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
            .build()

        try {
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error("DeepSeek API returned status {}", response.statusCode())
                throw IllegalStateException("Failed to generate questions from DeepSeek")
            }

            val responseJson = objectMapper.readTree(response.body())
            val generatedText = responseJson
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText()

            require(generatedText.isNotBlank()) { "DeepSeek returned an empty response" }

            return parseAiResponse(generatedText)
        } catch (e: Exception) {
            logger.error("Error calling DeepSeek API", e)
            throw IllegalStateException("Error generating questions with DeepSeek", e)
        }
    }

    private fun buildPrompt(request: AiQuestionRequest): String {
        val topicsDescription = request.topics.joinToString("; ") { "${it.questionCount} questions for '${it.topic}'" }
        return """
            Generate multiple-choice questions based on the following context:
            
            School Class Reference: ${request.className ?: "General"}
            Grade Level: ${request.gradeLevel ?: "General"}
            Subject: ${request.subjectName ?: "General"}
            
            Areas of Testing and Question Counts:
            ${topicsDescription}
            
            Total ${request.topics.sumOf { it.questionCount }} questions required.
            
            Requirements:
            1. Each question should have ${request.optionsCount} options (labeled A, B, C, D, E as applicable).
            2. Provide a correct answer (A, B, C, D, or E) and a detailed explanation for each question.
            3. Ensure the questions match the difficulty level of ${request.className ?: "the specified grade"}.
            4. **Format the question text, options, and explanation using rich HTML**. Use `<p>` tags for paragraphs, `<b>` for emphasis, and `<ul>`/`<li>` or `<br>` to ensure step-by-step calculations are on separate lines for clear readability. 
            5. For mathematical notation, use standard HTML entities (like `&frac12;`) or clear text representation (like `x^2`).
            6. Format the output strictly as a JSON object with a 'questions' array.
            
            Each object in the array should have:
            - 'instruction': optional instruction for the question (HTML string)
            - 'questionText': the question content (HTML string)
            - 'optionA', 'optionB', 'optionC', 'optionD', 'optionE': the options (HTML strings, use null for unused options)
            - 'correctAnswer': the letter (A, B, C, D, or E)
            - 'explanation': a brief, step-by-step explanation of the correct answer using HTML lists or line breaks (HTML string)
            
            Format:
            {
              "questions": [
                {
                  "instruction": "...",
                  "questionText": "...",
                  "optionA": "...",
                  "optionB": "...",
                  "optionC": "...",
                  "optionD": "...",
                  "optionE": "...",
                  "correctAnswer": "...",
                  "explanation": "..."
                }
              ]
            }
            Do not include markdown blocks or any other text. Return ONLY raw JSON.
        """.trimIndent()
    }

    private fun parseAiResponse(generatedText: String): List<GeneratedQuestionDto> {
        val sanitizedJson = generatedText.trim().removePrefix("```json").removeSuffix("```").trim()
        val aiResponse = objectMapper.readValue(sanitizedJson, AiQuestionResponse::class.java)
        return aiResponse.questions
    }

    private fun normalizedProvider(): String = provider.trim().lowercase().ifBlank { PROVIDER_GEMINI }
}
