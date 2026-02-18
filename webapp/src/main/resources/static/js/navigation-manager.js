/**
 * Navigation Manager for Thymeleaf + HTMX Applications
 * Manages browser history to prevent backstack pollution from content replacements
 */

class NavigationManager {
    constructor() {
        this.currentPageUrl = window.location.href;
        this.isContentReplacement = false;
        this.lastFullPageNavigation = window.location.href;
        this.contentReplacementUrls = new Set();

        this.init();
    }

    init() {
        // Listen for HTMX events
        document.addEventListener('htmx:beforeRequest', (event) => {
            this.handleBeforeRequest(event);
        });

        document.addEventListener('htmx:afterRequest', (event) => {
            this.handleAfterRequest(event);
        });

        document.addEventListener('htmx:pushedIntoHistory', (event) => {
            this.handlePushedIntoHistory(event);
        });

        // Listen for popstate (back/forward button)
        window.addEventListener('popstate', (event) => {
            this.handlePopState(event);
        });

        // Store initial page state
        this.storePageState();

        // Setup global click interceptor for non-HTMX links
        this.setupGlobalInterceptors();
    }

    /**
     * Setup global click interceptors to manage history for standard links
     */
    setupGlobalInterceptors() {
        document.addEventListener('click', (event) => {
            const link = event.target.closest('a');

            // Skip if not a link, or has special attributes/keys
            if (!link || !link.href || link.getAttribute('target') ||
                event.ctrlKey || event.metaKey || event.shiftKey ||
                link.hasAttribute('hx-get') || link.hasAttribute('hx-post') ||
                link.classList.contains('no-replace')) {
                return;
            }

            try {
                const url = new URL(link.href, window.location.origin);
                const currentUrl = new URL(window.location.href);

                // Only handle internal links
                if (url.origin !== currentUrl.origin) return;

                // Special handling for community section to prevent backstack pollution
                // If we are navigating within the community section, use location.replace
                if (url.pathname.startsWith('/admin/community') &&
                    currentUrl.pathname.startsWith('/admin/community')) {

                    // If navigating to a different sub-route, replace history entry
                    if (url.pathname !== currentUrl.pathname || url.search !== currentUrl.search) {
                        event.preventDefault();
                        window.location.replace(link.href);
                    }
                }
            } catch (e) {
                console.error('Navigation interceptor error:', e);
            }
        });
    }

    /**
     * Make a secure HTMX request with proper CSRF handling
     */
    makeSecureRequest(method, url, options = {}) {
        // Ensure CSRF token is included for POST requests
        if (method.toUpperCase() === 'POST') {
            const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';

            options.headers = options.headers || {};
            if (csrfToken) {
                options.headers[csrfHeader] = csrfToken;
            }
        }

        return htmx.ajax(method, url, options);
    }

    /**
     * Determines if a request should be treated as content replacement
     */
    isContentReplacementRequest(element, url) {
        // Check for specific attributes that indicate content replacement
        if (element.hasAttribute('hx-replace-content')) {
            return true;
        }

        // Check for specific target containers that indicate partial updates
        const target = element.getAttribute('hx-target');
        if (target) {
            const contentContainers = [
                '#main-content',
                '#content-area',
                '#page-content',
                '.content-container',
                '[data-content-area]',
                '#student-cards-container',
                '#staff-table-container',
                '#parent-table-container'
            ];

            if (contentContainers.some(selector => target.includes(selector.replace('#', '').replace('.', '')))) {
                return true;
            }
        }

        // Check URL patterns that typically indicate content replacement
        const contentReplacementPatterns = [
            /\/filter$/,
            /\/search$/,
            /\/page\/\d+$/,
            /\?.*page=/,
            /\?.*search=/,
            /\?.*filter=/,
            /\/htmx\//
        ];

        return contentReplacementPatterns.some(pattern => pattern.test(url));
    }

    /**
     * Determines if navigation should create a new history entry
     */
    shouldCreateHistoryEntry(url, element) {
        // Always create history for full page navigations
        if (!element || !element.hasAttribute('hx-get') && !element.hasAttribute('hx-post')) {
            return true;
        }

        // Don't create history for modal operations
        if (element.closest('.modal') || element.hasAttribute('hx-target') &&
            element.getAttribute('hx-target').includes('modal')) {
            return false;
        }

        // Don't create history for form submissions that replace content
        if (element.hasAttribute('hx-post') && this.isContentReplacementRequest(element, url)) {
            return false;
        }

        // Create history for navigation between different sections
        const currentSection = this.getCurrentSection(this.currentPageUrl);
        const newSection = this.getCurrentSection(url);

        return currentSection !== newSection;
    }

    /**
     * Gets the current section from URL
     */
    getCurrentSection(url) {
        const path = new URL(url, window.location.origin).pathname;
        const sections = [
            'dashboard',
            'community',
            'financial',
            'academic',
            'school-setup',
            'assessments',
            'system-admin'
        ];

        for (const section of sections) {
            if (path.includes(`/${section}`)) {
                return section;
            }
        }
        return 'unknown';
    }

    /**
     * Handle before HTMX request
     */
    handleBeforeRequest(event) {
        const element = event.detail.elt;
        const url = event.detail.requestConfig.url;

        this.isContentReplacement = this.isContentReplacementRequest(element, url);

        // If this is content replacement, prevent HTMX from pushing to history
        if (this.isContentReplacement) {
            // Add hx-push-url="false" to prevent history update
            element.setAttribute('hx-push-url', 'false');
        }
    }

    /**
     * Handle after HTMX request
     */
    handleAfterRequest(event) {
        const element = event.detail.elt;
        const url = event.detail.requestConfig.url;

        if (this.isContentReplacement) {
            // Replace current history entry instead of pushing new one
            this.replaceCurrentHistoryEntry(url);
            this.contentReplacementUrls.add(url);
        } else {
            // This is a new page navigation
            this.lastFullPageNavigation = url;
            this.currentPageUrl = url;
            this.storePageState();
        }

        // Clean up
        this.isContentReplacement = false;
        if (element.hasAttribute('hx-push-url') && element.getAttribute('hx-push-url') === 'false') {
            element.removeAttribute('hx-push-url');
        }
    }

    /**
     * Handle HTMX history push
     */
    handlePushedIntoHistory(event) {
        // If we determined this should be content replacement, 
        // replace the history entry instead of pushing
        if (this.isContentReplacement) {
            event.preventDefault();
            this.replaceCurrentHistoryEntry(event.detail.path);
        }
    }

    /**
     * Handle browser back/forward navigation
     */
    handlePopState(event) {
        const currentUrl = window.location.href;

        // If navigating back to a content replacement URL, 
        // redirect to the last full page navigation
        if (this.contentReplacementUrls.has(currentUrl)) {
            window.history.replaceState(null, '', this.lastFullPageNavigation);
            window.location.reload();
        }
    }

    /**
     * Replace current history entry
     */
    replaceCurrentHistoryEntry(url) {
        const state = {
            url: url,
            timestamp: Date.now(),
            isContentReplacement: true,
            parentPage: this.lastFullPageNavigation
        };

        window.history.replaceState(state, '', url);
    }

    /**
     * Store current page state
     */
    storePageState() {
        const state = {
            url: this.currentPageUrl,
            timestamp: Date.now(),
            isContentReplacement: false
        };

        window.history.replaceState(state, '', this.currentPageUrl);
    }

    /**
     * Manually mark next navigation as content replacement
     */
    markNextAsContentReplacement() {
        this.isContentReplacement = true;
    }

    /**
     * Manually mark next navigation as new page
     */
    markNextAsNewPage() {
        this.isContentReplacement = false;
    }
}

// Initialize navigation manager
let navigationManager;
document.addEventListener('DOMContentLoaded', function () {
    navigationManager = new NavigationManager();

    // Make it globally available
    window.navigationManager = navigationManager;
});

// Utility functions for manual control
window.markAsContentReplacement = function () {
    if (window.navigationManager) {
        window.navigationManager.markNextAsContentReplacement();
    }
};

window.markAsNewPage = function () {
    if (window.navigationManager) {
        window.navigationManager.markNextAsNewPage();
    }
};