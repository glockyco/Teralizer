# Dockerfile for Teralizer Java pipeline.
#
# This container runs the test generalization pipeline that processes
# Java projects and collects data into the PostgreSQL database.
#
# Build context should be the repository root:
#   docker build -t teralizer .
#
# Note: Uses JDK 8 for Symbolic Pathfinder compatibility.
# On ARM (Apple Silicon), this runs via Rosetta emulation.

FROM gradle:6.9.1-jdk8

# Install dependencies and clean up apt cache
RUN apt-get update && \
    apt-get install -y --no-install-recommends sqlite3 wget tar && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV MAVEN_VERSION=3.9.8
ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# Install Maven from Apache Archive (permanent URL, unlike downloads.apache.org)
RUN wget -q https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz && \
    tar -xzf apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt/ && \
    ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven && \
    rm apache-maven-${MAVEN_VERSION}-bin.tar.gz

# Copy project directory to the container
WORKDIR /app
COPY . /app

# Create directories
RUN mkdir -p database data logs

# Download dependencies
RUN ./gradlew dependencies --no-daemon
# Build the application
RUN ./gradlew build -x test --no-daemon

# Default: show usage
CMD ["./gradlew", "tasks", "--no-daemon"]
