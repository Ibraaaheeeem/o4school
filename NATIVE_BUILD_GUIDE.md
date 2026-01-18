# Spring Boot Native Image Production Guide

This guide details how to compile your 4school application into a native executable for production deployment.

## Option 1: Cloud Native Buildpacks (Recommended)
This is the easiest way to build a production-ready Docker image containing your native executable. You do not need to install GraalVM locally; you only need Docker.

### 1. Build the Image
Run the following command in your project root:
```bash
./gradlew bootBuildImage
```

To specifically build a native image (instead of a JVM image), you might need to configure the buildpack or use the native profile if configured. However, with the native plugin applied, you can often just run:

```bash
./gradlew bootBuildImage --imageName=4school-native
```
*Note: This creates a JVM image by default. To enable native image building with buildpacks, we need to set the environment variable.*

**Correct Command for Native Image:**
```bash
./gradlew bootBuildImage \
    --imageName=4school-native \
    --environment "BP_NATIVE_IMAGE=true"
```

### 2. Run the Container
```bash
docker run --rm -p 8080:8080 -e SERVER_PORT=8080 4school-native
```

---

## Option 2: Local Native Compile
Use this if you want to build the executable directly on your host machine (e.g., for testing or if you are deploying to a bare-metal Linux server).

### Prerequisites
1.  Install **GraalVM for JDK 21**.
2.  Install the `native-image` tool: `gu install native-image` (if not included).

### Build Command
```bash
./gradlew nativeCompile
```

### Output
The executable will be generated in:
`build/native/nativeCompile/4school`

### Running in Production
Upload this binary to your server and run it like any other executable:
```bash
./4school
```
*Note: The binary is OS-specific. If you build on macOS, it won't run on Linux. Use Option 1 or 3 for Linux deployments.*

---

## Option 3: Multi-Stage Dockerfile (For Custom CI/CD)
If you need a custom Dockerfile for your pipeline:

1. Create a file named `Dockerfile.native`:

```dockerfile
# Stage 1: Build the Native Image
FROM ghcr.io/graalvm/native-image-community:21-muslib AS build
WORKDIR /app
COPY . .
RUN ./gradlew nativeCompile --no-daemon

# Stage 2: Create the Runtime Image
FROM alpine:latest
WORKDIR /app
COPY --from=build /app/build/native/nativeCompile/4school .
EXPOSE 8080
ENTRYPOINT ["./4school"]
```

2. Build:
```bash
docker build -f Dockerfile.native -t 4school-native .
```

## Production Checklist
1.  **Environment Variables**: Pass DB credentials and other secrets via environment variables (e.g., `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`).
2.  **Logging**: Ensure `logback-spring.xml` is configured to output JSON (you have `logstash-logback-encoder`) for log aggregation systems.
3.  **Database**: Ensure your production database (PostgreSQL) is accessible from the container.
