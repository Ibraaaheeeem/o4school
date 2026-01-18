# FileCountLimitExceededException - Final Solution

## Problem Summary
When uploading a passport photograph while editing a student, the application throws:
```
org.apache.tomcat.util.http.fileupload.impl.FileCountLimitExceededException: attachment
```

## Root Cause
Tomcat 11 (used in Spring Boot 4.0) has very strict limits on multipart file uploads. The default `maxFileCount` is very low, and when a form with many fields + a file upload is submitted, it exceeds this limit.

## Attempted Solutions

### ❌ Attempt 1: Tomcat Configuration Class
**Problem**: Spring Boot 4.0 changed the package structure for embedded Tomcat classes. The `org.springframework.boot.web.embedded.tomcat` package is not accessible even with the `spring-boot-starter-tomcat` dependency.

### ❌ Attempt 2: Application Properties Only
**Problem**: There's no direct Spring Boot property to set `maxFileCount` in Tomcat 11.

### ⚠️ Attempt 3: System Properties (Current)
**Status**: Implemented but may not work as Tomcat might not read these properties.

## Current Configuration

### Files Modified:
1. **build.gradle**: Added `spring-boot-starter-tomcat` dependency
2. **application.properties**: Increased `max-request-size` to 10MB
3. **TomcatPropertiesConfig.kt**: Sets system properties (may not be effective)

## Alternative Solution (Recommended)

Since the Tomcat configuration is proving difficult in Spring Boot 4.0, here's an alternative approach:

### Option A: Use Standard Servlet MultipartConfig
Create a `web.xml` or use `@MultipartConfig` annotation on a servlet.

### Option B: Simplify the Form
The issue might be that HTMX is sending the form in a way that creates too many parts. We could:
1. Remove `enctype="multipart/form-data"` from the form
2. Handle file upload separately via AJAX
3. Use a two-step process: save student data first, then upload photo

### Option C: Use Spring's CommonsMultipartResolver
Switch from the default multipart resolver to Apache Commons FileUpload, which has different limits.

## Recommended Next Steps

### Step 1: Test Current Configuration
Run the application and try uploading a passport photo:
```bash
./gradlew bootRun
```

Watch the console for the message:
```
✓ Tomcat multipart properties configured
```

### Step 2: If Still Failing
We should implement **Option B** - separate the file upload from the form submission:

1. Submit the form without the file first
2. Then upload the file separately via AJAX
3. This avoids the multipart complexity

### Step 3: Alternative - Use Apache Commons FileUpload
Add dependency and configure:
```gradle
implementation 'commons-fileupload:commons-fileupload:1.5'
```

Then create a bean:
```kotlin
@Bean
fun multipartResolver(): CommonsMultipartResolver {
    val resolver = CommonsMultipartResolver()
    resolver.setMaxUploadSize(10485760) // 10MB
    resolver.setMaxInMemorySize(1048576) // 1MB
    return resolver
}
```

## Testing
1. Start the application
2. Edit a student
3. Try uploading a passport photo
4. Check the logs for errors

If the error persists, we'll need to implement one of the alternative solutions above.
