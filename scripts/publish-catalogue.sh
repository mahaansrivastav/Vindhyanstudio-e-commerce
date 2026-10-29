#!/usr/bin/env bash
# Publishes catalogue/ (catalogue.xlsx + asset_image/ photos) to PRODUCTION:
# products go into the production database, web-sized photos into Supabase Storage.
# Run it from your laptop whenever the Excel sheet or photos change.
#
# Needs backend/.env.production.local (never commit it) with the same DB_* and SUPABASE_* values
# as the live server. See DEPLOYMENT.md.
set -euo pipefail
cd "$(dirname "$0")/.."
ENV_FILE=backend/.env.production.local
[ -f "$ENV_FILE" ] || { echo "Missing $ENV_FILE (copy backend/.env.production.example and fill it in)"; exit 1; }
set -a; source "$ENV_FILE"; set +a

cd backend
./mvnw -q -DskipTests package
java -jar target/backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --server.port=0 \
  --app.catalogue.local-path=../catalogue \
  --app.catalogue.publish-only=true \
  --app.notifications.email=console
