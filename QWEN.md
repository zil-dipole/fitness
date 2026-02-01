# Fitness Bot - Development Context

## Project Overview

This is a Java-based Telegram bot application built with Spring Boot. Its primary purpose is to parse free-form fitness workout descriptions forwarded from Telegram messages, store them in a structured format, and later allow users to navigate through the exercises during their workout, log weights, and track progress.

The core functionality involves:
- Receiving forwarded messages via a Telegram webhook.
- Parsing the text content of these messages to extract workout routines (exercises, sets, reps, video links).
- Storing the parsed data in a relational database (PostgreSQL).
- Providing an interactive Telegram UI for users to step through exercises and log their performance.

Technologies used:
- **Language**: Java 21
- **Framework**: Spring Boot 3.3.0
- **Database**: PostgreSQL (via Spring Data JPA)
- **Messaging**: Telegram Bots API (using `telegrambots-spring-boot-starter`)
- **Caching/Queue**: Redis
- **Build Tool**: Maven
- **Testing**: TestContainers for integration testing with PostgreSQL

### Architecture Summary
The application follows a layered architecture:
- **Controller Layer**: Handles incoming HTTP requests from Telegram webhooks.
- **Service Layer**: Contains business logic, including parsing and persistence orchestration.
- **Model Layer**: JPA Entities representing the data model (`User`, `TrainingDay`, `Exercise`).
- **Parser Module**: A standalone component for converting unstructured text into structured objects.
- **Repository Layer**: Spring Data JPA repositories for database access.
- **Integration Testing**: TestContainers for testing with real PostgreSQL instances.

## Building, Running & Testing

### Prerequisites
- JDK 21
- Maven 3.6+
- Docker (for TestContainers integration testing)
- PostgreSQL database (for development - see Database Setup below)
- Telegram Bot Token (for webhook setup)

### Database Setup
For local development, you need to have a PostgreSQL instance running. You can either:

1. Install PostgreSQL locally and configure it with the credentials in `src/main/resources/application.yml`:
   - Database: `fitness_bot`
   - Username: `postgres`
   - Password: `postgres`
   - Host: `localhost`
   - Port: `5432`

2. Or modify the `src/main/resources/application.yml` file to use an embedded database like H2 for development:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: password
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

Don't forget to add the H2 dependency to your `pom.xml` if you choose this option:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

### Building the Application
```bash
mvn clean package
```

### Running the Application
```bash
# Ensure DATABASE_URL, REDIS_URL, TELEGRAM_BOT_TOKEN are set in environment
mvn spring-boot:run
```
or
```bash
# After building
java -jar target/fitness-bot-0.0.1-SNAPSHOT.jar
```

### Running Tests
```bash
# Run unit and integration tests with TestContainers
mvn test
```
The integration tests automatically start PostgreSQL containers using TestContainers, so Docker must be running.
Test configurations are defined in `src/test/resources/application.properties`.

### Development Requirements
After each code change, ensure that:

1. The service can run successfully:
   ```bash
   mvn spring-boot:run
   ```

2. All tests must pass before pushing changes:
   ```bash
   mvn test
   ```
   This includes both unit tests and integration tests with TestContainers. Docker must be running for integration tests.

3. For production builds:
   ```bash
   mvn clean package
   ```

## Development Conventions

- **Package Structure**:
  - `com.example.fitnessbot`: Root package.
  - `.model`: JPA entities.
  - `.repository`: Spring Data JPA repository interfaces.
  - `.service`: Business logic services.
  - `.parser`: Custom text parsing logic.
  - `.controller`: REST controllers for handling Telegram webhooks (not yet present but implied).
  - `.integration`: Integration tests using TestContainers.
- **Coding Style**:
  - Standard Java naming conventions.
  - Explicit getters and setters instead of Lombok annotations (removed due to Java 17 compatibility issues).
  - Entities use JPA annotations for ORM mapping.
- **Testing**:
  - Unit tests should reside in `src/test/java`.
  - Integration tests involving databases use TestContainers and are located in the `.integration` package.
  - Tests are annotated with `@SpringBootTest` and `@Testcontainers` for full Spring context loading.
- **Configuration**:
  - Externalized configuration via `application.yml` or `application.yaml`.
  - Database configuration in `src/main/resources/application.yml`.
  - Test configuration in `src/test/resources/application.yml`.
- **Persistence**:
  - Use of Spring Data JPA Repositories for data access patterns.
  - Entities should define relationships correctly (e.g., `@OneToMany`, `@ManyToOne`).
  - Integration tests use TestContainers to automatically provision PostgreSQL instances.

## Key Files & Components

- `pom.xml`: Maven build configuration, defining dependencies and plugins.
- `FitnessBotApplication.java`: Main Spring Boot application class.
- `TrainingDayParser.java`: Core utility for parsing unstructured workout text.
- `model/User.java`, `model/TrainingDay.java`, `model/Exercise.java`: Domain entities mapped to database tables.
- `repository/UserRepository.java`: Example Spring Data JPA repository interface.
- `service/TrainingDayService.java`: Central business logic service for managing training days.
- `src/test/java/com/example/fitnessbot/integration/*`: Integration tests using TestContainers with PostgreSQL.
- `src/main/resources/application.yml`: Main application configuration with PostgreSQL settings.
- `src/test/resources/application.yml`: Test configuration.

## Usage Instructions

This project is intended to be extended with:
- A Telegram webhook endpoint to receive forwarded messages.
- Full implementation of the `TrainingDayService` including persistence logic.
- Addition of controllers to handle inline keyboard interactions for navigation and logging.
- Population of database repositories for full CRUD capabilities.
- Implementation of Redis caching for improved performance.
- Enhanced parsing logic for more complex workout formats.

The application now includes:
- PostgreSQL database integration with JPA/Hibernate.
- Integration testing with TestContainers for reliable database tests.
- Automated schema creation and migration via Hibernate.
- Proper separation of main and test configurations.

Future enhancements could involve integrating with AI services for advanced parsing or generating workout plans, though the current focus is on deterministic parsing of structured input.