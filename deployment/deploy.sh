    #!/bin/bash

    # Configuration
    IMAGE_NAME="ghcr.io/ibraaaheeeem/o4school"
    STAGING_DOMAIN="staging.4school.app"
    PROD_DOMAIN="4school.app"
    HEALTH_CHECK_URL="http://$STAGING_DOMAIN/auth/login"

    # This script runs on the SERVER - just pulls and deploys
    # For building, use build-and-push.sh on your local machine first

    # Navigate to deployment folder
    cd "$(dirname "$0")" || exit 1

    # 1. Get version
    VERSION=${1:-latest}
    echo "🚀 Deploying version: $VERSION"

    # 2. Pull the version from Docker registry
    echo "📥 Pulling image from Docker registry..."
    docker pull $IMAGE_NAME:$VERSION
    if [ $? -ne 0 ]; then
        echo "❌ Failed to pull image. Make sure build-and-push.sh was run locally first."
        exit 1
    fi

    # 3. Ensure network and database are running
    echo "🗄️ Ensuring database and network are ready..."
    docker network create school_network 2>/dev/null || true
    
    # Clean up any stuck containers/ports
    docker compose down 2>/dev/null || true
    docker rm -f 4school_db 2>/dev/null || true
    
    docker compose up -d db

    # 4. Start Staging Container
    echo "🧪 Starting Staging container..."
    # Remove any existing staging container
    docker stop 4school_staging 2>/dev/null || true
    docker rm 4school_staging 2>/dev/null || true

    # Run staging on internal network (accessed via nginx)
    docker run -d \
    --name 4school_staging \
    --network school_network \
    -p 8081:8080 \
    --env-file .env \
    -e DB_URL=jdbc:postgresql://4school-db:5432/4school \
    -e SERVER_SERVLET_CONTEXT_PATH=/ \
    $IMAGE_NAME:$VERSION

    # 5. Health Check
    echo "⏳ Waiting for application to start..."
    MAX_RETRIES=15
    COUNT=0
    until $(curl --output /dev/null --silent --head --fail http://localhost:8081/auth/login 2>/dev/null); do
        printf '.'
        sleep 5
        COUNT=$((COUNT+1))
        if [ $COUNT -eq $MAX_RETRIES ]; then
            echo -e "\n❌ Staging health check failed after 75 seconds."
            docker stop 4school_staging && docker rm 4school_staging
            exit 1
        fi
    done

    echo -e "\n✅ Staging is UP at http://$STAGING_DOMAIN"
    echo "🔍 Please verify the new version before proceeding."
    echo "📝 Note: Make sure DNS points $STAGING_DOMAIN to this server's IP"

    # 6. User Approval
    read -p "❓ Do you want to push this version to PRODUCTION? (y/N): " confirm

    if [[ "$confirm" =~ ^[Yy]$ ]]; then
        echo "🚢 Promoting to Production..."
        
        # Remove staging
        docker stop 4school_staging && docker rm 4school_staging
        
        # Update production container  
        docker stop 4school_prod 2>/dev/null || true
        docker rm 4school_prod 2>/dev/null || true
        
        # Run production on internal network
        docker run -d \
        --name 4school_prod \
        --network school_network \
        -p 8080:8080 \
        --env-file .env \
        -e DB_URL=jdbc:postgresql://4school-db:5432/4school \
        -e SERVER_SERVLET_CONTEXT_PATH=/ \
        $IMAGE_NAME:$VERSION
        
        # Wait for production to start
        PROD_RETRIES=15
        PROD_COUNT=0
        while [ $PROD_COUNT -lt $PROD_RETRIES ]; do
            if curl --output /dev/null --silent --head --fail http://localhost:8080/auth/login 2>/dev/null; then
                break
            fi
            printf '.'
            sleep 5
            PROD_COUNT=$((PROD_COUNT+1))
        done
        
        echo -e "\n✨ Production updated successfully at http://$PROD_DOMAIN"
    else
        echo "🛑 Deployment cancelled. Production remains on the old version."
        # Cleanup staging if not approved
        docker stop 4school_staging && docker rm 4school_staging
    fi

    echo "🏁 Finished."
