# Improvement: Pessimistic UI for Optional Fees

## Goal
The user requested that the optional fee checkbox only change state if the server call is successful, and show an error otherwise.

## Implementation
1.  **Prevent Default Toggle**: Added `onclick="event.preventDefault()"` to the checkbox. This prevents the browser from toggling the checkbox immediately upon click.
2.  **Send Intended State**: Updated `hx-vals` to send `optedIn: !event.target.checked`. Since the checkbox state hasn't changed (due to preventDefault), we need to send the *opposite* of the current state to indicate the user's intent.
3.  **Server Response**:
    *   **Success**: The server returns the updated HTML fragment with the new state (checked/unchecked). HTMX swaps it, updating the UI.
    *   **Error**: The server returns the HTML fragment with the *original* state (since DB wasn't updated) and an error message. The UI reflects this (no change in checkbox, error shown).

## Result
The checkbox will not "flash" or change state until the server confirms the action. If an error occurs, the user sees an error message and the checkbox remains in its original state.
