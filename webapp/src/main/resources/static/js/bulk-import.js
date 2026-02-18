/**
 * Bulk Import - Client-side Excel Reading and Validation
 * Uses SheetJS (xlsx) library to read Excel files in the browser
 */

// Get CSRF token from meta tags
const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

let importData = {
    students: [],
    parents: [],
    staff: []
};

/**
 * Handle file selection
 */
function handleFileSelect(input) {
    const file = input.files[0];
    const uploadBtn = document.getElementById('uploadBtn');
    const fileNameSpan = document.getElementById('fileName');

    if (!file) {
        fileNameSpan.textContent = 'Choose Excel File or Drag & Drop';
        uploadBtn.disabled = true;
        return;
    }

    // Validate file size (5MB)
    if (file.size > 5 * 1024 * 1024) {
        alert('File size exceeds 5MB limit. Please choose a smaller file.');
        input.value = '';
        fileNameSpan.textContent = 'Choose Excel File or Drag & Drop';
        uploadBtn.disabled = true;
        return;
    }

    // Validate file extension
    if (!file.name.endsWith('.xlsx')) {
        alert('Please select an Excel file (.xlsx)');
        input.value = '';
        fileNameSpan.textContent = 'Choose Excel File or Drag & Drop';
        uploadBtn.disabled = true;
        return;
    }

    fileNameSpan.textContent = file.name;
    uploadBtn.disabled = false;
}

/**
 * Process the Excel file and send data to backend
 */
/**
 * Process the Excel file and send data to backend
 */
async function processExcelFile(event) {
    if (event) {
        event.preventDefault();
    }

    const fileInput = document.getElementById('excelFile');
    const file = fileInput.files[0];

    if (!file) {
        alert('Please select a file first');
        return;
    }

    // Show loading state
    const uploadBtn = document.getElementById('uploadBtn');
    const originalText = uploadBtn.innerHTML;
    uploadBtn.disabled = true;
    uploadBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';

    try {
        // Read the Excel file
        const data = await readExcelFile(file);

        // Validate that at least one sheet exists or just proceed (empty import)
        // Checks removed to allow partial/missing sheets

        // Sanitize and validate data
        const sanitizedData = sanitizeImportData(data);

        // Send data to backend for validation
        const response = await fetch('/admin/community/bulk-import/validate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify(sanitizedData)
        });

        if (!response.ok) {
            const error = await response.text();
            throw new Error(error || 'Validation failed');
        }

        const preview = await response.json();

        // Redirect to preview page
        window.location.href = '/admin/community/bulk-import/preview';

    } catch (error) {
        console.error('Error processing file:', error);
        alert('Error processing file: ' + error.message);
        uploadBtn.disabled = false;
        uploadBtn.innerHTML = originalText;
    }
}

/**
 * Read Excel file and extract data from all sheets
 */
function readExcelFile(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();

        reader.onload = function (e) {
            try {
                const data = new Uint8Array(e.target.result);
                const workbook = XLSX.read(data, { type: 'array' });

                // Extract data from each sheet
                const result = {
                    students: extractStudentsData(workbook),
                    parents: extractParentsData(workbook),
                    staff: extractStaffData(workbook)
                };

                resolve(result);
            } catch (error) {
                reject(new Error('Failed to read Excel file: ' + error.message));
            }
        };

        reader.onerror = function () {
            reject(new Error('Failed to read file'));
        };

        reader.readAsArrayBuffer(file);
    });
}

/**
 * Extract students data from Students sheet
 */
function extractStudentsData(workbook) {
    const sheetName = 'Students';
    const sheet = workbook.Sheets[sheetName];

    if (!sheet) {
        return [];
    }

    // Convert sheet to JSON
    const jsonData = XLSX.utils.sheet_to_json(sheet, { defval: '' });

    // Map to expected format
    return jsonData.map(row => ({
        firstName: String(row.FirstName || '').trim(),
        lastName: String(row.LastName || '').trim(),
        middleName: String(row.MiddleName || '').trim() || null,
        gender: String(row.Gender || '').trim(),
        dateOfBirth: formatDate(row.DateOfBirth),
        admissionNumber: String(row.AdmissionNumber || '').trim() || null,
        address: String(row.Address || '').trim() || null
    }));
}

/**
 * Extract parents data from Parents sheet
 */
function extractParentsData(workbook) {
    const sheetName = 'Parents';
    const sheet = workbook.Sheets[sheetName];

    if (!sheet) {
        return [];
    }

    const jsonData = XLSX.utils.sheet_to_json(sheet, { defval: '' });

    return jsonData.map(row => ({
        firstName: String(row.FirstName || '').trim(),
        lastName: String(row.LastName || '').trim(),
        email: String(row.Email || '').trim(),
        phoneNumber: String(row.PhoneNumber || '').trim(),
        address: String(row.Address || '').trim() || null
    }));
}

/**
 * Extract staff data from Staff sheet
 */
function extractStaffData(workbook) {
    const sheetName = 'Staff';
    const sheet = workbook.Sheets[sheetName];

    if (!sheet) {
        return [];
    }

    const jsonData = XLSX.utils.sheet_to_json(sheet, { defval: '' });

    return jsonData.map(row => ({
        firstName: String(row.FirstName || '').trim(),
        lastName: String(row.LastName || '').trim(),
        email: String(row.Email || '').trim(),
        phoneNumber: String(row.PhoneNumber || '').trim(),
        designation: String(row.Designation || '').trim(),
        dateOfHire: formatDate(row.DateOfHire) || null
    }));
}

/**
 * Format date to DD/MM/YYYY
 * Handles Excel date serial numbers and various date formats
 */
function formatDate(value) {
    if (!value) return '';

    // If it's already a string in DD/MM/YYYY format, return it
    if (typeof value === 'string' && /^\d{2}\/\d{2}\/\d{4}$/.test(value)) {
        return value;
    }

    let date;

    // Handle Excel serial date number
    if (typeof value === 'number') {
        // Excel dates are days since 1900-01-01
        const excelEpoch = new Date(1900, 0, 1);
        date = new Date(excelEpoch.getTime() + (value - 2) * 24 * 60 * 60 * 1000);
    } else if (value instanceof Date) {
        date = value;
    } else {
        // Try to parse as string
        date = new Date(value);
    }

    if (isNaN(date.getTime())) {
        return String(value); // Return as-is if can't parse
    }

    // Format as DD/MM/YYYY
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();

    return `${day}/${month}/${year}`;
}

/**
 * Sanitize import data and check for suspicious content
 */
function sanitizeImportData(data) {
    return {
        students: data.students.map(row => sanitizeRow(row, 'Student')),
        parents: data.parents.map(row => sanitizeRow(row, 'Parent')),
        staff: data.staff.map(row => sanitizeRow(row, 'Staff'))
    };
}

/**
 * Sanitize a single row of data
 */
function sanitizeRow(row, type) {
    const sanitized = {};
    for (const [key, value] of Object.entries(row)) {
        if (value === null || value === undefined) {
            sanitized[key] = null;
            continue;
        }

        const stringValue = String(value);

        // Check for suspicious patterns
        if (hasSuspiciousContent(stringValue)) {
            throw new Error(`Suspicious content detected in ${type} record (${key}): ${stringValue}. Please remove any HTML tags or script content.`);
        }

        // Basic sanitization (trim and remove control characters)
        sanitized[key] = stringValue.trim().replace(/[\x00-\x1F\x7F]/g, '');
    }
    return sanitized;
}

/**
 * Check for suspicious content (XSS, Injection)
 */
function hasSuspiciousContent(value) {
    const suspiciousPatterns = [
        /<script\b[^>]*>([\s\S]*?)<\/script>/i, // Script tags
        /javascript:/i,                          // Javascript protocol
        /on\w+\s*=/i,                            // Event handlers like onload=
        /<[^>]+>/,                               // HTML tags
        /alert\s*\(/i,                           // Alert calls
        /eval\s*\(/i,                            // Eval calls
        /document\.cookie/i                      // Cookie access
    ];

    return suspiciousPatterns.some(pattern => pattern.test(value));
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', function () {
    const form = document.getElementById('bulkImportForm');
    if (form) {
        form.addEventListener('submit', processExcelFile);
    }
});
