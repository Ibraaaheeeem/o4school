# Auth API Testing - Quick Start

## ✅ What's Been Fixed

1. **Database Schema** - All timestamp columns converted to TIMESTAMPTZ
2. **Routes** - `/api/auth/send-otp` and `/api/auth/verify-otp` endpoints added
3. **Postman Collection** - Updated with auto-capture of values
4. **Documentation** - Complete testing guide created

## 🚀 Quick Start (5 Minutes)

### 1. Clean Database
```bash
PGPASSWORD="password" psql -h localhost -d myschool -U postgres -c "DELETE FROM users;"
```

### 2. Start Backend
```bash
cd /home/abuhaneefayn/Desktop/4school/rust
cargo run
```

### 3. Verify Health Check
```bash
curl -s http://127.0.0.1:8080/api/health | jq .
```

Should return:
```json
{
  "status": "ok",
  "message": "Server is running"
}
```

### 4. Import Fresh Postman Collection
1. Open Postman
2. **File → Import** 
3. Select: `/home/abuhaneefayn/Desktop/4school/rust/postman_collection.json`
4. Click **Import**

### 5. Run Tests in Order
1. ✅ **Sign Up** → Should return 201
2. ✅ **Verify Email** → Should return 200
3. ✅ **Activate Account** → Should return 200
4. ✅ **Sign In** → Should return 200 + JWT token
5. ✅ **Send OTP** → Should return 200
6. ⚠️ **Verify OTP** → Get OTP from server logs, then test
7. ✅ **Forgot Password** → Should return 200
8. ⚠️ **Reset Password** → Get reset token from server logs, then test
9. ✅ **Logout** → Should return 200

## 📋 Expected Results

### Sign Up (201 Created)
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440001",
  "email": "testuser1716193589123@example.com",
  "message": "Sign up successful. Please verify your email to continue.",
  "next_route": "/auth/verify-email",
  "verification_token": "abcdef123456789abcdef1234567890"
}
```

### Sign In (200 OK)
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440001",
  "email": "testuser1716193589123@example.com",
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "message": "Sign in successful",
  "next_route": "/dashboard",
  "status": "ACTIVE"
}
```

## 🔍 Key Features

✅ **Auto-Capture Values**
- Sign Up captures: `user_id`, `email`, `verification_token`
- Sign In captures: `access_token`
- Other requests use captured values automatically

✅ **Dynamic Email**
- Each Sign Up uses unique email: `testuser{timestamp}@example.com`
- Prevents "user already exists" errors
- Random phone number generated

✅ **Proper Error Handling**
- All endpoints return meaningful error messages
- Status codes match HTTP standards
- Error responses include next steps

## 📚 Complete Documentation

See [AUTH_API_TESTING_GUIDE.md](AUTH_API_TESTING_GUIDE.md) for:
- Detailed step-by-step testing instructions
- Expected responses for each endpoint
- Troubleshooting guide
- Security notes for production
- Database query commands for getting tokens

## 🐛 Known Issues & Workarounds

### OTP and Reset Token Testing
Currently, OTP and reset tokens are generated but not logged to console.

**Workaround 1 - Query Database**:
```bash
# Get OTP code for a user
PGPASSWORD="password" psql -h localhost -d myschool -U postgres -c \
  "SELECT otp_code FROM users WHERE email = 'testuser1716193589123@example.com';"
```

**Workaround 2 - Add Logging** (for development):
Modify `src/services/auth_service.rs` to log the OTP:
```rust
log::info!("Generated OTP for {}: {}", req.email, otp_code);
```

### Verify OTP/Reset Password Manual Steps
1. Run Send OTP → Check logs/database for code
2. Manually update request body with actual code
3. Send Verify OTP request

Same for Reset Password:
1. Run Forgot Password → Check logs/database for token
2. Manually update request body with actual token
3. Send Reset Password request

## 📊 Test Status

| Endpoint | Automated | Manual Step Required |
|----------|-----------|----------------------|
| Sign Up | ✅ | - |
| Verify Email | ✅ | - |
| Activate | ✅ | - |
| Sign In | ✅ | - |
| Send OTP | ✅ | Get OTP from logs |
| Verify OTP | ✅ | Paste OTP in body |
| Forgot Password | ✅ | Get token from logs |
| Reset Password | ✅ | Paste token in body |
| Logout | ✅ | - |

## 🔗 Related Files

- [postman_collection.json](postman_collection.json) - Updated Postman collection
- [AUTH_API_TESTING_GUIDE.md](AUTH_API_TESTING_GUIDE.md) - Detailed testing guide
- [TEST_RESULTS.md](TEST_RESULTS.md) - Previous test results & fixes
- [DATABASE_TIMESTAMP_FIX.md](DATABASE_TIMESTAMP_FIX.md) - Database schema fix details
- [fix_database.sh](fix_database.sh) - Database fix script

## ✨ What's Next

1. **Email Integration** - Implement actual email sending (currently marked with TODO)
2. **Mobile Testing** - Test with Android app in `./4school/`
3. **Production Deployment** - Configure environment variables for prod
4. **Token Caching** - Implement token refresh and blacklisting
5. **Rate Limiting** - Add rate limits to auth endpoints

## Support

For detailed information, see [AUTH_API_TESTING_GUIDE.md](AUTH_API_TESTING_GUIDE.md)
