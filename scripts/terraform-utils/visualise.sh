#!/bin/bash

# Configuration - Update these to match your environment
BUCKET_NAME="sudoku-tf-state"
LOCAL_STATE_DIR="$HOME/tmp/tf-viz"
STATE_KEY="sudoku/terraform.tfstate"
DEFAULT_WS="default"

mkdir -p "$LOCAL_STATE_DIR"
trap 'rm -rf "$LOCAL_STATE_DIR"' EXIT

# 1. List Workspaces from S3
# Named workspaces live under the 'env:/' prefix; the default workspace
# uses the bare $STATE_KEY, so it's not listed there and must be added manually.
echo "Fetching workspaces from S3..."
named_workspaces=$(aws s3 ls "s3://$BUCKET_NAME/env:/" | awk '{print $2}' | tr -d '/')
workspaces="$DEFAULT_WS $named_workspaces"

# 2. Select Workspace
echo "Available workspaces:"
select ws in $workspaces; do
    if [ -n "$ws" ]; then
        echo "You selected: $ws"
        break
    else
        echo "Invalid selection."
    fi
done

# 3. Define S3 Key based on workspace
if [ "$ws" == "$DEFAULT_WS" ]; then
    S3_KEY="$STATE_KEY"
else
    S3_KEY="env:/$ws/$STATE_KEY"
fi

# 4. Download State
echo "Downloading state from s3://$BUCKET_NAME/$S3_KEY..."
aws s3 cp "s3://$BUCKET_NAME/$S3_KEY" "$LOCAL_STATE_DIR/terraform.tfstate"

# 5. Spin up Rover in Docker
echo "Starting Rover UI..."

# Move the downloaded state into the current project directory so Rover sees it
cp "$LOCAL_STATE_DIR/terraform.tfstate" "$(pwd)/terraform.tfstate"

# Run Rover, letting it detect the state file in the working directory
docker run --rm -it \
    -p 8080:9000 \
    -v "$(pwd)":/src \
    --platform linux/amd64 \
    im2nguyen/rover:latest \
    -workingDir /src
