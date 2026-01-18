// Community Management Global Functions

// Modal functions
window.openModal = function (modalId) {
    console.log('Opening modal:', modalId);
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.classList.add('active');
        console.log('Modal opened successfully');
        // Also add body class to prevent scrolling
        document.body.classList.add('modal-open');
    } else {
        console.error('Modal not found:', modalId);
        console.log('Available elements with IDs:', Array.from(document.querySelectorAll('[id]')).map(el => el.id));
    }
};

window.closeModal = function (modalId) {
    console.log('Closing modal:', modalId);
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.classList.remove('active');
        // Remove body class to restore scrolling
        document.body.classList.remove('modal-open');
    }
};

// Student management functions
window.confirmDelete = function (studentId, studentName) {
    document.getElementById('studentName').textContent = studentName;
    document.getElementById('deleteForm').action = `/admin/community/students/${studentId}/delete`;
    document.getElementById('deleteModal').classList.add('active');
};

window.confirmDeleteAssignment = function (assignmentId, assignmentInfo) {
    console.log('confirmDeleteAssignment called with:', { assignmentId, assignmentInfo });
    document.getElementById('assignmentInfo').textContent = assignmentInfo;
    const url = `/admin/community/students/remove-assignment/${assignmentId}`;
    console.log('Setting hx-post to:', url);
    document.getElementById('deleteAssignmentForm').setAttribute('hx-post', url);
    // Re-process HTMX attributes after changing them
    htmx.process(document.getElementById('deleteAssignmentForm'));
    document.getElementById('deleteAssignmentModal').classList.add('active');
};

// Staff management functions
window.confirmDeleteStaff = function (staffId, staffName) {
    if (document.getElementById('staffName')) {
        document.getElementById('staffName').textContent = staffName;
    }
    if (document.getElementById('deleteForm')) {
        document.getElementById('deleteForm').action = `/admin/community/staff/${staffId}/delete`;
    }
    if (document.getElementById('deleteModal')) {
        document.getElementById('deleteModal').classList.add('active');
    }
};

window.confirmDeleteTeacherAssignment = function (assignmentId, assignmentInfo) {
    if (document.getElementById('teacherAssignmentInfo')) {
        document.getElementById('teacherAssignmentInfo').textContent = assignmentInfo;
    }
    if (document.getElementById('deleteTeacherAssignmentForm')) {
        document.getElementById('deleteTeacherAssignmentForm').setAttribute('hx-post', `/admin/community/staff/remove-class-assignment/${assignmentId}`);
    }
    if (document.getElementById('deleteTeacherAssignmentModal')) {
        document.getElementById('deleteTeacherAssignmentModal').classList.add('active');
    }
};

window.confirmDeleteSubjectAssignment = function (assignmentId, assignmentInfo) {
    if (document.getElementById('subjectAssignmentInfo')) {
        document.getElementById('subjectAssignmentInfo').textContent = assignmentInfo;
    }
    if (document.getElementById('deleteSubjectAssignmentForm')) {
        document.getElementById('deleteSubjectAssignmentForm').setAttribute('hx-post', `/admin/community/staff/remove-subject-assignment/${assignmentId}`);
    }
    if (document.getElementById('deleteSubjectAssignmentModal')) {
        document.getElementById('deleteSubjectAssignmentModal').classList.add('active');
    }
};

// Parent management functions
window.confirmDeleteParent = function (parentId, parentName) {
    if (document.getElementById('parentName')) {
        document.getElementById('parentName').textContent = parentName;
    }
    if (document.getElementById('deleteParentForm')) {
        document.getElementById('deleteParentForm').action = `/admin/community/parents/${parentId}/delete`;
    }
    if (document.getElementById('deleteParentModal')) {
        document.getElementById('deleteParentModal').classList.add('active');
    }
};

window.confirmDeleteParentAssignment = function (assignmentId, assignmentInfo) {
    console.log('confirmDeleteParentAssignment called with:', { assignmentId, assignmentInfo });
    if (document.getElementById('parentAssignmentInfo')) {
        document.getElementById('parentAssignmentInfo').textContent = assignmentInfo;
    }
    if (document.getElementById('deleteParentAssignmentForm')) {
        const url = `/admin/community/parents/remove-assignment/${assignmentId}`;
        console.log('Setting hx-post to:', url);
        document.getElementById('deleteParentAssignmentForm').setAttribute('hx-post', url);
        // Re-process HTMX attributes after changing them
        htmx.process(document.getElementById('deleteParentAssignmentForm'));
    }
    if (document.getElementById('deleteParentAssignmentModal')) {
        document.getElementById('deleteParentAssignmentModal').classList.add('active');
    }
};

// Auto-search with debouncing
let searchTimeout;
const SEARCH_DELAY = 500; // 500ms delay after user stops typing

window.applyFilters = function () {
    const search = document.getElementById('searchInput')?.value || '';
    const trackId = document.getElementById('trackFilter')?.value || '';
    const classId = document.getElementById('classFilter')?.value || '';
    const designation = document.getElementById('designationFilter')?.value || '';

    // Mark this as content replacement since we're just filtering
    if (window.markAsContentReplacement) {
        window.markAsContentReplacement();
    }

    const url = new URL(window.location);
    if (search) url.searchParams.set('search', search);
    else url.searchParams.delete('search');

    if (trackId) url.searchParams.set('trackId', trackId);
    else url.searchParams.delete('trackId');

    if (classId) url.searchParams.set('classId', classId);
    else url.searchParams.delete('classId');

    if (designation) url.searchParams.set('designation', designation);
    else url.searchParams.delete('designation');

    url.searchParams.delete('page');

    // Use history.replaceState instead of changing location to avoid adding to backstack
    window.history.replaceState(null, '', url.toString());

    // Trigger HTMX request to update content
    const contentContainer = document.querySelector('[data-content-area]') ||
        document.querySelector('#main-content') ||
        document.querySelector('.content-container');

    if (contentContainer) {
        htmx.ajax('GET', url.toString(), {
            target: contentContainer,
            swap: 'innerHTML'
        });
    } else {
        // Fallback to page reload if no content container found
        window.location.replace(url.toString());
    }
};

// Debounced search function
window.debouncedSearch = function () {
    clearTimeout(searchTimeout);

    // Add searching indicator
    const searchContainer = document.querySelector('.search-input-container');
    if (searchContainer) {
        searchContainer.classList.add('searching');
    }

    searchTimeout = setTimeout(() => {
        // Remove searching indicator
        if (searchContainer) {
            searchContainer.classList.remove('searching');
        }
        applyFilters();
    }, SEARCH_DELAY);
};

window.clearFilters = function () {
    const url = new URL(window.location);
    url.searchParams.delete('search');
    url.searchParams.delete('trackId');
    url.searchParams.delete('classId');
    url.searchParams.delete('page');
    window.location.replace(url.toString());
};

window.updateClassFilter = function () {
    const trackId = document.getElementById('trackFilter').value;
    const classSelect = document.getElementById('classFilter');

    if (!trackId) {
        // Show all classes - this will be populated by the server-side template
        classSelect.innerHTML = '<option value="">All Classes</option>';
        // Note: The classes data should be available from the server
        return;
    }

    // Load classes for selected track
    fetch(`/admin/community/students/0/classes-by-track/${trackId}`)
        .then(response => response.json())
        .then(classes => {
            classSelect.innerHTML = '<option value="">All Classes in Track</option>';
            classes.forEach(cls => {
                const option = document.createElement('option');
                option.value = cls.id;
                option.textContent = cls.className + ' (' + (cls.gradeLevel || 'No Grade') + ')';
                classSelect.appendChild(option);
            });
        })
        .catch(error => {
            console.error('Error loading classes:', error);
            classSelect.innerHTML = '<option value="">Error loading classes</option>';
        });
};

// Initialize event listeners when DOM is loaded
document.addEventListener('DOMContentLoaded', function () {
    // Search input auto-search handler
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('input', function (e) {
            debouncedSearch();
        });

        // Still support Enter key for immediate search
        searchInput.addEventListener('keypress', function (e) {
            if (e.key === 'Enter') {
                clearTimeout(searchTimeout);
                applyFilters();
            }
        });
    }

    // Auto-search for filter dropdowns
    const trackFilter = document.getElementById('trackFilter');
    if (trackFilter) {
        trackFilter.addEventListener('change', function (e) {
            // Update class filter first if it exists
            if (window.updateClassFilter) {
                updateClassFilter();
            }
            // Then apply filters with slight delay to allow class filter to update
            setTimeout(() => {
                applyFilters();
            }, 100);
        });
    }

    const classFilter = document.getElementById('classFilter');
    if (classFilter) {
        classFilter.addEventListener('change', function (e) {
            applyFilters();
        });
    }

    const designationFilter = document.getElementById('designationFilter');
    if (designationFilter) {
        designationFilter.addEventListener('change', function (e) {
            applyFilters();
        });
    }

    // Debug: Check if containers exist
    const studentContainer = document.getElementById('student-cards-container');
    if (studentContainer) {
        console.log('Student cards container found:', studentContainer);
    }

    const staffContainer = document.getElementById('staff-table-container');
    if (staffContainer) {
        console.log('Staff table container found:', staffContainer);
    }

    const parentContainer = document.getElementById('parent-table-container');
    if (parentContainer) {
        console.log('Parent table container found:', parentContainer);
    }
});

// HTMX event handlers for better error handling
document.addEventListener('htmx:targetError', function (event) {
    console.error('HTMX target error:', event.detail);
    alert('There was an error updating the page. Please refresh and try again.');
});

document.addEventListener('htmx:responseError', function (event) {
    console.error('HTMX response error:', event.detail);
    alert('There was an error processing your request. Please try again.');
});

document.addEventListener('htmx:sendError', function (event) {
    console.error('HTMX send error:', event.detail);
    alert('There was a network error. Please check your connection and try again.');
});

// Teacher assignment removal functions
window.removeClassAssignment = function (assignmentId, assignmentInfo) {
    if (confirm(`Are you sure you want to remove this assignment?\n\n${assignmentInfo}`)) {
        // Get CSRF token
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') ||
            document.querySelector('input[name="_csrf"]')?.value;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';

        const headers = {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        };

        if (csrfToken) {
            headers[csrfHeader] = csrfToken;
        }

        fetch(`/admin/community/staff/remove-class-assignment/${assignmentId}`, {
            method: 'POST',
            headers: headers
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Reload the page to refresh the staff list
                    window.location.reload();
                } else {
                    alert('Error: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Error removing class assignment:', error);
                alert('Error removing assignment. Please try again.');
            });
    }
};

window.removeSubjectAssignment = function (assignmentId, assignmentInfo) {
    if (confirm(`Are you sure you want to remove this assignment?\n\n${assignmentInfo}`)) {
        // Get CSRF token
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') ||
            document.querySelector('input[name="_csrf"]')?.value;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';

        const headers = {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        };

        if (csrfToken) {
            headers[csrfHeader] = csrfToken;
        }

        fetch(`/admin/community/staff/remove-subject-assignment/${assignmentId}`, {
            method: 'POST',
            headers: headers
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Reload the page to refresh the staff list
                    window.location.reload();
                } else {
                    alert('Error: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Error removing subject assignment:', error);
                alert('Error removing assignment. Please try again.');
            });
    }
};

// Class assignment functions
window.loadClassesByTrackModal = function (selectElement) {
    if (!selectElement) selectElement = document.getElementById('trackId');
    const trackId = selectElement.value;
    const form = selectElement.closest('form');
    const classSelect = form ? form.querySelector('select[name="assignedClassId"]') : document.getElementById('classId');

    if (!trackId) {
        classSelect.innerHTML = '<option value="">Select Track First</option>';
        classSelect.disabled = true;
        return;
    }

    classSelect.disabled = true;
    classSelect.innerHTML = '<option value="">Loading...</option>';

    // Get student ID from the form or modal context
    let studentId = null;
    if (form) {
        const hxPost = form.getAttribute('hx-post');
        const match = hxPost.match(/\/students\/([^\/]+)\//);
        if (match) studentId = match[1];
    }

    if (!studentId) {
        studentId = getStudentIdFromContext();
    }

    fetch(`/admin/community/students/${studentId}/classes-by-track/${trackId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.text(); // Get as text first to see what we're getting
        })
        .then(text => {
            try {
                const data = JSON.parse(text);

                classSelect.innerHTML = '<option value="">Select Class</option>';

                // Handle different response formats
                let classes = data;
                if (data && typeof data === 'object' && !Array.isArray(data)) {
                    // If response is wrapped in an object, try to extract the array
                    classes = data.classes || data.data || [];
                }

                if (!Array.isArray(classes)) {
                    console.error('Expected array but got:', typeof classes, classes);
                    classSelect.innerHTML = '<option value="">No classes available</option>';
                    classSelect.disabled = false;
                    return;
                }

                if (classes.length === 0) {
                    classSelect.innerHTML = '<option value="">No classes available for this track</option>';
                } else {
                    classes.forEach(cls => {
                        const option = document.createElement('option');
                        option.value = cls.id;
                        option.textContent = `${cls.className} (${cls.gradeLevel || 'No Grade'}) - ${cls.currentEnrollment}/${cls.maxCapacity} students`;
                        classSelect.appendChild(option);
                    });
                }
                classSelect.disabled = false;
            } catch (parseError) {
                console.error('JSON parse error:', parseError);
                console.error('Response text:', text);
                classSelect.innerHTML = '<option value="">Error parsing response</option>';
                classSelect.disabled = false;
            }
        })
        .catch(error => {
            console.error('Error loading classes:', error);
            classSelect.innerHTML = '<option value="">Error loading classes</option>';
        });
};

// Helper function to get student ID from various contexts
window.getStudentIdFromContext = function () {
    // Try to get from form action URL
    const form = document.querySelector('form[hx-post*="/students/"]');
    if (form) {
        const hxPost = form.getAttribute('hx-post');
        const match = hxPost.match(/\/students\/([^\/]+)\/assign-class/);
        if (match) {
            return match[1];
        }
    }

    // Try to get from modal data attribute
    const modal = document.querySelector('.modal.active');
    if (modal) {
        const studentId = modal.getAttribute('data-student-id');
        if (studentId) {
            return studentId;
        }
    }

    // Try to get from URL if we're on a student page
    const urlMatch = window.location.pathname.match(/\/students\/([^\/]+)/);
    if (urlMatch) {
        return urlMatch[1];
    }

    // Fallback: use a placeholder that will work with the endpoint
    console.warn('Could not determine student ID, using placeholder');
    return '0'; // The endpoint uses this as a placeholder anyway
};

// Assessment Sidebar Handler Functions
// These functions act as bridges to the main page functions when available

window.handleSidebarSessionChip = function (element) {
    if (typeof selectSidebarSessionChip === 'function') {
        selectSidebarSessionChip(element);
    } else {
        console.log('Sidebar session chip clicked, but main page functions not available');
        // Fallback: redirect with filter parameter
        const sessionYear = element.getAttribute('data-value');
        if (sessionYear) {
            window.location.href = `/admin/assessments/examinations?sessionYear=${sessionYear}`;
        } else {
            window.location.href = '/admin/assessments/examinations';
        }
    }
};

window.handleSidebarChip = function (element) {
    if (typeof selectSidebarChip === 'function') {
        selectSidebarChip(element);
    } else {
        console.log('Sidebar chip clicked, but main page functions not available');
        // Fallback: redirect with filter parameter
        const type = element.getAttribute('data-type');
        const value = element.getAttribute('data-value');
        if (value && type) {
            const params = new URLSearchParams();
            if (type === 'term') params.append('term', value);
            if (type === 'examType') params.append('examType', value);
            window.location.href = `/admin/assessments/examinations?${params.toString()}`;
        } else {
            window.location.href = '/admin/assessments/examinations';
        }
    }
};

window.handleSidebarHierarchicalChip = function (element, level) {
    if (typeof selectSidebarHierarchicalChip === 'function') {
        selectSidebarHierarchicalChip(element, level);
    } else {
        console.log('Sidebar hierarchical chip clicked, but main page functions not available');
        // For hierarchical filters, just redirect to examinations page
        // The main page will handle the complex filtering
        window.location.href = '/admin/assessments/examinations';
    }
};

window.handleClearAllSidebarFilters = function () {
    if (typeof clearAllSidebarFilters === 'function') {
        clearAllSidebarFilters();
    } else {
        console.log('Clear all filters clicked, but main page functions not available');
        // Fallback: redirect to examinations page without parameters
        window.location.href = '/admin/assessments/examinations';
    }
};

window.handleApplySidebarFilters = function () {
    if (typeof applySidebarFilters === 'function') {
        applySidebarFilters();
    } else {
        console.log('Apply filters clicked, but main page functions not available');
        // Fallback: just reload the page
        window.location.reload();
    }
};

// ==========================================
// Staff Assignment Modal Functions
// ==========================================

window.initializeAssignmentForms = function () {
    console.log('=== INITIALIZING ASSIGNMENT FORMS ===');

    function setupForm(formId, formName) {
        const form = document.getElementById(formId);
        if (form) {
            console.log(`${formName} found. Processing HTMX...`);

            // Ensure HTMX processes the form
            if (typeof htmx !== 'undefined') {
                htmx.process(form);
            }

            // Remove existing listeners to avoid duplicates (if any)
            // Note: This is a bit tricky with anonymous functions, but since we re-run this on swap, it's okay.
            // A better way is to check a data attribute
            if (form.dataset.initialized === 'true') {
                console.log(`${formName} already initialized.`);
                return;
            }

            // Add debug listeners
            form.addEventListener('htmx:beforeRequest', function (event) {
                console.log(`=== ${formName} HTMX BEFORE REQUEST ===`, event.detail);
            });

            form.addEventListener('htmx:afterRequest', function (event) {
                console.log(`=== ${formName} HTMX AFTER REQUEST ===`, event.detail);
                if (event.detail.successful) {
                    closeModal('staffAssignmentModal');
                }
            });

            form.addEventListener('htmx:responseError', function (event) {
                console.error(`=== ${formName} HTMX ERROR ===`, event.detail);
            });

            form.dataset.initialized = 'true';
        } else {
            // It's possible the form isn't there (e.g. user doesn't have permission), so just log info
            console.log(`${formName} not found in this view.`);
        }
    }

    setupForm('classTeacherForm', 'Class Teacher Form');
    setupForm('subjectTeacherForm', 'Subject Teacher Form');
};

// Listen for HTMX swaps to initialize the modal when it loads
document.addEventListener('htmx:afterSwap', function (event) {
    // Check if the swapped content is inside the staff assignment modal
    if (event.target.id === 'staffAssignmentModal' || event.target.closest('#staffAssignmentModal')) {
        console.log('Staff assignment modal content swapped. Initializing forms...');
        window.initializeAssignmentForms();
    }
});

window.loadAssignmentClassesByTrack = function (assignmentType) {
    console.log('=== LOADING CLASSES BY TRACK ===', assignmentType);
    const trackSelect = document.getElementById(assignmentType + 'TrackId');
    const classSelect = document.getElementById(assignmentType + 'ClassId');
    const subjectSelect = document.getElementById('subjectId');

    const trackId = trackSelect.value;
    console.log('Selected track ID:', trackId);

    if (!trackId) {
        classSelect.innerHTML = '<option value="">First select a track...</option>';
        classSelect.disabled = true;
        if (subjectSelect) {
            subjectSelect.innerHTML = '<option value="">First select a class...</option>';
            subjectSelect.disabled = true;
        }
        return;
    }

    // Show loading state
    classSelect.innerHTML = '<option value="">Loading classes...</option>';
    classSelect.disabled = true;

    // Load classes for selected track
    const url = `/admin/community/staff/classes-by-track/${trackId}`;
    console.log('Fetching classes from:', url);

    fetch(url)
        .then(response => {
            console.log('Classes response status:', response.status);
            return response.json();
        })
        .then(classes => {
            console.log('Classes loaded:', classes);
            classSelect.innerHTML = '<option value="">Choose a class...</option>';
            classes.forEach(cls => {
                const option = document.createElement('option');
                option.value = cls.id;
                option.textContent = cls.className + (cls.gradeLevel ? ' (' + cls.gradeLevel + ')' : '');
                classSelect.appendChild(option);
            });
            classSelect.disabled = false;

            // Reset subject dropdown if it exists
            if (subjectSelect) {
                subjectSelect.innerHTML = '<option value="">First select a class...</option>';
                subjectSelect.disabled = true;
            }
        })
        .catch(error => {
            console.error('Error loading classes:', error);
            classSelect.innerHTML = '<option value="">Error loading classes</option>';
        });
};

window.loadAssignmentSubjectsByClass = function () {
    console.log('=== LOADING SUBJECTS BY CLASS ===');
    const classSelect = document.getElementById('subjectTeacherClassId');
    const subjectSelect = document.getElementById('subjectId');

    const classId = classSelect.value;
    console.log('Selected class ID:', classId);

    if (!classId) {
        subjectSelect.innerHTML = '<option value="">First select a class...</option>';
        subjectSelect.disabled = true;
        return;
    }

    // Show loading state
    subjectSelect.innerHTML = '<option value="">Loading subjects...</option>';
    subjectSelect.disabled = true;

    // Load subjects for selected class
    const url = `/admin/community/staff/subjects-by-class/${classId}`;
    console.log('Fetching subjects from:', url);

    fetch(url)
        .then(response => {
            console.log('Subjects response status:', response.status);
            return response.json();
        })
        .then(subjects => {
            console.log('Subjects loaded:', subjects);
            subjectSelect.innerHTML = '<option value="">Choose a subject...</option>';
            subjects.forEach(subject => {
                const option = document.createElement('option');
                option.value = subject.id;
                option.textContent = subject.subjectName + (subject.subjectCode ? ' (' + subject.subjectCode + ')' : '');
                subjectSelect.appendChild(option);
            });
            subjectSelect.disabled = false;
        })
        .catch(error => {
            console.error('Error loading subjects:', error);
            subjectSelect.innerHTML = '<option value="">Error loading subjects</option>';
        });
};