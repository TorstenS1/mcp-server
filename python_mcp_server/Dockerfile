# Use a slim Python image
FROM python:3.12-slim

# Set the working directory
WORKDIR /app

# Copy the requirements file and install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the application code
COPY . .

# Expose the port the application runs on
EXPOSE 18002

# Run the application
CMD ["python", "-m", "app.main"]
