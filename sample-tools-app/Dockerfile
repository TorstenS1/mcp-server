# Use a slim Java runtime image
FROM openjdk:21-jdk-slim
WORKDIR /app

# Copy the built JAR file
# Assumes the JAR has been built and is located in the target directory
COPY target/sample-tools-app-1.0-SNAPSHOT.jar .

# Expose the port the application runs on
EXPOSE 18003

# Run the application
CMD ["java", "-jar", "sample-tools-app-1.0-SNAPSHOT.jar"]
