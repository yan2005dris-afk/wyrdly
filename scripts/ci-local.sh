#!/usr/bin/env bash
#
# ci-local.sh — Run the full CI suite locally before pushing.
#
# Mirrors what GitHub Actions does for backend + frontend:
#   - Format (Spotless backend / Prettier frontend)
#   - Lint (ESLint frontend)
#   - Unit tests (Surefire backend / Vitest frontend)
#   - Build (Quarkus package / Vite build)
#   - Code coverage report
#
# Integration tests (Failsafe + Testcontainers) and Compose healthchecks
# are NOT included by default because they require Docker. Pass --with-integration
# to opt in.
#
# Usage:
#   ./scripts/ci-local.sh                 # frontend + backend unit/integration/build
#   ./scripts/ci-local.sh --with-integration  # also runs Testcontainers + Compose
#   ./scripts/ci-local.sh --frontend-only    # only the frontend CI
#   ./scripts/ci-local.sh --backend-only     # only the backend CI
#   ./scripts/ci-local.sh --fix              # auto-fix formatting where possible

set -euo pipefail

# ─── Args ────────────────────────────────────────────────────────────────────
WITH_INTEGRATION=0
FRONTEND_ONLY=0
BACKEND_ONLY=0
AUTO_FIX=0

for arg in "$@"; do
  case "$arg" in
    --with-integration) WITH_INTEGRATION=1 ;;
    --frontend-only)    FRONTEND_ONLY=1 ;;
    --backend-only)     BACKEND_ONLY=1 ;;
    --fix)              AUTO_FIX=1 ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \?//'
      exit 0 ;;
    *)
      echo "Unknown flag: $arg" >&2
      echo "Run with --help for usage." >&2
      exit 2 ;;
  esac
done

# ─── Helpers ──────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No color

PASS=0
FAIL=0
SKIP=0
declare -a RESULTS

step() {
  echo -e "\n${BLUE}${BOLD}▶ $*${NC}"
}

ok() {
  echo -e "${GREEN}  ✓ $*${NC}"
  RESULTS+=("✓ $*")
  PASS=$((PASS + 1))
}

fail() {
  echo -e "${RED}  ✗ $*${NC}"
  RESULTS+=("✗ $*")
  FAIL=$((FAIL + 1))
}

skip() {
  echo -e "${YELLOW}  ⊘ $*${NC}"
  RESULTS+=("⊘ $*")
  SKIP=$((SKIP + 1))
}

# ─── Backend ───────────────────────────────────────────────────────────────────
backend_format() {
  step "Backend · Format Check (Spotless)"
  if [[ $AUTO_FIX -eq 1 ]]; then
    (cd wyrdly-backend && ./mvnw spotless:apply -q 2>&1 | tail -3)
    ok "Spotless applied"
  else
    if (cd wyrdly-backend && ./mvnw spotless:check -q 2>&1 | tail -3); then
      ok "Spotless"
    else
      fail "Spotless (run with --fix to auto-format)"
    fi
  fi
}

backend_unit_tests() {
  step "Backend · Unit Tests (Surefire)"
  if (cd wyrdly-backend && ./mvnw test -q 2>&1 | tail -8); then
    ok "Surefire"
  else
    fail "Surefire"
  fi
}

backend_integration_tests() {
  step "Backend · Integration Tests (Failsafe + Testcontainers)"
  if command -v docker &>/dev/null; then
    if (cd wyrdly-backend && ./mvnw verify -q 2>&1 | tail -8); then
      ok "Failsafe + Testcontainers"
    else
      fail "Failsafe + Testcontainers"
    fi
  else
    skip "Docker not available; integration tests require Testcontainers"
  fi
}

backend_coverage() {
  step "Backend · Code Coverage (JaCoCo)"
  if (cd wyrdly-backend && ./mvnw verify -q 2>&1 | tail -5); then
    ok "JaCoCo coverage"
    echo -e "       ${BOLD}Report:${NC} wyrdly-backend/target/site/jacoco/index.html"
  else
    fail "JaCoCo"
  fi
}

backend_build() {
  step "Backend · Package Build (Quarkus)"
  if (cd wyrdly-backend && ./mvnw package -DskipTests -q 2>&1 | tail -5); then
    ok "Quarkus build"
  else
    fail "Quarkus build"
  fi
}

# ─── Frontend ──────────────────────────────────────────────────────────────────
frontend_install() {
  step "Frontend · Install (pnpm)"
  if (cd wyrdly-frontend && pnpm install --frozen-lockfile 2>&1 | tail -5); then
    ok "pnpm install"
  else
    fail "pnpm install"
  fi
}

frontend_format() {
  step "Frontend · Format Check (Prettier)"
  if [[ $AUTO_FIX -eq 1 ]]; then
    (cd wyrdly-frontend && pnpm run format 2>&1 | tail -3)
    ok "Prettier applied"
  else
    if (cd wyrdly-frontend && pnpm run format:check 2>&1 | tail -3); then
      ok "Prettier"
    else
      fail "Prettier (run with --fix to auto-format)"
    fi
  fi
}

frontend_lint() {
  step "Frontend · Lint (ESLint)"
  if (cd wyrdly-frontend && pnpm run lint 2>&1 | tail -5); then
    ok "ESLint"
  else
    fail "ESLint"
  fi
}

frontend_unit_tests() {
  step "Frontend · Unit Tests (Vitest)"
  if (cd wyrdly-frontend && pnpm run test 2>&1 | tail -5); then
    ok "Vitest"
  else
    fail "Vitest"
  fi
}

frontend_coverage() {
  step "Frontend · Code Coverage (Vitest v8)"
  if (cd wyrdly-frontend && pnpm run test:coverage 2>&1 | tail -5); then
    ok "Vitest coverage"
    echo -e "       ${BOLD}Report:${NC} wyrdly-frontend/coverage/index.html"
  else
    fail "Vitest coverage"
  fi
}

frontend_build() {
  step "Frontend · Production Build (Vite)"
  if (cd wyrdly-frontend && pnpm run build 2>&1 | tail -5); then
    ok "Vite build"
  else
    fail "Vite build"
  fi
}

# ─── Summary ───────────────────────────────────────────────────────────────────
summary() {
  echo ""
  echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"
  echo -e "${BOLD}  CI Local Summary${NC}"
  echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"
  for r in "${RESULTS[@]}"; do
    echo -e "  $r"
  done
  echo ""
  echo -e "  ${GREEN}Passed: $PASS${NC}  ${RED}Failed: $FAIL${NC}  ${YELLOW}Skipped: $SKIP${NC}"
  echo ""
  if [[ $FAIL -gt 0 ]]; then
    echo -e "${RED}${BOLD}  ✗ CI failed — fix the issues above before pushing.${NC}"
    echo -e "${RED}${BOLD}    Tip: re-run with --fix to auto-format.${NC}"
    exit 1
  else
    echo -e "${GREEN}${BOLD}  ✓ All checks passed. Safe to push.${NC}"
    exit 0
  fi
}

trap summary EXIT

# ─── Run ───────────────────────────────────────────────────────────────────────
echo -e "${BOLD}Wyrdly — Local CI Runner${NC}"
echo -e "  Root: $ROOT"
echo -e "  Integration tests: $([[ $WITH_INTEGRATION -eq 1 ]] && echo 'enabled' || echo 'disabled')"
echo -e "  Auto-fix:          $([[ $AUTO_FIX -eq 1 ]] && echo 'enabled' || echo 'disabled')"

# Backend
if [[ $FRONTEND_ONLY -eq 0 ]]; then
  backend_format
  backend_unit_tests
  if [[ $WITH_INTEGRATION -eq 1 ]]; then
    backend_integration_tests
    backend_coverage
  fi
  backend_build
fi

# Frontend
if [[ $BACKEND_ONLY -eq 0 ]]; then
  frontend_install
  frontend_format
  frontend_lint
  frontend_unit_tests
  if [[ $WITH_INTEGRATION -eq 1 ]]; then
    frontend_coverage
  fi
  frontend_build
fi
