# Frontend Progress

| Phase | Scope | Status |
|---|---|---|
| 1 | Backend analysis & planning | ✅ Done |
| 2 | Foundation (Vite, Tailwind, router, API client, stores, tokens, components, layouts) | ✅ Done |
| 3 | Auth & common pages | ✅ Done |
| 4 | Passenger | ✅ Done |
| 5 | Rider | ✅ Done |
| 6 | Vehicles | ✅ Done |
| 7 | Admin | ✅ Done (rides/bookings read-only — G-14) |
| 8 | Testing & refinement | ✅ 45 automated tests, lint/type-check/build clean, live smoke test against backend |

## Email/phone OTP verification + Google sign-in (2026-11-13)

Backend and frontend change (backend change explicitly requested).

- **Backend:** Flyway V11 (verification flags, `google_subject`, nullable password, `verification_codes`);
  `verification` module (6-digit SecureRandom codes stored as SHA-256, 10 min expiry, 5 attempts, 60 s resend
  cooldown, 5 codes/hour/channel, sent after commit via SMTP / Twilio REST or dev log); register returns
  `VERIFICATION_REQUIRED` instead of tokens; login of unverified accounts → 403 with challenge; Google ID tokens
  verified locally (Google JWKS, issuer, audience, expiry) via spring-security-oauth2-jose; new Google users add
  and verify a phone; same-email accounts are linked.
- **Frontend:** `/verify` (code entry with resend countdown), `/complete-signup` (Google phone step),
  `GoogleSignInButton` (Google Identity Services, theme-aware), Login/Register wired to the flow, Profile shows
  verification status and verifies a changed phone, Settings handles Google-only accounts.
- **Tests:** backend 109+ tests incl. `VerificationAndGoogleIntegrationTest` (Testcontainers PostgreSQL);
  frontend 67 tests incl. `verification.test.tsx`.

## UI/UX refinement pass (2026-11-13)

- **Themes:** Dark / Light / System via `src/theme/` (`useTheme.ts` store, `ThemeProvider.tsx`) and tokens in
  `src/styles/theme.css` (`@theme inline` → `--vr-*` variables switched by `<html data-theme>`). Inline boot script in
  `index.html` applies the saved choice before first paint. Preference stored in localStorage (`veloceride.theme`) and
  synced across tabs; the backend has no preferences API, so nothing is sent to the server.
- **Selectors:** icon menu in public/auth headers, Light/Dark/System segmented control in the profile menu and Settings → Appearance.
- **Landing page** at `/` for visitors (signed-in users go to `/dashboard`): hero + public search, real "Rides leaving soon"
  from `GET /api/rides` (hidden when empty), How it works, Why VeloceRide (only real features), CTA, compact footer.
- **Dashboard:** one primary/secondary action pair, intentional empty states with a single text link, "first ride" vs
  "next ride" wording from real data, Recent activity from real notifications (omitted when none), solid cards.
- **Global:** no hardcoded colours left in components (logo, map markers/tiles/tooltips, shadows, overlay are tokenised);
  notification bell is now a popover of the latest 5 notifications; dashed borders removed; reduced-motion support.
- **Light-theme accent:** `#0B7F75` for text/buttons (brief's `#0F9F91` fails WCAG AA for small text and white-on-teal
  labels); `#0F9F91` kept for non-text indicators. Light "faint" text `#657284` for the same reason.
- **Verified:** 56 automated tests; headless Edge via DevTools protocol at 375/768/1280/1440 px in both themes —
  87 checks, no horizontal overflow, theme applied on every page, signed-in pages reachable after real login.

## Pinned stack (Node 20.15 compatible)

React 19.3 · TypeScript 5.9 · Vite 6.4 · React Router 7.18 · TanStack Query 5.107 · Zustand 5.1 · Axios 1.21 ·
React Hook Form 7.92 + Zod 4.6 (@hookform/resolvers 5.9) · Tailwind 4.3 · lucide-react 1.72 · Leaflet 1.9 / react-leaflet 5 ·
@stomp/stompjs 7.5 · Vitest 3.2 + Testing Library + MSW 2 + jsdom 26 · ESLint 9.

Newer majors (Vite 7/8, Vitest 5, jsdom 31, MSW 3, ESLint 10) need Node ≥ 20.19/22.12; upgrade Node first to move to them.

## Page inventory (status per FRONTEND_PAGE_SPECIFICATION.md)

| # | Page | Route | Notes |
|---|---|---|---|
| 01 | Home | `/` | |
| 02 | Register | `/register` | |
| 03 | Login | `/login` | `returnTo` support |
| 04 | Forgot / reset password | `/forgot-password`, `/reset-password?token=` | Email delivery missing server-side (G-10) |
| 05 | Dashboard | `/dashboard` | Next trip, next/ongoing drive, waiting requests |
| 06 | Profile | `/profile` | |
| 07 | Edit profile | `/profile/edit` | Picture is a URL (G-11) |
| 08 | Notifications | `/notifications` | Live push via STOMP; deep links by type |
| 09 | Reviews & ratings | `/reviews`, `/users/:id` | |
| 10 | Settings | `/settings` | Mode, password, sign out everywhere, deactivate |
| 11–12 | Search + results | `/rides/search` | Seats flagged client-side (G-02) |
| 13 | Ride details | `/rides/:id` | Map when coordinates exist |
| 14 | Booking confirmation | modal | |
| 15 | My bookings | `/bookings` | Upcoming merges PENDING+ACCEPTED (G-09) |
| 16 | Booking details | `/bookings/:id` | Passenger & driver views, simulated payment (no CASH — G-07) |
| 17 | Passenger tracking | `/rides/:id/track` | STOMP, stale indicator, eligibility checked first |
| 18 | Review driver | modal | |
| 19 | Publish ride | `/rides/new` | Optional map pins |
| 20 | My rides | `/my-rides` | |
| 21 | Ride management / edit | `/my-rides/:id`, `/my-rides/:id/edit` | Locks committed terms when bookings exist |
| 22 | Booking requests | `/requests` | |
| 23 | Passenger management | section of `/my-rides/:id` | |
| 24 | Driver console | `/my-rides/:id/live` | Geolocation → `/app/location/update`, REST fallback |
| 25 | Review passengers | section of `/my-rides/:id` | |
| 26–28 | Vehicles | `/vehicles`, `/vehicles/new`, `/vehicles/:id/edit` | |
| 29–33 | Admin | `/admin/*` | + vehicle verification queue |

## Not built (intentionally)

- Stops, amenities, notification preferences, file uploads, chat, admin ride/booking actions, CASH confirmation by
  driver — the backend has no API for them (see BACKEND_API_GAPS.md). No placeholders fake them.
- Admin user detail page: the list already shows every field `AdminUserResponse` has.

## Known follow-ups

- Upgrade Node to ≥ 22.12 and move to Vite 7+/Vitest 5.
- Close high-severity backend gaps G-07 (driver payment lookup) and G-10 (reset email delivery).
- Add Playwright E2E against a disposable database (current live check is a read-only smoke test).
