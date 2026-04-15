// Shared Assessment Reports Module
// Used by both Staff Reports (class-reports.html) and Admin Reports (reports.html)

// Global state variables
let currentStudentData = null;
let isEditMode = false;

/**
 * Render the assessment table with dynamic columns based on student's score scheme
 * Supports different grading schemes (CA1+CA2+Exam, or custom components)
 */
function renderAssessmentTable() {
    const tbody = document.getElementById('assessmentTableBody');
    if (!tbody || !currentStudentData || !currentStudentData.subjects) {
        return;
    }

    tbody.innerHTML = '';
    currentStudentData.subjects.forEach(subject => {
        const tr = document.createElement('tr');
        const disabledAttr = !isEditMode ? 'disabled' : '';
        
        let rowHtml = `<td class="subject-name-cell">${subject.name}</td>`;
        
        // CA I
        const ca1Value = subject.ca1 || '';
        rowHtml += `
            <td class="score-input-cell">
                <div style="display: flex; gap: 0.4rem; align-items: center;">
                    <input type="number" class="score-input" data-component="ca1" data-subject="${subject.id}" 
                           data-max="20" value="${ca1Value}" min="0" max="20" 
                           ${disabledAttr} oninput="validateScoreInput(this)" 
                           onchange="updateRowTotal(this)" placeholder="0">
                    <span class="score-max">/ 20</span>
                </div>
            </td>
        `;
        
        // CA II
        const ca2Value = subject.ca2 || '';
        rowHtml += `
            <td class="score-input-cell">
                <div style="display: flex; gap: 0.4rem; align-items: center;">
                    <input type="number" class="score-input" data-component="ca2" data-subject="${subject.id}" 
                           data-max="20" value="${ca2Value}" min="0" max="20" 
                           ${disabledAttr} oninput="validateScoreInput(this)" 
                           onchange="updateRowTotal(this)" placeholder="0">
                    <span class="score-max">/ 20</span>
                </div>
            </td>
        `;
        
        // Exam
        const examValue = subject.exam || '';
        rowHtml += `
            <td class="score-input-cell">
                <div style="display: flex; gap: 0.4rem; align-items: center;">
                    <input type="number" class="score-input" data-component="exam" data-subject="${subject.id}" 
                           data-max="60" value="${examValue}" min="0" max="60" 
                           ${disabledAttr} oninput="validateScoreInput(this)" 
                           onchange="updateRowTotal(this)" placeholder="0">
                    <span class="score-max">/ 60</span>
                </div>
            </td>
        `;
        
        // Total and Grade
        const total = (parseFloat(ca1Value) || 0) + (parseFloat(ca2Value) || 0) + (parseFloat(examValue) || 0);
        const grade = getGrade(total);
        
        rowHtml += `
            <td class="text-center fw-bold row-total">${total}</td>
            <td class="text-center"><span class="badge bg-primary row-grade">${grade}</span></td>
        `;
        
        tr.innerHTML = rowHtml;
        tbody.appendChild(tr);
    });
}

/**
 * Get letter grade based on total score
 */
function getGrade(total) {
    if (total >= 70) return 'A';
    if (total >= 60) return 'B';
    if (total >= 50) return 'C';
    if (total >= 45) return 'D';
    if (total >= 40) return 'E';
    return 'F';
}

/**
 * Update row total and trigger summary update
 */
function updateRowTotal(input) {
    const tr = input.closest('tr');
    const inputs = tr.querySelectorAll('.score-input');
    let total = 0;

    inputs.forEach(inp => {
        const value = parseFloat(inp.value);
        if (!isNaN(value) && inp.value.trim() !== '') {
            total += value;
        }
    });

    const totalCell = tr.querySelector('.row-total');
    if (totalCell) {
        totalCell.textContent = total;
    }

    const gradeCell = tr.querySelector('.row-grade');
    if (gradeCell) {
        const grade = getGrade(total);
        gradeCell.textContent = grade;
        gradeCell.className = `badge ${getGradeBadgeClass(grade)} row-grade`;
    }

    updateSummary();
}

/**
 * Get CSS class for grade badge
 */
function getGradeBadgeClass(grade) {
    const classes = {
        'A': 'bg-success',
        'B': 'bg-primary',
        'C': 'bg-info text-dark',
        'D': 'bg-warning text-dark',
        'E': 'bg-secondary',
        'F': 'bg-danger'
    };
    return classes[grade] || 'bg-secondary';
}

/**
 * Validate score input (ensure within range and numeric)
 */
function validateScoreInput(input) {
    const max = parseInt(input.getAttribute('data-max'));
    const value = parseFloat(input.value);

    if (input.value === '') {
        input.classList.remove('is-invalid');
        return;
    }

    if (isNaN(value) || value < 0) {
        input.value = '';
        input.classList.add('is-invalid');
        showValidationMessage(input, 'Score must be a positive number');
        return;
    }

    if (value > max) {
        input.value = max;
        input.classList.add('is-invalid');
        showValidationMessage(input, `Score cannot exceed ${max}`);
        setTimeout(() => {
            input.classList.remove('is-invalid');
        }, 2000);
        return;
    }

    input.classList.remove('is-invalid');
}

/**
 * Show validation message below input
 */
function showValidationMessage(input, message) {
    const existingMessage = input.parentElement.querySelector('.validation-message');
    if (existingMessage) {
        existingMessage.remove();
    }

    const messageDiv = document.createElement('div');
    messageDiv.className = 'validation-message text-danger small position-absolute';
    messageDiv.style.cssText = 'top: 100%; left: 0; z-index: 1000; white-space: nowrap;';
    messageDiv.textContent = message;

    input.parentElement.style.position = 'relative';
    input.parentElement.appendChild(messageDiv);

    setTimeout(() => {
        if (messageDiv.parentElement) {
            messageDiv.remove();
        }
    }, 3000);
}

/**
 * Update summary totals (if summary elements exist)
 */
function updateSummary() {
    const totalCreditsEl = document.getElementById('totalCredits');
    const termGPAEl = document.getElementById('termGPA');
    
    if (!totalCreditsEl || !termGPAEl) {
        return;
    }

    let totalSubjects = 0;
    let totalScore = 0;

    document.querySelectorAll('#assessmentTableBody tr').forEach(tr => {
        const totalCell = tr.querySelector('.row-total');
        if (totalCell) {
            const total = parseFloat(totalCell.textContent) || 0;
            totalSubjects++;
            totalScore += total;
        }
    });

    const avgScore = totalSubjects > 0 ? (totalScore / (totalSubjects * 100) * 4.0).toFixed(2) : 0;

    totalCreditsEl.textContent = totalSubjects + '.0';
    termGPAEl.textContent = avgScore;
}

/**
 * Render behavioral assessment trait cards with rating buttons
 * 10 behavioral traits: fluency, handwriting, game, initiative, criticalThinking, 
 * punctuality, attentiveness, neatness, selfDiscipline, politeness
 */
function renderBehavioralAssessment() {
    const container = document.getElementById('behaviorContainer');
    if (!container) {
        return;
    }

    container.innerHTML = '';

    const traits = [
        { key: 'fluency', label: 'Fluency', icon: 'fa-comment-dots', color: '#3b82f6' },
        { key: 'handwriting', label: 'Handwriting', icon: 'fa-pen-nib', color: '#8b5cf6' },
        { key: 'game', label: 'Game/Sports', icon: 'fa-volleyball-ball', color: '#10b981' },
        { key: 'initiative', label: 'Initiative', icon: 'fa-lightbulb', color: '#f59e0b' },
        { key: 'criticalThinking', label: 'Critical Thinking', icon: 'fa-brain', color: '#ec4899' },
        { key: 'punctuality', label: 'Punctuality', icon: 'fa-clock', color: '#06b6d4' },
        { key: 'attentiveness', label: 'Attentiveness', icon: 'fa-eye', color: '#6366f1' },
        { key: 'neatness', label: 'Neatness', icon: 'fa-broom', color: '#14b8a6' },
        { key: 'selfDiscipline', label: 'Self Discipline', icon: 'fa-user-shield', color: '#ef4444' },
        { key: 'politeness', label: 'Politeness', icon: 'fa-smile', color: '#f97316' }
    ];

    traits.forEach(trait => {
        const value = currentStudentData[trait.key] || 0;

        let buttonsHtml = '';
        for (let i = 1; i <= 5; i++) {
            const activeClass = i === value ? 'active' : '';
            const disabledAttr = !isEditMode ? 'disabled' : '';
            buttonsHtml += `<button type="button" class="behavior-score-btn ${activeClass}" 
                                    ${disabledAttr}
                                    onclick="selectBehavior(this, '${trait.key}', ${i})">${i}</button>`;
        }

        const traitCard = document.createElement('div');
        traitCard.className = 'behavioral-trait-card';
        traitCard.innerHTML = `
            <div class="trait-info">
                <div class="trait-icon-wrapper" style="background: ${trait.color}15; color: ${trait.color};">
                    <i class="fas ${trait.icon}"></i>
                </div>
                <span class="trait-name">${trait.label}</span>
            </div>
            <div class="trait-rating">
                <div class="rating-buttons">
                    ${buttonsHtml}
                </div>
                <input type="hidden" id="behavior_${trait.key}" value="${value}">
            </div>
        `;
        container.appendChild(traitCard);
    });
}

/**
 * Select a behavioral trait rating
 */
window.selectBehavior = function (btn, key, value) {
    if (!isEditMode) return;
    
    const ratingButtons = btn.parentElement;
    ratingButtons.querySelectorAll('.behavior-score-btn').forEach(b => {
        b.classList.remove('active');
    });
    btn.classList.add('active');
    const hiddenInput = document.getElementById(`behavior_${key}`);
    if (hiddenInput) {
        hiddenInput.value = value;
    }
};

/**
 * Toggle edit mode for all input fields
 */
function toggleEditMode() {
    if (!currentStudentData) {
        alert('Please load a student first');
        return;
    }

    isEditMode = !isEditMode;

    // Render table and behavioral assessment with new edit mode state
    renderAssessmentTable();
    renderBehavioralAssessment();

    // Update UI
    updateEditModeUI();
}

/**
 * Update edit mode button and field states
 */
function updateEditModeUI() {
    const btn = document.getElementById('editModeToggle');
    if (btn) {
        btn.textContent = isEditMode ? 'Disable Edit' : 'Enable Edit';
        btn.closest('button').style.background = isEditMode ? '#ef4444' : '#3b82f6';
    }

    // Toggle score inputs
    document.querySelectorAll('.score-input').forEach(input => {
        input.disabled = !isEditMode;
    });

    // Toggle behavioral buttons
    document.querySelectorAll('.behavior-score-btn').forEach(btn => {
        btn.disabled = !isEditMode;
    });

    // Toggle attendance input
    const attendanceInput = document.getElementById('attendanceInput');
    if (attendanceInput) {
        attendanceInput.disabled = !isEditMode;
    }

    // Toggle comment textareas
    const classTeacherComment = document.getElementById('classTeacherComment');
    if (classTeacherComment) {
        classTeacherComment.disabled = !isEditMode;
    }

    const headTeacherComment = document.getElementById('headTeacherComment');
    if (headTeacherComment) {
        headTeacherComment.disabled = !isEditMode;
    }
}

/**
 * Load student data and render all sections
 */
async function loadStudentData(studentId, apiEndpoint) {
    if (!studentId) {
        console.warn('No student ID provided');
        return;
    }

    try {
        const response = await fetch(apiEndpoint);
        if (!response.ok) {
            throw new Error(`Failed to load student data: ${response.statusText}`);
        }

        currentStudentData = await response.json();
        console.log('Student data loaded:', currentStudentData);

        // Update student card
        updateStudentCard();

        // Show report section
        const reportSection = document.getElementById('reportSection');
        const emptyState = document.getElementById('emptyState');
        const cardContainer = document.getElementById('studentCardContainer');
        
        if (reportSection) reportSection.style.display = 'block';
        if (emptyState) emptyState.style.display = 'none';
        if (cardContainer) cardContainer.style.display = 'block';

        // Render assessment data
        renderAssessmentTable();
        renderBehavioralAssessment();
        updateAttendanceDisplay();
        updateCommentsDisplay();

        // Reset edit mode
        isEditMode = false;
        updateEditModeUI();

    } catch (error) {
        console.error('Error loading student data:', error);
        alert('Failed to load student data. Please try again.');
    }
}

/**
 * Update student card with current student info
 */
function updateStudentCard() {
    if (!currentStudentData) return;

    const names = currentStudentData.studentName ? currentStudentData.studentName.split(' ') : ['S', 'N'];
    const initials = names.length > 1 
        ? (names[0][0] + names[names.length - 1][0]).toUpperCase()
        : names[0].substring(0, 2).toUpperCase();

    const cardAvatar = document.getElementById('cardAvatar');
    const cardStudentName = document.getElementById('cardStudentName');
    const cardStudentId = document.getElementById('cardStudentId');
    const cardAdmissionNumber = document.getElementById('cardAdmissionNumber');
    const cardClassName = document.getElementById('cardClassName');

    if (cardAvatar) cardAvatar.textContent = initials;
    if (cardStudentName) cardStudentName.textContent = currentStudentData.studentName || 'N/A';
    if (cardStudentId) cardStudentId.textContent = currentStudentData.studentId || '---';
    if (cardAdmissionNumber) cardAdmissionNumber.textContent = currentStudentData.admissionNumber || '---';
    if (cardClassName) cardClassName.textContent = currentStudentData.className || '---';
}

/**
 * Update attendance display
 */
function updateAttendanceDisplay() {
    if (!currentStudentData) return;

    const attendance = currentStudentData.attendance || 0;
    const daysPresent = currentStudentData.daysPresent || 0;
    const daysAbsent = currentStudentData.daysAbsent || 0;

    const attendanceRate = document.getElementById('attendanceRate');
    const daysPresentEl = document.getElementById('daysPresent');
    const daysAbsentEl = document.getElementById('daysAbsent');
    const attendanceInput = document.getElementById('attendanceInput');

    if (attendanceRate) attendanceRate.textContent = attendance + '%';
    if (daysPresentEl) daysPresentEl.textContent = daysPresent;
    if (daysAbsentEl) daysAbsentEl.textContent = daysAbsent;
    if (attendanceInput) attendanceInput.value = daysPresent;
}

/**
 * Update comments display
 */
function updateCommentsDisplay() {
    if (!currentStudentData) return;

    const classTeacherComment = document.getElementById('classTeacherComment');
    const headTeacherComment = document.getElementById('headTeacherComment');

    if (classTeacherComment) {
        classTeacherComment.value = currentStudentData.classTeacherComment || '';
    }

    if (headTeacherComment) {
        headTeacherComment.value = currentStudentData.headTeacherComment || '';
    }
}

/**
 * Save assessment data
 * Implemented by consuming templates (staff/class-reports.html, admin/assessments/reports.html)
 * This is a placeholder that should be overridden
 */
async function saveAssessment() {
    console.warn('saveAssessment() not implemented. Override in consuming template.');
}

/**
 * Open import modal (placeholder)
 */
function openImportModal() {
    const modal = document.getElementById('importModal');
    if (modal) {
        modal.style.display = 'flex';
        modal.classList.add('active');
    }
}

/**
 * Close modal
 */
function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.style.display = 'none';
        modal.classList.remove('active');
    }
}

/**
 * Reset all form fields
 */
function clearAllEntries() {
    if (!confirm('Are you sure you want to clear all entries?')) return;
    
    document.querySelectorAll('.score-input').forEach(input => {
        if (!input.disabled) input.value = '';
    });
    
    document.querySelectorAll('.behavior-score-btn').forEach(btn => {
        btn.classList.remove('active');
    });

    document.querySelectorAll('input[id^="behavior_"]').forEach(input => {
        input.value = 0;
    });
}
