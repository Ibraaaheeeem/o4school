#!/bin/bash

# Pull the latest image
echo "Pulling latest image..."
docker-compose pull app

# Restart the containers
echo "Restarting containers..."
docker-compose up -d

echo "Deployment complete!"
