# Linux Deployment

This deployment runs the bot, PostgreSQL, and Redis with Docker Compose. The app image is built locally with Maven/Jib, pushed to Docker Hub, and pulled by the Linux host. Runtime secrets stay in `deploy/.env` on the host.

## 1. Prepare Server

Install Docker Engine with the Compose plugin, then create the deployment directory:

```bash
sudo mkdir -p /opt/fitness-bot
sudo chown "$USER":"$USER" /opt/fitness-bot
```

## 2. Configure Secrets

```bash
cd /opt/fitness-bot/deploy
cp .env.example .env
nano .env
```

Set at least:

- `APP_IMAGE`
- `POSTGRES_PASSWORD`
- `TELEGRAM_BOT_TOKEN`
- `OPENAI_API_KEY` or `NEBIUS_API_KEY` if AI parsing is enabled for users
- `ADMIN_PASSWORD`

Keep `APP_HTTP_BIND=127.0.0.1` if a reverse proxy is used on the host.

## 3. Build And Push App Image

Builds the image locally through Maven/Jib. The default tag is `YYYYMMDD-<git-sha>`, for example `20260427-a1b2c3d4e5f6`.
The default platform is `linux/arm64` for Raspberry Pi.

```bash
deploy/build-push-image.sh
```

Useful variants:

```bash
deploy/build-push-image.sh --skip-tests
deploy/build-push-image.sh --no-push
deploy/build-push-image.sh --update-env
deploy/build-push-image.sh --tag 20260427-a1b2c3d4e5f6
deploy/build-push-image.sh --platform linux/amd64
```

The script does not read or bake `deploy/.env` into the image. If `--update-env` is used, it writes only `APP_IMAGE=<repo>:<tag>` into local `deploy/.env`.

## 4. Start Manually

```bash
cd /opt/fitness-bot/deploy
docker compose pull app postgres redis
docker compose up -d
docker compose logs -f app
```

Health check:

```bash
curl http://127.0.0.1:8080/actuator/health
```

## 5. Deploy From This Machine Over SSH

The repository includes a single deployment script for the Raspberry Pi host:

```bash
deploy/deploy-raspberrypi.sh
```

Defaults:

- `REMOTE=lev@raspberrypi.local`
- `REMOTE_DIR=/opt/fitness-bot`
- builds and pushes `mghostl/fitness-bot:YYYYMMDD-<git-sha>` locally
- runs local `mvn test` before image build
- syncs only `deploy/` files to the remote host
- writes the produced image ref into the remote `.env`
- pulls that exact image on the remote host; it does not build there

Create `deploy/.env` locally before deploying and make sure Docker is logged in with push access to the repository. The script copies an effective env file to `/opt/fitness-bot/deploy/.env` on the remote host.

Useful variants:

```bash
deploy/deploy-raspberrypi.sh --install-systemd
deploy/deploy-raspberrypi.sh --skip-tests
deploy/deploy-raspberrypi.sh --tag 20260427-ce2b2296dfe4
deploy/deploy-raspberrypi.sh --repository mghostl/fitness-bot --platform linux/arm64
deploy/deploy-raspberrypi.sh --image mghostl/fitness-bot:20260427-ce2b2296dfe4
deploy/deploy-raspberrypi.sh --no-build-image
deploy/deploy-raspberrypi.sh --remote lev@rapsberrypi.local
deploy/deploy-raspberrypi.sh --remote lev@raspberrypi.local --dir /home/lev/fitness-bot
deploy/deploy-raspberrypi.sh --remote lev@192.168.x.x --ssh-timeout 20
```

Use `--image` for an already-pushed image. Use `--no-build-image` only when `APP_IMAGE` in local `deploy/.env` already points to the image you want to deploy.

If deployment stops at the SSH check, verify the host manually:

```bash
ssh lev@raspberrypi.local 'hostname'
```

If your Pi uses the earlier custom spelling, use `--remote lev@rapsberrypi.local`.

## 6. Run With systemd

```bash
sudo cp /opt/fitness-bot/deploy/systemd/fitness-bot.service /etc/systemd/system/fitness-bot.service
sudo systemctl daemon-reload
sudo systemctl enable --now fitness-bot
```

Useful commands:

```bash
sudo systemctl status fitness-bot
cd /opt/fitness-bot/deploy && docker compose logs -f app
sudo systemctl reload fitness-bot
sudo systemctl stop fitness-bot
```

## 7. Update Deployment

```bash
cd /opt/fitness-bot/deploy
docker compose pull app
sudo systemctl reload fitness-bot
```

Liquibase migrations run automatically when the app starts.

## 8. Enable AI Parser For A User

From this repository, target the Raspberry Pi deployment and enable AI parsing for the default Telegram username `@mghostl`. The script calls the admin HTTP API with `curl`:

```bash
deploy/set-ai-parser-user.sh
```

If the deployed app has not captured the Telegram username yet, pass the numeric Telegram ID explicitly:

```bash
deploy/set-ai-parser-user.sh --telegram-id 123456789
```

## 9. Scheduled PostgreSQL Backups

The deployment includes a host-side backup script and systemd timer:

- `deploy/backup-postgres.sh` creates a compressed custom-format `pg_dump`.
- `deploy/systemd/fitness-bot-postgres-backup.timer` runs it daily at `03:15` with a small randomized delay.
- Backups are stored locally under `/opt/fitness-bot/backups/postgres` by default.

Install the timer on the Raspberry Pi after deployment:

```bash
ssh lev@raspberrypi.local
cd /opt/fitness-bot/deploy
sudo ./install-postgres-backup.sh
```

To change the local backup directory or retention, configure it on the Pi:

```bash
cd /opt/fitness-bot/deploy
sudo cp -n backup.env.example backup.env
sudo nano backup.env
sudo systemctl restart fitness-bot-postgres-backup.timer
```

Example:

```bash
BACKUP_DIR=/opt/fitness-bot/backups/postgres
BACKUP_RETENTION_DAYS=14
```

To enable rsync copy to Google Drive, run the helper on the Pi with the existing rsync root target. It creates and uses `FitnessBot/backup/db` under that root:

```bash
cd /opt/fitness-bot/deploy
sudo ./enable-google-drive-postgres-rsync.sh --rsync-root user@example.com:/GoogleDrive --run-now
```

For a local Google Drive path mounted on the Pi:

```bash
sudo ./enable-google-drive-postgres-rsync.sh --rsync-root /mnt/google-drive --run-now
```

Run a backup immediately and inspect timer status:

```bash
sudo systemctl start fitness-bot-postgres-backup.service
systemctl list-timers fitness-bot-postgres-backup.timer
journalctl -u fitness-bot-postgres-backup.service -n 100 --no-pager
```

Manual backup without systemd:

```bash
cd /opt/fitness-bot/deploy
./backup-postgres.sh
```

Restore example:

```bash
cd /opt/fitness-bot/deploy
set -a
. ./.env
set +a
cat /opt/fitness-bot/backups/postgres/fitness_bot_postgres_YYYYMMDDTHHMMSSZ.dump \
  | docker compose exec -T postgres pg_restore \
      -U "$POSTGRES_USER" \
      -d "$POSTGRES_DB" \
      --clean \
      --if-exists
```
