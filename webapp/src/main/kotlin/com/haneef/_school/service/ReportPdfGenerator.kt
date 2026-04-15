package com.haneef._school.service

import com.haneef._school.entity.Assessment
import com.haneef._school.entity.School
import com.haneef._school.entity.Student
import com.haneef._school.entity.SubjectScore
import com.haneef._school.repository.SchoolRepository
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.events.Event
import com.itextpdf.kernel.events.IEventHandler
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ReportPdfGenerator(
    private val schoolRepository: SchoolRepository
) {
    
    private val font10 = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    private val font10Bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
    private val font12 = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    private val font12Bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
    private val font14Bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
    private val font16Bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)

    fun generateStudentReportPdf(
        student: Student,
        assessment: Assessment?,
        scores: List<SubjectScore>,
        schoolId: java.util.UUID,
        components: List<Pair<String, String>>,
        aliasMappings: Map<String, String>,
        extractScoreFn: (SubjectScore, String) -> Int?,
        calculateTotalFn: (SubjectScore) -> Int
    ): ByteArray {
        val school = schoolRepository.findById(schoolId).orElse(null) ?: throw IllegalArgumentException("School not found")
        val outputStream = ByteArrayOutputStream()
        val writer = PdfWriter(outputStream)
        val pdfDoc = PdfDocument(writer)
        pdfDoc.defaultPageSize = PageSize.A4
        
        // Add header/footer handler
        val pdfDoc2 = pdfDoc
        pdfDoc2.addEventHandler(PdfDocumentEvent.END_PAGE, HeaderFooterHandler(school))
        
        val document = Document(pdfDoc)
        document.setMargins(20f, 20f, 20f, 20f)

        // School Header
        addSchoolHeader(document, school)

        // Student Information Section
        addStudentInfoSection(document, student, assessment)

        // Scores Table
        addScoresTable(document, scores, components, extractScoreFn, calculateTotalFn)

        // Behavioral Assessment
        if (assessment != null) {
            addBehavioralAssessment(document, assessment)
        }

        // Comments Section
        if (assessment != null) {
            addCommentsSection(document, assessment)
        }

        document.close()
        return outputStream.toByteArray()
    }

    private fun addSchoolHeader(document: Document, school: School) {
        // School name and info
        val schoolNamePara = Paragraph(school.name ?: "School Name")
            .setFont(font16Bold)
            .setMarginBottom(5f)
        document.add(schoolNamePara)

        val address = "${school.city ?: ""}, ${school.state ?: ""}"
        val schoolInfoPara = Paragraph("$address | Phone: ${school.phone ?: ""}")
            .setFont(font10)
            .setMarginBottom(15f)
        document.add(schoolInfoPara)

        // Divider line
        val divider = Paragraph()
            .setBorder(SolidBorder(ColorConstants.DARK_GRAY, 1f))
            .setMarginBottom(15f)
        document.add(divider)

        // Report title
        val reportTitle = Paragraph("STUDENT ACADEMIC REPORT")
            .setFont(font14Bold)
            .setMarginBottom(15f)
        document.add(reportTitle)
    }

    private fun addStudentInfoSection(document: Document, student: Student, assessment: Assessment?) {
        val infoTable = Table(2)
        infoTable.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))

        // Left column - Photo placeholder
        val photoBorder = SolidBorder(ColorConstants.BLACK, 1f)
        val photoCell = Cell()
            .setBorder(photoBorder)
            .setHeight(100f)
        photoCell.add(Paragraph("PASSPORT\nPHOTO")
            .setFont(font10))
        infoTable.addCell(photoCell)

        // Right column - Student details
        val detailsCell = Cell()
            .setBorder(Border.NO_BORDER)
            .setPadding(10f)

        detailsCell.add(Paragraph("Student Information").setFont(font12Bold).setMarginBottom(8f))
        detailsCell.add(Paragraph("Name: ${student.user.fullName ?: "N/A"}").setFont(font10).setMarginBottom(3f))
        detailsCell.add(Paragraph("Admission No: ${student.admissionNumber ?: "N/A"}").setFont(font10).setMarginBottom(3f))
        detailsCell.add(Paragraph("Report Date: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}").setFont(font10).setMarginBottom(3f))
        if (assessment != null) {
            detailsCell.add(Paragraph("Attendance: ${assessment.attendance ?: 0}%").setFont(font10).setMarginBottom(3f))
        }

        infoTable.addCell(detailsCell)
        document.add(infoTable)
        document.add(Paragraph().setMarginBottom(15f)) // Spacing
    }

    private fun addScoresTable(
        document: Document,
        scores: List<SubjectScore>,
        components: List<Pair<String, String>>,
        extractScoreFn: (SubjectScore, String) -> Int?,
        calculateTotalFn: (SubjectScore) -> Int
    ) {
        document.add(Paragraph("Subject Performance").setFont(font12Bold).setMarginBottom(10f))

        // Build dynamic table with component columns
        val columnCount = 2 + components.size // Subject + components + Total
        val scoresTable = Table(columnCount)
        scoresTable.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))

        // Header row
        scoresTable.addCell(createHeaderCell("Subject"))
        components.forEach { (_, alias) ->
            scoresTable.addCell(createHeaderCell(alias))
        }
        scoresTable.addCell(createHeaderCell("Total"))

        // Data rows
        scores.forEach { score ->
            scoresTable.addCell(createDataCell(score.subject.subjectName))
            components.forEach { (_, alias) ->
                val componentScore = extractScoreFn(score, alias) ?: 0
                scoresTable.addCell(createDataCell(componentScore.toString()))
            }
            val total = calculateTotalFn(score)
            scoresTable.addCell(createDataCell(total.toString()))
        }

        document.add(scoresTable)
        document.add(Paragraph().setMarginBottom(15f))
    }

    private fun addBehavioralAssessment(document: Document, assessment: Assessment) {
        document.add(Paragraph("Behavioral Assessment").setFont(font12Bold).setMarginBottom(10f))

        val behaviorTable = Table(2)
        behaviorTable.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))

        val behaviors = listOf(
            "Attendance" to (assessment.attendance ?: 0),
            "Fluency in Communication" to (assessment.fluency ?: 0),
            "Neatness" to (assessment.neatness ?: 0),
            "Handwriting" to (assessment.handwriting ?: 0),
            "Initiative" to (assessment.initiative ?: 0),
            "Critical Thinking" to (assessment.criticalThinking ?: 0),
            "Punctuality" to (assessment.punctuality ?: 0),
            "Attentiveness" to (assessment.attentiveness ?: 0),
            "Self-Discipline" to (assessment.selfDiscipline ?: 0),
            "Politeness" to (assessment.politeness ?: 0),
            "Creativity/Games" to (assessment.game ?: 0)
        )

        behaviors.forEach { (label, value) ->
            behaviorTable.addCell(createDataCell(label))
            behaviorTable.addCell(createDataCell(value.toString()))
        }

        document.add(behaviorTable)
        document.add(Paragraph().setMarginBottom(15f))
    }

    private fun addCommentsSection(document: Document, assessment: Assessment) {
        document.add(Paragraph("Teacher Comments").setFont(font12Bold).setMarginBottom(5f))
        val classTeacherComment = assessment.classTeacherComment ?: "No comments"
        document.add(Paragraph(classTeacherComment).setFont(font10).setMarginBottom(15f))

        document.add(Paragraph("Principal Comments").setFont(font12Bold).setMarginBottom(5f))
        val headTeacherComment = assessment.headTeacherComment ?: "No comments"
        document.add(Paragraph(headTeacherComment).setFont(font10).setMarginBottom(15f))
    }

    private fun createHeaderCell(text: String): Cell {
        return Cell()
            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            .add(Paragraph(text).setFont(font10Bold))
            .setPadding(8f)
    }

    private fun createDataCell(text: String): Cell {
        return Cell()
            .add(Paragraph(text).setFont(font10))
            .setPadding(6f)
            .setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
    }

    /**
     * Header and footer handler for PDF pages
     */
    inner class HeaderFooterHandler(private val school: School) : IEventHandler {
        override fun handleEvent(event: Event) {
            val docEvent = event as PdfDocumentEvent
            val page = docEvent.page
            val pdfDoc = docEvent.document
            val pageSize = page.pageSize
            val pdfCanvas = com.itextpdf.kernel.pdf.canvas.PdfCanvas(page)

            // Footer
            val pageNum = pdfDoc.getNumberOfPages()
            val footerText = "Page $pageNum • Generated on ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}"
            pdfCanvas.beginText()
                .setFontAndSize(font10, 9f)
                .moveText((pageSize.width / 2 - 100).toDouble(), 20.0)
                .showText(footerText)
                .endText()
        }
    }
}
