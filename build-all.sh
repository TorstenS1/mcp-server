#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

echo "--- Building all projects ---"

# --- Build mcp-server (Java) ---
echo "
Building mcp-server..."
cd mcp-server
mvn clean install
cd ..

# --- Build mcp-client (Java) ---
echo "
Building mcp-client..."
cd mcp-client
mvn clean install
cd ..

# --- Build sample-tools-app (Java) ---
echo "
Building sample-tools-app..."
cd sample-tools-app
mvn clean install
cd ..

echo "
--- All projects built successfully! ---"

