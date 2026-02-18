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
import com.haneef._school.repository.GlobalSubjectRepository
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
    private val classSubjectRepository: ClassSubjectRepository,
    private val globalSubjectRepository: GlobalSubjectRepository
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
                ClassData("Nursery 1", 20),
                ClassData("Nursery 2", 20),
                ClassData("Nursery 3", 20)
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
                    schoolClass.gradeLevel = SchoolClass.GradeLevel.fromClassName(classData.name)
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
                        this.gradeLevel = SchoolClass.GradeLevel.fromClassName(classData.name)
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
        val createdSubjects = mutableListOf<Subject>()
        var assignedCount = 0

        // 1. Ensure we have classes to assign to
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)
        if (classes.isEmpty()) {
            return mapOf(
                "subjectsCount" to 0,
                "assignmentsCount" to 0,
                "message" to "No active classes found. Please generate or create classes first."
            )
        }

        // 2. Get all core subjects from the global catalog
        val coreGlobalSubjects = globalSubjectRepository.findByIsCoreTrueAndIsActiveTrue()
        
        for (globalSub in coreGlobalSubjects) {
            // Find or create local subject
            var localSubject = subjectRepository.findBySubjectNameIgnoreCaseAndIsActive(globalSub.name, true)
            
            if (localSubject == null) {
                localSubject = Subject(
                    subjectName = globalSub.name,
                    subjectCode = globalSub.code,
                    isCoreSubject = true,
                    description = "Category: ${globalSub.category}"
                ).apply {
                    this.isActive = true
                }
                localSubject = subjectRepository.save(localSubject)
                createdSubjects.add(localSubject)
            } else {
                // Ensure properties are synced locally too
                var updated = false
                if (localSubject.isCoreSubject != true) {
                    localSubject.isCoreSubject = true
                    updated = true
                }
                if (localSubject.subjectCode.isNullOrBlank() && !globalSub.code.isNullOrBlank()) {
                    localSubject.subjectCode = globalSub.code
                    updated = true
                }
                if (localSubject.description.isNullOrBlank()) {
                    localSubject.description = "Category: ${globalSub.category}"
                    updated = true
                }
                if (updated) {
                    subjectRepository.save(localSubject)
                }
            }

            // Assign to relevant classes based on grade level range
            for (schoolClass in classes) {
                val grade = schoolClass.gradeLevel ?: continue
                
                if (grade >= globalSub.minGradeLevel && grade <= globalSub.maxGradeLevel) {
                    val existingAssignment = classSubjectRepository.findBySchoolClassIdAndSubjectIdAndIsActive(
                        schoolClass.id!!, localSubject.id!!, true
                    )

                    if (existingAssignment == null) {
                        val assignment = ClassSubject(
                            schoolClass = schoolClass,
                            subject = localSubject
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

        // Get all core subjects from the global catalog
        val coreGlobalSubjects = globalSubjectRepository.findByIsCoreTrueAndIsActiveTrue()
        
        // Get all classes for this school
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)

        for (globalSub in coreGlobalSubjects) {
            // Find or create local subject
            var localSubject = subjectRepository.findBySubjectNameIgnoreCaseAndIsActive(globalSub.name, true)
            
            if (localSubject == null) {
                localSubject = Subject(
                    subjectName = globalSub.name,
                    subjectCode = globalSub.code,
                    isCoreSubject = true,
                    description = "Category: ${globalSub.category}"
                ).apply {
                    this.isActive = true
                }
                localSubject = subjectRepository.save(localSubject)
                createdSubjects.add(localSubject)
            } else {
                // Sync properties for existing subjects
                var updated = false
                if (localSubject.isCoreSubject != true) {
                    localSubject.isCoreSubject = true
                    updated = true
                }
                if (localSubject.subjectCode.isNullOrBlank() && !globalSub.code.isNullOrBlank()) {
                    localSubject.subjectCode = globalSub.code
                    updated = true
                }
                if (localSubject.description.isNullOrBlank()) {
                    localSubject.description = "Category: ${globalSub.category}"
                    updated = true
                }
                if (updated) {
                    subjectRepository.save(localSubject)
                }
            }

            // Assign to relevant classes based on grade level range
            for (schoolClass in classes) {
                val grade = schoolClass.gradeLevel ?: continue
                
                // Check if grade is within global subject's range
                if (grade >= globalSub.minGradeLevel && grade <= globalSub.maxGradeLevel) {
                    val existingAssignment = classSubjectRepository.findBySchoolClassIdAndSubjectIdAndIsActive(
                        schoolClass.id!!, localSubject.id!!, true
                    )

                    if (existingAssignment == null) {
                        val assignment = ClassSubject(
                            schoolClass = schoolClass,
                            subject = localSubject
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

    private data class DepartmentData(val name: String, val description: String)
    private data class ClassData(val name: String, val capacity: Int)
}
