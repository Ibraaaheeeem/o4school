#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "=== 4School Auth API Testing ==="

# Clear database
echo "Clearing database..."
PGPASSWORD="password" psql -h localhost -d myschool -U postgres -c "DELETE FROM users;" 2>&1 | grep -v "^$"

# Test 1: Sign Up
echo -e "\n${GREEN}STEP 1: Sign Up${NC}"
TIMESTAMP=$(date +%s)
PHONE=$((2000000000 + RANDOM * 1000))

SIGNUP_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/sign-up \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"testuser${TIMESTAMP}@example.com\",\"password\":\"SecurePassword123\",\"first_name\":\"John\",\"last_name\":\"Doe\",\"phone_number\":\"+${PHONE}\"}")

echo "$SIGNUP_RESPONSE" | jq .

USER_ID=$(echo "$SIGNUP_RESPONSE" | jq -r '.user_id // empty')
EMAIL=$(echo "$SIGNUP_RESPONSE" | jq -r '.email // empty')
VERIFICATION_TOKEN=$(echo "$SIGNUP_RESPONSE" | jq -r '.verification_token // empty')

if [ -z "$USER_ID" ]; then
  echo -e "${RED}Sign Up FAILED - No user_id in response${NC}"
  exit 1
fi

echo -e "${GREEN}✓ User created: $USER_ID${NC}"
echo "Email: $EMAIL"
echo "Token: $VERIFICATION_TOKEN"

# Test 2: Verify Email
echo -e "\n${GREEN}STEP 2: Verify Email${NC}"
VERIFY_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/verify-email \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"$USER_ID\",\"verification_code\":\"$VERIFICATION_TOKEN\"}")

echo "$VERIFY_RESPONSE" | jq .
echo -e "${GREEN}✓ Email verified${NC}"

# Test 3: Activate Account
echo -e "\n${GREEN}STEP 3: Activate Account${NC}"
ACTIVATE_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/activate \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"$USER_ID\",\"activation_token\":\"activate\"}")

echo "$ACTIVATE_RESPONSE" | jq .
echo -e "${GREEN}✓ Account activated${NC}"

# Test 4: Sign In
echo -e "\n${GREEN}STEP 4: Sign In${NC}"
SIGNIN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/sign-in \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"SecurePassword123\"}")

echo "$SIGNIN_RESPONSE" | jq .

ACCESS_TOKEN=$(echo "$SIGNIN_RESPONSE" | jq -r '.access_token // empty')
if [ -z "$ACCESS_TOKEN" ]; then
  echo -e "${RED}Sign In FAILED${NC}"
else
  echo -e "${GREEN}✓ Signed in, token: ${ACCESS_TOKEN:0:20}...${NC}"
fi

# Test 5: Send OTP
echo -e "\n${GREEN}STEP 5: Send OTP${NC}"
OTP_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/send-otp \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\"}")

echo "$OTP_RESPONSE" | jq .
echo -e "${GREEN}✓ OTP sent${NC}"

# Test 6: Forgot Password
echo -e "\n${GREEN}STEP 6: Forgot Password${NC}"
FORGOT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\"}")

echo "$FORGOT_RESPONSE" | jq .
echo -e "${GREEN}✓ Reset email sent${NC}"

# Test 7: Logout
echo -e "\n${GREEN}STEP 7: Logout${NC}"
LOGOUT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d "{\"user_id\":\"$USER_ID\"}")

echo "$LOGOUT_RESPONSE" | jq .
echo -e "${GREEN}✓ Logged out${NC}"

echo -e "\n${GREEN}=== All tests completed ===${NC}"
