# Fitness Bot

A Telegram bot for parsing workout programs and tracking progress.

## Prerequisites

- Java 21
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

### Use Nebius Token Factory for the AI parser

The AI parser already calls an OpenAI-compatible API, so Nebius Token Factory can be used by setting environment variables before starting the app:

```bash
export NEBIUS_API_KEY="<your-nebius-api-key>"
export NEBIUS_BASE_URL="https://api.tokenfactory.eu-west1.nebius.com/v1"
export NEBIUS_MODEL="moonshotai/Kimi-K2.5"
```

The application also still accepts `OPENAI_API_KEY`, `OPENAI_BASE_URL`, and `OPENAI_MODEL`. `OPENAI_*` takes precedence if both are set.

### Admin HTTP endpoint for parser switching

The app also exposes an admin-only HTTP endpoint protected with Basic Auth:

```bash
export ADMIN_USERNAME="admin"
export ADMIN_PASSWORD="change-me"
```

Update the parser flag for a Telegram user:

```bash
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" \
  -X PUT http://localhost:8080/admin/users/123456789/parser \
  -H "Content-Type: application/json" \
  -d '{"enabled":true}'
```

### Run Tests

To run tests:

```bash
mvn test
```

This will run both unit tests and integration tests with Java 21.

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
