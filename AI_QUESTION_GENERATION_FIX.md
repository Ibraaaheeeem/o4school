# AI Question Generation Error - Fix & Troubleshooting

## Problem
Users get: **"An error occurred while generating questions"** in production with no useful logs.

## Root Cause
- Production was configured to use **DeepSeek** AI provider by default
- The `DEEPSEEK_API_KEY` environment variable was **NOT** set in Docker Compose
- Production logging was set to `WARN` level, hiding the error details

## Solution Applied ✅

### 1. **Switched to Gemini** (Recommended)
- Updated `deployment/docker-compose.yml` to use Gemini instead of DeepSeek
- Gemini API key is already configured in the environment
- Added `AI_PROVIDER=gemini` to environment variables

### 2. **Improved Error Logging**
- Added specific error messages when API keys are missing
- Enabled `INFO` level logging for `AiService` in production
- Added provider info logging to track which AI service is being used

### 3. **Files Modified**
- ✅ `deployment/docker-compose.yml` - Set AI_PROVIDER=gemini
- ✅ `core/src/main/kotlin/com/haneef/_school/service/AiService.kt` - Enhanced logging
- ✅ `webapp/src/main/resources/application-prod.properties` - Explicit AiService logging

## Verification Steps

### Check Current Configuration
```bash
# SSH into production server
ssh root@vmi3141028

# Check if GEMINI_API_KEY is set
echo $GEMINI_API_KEY

# View docker-compose configuration
cd /home/o4school/deployment
grep -A 20 "environment:" docker-compose.yml | grep -E "(AI_PROVIDER|GEMINI|DEEPSEEK)"
```

### View Logs in Production
```bash
# Follow application logs
docker compose logs -f app | grep -i "ai\|question\|gemini"

# Search for errors
docker compose logs app | grep -i "error"
```

### Test Question Generation
1. Go to **Staff Dashboard → Examinations → Questions**
2. Click **"Generate with AI"**
3. Select topics and click **"Generate"**
4. Check server logs for detailed error messages

## Troubleshooting

### If Still Getting Error:

#### 1. **Check API Key is Set**
```bash
# SSH to server
docker exec 4school_app env | grep GEMINI_API_KEY
```

#### 2. **Verify Provider is Set to Gemini**
```bash
docker exec 4school_app env | grep AI_PROVIDER
# Should output: AI_PROVIDER=gemini
```

#### 3. **Restart Application**
```bash
cd /home/o4school/deployment
docker compose restart app
```

#### 4. **Check Detailed Logs**
```bash
docker compose logs app | tail -100
# Look for messages like:
# "Starting AI question generation with provider: gemini"
# or error messages with "❌ CRITICAL:"
```

## Alternative: Use DeepSeek

If you prefer to use DeepSeek instead, add to `.env` file:
```bash
DEEPSEEK_API_KEY=your-deepseek-api-key-here
AI_PROVIDER=deepseek
```

## Performance Notes

- **Gemini**: Faster responses, 5-minute timeout
- **DeepSeek**: Slower but more detailed reasoning, useful for complex questions

## Rollback (if needed)

To revert to DeepSeek:
```bash
# Update docker-compose.yml
# Change:
# - AI_PROVIDER=gemini
# - GEMINI_API_KEY=${GEMINI_API_KEY}
# To:
# - AI_PROVIDER=deepseek
# - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}

docker compose up -d app
```

## Quick Diagnostic Command
```bash
docker compose logs app 2>&1 | grep -E "AI question generation|❌ CRITICAL|Error calling" | head -20
```

---

**Last Updated**: April 17, 2026  
**Status**: ✅ Fixed
