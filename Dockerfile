FROM gradle:6.9.1-jdk8

# Install dependencies and clean up apt cache
RUN apt-get update && \
    apt-get install -y --no-install-recommends sqlite3 wget tar && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV MAVEN_VERSION=3.9.8
ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# Install Maven manually
RUN wget -q https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz && \
    tar -xzf apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt/ && \
    ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven && \
    rm apache-maven-${MAVEN_VERSION}-bin.tar.gz

# Copy project directory to the container
WORKDIR /app
COPY . /app

RUN mkdir "database"

ENV PROJECT_CONFIG_PATH="./project-configs/eqbench.conf"

# Download dependencies
RUN ./gradlew dependencies --no-daemon
# Build the application
RUN ./gradlew build --no-daemon

CMD ["sh", "-c", "./gradlew -Dteralizer.config=${PROJECT_CONFIG_PATH} run --no-daemon"]
