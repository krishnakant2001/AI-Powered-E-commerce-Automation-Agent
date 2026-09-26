# -------------------------------
# Stage 1: Build the JAR
# -------------------------------
FROM maven:3.9.10-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom first (better caching)
COPY pom.xml .

# Download dependencies
RUN mvn -B dependency:go-offline

# Copy source code
COPY src ./src

# Build the JAR
RUN mvn -B package -DskipTests


# -------------------------------
# Stage 2: Runtime image
# -------------------------------
FROM eclipse-temurin:21-jre-alpine

# Create non-root user
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# Copy JAR with correct ownership
COPY --from=build --chown=app:app /app/target/*.jar app.jar

# Switch to non-root user
USER app

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]