// Questions Management JavaScript
console.log('Questions management script loaded');

// Global variables
let examinationId = 0;
let classId = 0;
let questionCount = 0;
const quillInstances = new Map();
const MAX_IMAGE_SIZE = 500 * 1024; // 500KB
const MAX_CONTENT_LENGTH = 50000; // 50k chars per field

// Initialize with examination and class IDs
window.initQuestionsManagement = function (examId, clsId) {
    examinationId = examId;
    classId = clsId;
    console.log('Questions management initialized with examId:', examId, 'classId:', clsId);

    // Initialize existing Quill editors
    initQuill(document);
};

// Global initQuill function
window.initQuill = function (element) {
    console.log('initQuill function called');

    // Safety check for Quill availability
    if (typeof Quill === 'undefined') {
        console.warn('Quill editor is not available. Please ensure Quill.js is loaded.');
        return;
    }

    const editors = element.querySelectorAll('.quill-editor');
    console.log('Found', editors.length, 'Quill editors');

    editors.forEach(editor => {
        if (editor.classList.contains('ql-container')) return;

        const isMinimal = editor.classList.contains('minimal');
        const toolbarOptions = isMinimal ? [
            ['bold', 'italic', 'underline'],
            [{ 'script': 'sub' }, { 'script': 'super' }],
            ['image'],
            ['clean']
        ] : [
            ['bold', 'italic', 'underline', 'strike'],
            [{ 'list': 'ordered' }, { 'list': 'bullet' }],
            [{ 'script': 'sub' }, { 'script': 'super' }],
            ['image'],
            ['clean']
        ];

        const quill = new Quill(editor, {
            theme: 'snow',
            modules: {
                toolbar: toolbarOptions
            },
            placeholder: editor.getAttribute('data-placeholder') || 'Type here...'
        });

        // Image size validation
        quill.getModule('toolbar').addHandler('image', () => {
            const input = document.createElement('input');
            input.setAttribute('type', 'file');
            input.setAttribute('accept', 'image/*');
            input.click();

            input.onchange = () => {
                const file = input.files[0];
                if (file && file.size > MAX_IMAGE_SIZE) {
                    alert('Image size exceeds 500KB limit');
                    return;
                }

                if (file) {
                    const reader = new FileReader();
                    reader.onload = (e) => {
                        const range = quill.getSelection();
                        quill.insertEmbed(range.index, 'image', e.target.result);
                    };
                    reader.readAsDataURL(file);
                }
            };
        });

        // Content length limit
        quill.on('text-change', () => {
            if (quill.getLength() > MAX_CONTENT_LENGTH) {
                quill.deleteText(MAX_CONTENT_LENGTH, quill.getLength());
                alert('Content length limit reached');
            }
        });

        quillInstances.set(editor, quill);
    });
};

// Global addQuestion function
window.addQuestion = function () {
    console.log('Adding new question');
    const template = document.getElementById('questionTemplate');
    const newQuestionsList = document.getElementById('newQuestionsList');

    if (!template || !newQuestionsList) {
        console.error('Template or container not found');
        return;
    }

    const clone = template.content.cloneNode(true);
    const questionIndex = questionCount++;
    const questionNumber = questionIndex + 1;

    // Replace placeholders
    clone.innerHTML = clone.innerHTML.replace(/__INDEX__/g, questionIndex);
    clone.innerHTML = clone.innerHTML.replace(/__NUMBER__/g, questionNumber);

    newQuestionsList.appendChild(clone);

    // Initialize Quill editors for the new question
    const newQuestionCard = newQuestionsList.lastElementChild;
    window.initQuill(newQuestionCard);
};

// Global removeQuestion function
window.removeQuestion = function (button) {
    const questionCard = button.closest('.question-card');
    if (questionCard) {
        questionCard.remove();
    }
};

// Global editQuestion function
window.editQuestion = function (questionId) {
    console.log('Editing question:', questionId);

    // Fetch question data with CSRF protection
    // Fetch question data with CSRF protection
    const headers = {};
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
    if (csrfToken) {
        headers[csrfHeader] = csrfToken;
    }

    fetch(`/admin/assessments/examinations/${examinationId}/questions/${questionId}`, {
        headers: headers
    })
        .then(response => response.json())
        .then(question => {
            console.log('Question data:', question);

            // Populate modal fields
            document.getElementById('edit-question-id').value = question.id;
            document.getElementById('edit-correctAnswer').value = question.correctAnswer;
            document.getElementById('edit-marks').value = question.marks;

            // Wait for Quill editors to be initialized
            setTimeout(() => {
                // Populate Quill editors
                const instructionEditor = quillInstances.get(document.getElementById('edit-instruction-editor'));
                const questionTextEditor = quillInstances.get(document.getElementById('edit-questionText-editor'));
                const optionAEditor = quillInstances.get(document.getElementById('edit-optionA-editor'));
                const optionBEditor = quillInstances.get(document.getElementById('edit-optionB-editor'));
                const optionCEditor = quillInstances.get(document.getElementById('edit-optionC-editor'));
                const optionDEditor = quillInstances.get(document.getElementById('edit-optionD-editor'));
                const optionEEditor = quillInstances.get(document.getElementById('edit-optionE-editor'));

                if (instructionEditor) instructionEditor.root.innerHTML = question.instruction || '';
                if (questionTextEditor) questionTextEditor.root.innerHTML = question.questionText || '';
                if (optionAEditor) optionAEditor.root.innerHTML = question.optionA || '';
                if (optionBEditor) optionBEditor.root.innerHTML = question.optionB || '';
                if (optionCEditor) optionCEditor.root.innerHTML = question.optionC || '';
                if (optionDEditor) optionDEditor.root.innerHTML = question.optionD || '';
                if (optionEEditor) optionEEditor.root.innerHTML = question.optionE || '';

                // Show modal
                document.getElementById('editQuestionModal').style.display = 'block';
            }, 100);
        })
        .catch(error => {
            console.error('Error fetching question:', error);
            alert('Error loading question data. Please try again.');
        });
};

window.closeEditModal = function () {
    document.getElementById('editQuestionModal').style.display = 'none';
};

window.saveEditedQuestion = function () {
    const questionId = document.getElementById('edit-question-id').value;
    const correctAnswer = document.getElementById('edit-correctAnswer').value;
    const marks = document.getElementById('edit-marks').value;

    // Get content from Quill editors
    const instructionEditor = quillInstances.get(document.getElementById('edit-instruction-editor'));
    const questionTextEditor = quillInstances.get(document.getElementById('edit-questionText-editor'));
    const optionAEditor = quillInstances.get(document.getElementById('edit-optionA-editor'));
    const optionBEditor = quillInstances.get(document.getElementById('edit-optionB-editor'));
    const optionCEditor = quillInstances.get(document.getElementById('edit-optionC-editor'));
    const optionDEditor = quillInstances.get(document.getElementById('edit-optionD-editor'));
    const optionEEditor = quillInstances.get(document.getElementById('edit-optionE-editor'));

    // Create form data (the endpoint expects form data, not JSON)
    const formData = new FormData();
    formData.append('instruction', instructionEditor ? instructionEditor.root.innerHTML : '');
    formData.append('questionText', questionTextEditor ? questionTextEditor.root.innerHTML : '');
    formData.append('optionA', optionAEditor ? optionAEditor.root.innerHTML : '');
    formData.append('optionB', optionBEditor ? optionBEditor.root.innerHTML : '');
    formData.append('optionC', optionCEditor ? optionCEditor.root.innerHTML : '');
    formData.append('optionD', optionDEditor ? optionDEditor.root.innerHTML : '');
    formData.append('optionE', optionEEditor ? optionEEditor.root.innerHTML : '');
    formData.append('correctAnswer', correctAnswer);
    formData.append('marks', marks);

    // Add CSRF token to form data
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    if (csrfToken) {
        formData.append('_csrf', csrfToken);
    }

    // Save question
    fetch(`/admin/assessments/examinations/${examinationId}/questions/${questionId}/update`, {
        method: 'POST',
        body: formData
    })
        .then(response => response.text())
        .then(html => {
            // Check if response contains success message
            if (html.includes('success')) {
                alert('Question updated successfully!');
                closeEditModal();
                // Reload the page to show updated question
                window.location.reload();
            } else {
                // Extract error message from HTML
                const parser = new DOMParser();
                const doc = parser.parseFromString(html, 'text/html');
                const errorMsg = doc.textContent || 'Error updating question';
                alert(errorMsg);
            }
        })
        .catch(error => {
            console.error('Error saving question:', error);
            alert('Error saving question. Please try again.');
        });
};

window.deleteQuestion = function (questionId) {
    if (confirm('Are you sure you want to delete this question? This action cannot be undone.')) {
        // Create form data with CSRF token
        const formData = new FormData();
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        if (csrfToken) {
            formData.append('_csrf', csrfToken);
        }

        fetch(`/admin/assessments/examinations/${examinationId}/questions/${questionId}/delete`, {
            method: 'POST',
            body: formData
        })
            .then(response => response.text())
            .then(html => {
                // Check if response contains success message
                if (html.includes('success')) {
                    alert('Question deleted successfully!');
                    // Remove the question card from the page
                    const questionCard = document.getElementById('question-' + questionId);
                    if (questionCard) {
                        questionCard.remove();
                    }
                } else {
                    // Extract error message from HTML
                    const parser = new DOMParser();
                    const doc = parser.parseFromString(html, 'text/html');
                    const errorMsg = doc.textContent || 'Error deleting question';
                    alert(errorMsg);
                }
            })
            .catch(error => {
                console.error('Error deleting question:', error);
                alert('Error deleting question. Please try again.');
            });
    }
};

window.goBackToAssessments = function () {
    window.location.href = `/staff/classes/${classId}/assessments`;
};