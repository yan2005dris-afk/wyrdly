# Feature: Fix and Refactor PR #54 (HU09 Post Reactions and HU08 Feed)

## Objective
Address critical findings from PR #54 review: resolve merge conflicts with `develop` (preserving skeleton loading in `FeedPage` and merged properties), eliminate domain-layer framework coupling (`@JsonDeserialize` in `ReactionType`), fix JAX-RS path matching and identity retrieval in Redis filters (`ReactionIdempotencyFilter` and `ReactionRateLimitFilter`), adopt declarative `@Valid` in `PostResource`, format backend with Spotless to pass CI, and verify 100% test coverage.

## Scope
- Merge resolution: Sync branch with `origin/develop`, resolving conflicts in `.gitignore`, `pnpm-lock.yaml`, `application.properties`, and `FeedPage.tsx` (preserving skeletons from PR #57).
- Architecture & Domain: Decoupled `com.wyrdly.post.domain.model.ReactionType` from Jackson; moved deserializer to `com.wyrdly.infrastructure.jackson.ReactionTypeDeserializer` and applied to `ReactPostRequest` DTO.
- JAX-RS Filters: Normalized path matching in `ReactionIdempotencyFilter` and `ReactionRateLimitFilter`; used `jwt.getSubject()` for consistent `userId` keys.
- Declarative Validation: Adopted standard `@Valid` on `PostResource` endpoint methods instead of imperative manual validation boilerplate.
- Code style: Formatted with `mvn spotless:apply` and frontend prettier.
- Verification: Ran backend test suite (`mvn test`) and frontend test suite (`pnpm test` + build + lint).

## Tasks
- [x] TASK-1: Merge `origin/develop` into `13-featbackend-hu09---sistema-de-reacciones-a-publicaciones` and cleanly resolve all conflicts.
- [x] TASK-2: Refactor domain model `ReactionType` to remove Jackson annotations, ensuring clean hexagonal boundaries.
- [x] TASK-3: Fix path normalization and user identity extraction in `ReactionIdempotencyFilter` and `ReactionRateLimitFilter`.
- [x] TASK-4: Refactor `PostResource` to use declarative `@Valid` validation.
- [x] TASK-5: Run code formatting (`mvn spotless:apply` and frontend prettier) and execute full test verification.

## Verification Evidence
- Backend Tests: 146/146 tests passed in `mvn test` (Failures: 0, Errors: 0, Skipped: 0).
- Backend Spotless: `mvn spotless:check` passed with 0 violations.
- Frontend Tests: 48/48 test files passed (261/261 tests passed).
- Frontend Lint & Format: `pnpm run lint` and `pnpm run format:check` 100% compliant.
- Frontend Build: `pnpm run build` (`tsc -b && vite build`) passed with exit code 0.
