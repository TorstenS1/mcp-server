#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration ---
CLIENT_PORT=18004
PROMPT="What are the details for user a1b2c3d4-e5f6-7890-1234-567890abcdef?"
WAIT_TIME=15 # Seconds to wait for services to start

# --- Main Script ---

echo "Starting all services..."
docker-compose up --build -d

echo "Waiting ${WAIT_TIME} seconds for services to initialize..."
sleep ${WAIT_TIME}

echo "\nSending prompt to MCP Client: '${PROMPT}'"

# Send the prompt to the client and store the response
RESPONSE=$(curl -s -X POST http://localhost:${CLIENT_PORT}/prompt \
-H "Content-Type: text/plain" \
-d "${PROMPT}")

echo "\nReceived response:"
echo "${RESPONSE}"

echo "\nShutting down services..."
docker-compose down

echo "\nEnd-to-end test complete."

# --- Verification (Optional) ---
# You can add a check here to see if the response contains expected text.
# For example:
# if [[ "${RESPONSE}" == *"Anna Musterfrau"* ]]; then
#   echo "\nVerification successful!"
#   exit 0
# else
#   echo "\nVerification failed!"
#   exit 1
# fi
