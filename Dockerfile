FROM ghcr.io/graalvm/native-image-community:21-muslib AS builder

# 2. Install xargs (provided by findutils) and build essentials
RUN microdnf update -y && microdnf install -y findutils tar gzip gcc zlib-devel

WORKDIR /app

# Cache dependencies
COPY gradle gradle
COPY gradlew .
COPY build.gradle .
COPY settings.gradle .
# Remove hardcoded java.home from gradle.properties to use container's JDK
COPY gradle.properties .
RUN sed -i '/org.gradle.java.home/d' gradle.properties
RUN sed -i 's|distributionUrl=.*|distributionUrl=file:///app/gradle/Gradle_v8.10.2.zip|' gradle/wrapper/gradle-wrapper.properties
RUN ./gradlew dependencies --no-daemon

# Build native image
COPY . .
# Remove hardcoded java.home again as COPY . . overwrites it
RUN sed -i '/org.gradle.java.home/d' gradle.properties
RUN sed -i 's|distributionUrl=.*|distributionUrl=file:///app/gradle/Gradle_v8.10.2.zip|' gradle/wrapper/gradle-wrapper.properties
RUN chmod +x gradlew
RUN ./gradlew nativeCompile --no-daemon

FROM gcr.io/distroless/static-debian12:latest
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/4school /app/4school
EXPOSE 8080
ENTRYPOINT ["/app/4school"]
