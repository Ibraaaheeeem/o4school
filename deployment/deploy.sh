#!/bin/bash

# Configuration
IMAGE_NAME="ghcr.io/ibraaaheeeem/o4school"
STAGING_PORT=8081
PROD_PORT=8080
HEALTH_CHECK_URL="http://localhost:$STAGING_PORT/auth/login"

# Navigate to project root (one level up from deployment folder)
PROJECT_ROOT="$(dirname "$0")/.."
cd "$PROJECT_ROOT" || exit 1

# 1. Get version
VERSION=${1:-latest}
echo "🚀 Preparing deployment for version: $VERSION"

# 2. Clean build the application
echo "🔨 Building application with clean build..."
./gradlew clean build -x test
if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

# 3. Build Docker image locally with no-cache
echo "🐳 Building Docker image locally (no cache)..."
docker build --no-cache -t $IMAGE_NAME:$VERSION .
if [ $? -ne 0 ]; then
    echo "❌ Docker build failed"
    exit 1
fi

# Navigate back to deployment folder for docker-compose
cd "$(dirname "$0")"

# 2. Ensure network and database are running
echo "🗄️ Ensuring database and network are ready..."
docker compose up -d db

# 3. Start Staging Container
echo "🧪 Starting Staging container on port $STAGING_PORT..."
# Remove any existing staging container
docker stop 4school_staging 2>/dev/null || true
docker rm 4school_staging 2>/dev/null || true

# We use the existing docker-compose environment variables if possible, 
# but for a standalone staging test, we can use docker run.
# To ensure it has all DB links, we run it on the same network.
docker run -d \
  --name 4school_staging \
  --network deployment_school_network \
  -p $STAGING_PORT:8080 \
  --env-file .env \
  -e DB_URL=jdbc:postgresql://4school_db:5432/j4school \
  $IMAGE_NAME:$VERSION

# 3. Health Check
echo "⏳ Waiting for application to start..."
MAX_RETRIES=12
COUNT=0
until $(curl --output /dev/null --silent --head --fail $HEALTH_CHECK_URL); do
    printf '.'
    sleep 5
    COUNT=$((COUNT+1))
    if [ $COUNT -eq $MAX_RETRIES ]; then
        echo -e "\n❌ Staging health check failed after 1 minute."
        docker stop 4school_staging && docker rm 4school_staging
        exit 1
    fi
done

echo -e "\n✅ Staging is UP at http://your-vps-ip:$STAGING_PORT"
echo "🔍 Please verify the new version before proceeding."

# 4. User Approval
read -p "❓ Do you want to push this version to PRODUCTION? (y/N): " confirm

if [[ "$confirm" =~ ^[Yy]$ ]]; then
    echo "🚢 Promoting to Production..."
    
    # Update the environment for docker-compose
    export APP_VERSION=$VERSION
    export HOST_PORT=$PROD_PORT
    
    # Restart production
    docker compose up -d app
    
    echo "✨ Production updated successfully!"
else
    echo "🛑 Deployment cancelled. Production remains on the old version."
fi

# 5. Cleanup Staging
echo "🧹 Cleaning up staging container..."
docker stop 4school_staging && docker rm 4school_staging

echo "🏁 Finished."
