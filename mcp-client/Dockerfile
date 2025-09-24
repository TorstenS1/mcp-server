# Use a slim Java runtime image
FROM openjdk:21-jdk-slim
WORKDIR /app

# Copy the built JAR file
# Assumes the JAR has been built and is located in the target directory
COPY target/mcp-client-1.0-SNAPSHOT.jar .

# Copy the configuration file
COPY config.yml .

# Expose the port the application runs on
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "mcp-client-1.0-SNAPSHOT.jar"]
