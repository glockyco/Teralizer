FROM gradle:6.9.1-jdk8

# Install dependencies
RUN apt-get update && \
    apt-get install -y sqlite3 maven

# Copy project directory to the container
WORKDIR /app
COPY . /app

RUN mkdir "database"

ENV PROJECT_CONFIG_PATH="./project-configs/eqbench.conf"

RUN gradle build --no-daemon

CMD ["sh", "-c","gradle -Dteralizer.config=$PROJECT_CONFIG_PATH run"]
