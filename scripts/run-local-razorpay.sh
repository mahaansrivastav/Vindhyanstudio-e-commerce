#!/usr/bin/env bash
# Runs the backend locally with Razorpay TEST keys from backend/.env.razorpay.local (git-ignored):
#   RAZORPAY_KEY_ID=rzp_test_...
#   RAZORPAY_KEY_SECRET=...
set -euo pipefail
cd "$(dirname "$0")/../backend"
[ -f .env.razorpay.local ] || { echo "Create backend/.env.razorpay.local first"; exit 1; }
set -a; source .env.razorpay.local; set +a
case "${RAZORPAY_KEY_ID:-}" in rzp_test_*) ;; *) echo "Refusing: RAZORPAY_KEY_ID must be a TEST key (rzp_test_...)"; exit 1;; esac
export PAYMENT_PROVIDER=razorpay PORT=${PORT:-8081} APP_PUBLIC_BASE_URL=http://localhost:${PORT:-8081}
exec ./mvnw -q spring-boot:run
