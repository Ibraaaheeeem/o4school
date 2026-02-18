// Global Modal Functions
(function () {
    'use strict';

    // Define modal functions globally
    window.openModal = function (modalId) {
        console.log('Opening modal:', modalId);
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.add('active');
            console.log('Modal opened successfully');

            // Add click outside to close
            modal.addEventListener('click', function (e) {
                if (e.target === modal) {
                    closeModal(modalId);
                }
            });

            // Add escape key to close
            document.addEventListener('keydown', function escapeHandler(e) {
                if (e.key === 'Escape') {
                    closeModal(modalId);
                    document.removeEventListener('keydown', escapeHandler);
                }
            });
        } else {
            console.error('Modal not found:', modalId);
        }
    };

    window.closeModal = function (modalId) {
        console.log('Closing modal:', modalId);
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.remove('active');
            // If it's the specific community context, also restore scrolling if needed
            document.body.classList.remove('modal-open');
        }
    };

    window.closeAllModals = function () {
        console.log('Closing all possible modals');
        const modals = [
            'staffModal', 'studentModal', 'parentModal',
            'staffAssignmentModal', 'deleteModal', 'studentClassModal',
            'parentStudentModal'
        ];
        modals.forEach(id => {
            const modal = document.getElementById(id);
            if (modal) {
                modal.classList.remove('active');
            }
        });
        document.body.classList.remove('modal-open');

        // Dispatch refresh event for lists that might need it
        document.body.dispatchEvent(new CustomEvent('refreshCommunityList'));
    };

    // Initialize when DOM is ready
    document.addEventListener('DOMContentLoaded', function () {
        console.log('Modal functions initialized');
    });
})();