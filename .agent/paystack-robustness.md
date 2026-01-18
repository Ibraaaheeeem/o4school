# Improvement: Paystack Service Robustness

## Issue
The user encountered a `ResourceAccessException` ("Remote host terminated the handshake") when fetching Paystack providers. This could potentially hang the application or cause alarm due to ERROR logs.

## Fix
1.  **Timeouts**: Configured `RestTemplate` with 5-second connection and read timeouts to prevent the application from hanging indefinitely if the Paystack API is unreachable.
2.  **Log Level**: Downgraded the log level for `getAvailableProviders` failure from `ERROR` to `WARN`. This reflects that the failure is handled gracefully (falling back to default providers) and is not a critical application crash.

## Result
The application will now continue running smoothly even if Paystack is down, without hanging or flooding the logs with critical errors for this specific operation.
