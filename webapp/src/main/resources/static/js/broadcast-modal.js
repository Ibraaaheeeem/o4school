/**
 * Shared Broadcast Modal JavaScript
 * Handles recipient selection, template loading, and broadcast submission across all channels.
 */

// Helper: Get modal configuration from data attributes
function getBroadcastConfig() {
    const modal = document.getElementById('broadcast-modal');
    if (!modal) return null;
    return {
        channel: modal.getAttribute('data-channel'),
        actionUrl: modal.getAttribute('data-action-url'),
        utilityUrl: modal.getAttribute('data-utility-url') || modal.getAttribute('data-action-url')
    };
}

// Fallback authenticatedFetch if not defined globally
if (typeof window.authenticatedFetch !== 'function') {
    window.authenticatedFetch = async function (url, options = {}) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

        if (!options.headers) options.headers = {};
        if (csrfToken && csrfHeader) {
            options.headers[csrfHeader] = csrfToken;
        }

        const response = await fetch(url, options);
        if (response.redirected && response.url.includes('/login')) {
            window.location.href = '/login';
            return new Promise(() => { });
        }
        return response;
    };
}

function showBroadcastModal() {
    const modal = document.getElementById('broadcast-modal');
    if (modal) {
        modal.classList.add('active');
        openBroadcastTab(null, 'recipients'); // Always start with recipients tab
        fetchTemplates();
        updateRecipientCount();
    }
}

function closeBroadcastModal(event) {
    if (event && event.target.id !== 'broadcast-modal' && event.target.tagName !== 'BUTTON') {
        return;
    }
    const modal = document.getElementById('broadcast-modal');
    if (modal) modal.classList.remove('active');
}

function openBroadcastTab(event, tabId) {
    // Hide all tab contents
    document.querySelectorAll('#broadcast-modal .tab-content').forEach(el => {
        el.style.display = 'none';
    });
    // Deactivate all tab buttons
    document.querySelectorAll('#broadcast-modal .tab-button').forEach(btn => {
        btn.classList.remove('active');
    });
    // Show the selected tab
    const tabEl = document.getElementById(tabId);
    if (tabEl) tabEl.style.display = 'block';

    // Support both .tab-button (new style) and .tab-btn (old style)
    if (event && event.currentTarget) {
        event.currentTarget.classList.add('active');
    } else {
        const btn = document.querySelector(`.tab-button[onclick*="${tabId}"], .tab-btn[onclick*="${tabId}"]`);
        if (btn) btn.classList.add('active');
    }

    // When switching to compose tab, fetch templates
    if (tabId === 'compose') {
        fetchTemplates();
    }
}

// Recipient selection state
const _activeRecipientTabs = { PARENTS: false, STAFF: false };

function toggleRecipientTab(type) {
    const previousType = (() => {
        if (_activeRecipientTabs.PARENTS && _activeRecipientTabs.STAFF) return 'ALL';
        if (_activeRecipientTabs.PARENTS) return 'PARENTS';
        if (_activeRecipientTabs.STAFF) return 'STAFF';
        return 'NONE';
    })();

    _activeRecipientTabs[type] = !_activeRecipientTabs[type];

    // Determine new recipientType
    let recipientType = 'NONE';
    if (_activeRecipientTabs.PARENTS && _activeRecipientTabs.STAFF) {
        recipientType = 'ALL';
    } else if (_activeRecipientTabs.PARENTS) {
        recipientType = 'PARENTS';
    } else if (_activeRecipientTabs.STAFF) {
        recipientType = 'STAFF';
    }

    // If type actually changed, clear lists and filters
    if (recipientType !== previousType) {
        clearRecipientSelections();
    }

    // Update button styles
    updateRecipientTabStyles();

    // Update hidden input
    const hiddenInput = document.getElementById('recipientTypeInput');
    if (hiddenInput) hiddenInput.value = recipientType;

    // Show/hide UI sections
    const searchSection = document.getElementById('recipient-search-section');
    if (searchSection) searchSection.style.display = recipientType !== 'NONE' ? 'block' : 'none';

    // Update labels and detailed filters
    updateRecipientListLabels(recipientType);
    toggleAcademicFilters();

    // Refresh data
    updateRecipientCount();
    fetchTemplates();
    loadRecipientCards();
}

function clearRecipientSelections() {
    const containers = [
        'manual-ids-container', 'excluded-ids-container',
        'manual-phones-container', 'excluded-phones-container'
    ];
    containers.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.innerHTML = '';
    });

    document.querySelectorAll('.class-checkbox, .track-checkbox').forEach(cb => cb.checked = false);

    ['studentGender', 'studentStatus', 'feeStatus'].forEach(name => {
        const sel = document.querySelector(`select[name="${name}"]`);
        if (sel) sel.selectedIndex = 0;
    });

    const applyCheckbox = document.getElementById('add-all-checkbox');
    if (applyCheckbox) applyCheckbox.checked = true;
    const addAllInput = document.getElementById('add-all-input');
    if (addAllInput) addAllInput.value = 'true';

    updateSelectedRecipientsDisplay([]);

    const grid = document.getElementById('recipient-card-grid');
    if (grid) {
        grid.innerHTML = '<div style="grid-column: span 2; padding: 1.5rem; text-align: center; color: #94a3b8; font-size: 0.85rem;">Select a recipient type above to browse.</div>';
    }
}

function updateRecipientTabStyles() {
    const tabParents = document.getElementById('tab-parents');
    const tabStaff = document.getElementById('tab-staff');

    // Parents: Indigo theme
    const parentsActiveBg = '#4f46e5';
    const parentsActiveFg = 'white';
    const parentsShadow = '0 4px 12px rgba(79,70,229,0.35)';

    // Staff: Emerald theme
    const staffActiveBg = '#10b981';
    const staffActiveFg = 'white';
    const staffShadow = '0 4px 12px rgba(16,185,129,0.35)';

    const inactiveBg = 'transparent';
    const inactiveFg = '#64748b';

    if (tabParents) {
        const isActive = _activeRecipientTabs.PARENTS;
        tabParents.style.background = isActive ? parentsActiveBg : inactiveBg;
        tabParents.style.color = isActive ? parentsActiveFg : inactiveFg;
        tabParents.style.boxShadow = isActive ? parentsShadow : 'none';
        tabParents.style.borderColor = isActive ? parentsActiveBg : 'transparent';
    }
    if (tabStaff) {
        const isActive = _activeRecipientTabs.STAFF;
        tabStaff.style.background = isActive ? staffActiveBg : inactiveBg;
        tabStaff.style.color = isActive ? staffActiveFg : inactiveFg;
        tabStaff.style.boxShadow = isActive ? staffShadow : 'none';
        tabStaff.style.borderColor = isActive ? staffActiveBg : 'transparent';
    }
}

function updateRecipientListLabels(recipientType) {
    const listLabel = document.getElementById('recipient-list-label');
    const groupLabel = document.getElementById('recipient-list-group-label');
    if (listLabel) {
        listLabel.textContent = recipientType === 'STAFF' ? 'Staff List' :
            recipientType === 'ALL' ? 'All Recipients' : 'Parent List';
    }
    if (groupLabel) {
        groupLabel.textContent = `(group by: ${recipientType === 'ALL' ? 'All' :
            recipientType === 'STAFF' ? 'Staff' : 'All Parents'})`;
    }
}

function toggleAcademicFilters() {
    const type = document.getElementById('recipientTypeInput')?.value || 'NONE';
    const academicSection = document.getElementById('academic-filters-section');
    const parentFilters = document.getElementById('parent-only-filters');
    const classLabel = document.getElementById('class-selection-label');

    const showAcademic = (type === 'PARENTS' || type === 'STAFF' || type === 'ALL');
    const showParentFilters = (type === 'PARENTS' || type === 'ALL');

    if (academicSection) academicSection.style.display = showAcademic ? 'block' : 'none';
    if (parentFilters) parentFilters.style.display = showParentFilters ? 'block' : 'none';

    if (type === 'PARENTS') {
        if (classLabel) classLabel.innerText = "Parent's Child's Classes";
    } else if (type === 'STAFF') {
        if (classLabel) classLabel.innerText = "Staff's Assigned Classes";
    } else if (type === 'ALL') {
        if (classLabel) classLabel.innerText = "Filter by Class (Applies to both)";
    }
}

async function updateRecipientCount() {
    const config = getBroadcastConfig();
    const form = document.getElementById('broadcast-form');
    const badge = document.getElementById('recipient-preview-badge');
    const addAllCount = document.getElementById('add-all-count');

    if (!form || !config) return;

    try {
        const formData = new FormData(form);
        const response = await window.authenticatedFetch(`${config.utilityUrl}/recipients`, {
            method: 'POST',
            body: formData
        });
        const recipients = await response.json();
        window.currentRecipients = recipients;

        if (badge) {
            badge.innerText = `Recipients: ${recipients.length}`;
            badge.style.background = recipients.length > 0 ? '#e0e7ff' : '#fee2e2';
            badge.style.color = recipients.length > 0 ? '#4338ca' : '#ef4444';
        }

        if (addAllCount) addAllCount.textContent = recipients.length.toLocaleString();

        updateSelectedRecipientsDisplay(recipients);
        validateBalance(recipients.length);
        updateLegacyPreviewList(recipients);

    } catch (err) {
        console.error("Failed to update recipients:", err);
    }
}

function validateBalance(count) {
    const config = getBroadcastConfig();
    const sendBtn = document.getElementById('send-broadcast-btn');
    const warningDiv = document.getElementById('insufficient-units-warning');
    const balanceDisplay = document.getElementById('warning-balance-display');

    // Check global balance variables based on channel
    let balance = Infinity;
    if (config.channel === 'whatsapp' && typeof WHATSAPP_BALANCE !== 'undefined') balance = WHATSAPP_BALANCE;
    else if (config.channel === 'sms' && typeof SMS_BALANCE !== 'undefined') balance = SMS_BALANCE;
    else if (config.channel === 'internal') balance = Infinity; // Usually free

    if (sendBtn && warningDiv && balance !== Infinity) {
        if (count > balance) {
            warningDiv.style.display = 'flex';
            if (balanceDisplay) balanceDisplay.innerText = new Intl.NumberFormat().format(balance);
            sendBtn.disabled = true;
            sendBtn.style.opacity = '0.5';
        } else {
            warningDiv.style.display = 'none';
            sendBtn.disabled = false;
            sendBtn.style.opacity = '1';
        }
    }
}

function updateLegacyPreviewList(recipients) {
    const tbody = document.getElementById('preview-list-body');
    if (!tbody) return;

    if (recipients.length === 0) {
        tbody.innerHTML = '<tr><td colspan="3" style="text-align: center; padding: 3rem 1rem; color: #94a3b8;">No recipients matching filters</td></tr>';
    } else {
        tbody.innerHTML = recipients.slice(0, 50).map(r => `
            <tr style="border-bottom: 1px solid #f1f5f9;">
                <td style="padding: 10px 12px;"><div style="font-weight: 600; color: #334155;">${r.name}</div></td>
                <td style="padding: 10px 12px; color: #64748b; font-family: monospace; font-size: 0.85rem;">${r.phoneNumber || 'N/A'}</td>
                <td style="padding: 10px 12px; text-align: right;">
                    <button type="button" onclick="handleRemoveRecipient('${r.userId}', '${r.phoneNumber}')" 
                        style="background: #fee2e2; border: none; color: #ef4444; width: 28px; height: 28px; border-radius: 50%; cursor: pointer;">&times;</button>
                </td>
            </tr>
        `).join('') + (recipients.length > 50 ? `<tr><td colspan="3" style="text-align: center; font-size: 0.75rem; color: #94a3b8; padding: 0.5rem;">...and ${recipients.length - 50} more</td></tr>` : '');
    }
}

let searchTimeout = null;
function searchManualRecipients(query) {
    clearTimeout(searchTimeout);
    const config = getBroadcastConfig();
    const dropdown = document.getElementById('search-results-dropdown');
    const recipientType = document.getElementById('recipientTypeInput')?.value || '';

    if (!query || query.length < 2 || !config) {
        if (dropdown) dropdown.style.display = 'none';
        return;
    }

    searchTimeout = setTimeout(async () => {
        try {
            const response = await window.authenticatedFetch(`${config.utilityUrl}/search?query=${encodeURIComponent(query)}&recipientType=${recipientType}`);
            const results = await response.json();

            if (dropdown) {
                if (results.length === 0) {
                    dropdown.innerHTML = '<div style="padding: 12px; color: #94a3b8; text-align: center;">No results found</div>';
                } else {
                    dropdown.innerHTML = results.map(r => `
                        <div onclick="addManualRecipient('${r.userId}', '${r.name}', '${r.phoneNumber}', '${r.roles?.join(', ') || ""}')" 
                            style="padding: 10px 12px; cursor: pointer; border-bottom: 1px solid #f1f5f9;">
                            <div style="font-weight: 600; font-size: 0.85rem;">${r.name}</div>
                            <div style="font-size: 0.75rem; color: #64748b;">${r.roles?.join(', ') || (r.classLabel || "")} • ${r.phoneNumber || 'No phone'}</div>
                        </div>
                    `).join('');
                }
                dropdown.style.display = 'block';
            }
        } catch (err) {
            console.error("Search failed:", err);
        }
    }, 300);
}

function addManualRecipient(userId, name, phone, roles) {
    const container = document.getElementById('manual-ids-container');
    if (!container) return;

    if (container.querySelector(`input[value="${userId}"]`)) {
        return;
    }

    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = 'manualUserIds';
    input.value = userId;
    input.id = `manual-id-${userId}`;
    container.appendChild(input);

    // Clear any existing exclusion
    const exclusion = document.querySelector(`#excluded-ids-container input[value="${userId}"]`);
    if (exclusion) exclusion.remove();

    const dropdown = document.getElementById('search-results-dropdown');
    if (dropdown) dropdown.style.display = 'none';
    const searchInput = document.getElementById('recipient-search');
    if (searchInput) searchInput.value = '';

    uncheckNoFilter();
    updateRecipientCount();
    loadRecipientCards(); // Refresh grid state if needed
}

function handleRemoveRecipient(userId, phone) {
    const manualInput = document.getElementById(`manual-id-${userId}`);
    const manualPhone = document.getElementById(`manual-phone-${phone}`);
    if (manualInput) {
        manualInput.remove();
    } else if (manualPhone) {
        manualPhone.remove();
    } else {
        addExclusion(userId, phone);
    }
    updateRecipientCount();
}

function addExclusion(userId, phone) {
    if (userId && userId !== 'null') {
        const container = document.getElementById('excluded-ids-container');
        if (container && !container.querySelector(`input[value="${userId}"]`)) {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'excludedUserIds';
            input.value = userId;
            container.appendChild(input);
        }
    } else if (phone && phone !== 'null') {
        const container = document.getElementById('excluded-phones-container');
        if (container && !container.querySelector(`input[value="${phone}"]`)) {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'excludedPhoneNumbers';
            input.value = phone;
            container.appendChild(input);
        }
    }
}

function handleAddAllCheckbox(checked) {
    const el = document.getElementById('add-all-input');
    if (el) el.value = checked ? 'true' : 'false';
    updateRecipientCount();
}

async function loadRecipientCards() {
    const grid = document.getElementById('recipient-card-grid');
    const config = getBroadcastConfig();
    if (!grid || !config) return;

    const recipientType = document.getElementById('recipientTypeInput')?.value || 'NONE';
    if (recipientType === 'NONE') {
        grid.innerHTML = '<div style="grid-column: span 2; padding: 1.5rem; text-align: center; color: #94a3b8; font-size: 0.85rem;">Select a recipient type above to browse.</div>';
        return;
    }

    grid.innerHTML = '<div style="grid-column: span 2; padding: 1.5rem; text-align: center; color: #94a3b8;"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';

    try {
        const form = document.getElementById('broadcast-form');
        const formData = new FormData(form);
        const response = await window.authenticatedFetch(`${config.utilityUrl}/recipients`, {
            method: 'POST',
            body: formData
        });
        const recipients = await response.json();

        if (!recipients.length) {
            grid.innerHTML = '<div style="grid-column: span 2; padding: 1.25rem; text-align: center; color: #94a3b8; font-size: 0.85rem;">No recipients found for these filters.</div>';
            return;
        }

        grid.innerHTML = recipients.slice(0, 24).map(r => {
            const initials = r.name ? r.name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : '?';
            const subtitle = r.type === 'STAFF' ? (r.roles?.join(', ') || 'Staff') : (r.classLabel || 'Parent');
            const safeName = r.name.replace(/'/g, "\\'");
            return `
                <div class="recipient-card">
                    <div class="card-avatar">${initials}</div>
                    <div class="card-info">
                        <div class="card-name">${r.name}</div>
                        <div class="card-meta">${subtitle}</div>
                    </div>
                    <button type="button" class="card-add-btn"
                        onclick="addManualRecipient('${r.userId}', '${safeName}', '${r.phoneNumber}', ['card'])">+</button>
                </div>
            `;
        }).join('');

    } catch (err) {
        grid.innerHTML = '<div style="grid-column: span 2; padding: 1rem; color: #ef4444; font-size: 0.85rem;">Failed to load recipients.</div>';
    }
}

function updateSelectedRecipientsDisplay(recipients) {
    const namesEl = document.getElementById('selected-recipients-names');
    if (!namesEl) return;

    if (recipients.length === 0) {
        namesEl.innerHTML = '<span style="color: #94a3b8; font-style: italic;">none selected, search above or use \'Send to all matching\'</span>';
    } else {
        namesEl.innerHTML = recipients.map(r => `
            <span class="recipient-pill">
                ${r.name}
                <i class="fas fa-times-circle" 
                   onclick="handleRemoveRecipient('${r.userId}', '${r.phoneNumber}')" 
                   title="Remove recipient"></i>
            </span>`).join('');
    }
}

async function fetchTemplates() {
    const selector = document.getElementById('template-selector');
    const config = getBroadcastConfig();
    if (!selector || !config) return;

    const recipientType = document.getElementById('recipientTypeInput')?.value || '';
    selector.innerHTML = '<option value="">-- Loading templates... --</option>';

    try {
        const response = await window.authenticatedFetch(`${config.utilityUrl}/templates?recipientType=${recipientType}`);
        const templates = await response.json();

        selector.innerHTML = '<option value="">-- Select Template --</option>';
        if (templates.length === 0) {
            selector.innerHTML = '<option value="">-- No templates found --</option>';
        }

        templates.forEach(t => {
            const opt = document.createElement('option');
            opt.value = t.name;
            opt.innerText = t.name;
            opt.dataset.components = t.components;
            opt.dataset.targetRole = t.target_role;
            opt.dataset.mapping = t.mapping;
            selector.appendChild(opt);
        });
    } catch (err) {
        console.error("Failed to fetch templates:", err);
        selector.innerHTML = '<option value="">-- Error loading templates --</option>';
    }
}

function loadTemplateDetails(templateName) {
    const container = document.getElementById('template-params-container');
    const inputsContainer = document.getElementById('template-params-inputs');
    const messageArea = document.getElementById('free-form-message');
    const selector = document.getElementById('template-selector');
    const selectedOpt = selector.options[selector.selectedIndex];

    if (!templateName) {
        if (container) container.style.display = 'none';
        if (messageArea) {
            messageArea.disabled = false;
            messageArea.placeholder = "Enter your announcement here...";
        }
        const warningDiv = document.getElementById('template-qualification-warning');
        if (warningDiv) warningDiv.style.display = 'none';
        return;
    }

    validateTemplateQualification(selectedOpt.dataset.targetRole);

    if (container) container.style.display = 'block';
    if (messageArea) {
        messageArea.disabled = true;
        messageArea.placeholder = "Message is predefined by template";
        messageArea.value = "[Template: " + templateName + " selected]";
    }

    try {
        const components = JSON.parse(selectedOpt.dataset.components || '[]');
        const mappingStr = selectedOpt.dataset.mapping || '';
        const bodyComp = components.find(c => c.type === 'BODY');
        const text = bodyComp?.text || "";

        const mapping = {};
        mappingStr.split(',').forEach(m => {
            if (m.includes('=')) {
                const [id, key] = m.split('=');
                mapping[id.trim()] = key.trim();
            } else if (m.trim()) mapping[m.trim()] = m.trim();
        });

        const systemVars = [
            'name', 'first_name', 'last_name', 'phone', 'date', 'school_name', 'school_contact',
            'parent_name', 'students', 'student_name', 'student_names', 'class_name', 'current_session', 'current_term',
            'academic_year', 'term_number', 'current_bill', 'current_balance', 'term_fees', 'settled_bill', 'outstanding',
            'total_bill', 'balance', 'amount', 'dedicated_account', 'account_number', 'staff_name', 'cadre', 'department'
        ];

        const varMatches = text.match(/\{\{([a-zA-Z0-9_]+)\}\}/g) || [];
        const uniqueVars = [...new Set(varMatches)];

        if (uniqueVars.length === 0) {
            if (inputsContainer) inputsContainer.innerHTML = `<div class="template-inline-preview">${text.replace(/\\n/g, '<br>')}</div>`;
        } else {
            let previewHtml = text.replace(/ +/g, ' ').replace(/\\n\\n+/g, '\\n\\n');

            uniqueVars.forEach(v => {
                const key = v.replace(/\{\{|\}\}/g, '');
                const mappedKey = mapping[key] || key;
                const isAutoFilled = systemVars.includes(mappedKey);

                let inputHtml = isAutoFilled
                    ? `<span class="inline-param-input" style="background: #e2e8f0 !important; cursor: not-allowed; border-bottom: none !important; opacity: 1; width: auto; font-family: inherit;" title="Auto-filled: ${mappedKey}">${mappedKey}</span>`
                    : `<input type="text" name="manualParam_${key}" placeholder="${v}" class="inline-param-input" required title="Enter value for ${key}" oninput="updateUsageEstimate()">`;

                previewHtml = previewHtml.split(v).join(inputHtml);
            });

            previewHtml = previewHtml.replace(/\\n/g, '<br>');
            if (inputsContainer) {
                inputsContainer.innerHTML = `<div class="template-inline-preview">${previewHtml}</div>`;
                inputsContainer.style.display = 'block';
            }
            updateUsageEstimate();
        }
    } catch (e) {
        console.error("Error parsing template components:", e);
        if (inputsContainer) inputsContainer.innerHTML = 'Error loading params';
    }
}

function validateTemplateQualification(targetRole) {
    const recipients = window.currentRecipients || [];
    let unqualifiedCount = 0;
    let unqualifiedType = '';

    if (targetRole === 'PARENT') {
        unqualifiedCount = recipients.filter(r => r.type === 'STAFF').length;
        unqualifiedType = 'Staff';
    } else if (targetRole === 'STAFF') {
        unqualifiedCount = recipients.filter(r => r.type === 'PARENT').length;
        unqualifiedType = 'Parents';
    }

    let warningDiv = document.getElementById('template-qualification-warning');
    if (unqualifiedCount > 0) {
        if (!warningDiv) {
            warningDiv = document.createElement('div');
            warningDiv.id = 'template-qualification-warning';
            warningDiv.style.cssText = 'background: #fff7ed; border: 1px solid #ffedd5; color: #9a3412; padding: 1rem; border-radius: 12px; font-size: 0.85rem; margin-bottom: 1rem; display: flex; align-items: center; gap: 0.75rem;';
            const container = document.getElementById('template-params-container');
            if (container) container.parentNode.insertBefore(warningDiv, container);
        }
        warningDiv.innerHTML = `
            <svg width="20" height="20" fill="none" stroke="currentColor" viewBox="0 0 24 24" style="flex-shrink: 0;">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <span><b>Warning:</b> ${unqualifiedCount} recipient(s) (${unqualifiedType}) are not qualified for this template and will be skipped.</span>
        `;
        warningDiv.style.display = 'flex';
    } else if (warningDiv) {
        warningDiv.style.display = 'none';
    }
}

function updateUsageEstimate() {
    const text = document.getElementById('free-form-message')?.value || "";
    const charCountEl = document.getElementById('char-count');
    const pageCountEl = document.getElementById('page-count');

    if (charCountEl) charCountEl.innerText = text.length;

    if (pageCountEl) {
        // SMS paging logic: 160 per segment, 153 if multiple (standard GSM)
        let pages = 1;
        if (text.length > 160) {
            pages = Math.ceil(text.length / 153);
        } else if (text.length > 0) {
            pages = 1;
        } else {
            pages = 0;
        }
        pageCountEl.innerText = pages;
    }
}

function uncheckNoFilter() {
    const checkbox = document.getElementById('add-all-checkbox');
    if (checkbox && checkbox.checked) {
        checkbox.checked = false;
        handleAddAllCheckbox(false);
    }
}

function toggleTrackClasses(checkbox) {
    const trackId = checkbox.dataset.trackId;
    const isChecked = checkbox.checked;
    document.querySelectorAll(`.class-checkbox[data-track-id="${trackId}"]`).forEach(cb => {
        cb.checked = isChecked;
    });
    updateRecipientCount();
}

function syncTrackCheckbox(classCheckbox) {
    const trackId = classCheckbox.dataset.trackId;
    const trackCheckbox = document.querySelector(`.track-checkbox[data-track-id="${trackId}"]`);
    if (trackCheckbox) {
        const siblingClasses = document.querySelectorAll(`.class-checkbox[data-track-id="${trackId}"]`);
        const allChecked = Array.from(siblingClasses).every(cb => cb.checked);
        trackCheckbox.checked = allChecked;
    }
}

function toggleTemplateUsage(useTemplate) {
    const templateArea = document.getElementById('template-selection-area');
    const freeFormArea = document.getElementById('free-form-section');
    const templateSelector = document.getElementById('template-selector');
    const freeFormInput = document.getElementById('free-form-message');

    if (templateArea) templateArea.style.display = useTemplate ? 'block' : 'none';
    if (freeFormArea) freeFormArea.style.display = useTemplate ? 'none' : 'block';

    if (templateSelector) templateSelector.required = useTemplate;
    if (freeFormInput) freeFormInput.required = !useTemplate;
}

function clearAllRecipients() {
    if (confirm("Remove all selected recipients and filters?")) {
        clearRecipientSelections();
        updateRecipientCount();
    }
}


async function applyAiFilter(event) {
    const queryInput = document.getElementById('ai-query-input');
    const query = queryInput ? queryInput.value.trim() : "";
    if (!query) return;

    const btn = (event && event.currentTarget) || document.querySelector('button[onclick*="applyAiFilter"]');
    const originalText = btn ? btn.innerText : "";
    if (btn) {
        btn.innerText = 'AI thinking...';
        btn.disabled = true;
    }

    try {
        const response = await window.authenticatedFetch('/api/admin/query/parents', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ query: query })
        });
        const parents = await response.json();
        parents.forEach(p => addManualRecipient(p.id, p.name, p.phone, ['AI Found']));
        alert(`Added ${parents.length} recipients.`);
    } catch (err) {
        console.error("AI filter failed:", err);
        alert("AI filter failed. Please try again.");
    } finally {
        if (btn) {
            btn.innerText = originalText || 'Apply AI Magic';
            btn.disabled = false;
        }
    }
}

function selectVariable(code, label) {
    // Standard broadcast-textarea
    let textarea = document.getElementById('broadcast-textarea');
    // Also support free-form-message if that's what's active
    if (!textarea || textarea.style.display === 'none') {
        const freeForm = document.getElementById('free-form-message');
        if (freeForm && freeForm.style.display !== 'none') textarea = freeForm;
    }

    if (textarea) {
        const start = textarea.selectionStart;
        const end = textarea.selectionEnd;
        const text = textarea.value;
        textarea.value = text.substring(0, start) + `{{${code}}}` + text.substring(end);
        textarea.focus();
        textarea.setSelectionRange(start + code.length + 4, start + code.length + 4);

        // Trigger estimate update if applicable
        if (typeof updateUsageEstimate === 'function') updateUsageEstimate();
    }

    // Close any variable picker modals
    const modal = document.getElementById('variable-picker-modal');
    if (modal) modal.classList.remove('active');
}

async function submitBroadcast(event) {
    if (event) event.preventDefault();

    if (!confirm('Are you sure you want to send this broadcast message?')) return;

    const config = getBroadcastConfig();
    const btn = document.getElementById('send-broadcast-btn');
    const originalText = btn ? btn.innerText : 'Send Broadcast';

    if (btn) {
        btn.disabled = true;
        btn.innerText = 'Sending Broadcast...';
    }

    try {
        const form = document.getElementById('broadcast-form');
        const formData = new FormData(form);

        const response = await window.authenticatedFetch(config.actionUrl, {
            method: 'POST',
            body: formData
        });

        // Handle both HTML and JSON responses since some channels like internal might return JSON
        const contentType = response.headers.get("content-type");
        if (contentType && contentType.includes("application/json")) {
            const result = await response.json();
            if (result.success) {
                alert(`Successfully sent broadcast.`);
                closeBroadcastModal();
                window.location.reload();
            } else {
                alert('Broadcast failed: ' + (result.message || 'Unknown error'));
            }
        } else {
            // Assume HTML/Redirect - if it's 200 OK we can reload or show success
            if (response.ok) {
                alert('Broadcast sent successfully!');
                closeBroadcastModal();
                window.location.reload();
            } else {
                alert('Broadcast submission failed. Status: ' + response.status);
            }
        }
    } catch (err) {
        console.error("Broadcast submission failed:", err);
        alert('Connection error while sending broadcast.');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerText = originalText;
        }
    }
}

async function sendTestBroadcast(event) {
    const testPhone = document.getElementById('test-recipient-phone')?.value;
    if (!testPhone) {
        alert("Please enter a test phone number.");
        return;
    }

    const btn = event.currentTarget;
    const originalText = btn.innerText;
    btn.innerText = "Sending...";
    btn.disabled = true;

    const config = getBroadcastConfig();
    const form = document.getElementById('broadcast-form');
    const formData = new FormData(form);
    formData.append("testPhone", testPhone);

    try {
        const response = await window.authenticatedFetch(`${config.actionUrl}/test`, {
            method: 'POST',
            body: formData
        });
        const result = await response.json();
        if (result.success) {
            alert("Test broadcast sent successfully to " + testPhone);
        } else {
            alert("Failed to send test broadcast: " + (result.error || result.message || "Unknown error"));
        }
    } catch (e) {
        console.error("Test send error:", e);
        alert("Error sending test broadcast.");
    } finally {
        btn.innerText = originalText;
        btn.disabled = false;
    }
}
