# Feature: Auth Tokens In-Memory & HttpOnly Cookie Architecture

## Objective
Migrate frontend authentication from `localStorage` persistence to an in-memory Access Token storage architecture, relying on the backend's existing `HttpOnly` Refresh Token cookie for secure session persistence and silent rotation.

## Architecture Context & Problem Statement
Currently, `wyrdly-frontend` stores JWT access tokens in `localStorage` (`wyrdly_token`).
While standard in simple SPAs, this exposes the application to token theft via Cross-Site Scripting (XSS) and was flagged by `react-doctor` (`auth-token-in-web-storage` x4).

The Quarkus backend (`AuthResource.java`) **already implements**:
1. `NewCookie refreshCookie = buildRefreshTokenCookie(response.refreshToken());` with `httpOnly(true)`, `path("/api/auth")`, `maxAge(7 days)`.
2. `@POST /api/auth/refresh` with `@CookieParam("refreshToken")` to issue fresh access tokens.
3. `@POST /api/auth/logout` with `buildClearRefreshTokenCookie()` (`maxAge(0)`).

## Proposed Architecture
1. **In-Memory Token Store (`tokenStore.ts`)**:
   - Module-level variable `let inMemoryAccessToken: string | null = null`.
   - Accessors: `getAccessToken()`, `setAccessToken(token: string | null)`.
2. **Axios Request Interceptor (`axios.ts`)**:
   - Attach `Authorization: Bearer ${getAccessToken()}` if present.
   - Do NOT access `localStorage`.
3. **Axios Response Interceptor (`axios.ts`)**:
   - On 401, call `/api/auth/refresh` with `withCredentials: true`.
   - On success, update `setAccessToken(newToken)` and replay failed queue.
   - On failure, clear in-memory token and reject.
4. **Auth Context (`AuthContext.tsx`)**:
   - `token` state synced with `setAccessToken`.
   - On app startup (`initAuth`), invoke `authApi.refresh()` silently using the `HttpOnly` cookie.
   - If valid, set in-memory token and user state; if invalid (401), gracefully settle as unauthenticated.
   - Remove all `localStorage.getItem("wyrdly_token")`, `setItem`, `removeItem`.
5. **Testing & Verification**:
   - Update `AuthPage.test.tsx`, `ChatPage.test.tsx`, `FeedPage.test.tsx`, `ProfilePage.test.tsx`, `MainLayout.test.tsx`, and all mock setups.
   - Run Vitest (all 48 test suites).
   - Run ESLint, Prettier, and Vite build.
   - Re-run `react-doctor` to observe elimination of `auth-token-in-web-storage`.

## Tasks
- [x] TASK-01: Create `src/api/tokenStore.ts` with in-memory getter and setter.
- [x] TASK-02: Refactor `src/api/axios.ts` to use `tokenStore` instead of `localStorage`.
- [x] TASK-03: Refactor `src/features/auth/context/AuthContext.tsx` to keep access token in memory and bootstrap via silent refresh.
- [x] TASK-04: Update test mocks across all test suites to support in-memory token and silent refresh (added `src/api/tokenStore.test.ts`).
- [x] TASK-05: Run full verification (Vitest 49/49 suites passed, ESLint, Prettier, Vite build, react-doctor 100/100).
- [x] TASK-06: Commit work units, push `feature/auth-refresh-tokens-httponly-cookie`, and open PR to `develop`.


