# Multi-User Type Authentication & School Role Testing

Complete Postman collection for testing authentication with multiple user types and school role management.

## 📋 Test Structure

### 1. **Sign Up Tests** (4 user types)
- `Sign Up - SCHOOL_ADMIN`: Create admin account (no school_slug)
- `Sign Up - PARENT`: Create parent with demo-hsc school
- `Sign Up - STAFF`: Create staff with demo-hsc school  
- `Sign Up - STUDENT`: Create student with demo-hsc school

**Environment variables captured:**
- `admin_user_id`, `admin_email`, `admin_verification_token`
- `parent_user_id`, `parent_email`, `parent_verification_token`
- `staff_user_id`, `staff_email`, `staff_verification_token`
- `student_user_id`, `student_email`, `student_verification_token`

---

### 2. **Email Verification Tests**
One verification test for each user type, using captured tokens.

---

### 3. **Account Activation Tests**
One activation test for each user type.

---

### 4. **User School Role Management**
Tests for creating school roles:

#### Initial Role Creation (3 tests)
- `Create User School Role - PARENT`: Assign PARENT to demo-hsc
- `Create User School Role - STAFF`: Assign STAFF to demo-hsc
- `Create User School Role - STUDENT`: Assign STUDENT to demo-hsc

#### Additional Role Creation (1 test)
- `Create Additional Role - PARENT (Second School)`: Add same user to different school
  - Should succeed (same user, different school)
  - Captures `parent_school_role_id`

---

### 5. **Duplicate Prevention Tests**
Verify that duplicate roles are properly rejected:

#### Duplicate Prevention (3 tests)
- `Attempt Duplicate - PARENT in demo-hsc`: 
  - ❌ Should return **409 Conflict**
  - Prevents: Same user + same school + same role

- `Attempt Duplicate - STAFF in demo-hsc`:
  - ❌ Should return **409 Conflict**

- `Attempt Duplicate - STUDENT in demo-hsc`:
  - ❌ Should return **409 Conflict**

**Expected Behavior:**
```
User + School + Role combination = Unique
- ✅ PARENT + demo-hsc + PARENT (first time)
- ✅ PARENT + another-school + PARENT (different school)
- ❌ PARENT + demo-hsc + PARENT (duplicate - 409)
```

---

### 6. **Query & Utility Endpoints**
- `Get User by ID`: Retrieve user details
- `Get User School Roles`: List all school roles for a user

---

## 🚀 How to Use

### Step 1: Import Collection
1. Open Postman
2. Click **File** → **Import**
3. Select: `postman_collection_multi_user_types.json`
4. Click **Import**

### Step 2: Create Environment
Create a new Postman environment `4School-Multi-User` with:
```
base_url: http://localhost:8080
```

### Step 3: Run Tests in Order

#### Phase 1: Sign Up All Users
Run in sequence:
1. `Sign Up - SCHOOL_ADMIN`
2. `Sign Up - PARENT`
3. `Sign Up - STAFF`
4. `Sign Up - STUDENT`

**Check Postman Console** (Cmd+Alt+C) to see captured IDs.

#### Phase 2: Verify Emails
Run all 4 verify email tests (order doesn't matter).

#### Phase 3: Activate Accounts  
Run all 4 activate tests (order doesn't matter).

#### Phase 4: Create School Roles
Run in sequence:
1. `Create User School Role - PARENT`
2. `Create User School Role - STAFF`
3. `Create User School Role - STUDENT`
4. `Create Additional Role - PARENT (Second School)`

**Expected**: All should return **201 Created**

#### Phase 5: Test Duplicate Prevention
Run all 3 duplicate tests (order doesn't matter).

**Expected**: All should return **409 Conflict**

---

## 📊 Request/Response Details

### Sign Up Request Body
```json
{
  "email": "parent{{$timestamp}}@example.com",
  "password": "SecurePassword123",
  "first_name": "Parent",
  "last_name": "User",
  "phone_number": "+1{{$randomInt}}",
  "user_type": "PARENT",
  "school_slug": "demo-hsc"
}
```

**Fields:**
- `email`: Unique, generated with {{$timestamp}}
- `password`: Minimum 8 characters
- `first_name`, `last_name`: Required
- `phone_number`: Generated randomly, must be unique
- `user_type`: `SCHOOL_ADMIN`, `PARENT`, `STAFF`, `STUDENT`
- `school_slug`: Required for non-SCHOOL_ADMIN users
  - Use `"demo-hsc"` for all non-admin users

### User School Role Request Body
```json
{
  "user_id": "{{parent_user_id}}",
  "school_id": "DEMO_HSC",
  "role": "PARENT",
  "status": "ACTIVE"
}
```

**Constraints:**
- `user_id` + `school_id` + `role` must be unique
- Cannot create duplicate combinations
- Duplicate attempts return 409 Conflict

---

## 📈 Test Results Interpretation

### ✅ Success Scenarios
```
Sign Up (Parent):        201 Created ✓
Verify Email (Parent):   200 OK ✓
Activate (Parent):       200 OK ✓
Create Role (Parent):    201 Created ✓
Additional Role:         201 Created ✓
Duplicate Role:          409 Conflict ✓ (correctly rejected)
```

### ❌ Failure Scenarios to Watch For

| Error | Cause | Fix |
|-------|-------|-----|
| 400 Sign Up | Validation failed | Check email format, password length, required fields |
| 400 Verify Email | Wrong token or user_id | Ensure captured values are used |
| 400 Create Role | Invalid school_id or role | Use "DEMO_HSC" and valid role names |
| 409 Create Role | Duplicate combination | Expected for duplicate test - **this is correct** |
| 404 Create Role | Invalid user_id | User might not exist or email not verified |

---

## 🔍 Console Output Examples

### Expected Console Logs (Test Scripts)

**After Sign Up:**
```
✅ PARENT Sign Up Success
User ID: a78f7ce2-24f8-48fe-a2d3-cd6b7ab71732
```

**After Create Role:**
```
✅ PARENT School Role Created
Role: PARENT
```

**After Duplicate Attempt:**
```
✅ CORRECT: Duplicate role prevented (409 Conflict)
```

---

## 🛠️ Database Queries (If Needed)

Check created users:
```bash
PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
-c "SELECT id, email, user_type FROM users ORDER BY created_at DESC LIMIT 5;"
```

Check user school roles:
```bash
PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
-c "SELECT u.id, u.email, usr.role, usr.status FROM users u 
    LEFT JOIN user_school_roles usr ON u.id = usr.user_id 
    ORDER BY u.created_at DESC LIMIT 10;"
```

Check duplicate constraint:
```bash
PGPASSWORD="password" psql -h localhost -d myschool -U postgres \
-c "SELECT user_id, school_id, role, COUNT(*) FROM user_school_roles 
    GROUP BY user_id, school_id, role HAVING COUNT(*) > 1;"
```

---

## 📋 Test Checklist

- [ ] Collection imported successfully
- [ ] Environment created with `base_url`
- [ ] Phase 1: All 4 sign-ups return 201
- [ ] Phase 2: All 4 verify emails return 200
- [ ] Phase 3: All 4 activations return 200
- [ ] Phase 4: All 4 role creations return 201 (3 initial + 1 additional school)
- [ ] Phase 5: All 3 duplicate tests return 409
- [ ] Console shows ✅ for all successes
- [ ] No errors in Postman console

---

## 🎯 Key Testing Points

✅ **Multi-user type support:** Create different user types in one workflow
✅ **School slug handling:** Non-admin users require school_slug
✅ **Variable capture:** Automatic environment variable capture for all user types
✅ **Duplicate prevention:** Confirms 409 conflict on duplicate roles
✅ **Multiple school roles:** Same user can have roles in multiple schools
✅ **Role uniqueness:** Composite unique constraint (user_id + school_id + role)

---

## 📞 Troubleshooting

**No variable captures?**
- Check Postman console for test script execution
- Verify response codes are 201/200 before capture scripts run

**All users fail to sign up?**
- Check `base_url` environment variable
- Verify server is running on http://localhost:8080

**School role creation fails?**
- Ensure user is activated before creating role
- Use correct school_id format (e.g., "DEMO_HSC")
- Check if role type matches user_type

**Duplicate test doesn't return 409?**
- Backend constraint might not be implemented yet
- Verify database has unique constraint on (user_id, school_id, role)

---

## 📝 File Location
`/home/abuhaneefayn/Desktop/4school/rust/postman_collection_multi_user_types.json`
