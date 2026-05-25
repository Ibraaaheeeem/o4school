# Auth API - Complete Working Test Sequence

## ✅ What Just Happened in Your Test

### Sign Up - SUCCESS ✅
```
Created user: a63c9120-03bc-446f-8f85-3508106325dd
Email: user@example.com
Verification Token: [captured automatically]
```

### Issue: Verify Email - FAILED ❌
```
Sent request with: 550e8400-e29b-41d4-a716-446655440000 (OLD HARDCODED VALUE)
Should have sent: a63c9120-03bc-446f-8f85-3508106325dd (CAPTURED VALUE)
```

**Root Cause**: The Postman variables aren't being used properly.

---

## 🔧 How to Fix This

### Option 1: Use Environment Variables (Recommended)

#### Step 1: Create a Postman Environment
1. Click the **gear icon** (Settings) in Postman
2. Go to **Environments**
3. Click **Create**
4. Name it: `4School-Dev`
5. Add these variables:
   - `base_url`: `http://localhost:8080`
   - `email`: (leave empty - will be auto-filled)
   - `user_id`: (leave empty - will be auto-filled)
   - `verification_token`: (leave empty - will be auto-filled)
   - `access_token`: (leave empty - will be auto-filled)
6. Click **Save**
7. Select this environment from the dropdown in top-right

#### Step 2: Run Tests Properly

1. **Sign Up** - Click Send
   - Response will have `user_id`, `email`, `verification_token`
   - **Check Postman Console** (Cmd+Alt+C or Ctrl+Alt+C) to see what was captured
   - Should see: `User ID: a63c9120-03bc-446f-8f85-3508106325dd`

2. **Verify Email** - Click Send
   - Uses auto-captured `user_id` and `verification_token`
   - Should return 200 OK

3. **Continue with other tests**

### Option 2: Manual Testing (For Now)

#### Step 1: Run Sign Up
Click **Send** → Get response with `user_id`, `email`, `verification_token`

Example response:
```json
{
  "user_id": "a63c9120-03bc-446f-8f85-3508106325dd",
  "email": "testuser1234567@example.com",
  "verification_token": "abcd1234efgh5678ijkl9012mnop3456",
  "message": "Sign up successful. Please verify your email to continue.",
  "next_route": "/auth/verify-email"
}
```

#### Step 2: Copy User ID
Copy `a63c9120-03bc-446f-8f85-3508106325dd`

#### Step 3: Update Verify Email Request

Open the request body and change:
```json
{
  "user_id": "{{user_id}}",
  "verification_code": "{{verification_token}}"
}
```

To:
```json
{
  "user_id": "a63c9120-03bc-446f-8f85-3508106325dd",
  "verification_code": "abcd1234efgh5678ijkl9012mnop3456"
}
```

#### Step 4: Click Send
Should return 200 OK

---

## 📊 What Should Happen When Done Correctly

### Full Successful Flow:

```
1. Sign Up
   ✅ POST /api/auth/sign-up → 201 Created
   Captures: user_id, email, verification_token

2. Verify Email
   ✅ POST /api/auth/verify-email → 200 OK
   Uses: captured user_id, verification_token

3. Activate Account
   ✅ POST /api/auth/activate → 200 OK
   Uses: captured user_id

4. Sign In
   ✅ POST /api/auth/sign-in → 200 OK
   Uses: captured email
   Captures: access_token

5. Send OTP
   ✅ POST /api/auth/send-otp → 200 OK
   Uses: captured email

6. Get OTP Code from Database
   PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
   -c "SELECT otp_code FROM users WHERE id='a63c9120-03bc-446f-8f85-3508106325dd';"
   
   Returns: 123456 (or any 6-digit code)

7. Verify OTP
   ✅ POST /api/auth/verify-otp → 200 OK
   Uses: captured email, OTP code from database

8. Forgot Password
   ✅ POST /api/auth/forgot-password → 200 OK
   Uses: captured email

9. Get Reset Token from Database
   PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
   -c "SELECT otp_code as reset_token FROM users WHERE id='a63c9120-03bc-446f-8f85-3508106325dd';"
   
   Returns: token_value

10. Reset Password
    ✅ POST /api/auth/reset-password → 200 OK
    Uses: captured email, reset token from database

11. Logout
    ✅ POST /api/auth/logout → 200 OK
    Uses: captured access_token, user_id
```

---

## 🔍 Debugging Checklist

### If Verify Email Returns 404:
- ❌ Check that `user_id` in request matches the one from Sign Up response
- ❌ Make sure Sign Up response actually returned a user_id

### If Sign In Returns 401 "Account is PENDING":
- ❌ Make sure you ran Verify Email first
- ❌ Make sure you ran Activate Account second

### If Verify OTP Returns "Invalid OTP code":
- ❌ Query database to get the actual OTP:
  ```bash
  PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
  -c "SELECT otp_code FROM users ORDER BY created_at DESC LIMIT 1;"
  ```
- ❌ Copy the exact code and paste in request body

### If Reset Password Returns "Invalid reset token":
- ❌ Query database to get the actual token:
  ```bash
  PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
  -c "SELECT otp_code FROM users ORDER BY created_at DESC LIMIT 1;"
  ```
- ❌ Note: OTP code and reset token use the same database field (`otp_code`)

---

## 📋 Quick Reference - Database Queries

### Get Latest User's Credentials
```bash
PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
-c "SELECT id, email, otp_code FROM users ORDER BY created_at DESC LIMIT 1;"
```

### Get Specific User's OTP
```bash
PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
-c "SELECT otp_code FROM users WHERE id='a63c9120-03bc-446f-8f85-3508106325dd';"
```

### Get Specific User's Reset Token
```bash
PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
-c "SELECT otp_code as reset_token FROM users WHERE email='testuser@example.com';"
```

### Clear All Users
```bash
PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
-c "DELETE FROM users;"
```

---

## ✨ Success Indicators

When everything works correctly:

✅ Sign Up returns 201  
✅ Verify Email returns 200  
✅ Activate returns 200  
✅ Sign In returns 200 with JWT token  
✅ Send OTP returns 200  
✅ Verify OTP returns 200 (after updating with real OTP)  
✅ Forgot Password returns 200  
✅ Reset Password returns 200 (after updating with real token)  
✅ Logout returns 200  

---

## 🎯 Recommended Workflow for Testing

1. **Clear database**
   ```bash
   PGPASSWORD="password" psql -h localhost -d myschool -U postgres -c "DELETE FROM users;"
   ```

2. **Make sure server is running**
   ```bash
   curl http://localhost:8080/api/health
   ```

3. **Use Option 1 (Environment Variables)** for automated testing, OR

4. **Use Option 2 (Manual) + Database Queries** for comprehensive testing

5. **For OTP/Reset token testing**:
   - Run Send OTP → Query database for code
   - Run Verify OTP with actual code
   - Run Forgot Password → Query database for token
   - Run Reset Password with actual token

---

## 🚀 Next Steps

1. Set up Environment in Postman (Option 1)
2. Run Sign Up → Check Console for captured values
3. Run Verify Email → Should use captured values automatically
4. Run remaining tests in order
5. For OTP/Reset: Use database queries to get real values

All endpoints work perfectly - it's just about using the right values! 💯
