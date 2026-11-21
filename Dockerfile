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

# The tag names the version, the digest the exact multi-architecture index.
FROM gradle:6.9.1-jdk8@sha256:ea62572d6b0fff0665398113978a7cae1f28371bf6dbb967fb4f4716cbf4273f

# Set environment variables
ENV MAVEN_VERSION=3.9.8
ENV MAVEN_SHA512=7d171def9b85846bf757a2cec94b7529371068a0670df14682447224e57983528e97a6d1b850327e4ca02b139abaab7fcb93c4315119e6f0ffb3f0cbc0d0b9a2
ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# Install Maven from Apache Archive (permanent URL, unlike downloads.apache.org)
# and refuse an archive whose checksum differs from the pinned one.
RUN wget -q https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz && \
    echo "${MAVEN_SHA512}  apache-maven-${MAVEN_VERSION}-bin.tar.gz" | sha512sum -c - && \
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
