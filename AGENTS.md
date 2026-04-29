# Fitness Bot - Agent Context

## Current Project State

This is a Java 21 Spring Boot 3.3 Telegram bot for parsing workout programs, saving them as structured training days, starting active programs, walking users through workout days, and logging loads/history per exercise.

The bot uses Telegram long polling through `FitnessTelegramBot`; it is not a webhook-first app. It runs when `telegram.bot.token` is configured.

Primary runtime dependencies:
- PostgreSQL for persistence.
- Liquibase for schema migrations.
- Redis for Spring data/health integration.
- Telegram Bots API `telegrambots-spring-boot-starter`.
- Optional OpenAI-compatible parser using `OPENAI_*` or `NEBIUS_*` environment variables.
- Spring Security for the admin HTTP endpoint.

## Build And Test Commands

Use these from the repository root:

```bash
mvn test
```

This is the default verification command. It runs unit tests and Docker/Testcontainers-backed integration tests. Docker must be running. Do not skip Docker-backed tests unless the user explicitly asks.

```bash
mvn clean package
```

Builds the Spring Boot jar.

```bash
docker-compose up -d
```

Starts local PostgreSQL and Redis for development. Local PostgreSQL is exposed on `localhost:5433`, Redis on `localhost:6379`.

```bash
mvn spring-boot:run
```

Runs the bot locally using `src/main/resources/application.yml` and environment variables.

## Local Configuration

Main config is in `src/main/resources/application.yml`.

Important defaults:
- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/fitness_bot`
- `SPRING_DATASOURCE_USERNAME=postgres`
- `SPRING_DATASOURCE_PASSWORD=postgres`
- `SPRING_DATA_REDIS_HOST=localhost`
- `SPRING_DATA_REDIS_PORT=6379`
- `SERVER_PORT=8080`
- `TELEGRAM_BOT_USERNAME=zil_fit_bot`

Required for a real bot run:
- `TELEGRAM_BOT_TOKEN`

AI parser configuration:
- `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL` take precedence.
- `NEBIUS_API_KEY`, `NEBIUS_BASE_URL`, `NEBIUS_MODEL` are supported as fallback.

Admin endpoint auth:
- `ADMIN_USERNAME`
- `ADMIN_PASSWORD`

Do not commit or bake secrets into images. `src/main/resources/application-with-creds.yml` is explicitly excluded from packaged resources.

## Deployment

Deployment files are under `deploy/`.

Production compose file:
- `deploy/compose.yml`
- Pulls the app image from `APP_IMAGE`.
- Does not build the app on the server.
- Uses Postgres and Redis containers.

Build and push image locally with Maven/Jib:

```bash
deploy/build-push-image.sh
```

Defaults:
- repository: `mghostl/fitness-bot`
- tag: `YYYYMMDD-<git-sha>`
- platform: `linux/arm64`
- runs `mvn test` before image build unless `--skip-tests`
- pushes by default unless `--no-push`

Deploy to Raspberry Pi in one command:

```bash
deploy/deploy-raspberrypi.sh
```

Defaults:
- remote: `lev@raspberrypi.local`
- remote dir: `/opt/fitness-bot`
- builds and pushes the image locally
- writes the produced image ref into the effective remote `.env`
- syncs only `deploy/` files to the remote host
- pulls the exact app image on the host

Useful variants:

```bash
deploy/deploy-raspberrypi.sh --skip-tests
deploy/deploy-raspberrypi.sh --image mghostl/fitness-bot:20260427-ce2b2296dfe4
deploy/deploy-raspberrypi.sh --no-build-image
deploy/deploy-raspberrypi.sh --remote lev@rapsberrypi.local
```

`deploy/.env` contains local deployment secrets and must not be logged or committed. Use `deploy/.env.example` for shape/reference.

If Docker Hub auth is needed for Jib, use:
- `JIB_FROM_USERNAME`
- `JIB_FROM_PASSWORD`
- `JIB_TO_USERNAME`
- `JIB_TO_PASSWORD`

## Domain Model

Core entities:
- `User`: Telegram user, active program/day, parser preference.
- `Program`: saved training program owned by a user.
- `ProgramTrainingDay`: ordered link between program and training day.
- `TrainingDay`: parsed workout day with raw text and exercises.
- `Exercise`: parsed exercise, optional canonical exercise link, video URLs, last weight.
- `WorkoutSession`: active/completed/abandoned workout session.
- `WorkoutSetLog`: logged loads for exercise sets/rounds.

Schema changes are managed by Liquibase changelogs in `src/main/resources/db/changelog/`.

Do not rely on Hibernate auto-DDL. `spring.jpa.hibernate.ddl-auto` is `validate`.

## Main Code Areas

Application entry:
- `src/main/java/com/example/fitnessbot/FitnessBotApplication.java`

Telegram bot:
- `telegram/FitnessTelegramBot.java`
- `telegram/DefaultMenuKeyboardFactory.java`
- `telegram/commands/*`

Services:
- `service/TrainingDayService.java`
- `service/ProgramService.java`
- `service/WorkoutService.java`
- `service/ProgramCreationSessionManager.java`
- `service/ProgramRenameSessionManager.java`

Parsers:
- `parser/TrainingDayParser.java`
- `parser/OpenAiTrainingDayParser.java`
- `parser/TrainingDayTitleNormalizer.java`

Admin:
- `admin/AdminUserController.java`
- `admin/AdminSecurityConfig.java`

Repositories:
- `repository/*Repository.java`

## Bot Behavior To Preserve

Program creation:
- `/create_program <name>` starts an in-memory draft session.
- During a draft, users can send or forward training day text.
- Each training day is parsed and added to the draft.
- `/finish_program` saves links from the draft days into the program.
- `/cancel_program` is only useful/visible when a draft exists.

Program viewing:
- `/show_program` lists saved programs with inline buttons.
- `/show_program <id or name>` opens a program.
- Numeric-leading names such as `2024 Strength` must be treatable as names unless the selector is explicitly numeric or `#123`.
- Program detail UI includes start, rename, delete, and day buttons.

Starting programs:
- Tapping `Start Program` makes the program active, sets the first training day active, starts the workout day immediately, and shows the first exercise. Do not add an extra "Start Day" step here.

Active workout day:
- `/active_day` shows the active training day.
- Workout input accepts numeric loads, custom load strings such as `orange band`, and `none`.
- The UI can offer buttons for previous load and no load.
- `none` is explicit; blank input is not accepted as no-load.
- Exercise history should use canonical exercise linkage so history can span programs for the same user.
- Circuit/round sections should progress round-by-round: A, B, C, then round 2 A, B, C, etc.

Parser behavior:
- `TrainingDayParser` is deterministic and handles common Telegram formats.
- It supports bullet lists, numbered lists, inline video URLs, sets/reps, RPE/notes, compact pasted Telegram text with inline `⁃` bullets, and circuit section notes.
- `OpenAiTrainingDayParser` is optional per user via `User.useAiParser`.
- Admin endpoint can switch parser mode for a Telegram user:

```bash
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" \
  -X PUT http://localhost:8080/admin/users/123456789/parser \
  -H "Content-Type: application/json" \
  -d '{"useAiParser":true}'
```

## Testing Guidance

Add or update tests for every behavior change.

Common focused tests:

```bash
mvn -Dtest=TrainingDayParserTest test
mvn -Dtest=WorkoutServiceTest test
mvn -Dtest=ProgramServiceTest test
mvn -Dtest=TelegramUiInteractionTest test
mvn -Dtest=ShowProgramCommandHandlerTest test
```

Before finalizing changes, run:

```bash
mvn test
```

If tests fail, find and fix the root cause. Do not remove meaningful assertions just to make tests pass.

Avoid broad lenient Mockito stubs. Use precise stubs unless existing test setup forces leniency.

## Engineering Constraints

- Preserve user changes in the working tree. Do not reset or revert unrelated files.
- Use Liquibase for schema changes.
- Keep secrets out of Docker images, git, logs, and final responses.
- Prefer deterministic parser changes with regression tests using real examples.
- Keep Telegram messages concise, readable during workouts, and safe for the configured parse mode.
- Escape HTML when sending Telegram messages with `parseMode=HTML`.
- Run Docker-backed tests when relevant; the user has explicitly said Docker works locally.
