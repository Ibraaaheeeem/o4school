#!/bin/bash

# Navigate to the directory where this script (and docker-compose.yml) is located
cd "$(dirname "$0")"

# Pull the latest image
echo "Pulling latest image..."
docker compose pull app

# Restart the containers
echo "Restarting containers..."
docker compose up -d

echo "Deployment complete!"
