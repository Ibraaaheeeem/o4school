/**
 * Dashboard Tabs Management
 * Handles tab switching and persistence across refreshes/navigation.
 */

document.addEventListener('DOMContentLoaded', function () {
    initTabs();
});

function initTabs() {
    const tabButtons = document.querySelectorAll('.tab-btn');

    if (tabButtons.length === 0) return;

    // Restore active tab from localStorage
    const currentPath = window.location.pathname;
    const storageKey = `activeTab_${currentPath}`;
    const savedTabId = localStorage.getItem(storageKey);

    if (savedTabId) {
        const savedTabBtn = document.querySelector(`.tab-btn[data-tab-target="${savedTabId}"]`);
        if (savedTabBtn) {
            activateTab(savedTabBtn);
        }
    }

    // Add click event listeners
    tabButtons.forEach(btn => {
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            activateTab(this);

            // Save to localStorage
            const targetId = this.getAttribute('data-tab-target');
            localStorage.setItem(storageKey, targetId);
        });
    });
}

function activateTab(btn) {
    const targetId = btn.getAttribute('data-tab-target');
    const container = btn.closest('.tabs-container');

    if (!container || !targetId) return;

    // Update buttons state
    const buttons = container.querySelectorAll('.tab-btn');
    buttons.forEach(b => b.classList.remove('active'));
    btn.classList.add('active');

    // Update panes state
    // Assuming panes are siblings or identifiable by ID
    const pane = document.getElementById(targetId);
    if (pane) {
        // Find all panes that are siblings of the target pane
        const parent = pane.parentElement;
        const siblings = parent.children;

        for (let i = 0; i < siblings.length; i++) {
            if (siblings[i].classList.contains('tab-pane')) {
                siblings[i].classList.remove('active');
            }
        }

        pane.classList.add('active');
    }
}
