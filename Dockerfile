FROM gradle:6.9.1-jdk8

# Install dependencies
RUN apt-get update && \
    apt-get install -y sqlite3

# Set environment variables
ENV MAVEN_VERSION=3.9.8
ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# Install Maven manually
RUN apt-get update && apt-get install -y wget tar && \
    wget https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz && \
    tar -xvzf apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt/ && \
    ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven && \
    rm -rf apache-maven-${MAVEN_VERSION}-bin.tar.gz

# Copy project directory to the container
WORKDIR /app
COPY . /app

RUN mkdir "database"

ENV PROJECT_CONFIG_PATH="./project-configs/eqbench.conf"

RUN gradle build --no-daemon

CMD ["sh", "-c","gradle -Dteralizer.config=$PROJECT_CONFIG_PATH run"]
