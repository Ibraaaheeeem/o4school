#!/bin/bash

# This script builds locally and pushes to Docker registry
# GitHub Actions will use the image pushed by this script

set -e

# Configuration
IMAGE_NAME="ghcr.io/ibraaaheeeem/o4school"
VERSION=${1:-$(git rev-parse --short HEAD)}

echo "🚀 Building and pushing version: $VERSION"

# 1. Clean build
echo "🔨 Clean building application..."
./gradlew clean build -x test

# 2. Build Docker image with no-cache
echo "🐳 Building Docker image..."
docker build --no-cache -t $IMAGE_NAME:$VERSION -t $IMAGE_NAME:latest .

# 3. Push to registry
echo "📤 Pushing to Docker registry..."
docker push $IMAGE_NAME:$VERSION
docker push $IMAGE_NAME:latest

echo "✅ Build and push complete!"
echo "📝 To deploy on server, run:"
echo "   ./deployment/deploy.sh $VERSION"
