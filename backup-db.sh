#!/bin/bash

# Database Backup Script
# This script backs up the PostgreSQL database from a remote server and downloads it locally

# Configuration
REMOTE_USER="${1:-root}"  # Default to 'ubuntu', can be overridden as first argument
REMOTE_HOST="${2:-www.4school.app}"        # Remote server IP/hostname as second argument
REMOTE_DB="${3:-j4school}"  # Database name as third argument (default: elearner)
DB_USER="${4:-postgres}"    # Database user as fourth argument (default: postgres)
LOCAL_BACKUP_DIR="${5:-.}" # Local backup directory as fifth argument (default: current directory)

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Validate required parameters
if [ -z "$REMOTE_HOST" ]; then
    echo -e "${RED}Error: Remote host not specified${NC}"
    echo "Usage: $0 [remote_user] <remote_host> [database_name] [db_user] [local_backup_dir]"
    echo ""
    echo "Examples:"
    echo "  $0 ubuntu 192.168.1.100"
    echo "  $0 ubuntu example.com elearner postgres ~/backups"
    echo "  $0 root server.com mydb postgres ."
    exit 1
fi

# Create local backup directory if it doesn't exist
mkdir -p "$LOCAL_BACKUP_DIR"

# Generate timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILENAME="elearner_backup_${TIMESTAMP}.sql.gz"
REMOTE_BACKUP_PATH="/tmp/${BACKUP_FILENAME}"
LOCAL_BACKUP_PATH="${LOCAL_BACKUP_DIR}/${BACKUP_FILENAME}"

echo -e "${YELLOW}=== Database Backup Script ===${NC}"
echo -e "${YELLOW}Remote User:${NC} $REMOTE_USER"
echo -e "${YELLOW}Remote Host:${NC} $REMOTE_HOST"
echo -e "${YELLOW}Database:${NC} $REMOTE_DB"
echo -e "${YELLOW}DB User:${NC} $DB_USER"
echo -e "${YELLOW}Backup File:${NC} $BACKUP_FILENAME"
echo ""

# Step 1: SSH into remote server and create backup
echo -e "${YELLOW}Step 1: Creating backup on remote server...${NC}"
ssh $REMOTE_USER@$REMOTE_HOST << EOF
    echo "Backing up database '$REMOTE_DB'..."
    pg_dump -U $DB_USER $REMOTE_DB | gzip > $REMOTE_BACKUP_PATH
    if [ $? -eq 0 ]; then
        echo "Backup created successfully: $REMOTE_BACKUP_PATH"
        ls -lh $REMOTE_BACKUP_PATH
    else
        echo "Error: Backup failed"
        exit 1
    fi
EOF

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Failed to create backup on remote server${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Backup created on remote server${NC}"
echo ""

# Step 2: Download backup from remote server
echo -e "${YELLOW}Step 2: Downloading backup to local machine...${NC}"
scp $REMOTE_USER@$REMOTE_HOST:$REMOTE_BACKUP_PATH $LOCAL_BACKUP_PATH

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Backup downloaded successfully${NC}"
    echo -e "${YELLOW}Local path:${NC} $LOCAL_BACKUP_PATH"
    ls -lh $LOCAL_BACKUP_PATH
else
    echo -e "${RED}Error: Failed to download backup${NC}"
    exit 1
fi

echo ""

# Step 3: Clean up remote backup file
echo -e "${YELLOW}Step 3: Cleaning up remote backup file...${NC}"
ssh $REMOTE_USER@$REMOTE_HOST "rm -f $REMOTE_BACKUP_PATH && echo 'Remote backup file deleted'"

echo -e "${GREEN}✓ Remote backup file cleaned up${NC}"
echo ""

echo -e "${GREEN}=== Backup Complete ===${NC}"
echo -e "${GREEN}Backup saved to: $LOCAL_BACKUP_PATH${NC}"
echo ""
echo -e "${YELLOW}To restore this backup later, run:${NC}"
echo -e "  gunzip < $LOCAL_BACKUP_PATH | psql -U $DB_USER $REMOTE_DB"
echo ""
