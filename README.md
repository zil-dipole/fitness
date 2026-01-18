# Fitness Bot

A Telegram bot for parsing workout programs and tracking progress.

## Prerequisites

- Java 17
- Maven 3.6+
- Docker and Docker Compose (for local development)

## Getting Started

### Start Dependencies with Docker

To start PostgreSQL and Redis for local development:

```bash
docker-compose up -d
```

This will start:
- PostgreSQL on port 5433 with database `fitness_bot`, username `postgres` and password `postgres`
- Redis on port 6379

### Run the Application

To run the application:

```bash
mvn spring-boot:run
```

### Run Tests

To run tests:

```bash
mvn test
```

## Development

The application uses:
- PostgreSQL for data storage
- Liquibase for database migrations
- TestContainers for integration tests

## Success Criteria

✅ PostgreSQL is used everywhere (local development, tests, production)
✅ Liquibase manages all database migrations
✅ All integration tests pass
✅ Application connects successfully to PostgreSQL
✅ Database schema is properly created with all tables
✅ Entities are correctly mapped to database columns