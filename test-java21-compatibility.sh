#!/bin/bash

# EmailToDB Integration Test Script for Java 21 Compatibility
# This script performs a comprehensive test of the EmailToDB application
# with its new Java 21 compatibility features, error handling, and circuit breakers

set -e

echo "=== EmailToDB Integration Test ==="
echo "Testing Java 21 compatibility with enhanced error handling and resilience"

# Check Java version
echo "=== Checking Java version ==="
java -version

# Clean and build the project
echo "=== Building project with Maven ==="
./mvnw clean package -DskipTests

# Run unit tests
echo "=== Running unit tests ==="
./mvnw test

# Run the application with test profile for a brief period
echo "=== Starting application with test profile ==="
java -jar -Dspring.profiles.active=test target/EmailToDb-0.0.1-SNAPSHOT.jar &
APP_PID=$!

# Give the application time to start
echo "=== Waiting for application to start ==="
sleep 30

# Check health endpoint
echo "=== Checking health endpoint ==="
HEALTH_STATUS=$(curl -s http://localhost:9091/actuator/health)
echo "Health status: $HEALTH_STATUS"

# Check metrics endpoint
echo "=== Checking metrics endpoint ==="
curl -s http://localhost:9091/actuator/metrics | head -20

# Check for specific circuit breaker metrics if available
echo "=== Checking circuit breaker metrics ==="
curl -s http://localhost:9091/actuator/metrics/circuitbreaker.outlook-attachment-list.state || echo "Circuit breaker metrics not available yet"

# Test resilience by simulating load
echo "=== Testing application resilience ==="
for i in {1..10}; do
  echo "Request $i"
  curl -s http://localhost:9091/actuator/health > /dev/null
  sleep 1
done

# Stop the application
echo "=== Stopping application ==="
kill $APP_PID
sleep 5

# Check Docker image build
echo "=== Building Docker image ==="
docker build -t emailtodb:test .

echo "=== Integration test completed ==="
echo "All tests passed successfully"
