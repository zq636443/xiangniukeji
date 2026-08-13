# External Order Auto-Renewal Income Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Accrue external-order renewal income at expiry, extend the rental period, include each renewal in monthly settlement, and safely backfill overdue production orders.

**Architecture:** Store each accrued renewal as an immutable, uniquely keyed event. A transactional service locks due external orders, creates a renewal-specific settlement snapshot and income entries without sign fee, then advances `expected_return_at`. Scheduled and startup runners call the same idempotent service; statement generation reads accrued renewal events by `period_start_at`.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring JDBC, MySQL 8, Flyway, JUnit 5, AssertJ.

---

### Task 1: Renewal event schema and repository

**Files:**
- Create: `server/rental-api/src/main/resources/db/migration/V59__external_order_auto_renewal_income.sql`
- Create: `server/rental-api/src/main/java/com/xniu/rental/externalorder/model/ExternalOrderRenewalEvent.java`
- Create: `server/rental-api/src/main/java/com/xniu/rental/externalorder/repository/ExternalOrderRenewalRepository.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/settlement/model/IncomeSourceType.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/settlement/model/SnapshotSourceType.java`

- [ ] Add a failing integration test asserting `external_order_renewal_event` can uniquely store one event per order and period start.
- [ ] Run `./mvnw -Dtest=ExternalRentalOrderIntegrationTests#dueExternalOrderShouldAccrueRenewalIncome test` and verify it fails because the renewal service/schema is absent.
- [ ] Add the event table, indexes, `EXTERNAL_RENEWAL` source enums, record, and JDBC repository methods for due-order locking, event creation, lookup, reversal, and period advancement.
- [ ] Re-run the focused test until schema wiring compiles and the expected behavioral assertion remains the only failure.

### Task 2: Transactional accrual and income generation

**Files:**
- Create: `server/rental-api/src/main/java/com/xniu/rental/externalorder/service/ExternalOrderAutoRenewalService.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/settlement/service/SettlementService.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/settlement/service/SettlementIncomeService.java`
- Modify: `server/rental-api/src/test/java/com/xniu/rental/ExternalRentalOrderIntegrationTests.java`

- [ ] Test that a due active order creates one event, advances 30 days, creates channel/platform/store/maintenance/referral/investor entries, and creates no `MERCHANT_ORDER_FEE` or `PLATFORM_ORDER_FEE_SERVICE_FEE` entry.
- [ ] Test exact expected amounts from a 129 yuan renewal under 5%/3%/15%/10%/20% rules.
- [ ] Test a second scan is idempotent and a multi-period overdue order accrues each elapsed period once.
- [ ] Implement renewal snapshot cloning from the original order snapshot, recalculating the new settlement base and period battery cost while preserving rates, channel, store, assets, and investor assignment.
- [ ] Implement income creation with `EXTERNAL_RENEWAL`, event id as source id, event number as source number, zero sign fee, and `period_start_at` as occurred time.
- [ ] Implement the locked scan loop and operation log entry, then run focused tests to green.

### Task 3: Schedule, startup backfill, and lifecycle safety

**Files:**
- Create: `server/rental-api/src/main/java/com/xniu/rental/job/ExternalOrderRenewalScheduleJob.java`
- Create: `server/rental-api/src/main/java/com/xniu/rental/externalorder/service/ExternalOrderRenewalBackfillRunner.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/externalorder/service/ExternalRentalOrderService.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/externalorder/model/ExternalOrderOperationType.java`
- Modify: `server/rental-api/src/test/java/com/xniu/rental/ExternalRentalOrderIntegrationTests.java`

- [ ] Test that inactive, terminated, not-due, and auto-renew-disabled orders are skipped.
- [ ] Test termination removes pending renewal income and reverses events, while locked statement lines prevent termination.
- [ ] Test deletion removes reversible renewal events/snapshots and refuses non-pending renewal income.
- [ ] Add hourly scheduled scan and post-startup idempotent backfill using the same service.
- [ ] Extend terminate/delete cleanup checks and operations to renewal sources, then run focused tests to green.

### Task 4: Monthly settlement integration

**Files:**
- Modify: `server/rental-api/src/main/java/com/xniu/rental/settlement/repository/SettlementStatementRepository.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/settlement/repository/SettlementIncomeRepository.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/settlement/service/SettlementStatementService.java`
- Modify: `server/rental-api/src/test/java/com/xniu/rental/ExternalRentalOrderIntegrationTests.java`

- [ ] Test that renewal events are included by `period_start_at` in the correct statement month and create merchant/investor lines without increasing business order count.
- [ ] Test statement settlement marks matching `EXTERNAL_RENEWAL` income entries settled.
- [ ] Add renewal-event month query and statement line registration from its renewal snapshot.
- [ ] Extend statement-to-income source matching for `EXTERNAL_RENEWAL`, then run the focused settlement tests to green.

### Task 5: Full verification, Git publish, and production deployment

**Files:**
- Modify: `docs/feature-content-updates-changelog.md`

- [ ] Run `./mvnw test` and `./mvnw package -DskipTests` in `server/rental-api`.
- [ ] Run admin, merchant-mini, and user-mini typecheck/build commands and `git diff --check`.
- [ ] Verify V1 to V59 migration in the test database and inspect the staged diff for secrets.
- [ ] Commit task files, push `feature/content-updates`, and verify local, tracking, and remote hashes match.
- [ ] Back up production database, API jar, and admin bundle; upload the new jar and recreate only the API container.
- [ ] Verify Flyway V59, API/admin health, zero unexpected restarts, and public endpoint response.
- [ ] Query production to confirm the 12 prior due orders each have one renewal event, renewal base totals 1548 yuan, expected return dates moved 30 days, no renewal sign-fee income exists, and per-beneficiary totals balance to renewal base after battery costs.
