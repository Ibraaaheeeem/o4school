package com.haneef._school.service

import java.util.UUID

import com.haneef._school.entity.ClassSubject
import com.haneef._school.entity.Department
import com.haneef._school.entity.EducationTrack
import com.haneef._school.entity.SchoolClass
import com.haneef._school.entity.Subject
import com.haneef._school.repository.ClassSubjectRepository
import com.haneef._school.repository.DepartmentRepository
import com.haneef._school.repository.EducationTrackRepository
import com.haneef._school.repository.SchoolClassRepository
import com.haneef._school.repository.SubjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SchoolStructureService(
    private val educationTrackRepository: EducationTrackRepository,
    private val departmentRepository: DepartmentRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val subjectRepository: SubjectRepository,
    private val classSubjectRepository: ClassSubjectRepository
) {

    @Transactional
    fun generateDefaultStructure(schoolId: UUID): Map<String, Any> {
        // Check if school already has active tracks
        val existingTracksCount = educationTrackRepository.countBySchoolIdAndIsActive(schoolId, true)
        if (existingTracksCount > 0L) {
            throw IllegalStateException("School already has education tracks configured")
        }

        // 1. Create or Reactivate Conventional Track
        var conventionalTrack = educationTrackRepository.findBySchoolIdAndName(schoolId, "Conventional")
        
        if (conventionalTrack != null) {
            conventionalTrack.isActive = true
            conventionalTrack.description = "Traditional academic education approach"
        } else {
            conventionalTrack = EducationTrack(
                name = "Conventional",
                description = "Traditional academic education approach"
            ).apply {
                this.schoolId = schoolId
                this.isActive = true
            }
        }
        conventionalTrack = educationTrackRepository.save(conventionalTrack)

        // 2. Create Departments
        val departmentsData = listOf(
            DepartmentData("Nursery", "Early childhood education (Ages 3-5)"),
            DepartmentData("Primary", "Elementary education (Ages 6-11)"),
            DepartmentData("Junior Secondary", "High school education (Ages 12-14)"),
            DepartmentData("Senior Secondary", "High school education (Ages 15-18)")
        )

        val departments = mutableListOf<Department>()
        
        for (deptData in departmentsData) {
            var department = departmentRepository.findBySchoolIdAndNameAndTrackId(
                schoolId, deptData.name, conventionalTrack.id!!
            )

            if (department != null) {
                department.isActive = true
                department.description = deptData.description
            } else {
                department = Department(
                    name = deptData.name,
                    track = conventionalTrack,
                    description = deptData.description
                ).apply {
                    this.schoolId = schoolId
                    this.isActive = true
                }
            }
            departments.add(departmentRepository.save(department))
        }

        // 3. Create Classes
        val classesData = mapOf(
            "Nursery" to listOf(
                ClassData("Kindergarten", 25),
                ClassData("Pre-Nursery", 25),
                ClassData("Nursery 1", 20),
                ClassData("Nursery 2", 20)
            ),
            "Primary" to listOf(
                ClassData("Primary 1", 30),
                ClassData("Primary 2", 30),
                ClassData("Primary 3", 30),
                ClassData("Primary 4", 30),
                ClassData("Primary 5", 30),
                ClassData("Primary 6", 30)
            ),
            "Junior Secondary" to listOf(
                ClassData("JSS 1", 35),
                ClassData("JSS 2", 35),
                ClassData("JSS 3", 35)
            ),
            "Senior Secondary" to listOf(
                ClassData("SSS 1", 35),
                ClassData("SSS 2", 35),
                ClassData("SSS 3", 35)
            )
        )

        val createdClasses = mutableListOf<SchoolClass>()

        for (department in departments) {
            val deptClasses = classesData[department.name] ?: continue
            
            for (classData in deptClasses) {
                var schoolClass = schoolClassRepository.findBySchoolIdAndClassNameAndDepartmentId(
                    schoolId, classData.name, department.id!!
                )

                if (schoolClass != null) {
                    schoolClass.isActive = true
                    schoolClass.classCode = classData.name.replace(" ", "").uppercase()
                    schoolClass.gradeLevel = classData.name
                    schoolClass.track = conventionalTrack
                    schoolClass.track = conventionalTrack
                    schoolClass.maxCapacity = classData.capacity
                    schoolClass.currentEnrollment = 0
                } else {
                    schoolClass = SchoolClass(
                        className = classData.name
                    ).apply {
                        this.schoolId = schoolId
                        this.isActive = true
                        this.classCode = classData.name.replace(" ", "").uppercase()
                        this.gradeLevel = classData.name
                        this.department = department
                        this.track = conventionalTrack
                        this.maxCapacity = classData.capacity
                        this.currentEnrollment = 0
                    }
                }
                createdClasses.add(schoolClassRepository.save(schoolClass))
            }
        }

        // 4. Generate default subjects and assign them to classes
        val subjectsResult = generateDefaultSubjectsForStructure(schoolId)
        
        return mapOf(
            "track" to conventionalTrack,
            "departmentsCount" to departments.size,
            "classesCount" to createdClasses.size,
            "subjectsCount" to (subjectsResult["subjectsCount"] ?: 0),
            "assignmentsCount" to (subjectsResult["assignmentsCount"] ?: 0),
            "message" to "Default school structure created successfully with ${subjectsResult["subjectsCount"] ?: 0} subjects and ${subjectsResult["assignmentsCount"] ?: 0} class assignments"
        )
    }

    @Transactional
    fun generateDefaultSubjects(schoolId: UUID): Map<String, Any> {
        // 1. Check if departments exist, if not, generate default structure first
        val existingDepartmentsCount = departmentRepository.countBySchoolIdAndIsActive(schoolId, true)
        if (existingDepartmentsCount == 0L) {
            println("No departments found for school $schoolId. Generating default structure first.")
            generateDefaultStructure(schoolId)
        }

        val createdSubjects = mutableListOf<Subject>()
        var assignedCount = 0

        // Define subject mappings for each department (education level)
        val subjectMappings = mapOf(
            "Nursery" to nurserySubjects,
            "Primary" to primarySubjects,
            "Junior Secondary" to jssSubjects,
            "Senior Secondary" to sssSubjects
        )

        for ((deptName, subjectsList) in subjectMappings) {
            // Find the academic level department (e.g., Nursery, Primary)
            val academicDepartments = departmentRepository.findBySchoolIdAndIsActive(schoolId, true)
                .filter { it.name == deptName }
            
            if (academicDepartments.isEmpty()) {
                println("Academic Department '$deptName' not found for school $schoolId. Skipping subjects generation for this level.")
                continue
            }
            
            val academicDepartment = academicDepartments.first()
            
            // Get all classes in this academic department
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)
                .filter { it.department?.id == academicDepartment.id }

            for (subjectData in subjectsList) {
                // Find or create subject
                var subject = subjectRepository.findBySubjectNameAndSchoolIdAndIsActive(subjectData.name, schoolId, true)
                
                if (subject == null) {
                    subject = Subject(
                        subjectName = subjectData.name,
                        subjectCode = subjectData.code,
                        isCoreSubject = subjectData.core,
                        description = "Category: ${subjectData.dept}" // Store the category (Math, Science) in description
                    ).apply {
                        this.schoolId = schoolId
                        this.isActive = true
                    }
                    subject = subjectRepository.save(subject)
                    createdSubjects.add(subject)
                } else {
                    // Update existing if needed
                    var updated = false
                    if (!subject.isActive) {
                        subject.isActive = true
                        updated = true
                    }
                    // Ensure description contains category
                    if (subject.description != "Category: ${subjectData.dept}") {
                        subject.description = "Category: ${subjectData.dept}"
                        updated = true
                    }
                    
                    if (updated) {
                        subjectRepository.save(subject)
                    }
                }

                // Assign to all classes in the academic department
                for (schoolClass in classes) {
                    val existingAssignment = classSubjectRepository.findBySchoolClassIdAndSubjectIdAndIsActive(
                        schoolClass.id!!, subject.id!!, true
                    )

                    if (existingAssignment == null) {
                        val assignment = ClassSubject(
                            schoolClass = schoolClass,
                            subject = subject
                        ).apply {
                            this.schoolId = schoolId
                            this.isActive = true
                        }
                        classSubjectRepository.save(assignment)
                        assignedCount++
                    }
                }
            }
        }

        val message = if (createdSubjects.isEmpty() && assignedCount == 0) {
            "No new subjects or assignments were created. Structure might already exist."
        } else {
            "Generated ${createdSubjects.size} new subjects and $assignedCount class assignments."
        }

        return mapOf(
            "subjectsCount" to createdSubjects.size,
            "assignmentsCount" to assignedCount,
            "message" to message
        )
    }

    @Transactional
    private fun generateDefaultSubjectsForStructure(schoolId: UUID): Map<String, Any> {
        val createdSubjects = mutableListOf<Subject>()
        var assignedCount = 0

        // Define subject mappings for each department (education level)
        val subjectMappings = mapOf(
            "Nursery" to nurserySubjects,
            "Primary" to primarySubjects,
            "Junior Secondary" to jssSubjects,
            "Senior Secondary" to sssSubjects
        )

        for ((deptName, subjectsList) in subjectMappings) {
            // Find the academic level department (e.g., Nursery, Primary)
            val academicDepartments = departmentRepository.findBySchoolIdAndIsActive(schoolId, true)
                .filter { it.name == deptName }
            
            if (academicDepartments.isEmpty()) {
                continue
            }
            
            val academicDepartment = academicDepartments.first()
            
            // Get all classes in this academic department
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)
                .filter { it.department?.id == academicDepartment.id }

            for (subjectData in subjectsList) {
                // Find or create subject
                var subject = subjectRepository.findBySubjectNameAndSchoolIdAndIsActive(subjectData.name, schoolId, true)
                
                if (subject == null) {
                    subject = Subject(
                        subjectName = subjectData.name,
                        subjectCode = subjectData.code,
                        isCoreSubject = subjectData.core,
                        description = "Category: ${subjectData.dept}"
                    ).apply {
                        this.schoolId = schoolId
                        this.isActive = true
                    }
                    subject = subjectRepository.save(subject)
                    createdSubjects.add(subject)
                }

                // Assign to all classes in the academic department
                for (schoolClass in classes) {
                    val existingAssignment = classSubjectRepository.findBySchoolClassIdAndSubjectIdAndIsActive(
                        schoolClass.id!!, subject.id!!, true
                    )

                    if (existingAssignment == null) {
                        val assignment = ClassSubject(
                            schoolClass = schoolClass,
                            subject = subject
                        ).apply {
                            this.schoolId = schoolId
                            this.isActive = true
                        }
                        classSubjectRepository.save(assignment)
                        assignedCount++
                    }
                }
            }
        }

        return mapOf(
            "subjectsCount" to createdSubjects.size,
            "assignmentsCount" to assignedCount
        )
    }

    private data class SubjectData(
        val name: String,
        val code: String,
        val dept: String,
        val core: Boolean
    )

    private data class DepartmentData(val name: String, val description: String)
    private data class ClassData(val name: String, val capacity: Int)

    private val nurserySubjects = listOf(
        SubjectData("Numeracy", "NUM", "Mathematics", true),
        SubjectData("Literacy", "LIT", "Languages", true),
        SubjectData("Science and Health Education", "SHE", "Sciences", true),
        SubjectData("Cultural and Creative Arts", "CCA", "Arts", true),
        SubjectData("Moral Instruction", "MOR", "General Studies", true)
    )

    private val primarySubjects = listOf(
        SubjectData("Arabic", "ARA", "Languages", false),
        SubjectData("Basic Science", "BSC", "Sciences", true),
        SubjectData("Basic Technology", "BTE", "Vocational", true),
        SubjectData("Christian Religious Studies", "CRS", "General Studies", false),
        SubjectData("Civic Education (Basic)", "CVE", "Social Studies", true),
        SubjectData("Cultural and Creative Arts", "CCA", "Arts", true),
        SubjectData("English Language", "ENG", "Languages", true),
        SubjectData("General Mathematics", "MTH", "Mathematics", true),
        SubjectData("Hausa Language", "HAU", "Languages", false),
        SubjectData("Igbo Language", "IGB", "Languages", false),
        SubjectData("Information Technology (IT)", "ICT", "Vocational", true),
        SubjectData("Islamic Studies", "ISL", "General Studies", false),
        SubjectData("Physical & Health Education", "PHE", "General Studies", true),
        SubjectData("Security Education", "SEC", "Social Studies", true),
        SubjectData("Social Studies", "SST", "Social Studies", true),
        SubjectData("Yoruba Language", "YOR", "Languages", false)
    )

    private val jssSubjects = listOf(
        SubjectData("Agriculture", "AGR", "Vocational", true),
        SubjectData("Arabic", "ARA", "Languages", false),
        SubjectData("Basic Science", "BSC", "Sciences", true),
        SubjectData("Basic Technology", "BTE", "Vocational", true),
        SubjectData("Business Studies", "BUS", "Commercial", true),
        SubjectData("Christian Religious Studies", "CRS", "General Studies", false),
        SubjectData("Civic Education (Basic)", "CVE", "Social Studies", true),
        SubjectData("Cultural And Creative Arts", "CCA", "Arts", true),
        SubjectData("English Language", "ENG", "Languages", true),
        SubjectData("Entrepreneurship", "ENT", "Commercial", true),
        SubjectData("French Language", "FRE", "Languages", false),
        SubjectData("General Mathematics", "MTH", "Mathematics", true),
        SubjectData("Hausa Language", "HAU", "Languages", false),
        SubjectData("Home Economics", "HEC", "Vocational", true),
        SubjectData("Igbo Language", "IGB", "Languages", false),
        SubjectData("Information Technology (IT)", "ICT", "Vocational", true),
        SubjectData("Islamic Studies", "ISL", "General Studies", false),
        SubjectData("Physical & Health Education", "PHE", "General Studies", true),
        SubjectData("Security Education", "SEC", "Social Studies", true),
        SubjectData("Social Studies", "SST", "Social Studies", true),
        SubjectData("Yoruba Language", "YOR", "Languages", false),
        SubjectData("Machine Woodworking", "MWW", "Vocational", false),
        SubjectData("Marketing", "MKT", "Commercial", false),
        SubjectData("Metal Work", "MTW", "Vocational", false),
        SubjectData("Mining", "MIN", "Vocational", false),
        SubjectData("Music", "MUS", "Arts", false),
        SubjectData("Office Practice", "OFP", "Commercial", false),
        SubjectData("Painting And Decoration", "PAD", "Vocational", false),
        SubjectData("Photography", "PHO", "Arts", false),
        SubjectData("Physical Education", "PED", "General Studies", false),
        SubjectData("Physics", "PHY", "Sciences", false),
        SubjectData("Plumbing And Pipe Fitting", "PPF", "Vocational", false),
        SubjectData("Printing Craft Practice", "PCP", "Vocational", false),
        SubjectData("Radio Television And Electrical Work", "RTE", "Vocational", false),
        SubjectData("Radio Television And Repairs", "RTR", "Vocational", false),
        SubjectData("Salesmanship", "SAL", "Commercial", false),
        SubjectData("Store Keeping", "STK", "Commercial", false),
        SubjectData("Store Management", "STM", "Commercial", false),
        SubjectData("Technical Drawings", "TDR", "Vocational", false),
        SubjectData("Textile trade", "TXT", "Vocational", false),
        SubjectData("Tie And Dye Craft", "TDC", "Vocational", false),
        SubjectData("Tourism", "TOU", "Commercial", false),
        SubjectData("Upholstery", "UPH", "Vocational", false),
        SubjectData("Visual Art", "VAR", "Arts", false),
        SubjectData("Welding & Fabrication", "WEF", "Vocational", false),
        SubjectData("Wood-Work", "WWK", "Vocational", false)
    )

    private val sssSubjects = listOf(
        // Core subjects
        SubjectData("English Language", "ENG", "Languages", true),
        SubjectData("General Mathematics", "MTH", "Mathematics", true),

        // Science subjects
        SubjectData("Agricultural Science", "AGR", "Sciences", false),
        SubjectData("Biology", "BIO", "Sciences", false),
        SubjectData("Chemistry", "CHE", "Sciences", false),
        SubjectData("Physics", "PHY", "Sciences", false),
        SubjectData("Further Mathematics", "FMT", "Mathematics", false),

        // Arts subjects
        SubjectData("Literature-in-English", "LIT", "Languages", false),
        SubjectData("Government", "GOV", "Arts", false),
        SubjectData("Economics", "ECO", "Commercial", false),
        SubjectData("Geography", "GEO", "Arts", false),
        SubjectData("History", "HIS", "Arts", false),
        SubjectData("Christian Religious Studies", "CRS", "General Studies", false),
        SubjectData("Islamic Studies", "ISL", "General Studies", false),
        SubjectData("Civic Education (Senior)", "CVE", "Social Studies", false),

        // Commercial subjects
        SubjectData("Commerce", "COM", "Commercial", false),
        SubjectData("Financial Accounting", "FAC", "Commercial", false),
        SubjectData("Book Keeping", "BKP", "Commercial", false),
        SubjectData("Insurance", "INS", "Commercial", false),
        SubjectData("Marketing", "MKT", "Commercial", false),
        SubjectData("Office Practice", "OFP", "Commercial", false),
        SubjectData("Salesmanship", "SAL", "Commercial", false),
        SubjectData("Store Keeping", "STK", "Commercial", false),
        SubjectData("Store Management", "STM", "Commercial", false),

        // Technical/Vocational subjects
        SubjectData("Computer & IT", "ICT", "Vocational", false),
        SubjectData("Technical Drawings", "TDR", "Vocational", false),
        SubjectData("Basic Electricity", "BEL", "Vocational", false),
        SubjectData("Basic Electronics", "BEE", "Vocational", false),
        SubjectData("Auto Mechanics", "AME", "Vocational", false),
        SubjectData("Auto Mechanical Works", "AMW", "Vocational", false),
        SubjectData("Auto Electrical Works", "AEW", "Vocational", false),
        SubjectData("Auto Body Repair and Spray Painting", "ABR", "Vocational", false),
        SubjectData("Building Construction", "BCO", "Vocational", false),
        SubjectData("Carpentry and Joinery", "CAJ", "Vocational", false),
        SubjectData("Electrical Installation and Maintenance Work", "EIM", "Vocational", false),
        SubjectData("Machine Woodworking", "MWW", "Vocational", false),
        SubjectData("Metal Work", "MTW", "Vocational", false),
        SubjectData("Welding & Fabrication", "WEF", "Vocational", false),
        SubjectData("Wood-Work", "WWK", "Vocational", false),
        SubjectData("Block Laying, Brick Laying & Concrete Works", "BBC", "Vocational", false),
        SubjectData("Plumbing And Pipe Fitting", "PPF", "Vocational", false),
        SubjectData("Air Conditioning and Refrigeration", "ACR", "Vocational", false),
        SubjectData("Radio Television And Electrical Work", "RTE", "Vocational", false),
        SubjectData("Radio Television And Repairs", "RTR", "Vocational", false),
        SubjectData("GSM Maintenance and Repairs", "GSM", "Vocational", false),
        SubjectData("Printing Craft Practice", "PCP", "Vocational", false),

        // Home Economics/Life Skills
        SubjectData("Foods & Nutrition", "FNU", "Vocational", false),
        SubjectData("Clothing & Textiles", "CLT", "Vocational", false),
        SubjectData("Home Management", "HMG", "Vocational", false),
        SubjectData("Catering and Craft Practice", "CCP", "Vocational", false),
        SubjectData("Garment Making", "GMK", "Vocational", false),
        SubjectData("Textile trade", "TXT", "Vocational", false),
        SubjectData("Tie And Dye Craft", "TDC", "Vocational", false),
        SubjectData("Dyeing and Bleaching", "DAB", "Vocational", false),
        SubjectData("Cosmetology", "COS", "Vocational", false),

        // Creative Arts
        SubjectData("Visual Art", "VAR", "Arts", false),
        SubjectData("Music", "MUS", "Arts", false),
        SubjectData("Photography", "PHO", "Arts", false),
        SubjectData("Painting And Decoration", "PAD", "Arts", false),

        // Languages
        SubjectData("French Language", "FRE", "Languages", false),
        SubjectData("Arabic", "ARA", "Languages", false),
        SubjectData("Igbo Language", "IGB", "Languages", false),
        SubjectData("Yoruba Language", "YOR", "Languages", false),

        // Other Vocational
        SubjectData("Animal Husbandry", "ANH", "Sciences", false),
        SubjectData("Fisheries", "FSH", "Sciences", false),
        SubjectData("Mining", "MIN", "Vocational", false),
        SubjectData("Tourism", "TOU", "Commercial", false),
        SubjectData("Data Processing", "DAP", "Vocational", false),
        SubjectData("Keyboarding", "KEY", "Vocational", false),
        SubjectData("Furniture Making", "FMK", "Vocational", false),
        SubjectData("Upholstery", "UPH", "Vocational", false),
        SubjectData("Leather Goods", "LGD", "Vocational", false),
        SubjectData("Automobile Parts Merchandising", "APM", "Commercial", false),
        SubjectData("Autopart Merchandizing", "APZ", "Commercial", false),
        SubjectData("Physical Education", "PED", "General Studies", false),
        SubjectData("Health Education", "HED", "General Studies", false)
    )
}
