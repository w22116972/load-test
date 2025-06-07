# ---- Stage 1: The Builder ----
# Use a base image that contains Maven and a GraalVM-compatible JDK
FROM maven:3.9-amazoncorretto-21 AS builder

# Set the working directory
WORKDIR /app

# Copy pom.xml and download dependencies to leverage Docker's layer caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Run the native compilation! -Pnative activates the profile we configured in pom.xml
# The result will be an executable in the target/ directory
RUN mvn -Pnative -DskipTests package

# ---- Stage 2: The Final Image ----
# Use Google's distroless base image, which is extremely lightweight and contains no shell or other unnecessary tools
FROM gcr.io/distroless/cc-debian12

# Set the working directory
WORKDIR /app

# From the builder stage, copy only the compiled native executable into the final image
# Note: The executable name is determined by ${project.artifactId} in the pom.xml
COPY --from=builder /app/target/virtual-thread-loader .

# Set the container's entrypoint to directly run our program
ENTRYPOINT ["./virtual-thread-loader"]
