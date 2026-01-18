package com.haneef._school.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "questions",
    indexes = [
        Index(columnList = "examination_id", name = "idx_question_exam")
    ]
)
class Question(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    var examination: Examination,
    
    @Column(columnDefinition = "TEXT")
    var instruction: String? = null,
    
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    var questionText: String,
    
    @Column(name = "question_image_url")
    var questionImageUrl: String? = null,
    
    @Column(name = "option_a", nullable = false, columnDefinition = "TEXT")
    var optionA: String,
    
    @Column(name = "option_b", nullable = false, columnDefinition = "TEXT")
    var optionB: String,
    
    @Column(name = "option_c", columnDefinition = "TEXT")
    var optionC: String? = null,
    
    @Column(name = "option_d", columnDefinition = "TEXT")
    var optionD: String? = null,
    
    @Column(name = "option_e", columnDefinition = "TEXT")
    var optionE: String? = null,
    
    @Column(name = "correct_answer", nullable = false)
    var correctAnswer: String, // A, B, C, D, E
    
    var marks: Double? = 1.0
) : TenantAwareEntity() {
    
    constructor() : this(
        examination = Examination(),
        questionText = "",
        optionA = "",
        optionB = "",
        correctAnswer = ""
    )
}