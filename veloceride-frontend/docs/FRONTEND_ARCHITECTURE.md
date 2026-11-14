# Frontend Architecture

## Stack

| Concern | Choice | Notes |
|---|---|---|
| Build | Vite + React + TypeScript (strict) | |
| Routing | React Router (data-less `createBrowserRouter`) | route guards as layout routes |
| Styling | Tailwind CSS with design tokens as CSS variables | Inter font |
| HTTP | Axios, one instance | envelope unwrapping + refresh |
| Server state | TanStack Query | caching, loading/error states, invalidation after mutations (added: avoids hand-rolled fetch state on ~35 screens) |
| Client state | Zustand | auth session, UI mode, realtime connection state |
| Forms | React Hook Form + Zod | schemas mirror backend rules exactly |
| Icons | lucide-react | |
| Maps | Leaflet + react-leaflet, OpenStreetMap tiles | |
| Realtime | `@stomp/stompjs` (native WebSocket `/ws`) | no SockJS unless a target network requires it |
| Tests | Vitest + React Testing Library + MSW | MSW mocks only in tests, never in the app |

Exact versions are pinned at install time in Phase 2 (latest stable, mutually compatible) and recorded in `FRONTEND_PROGRESS.md`.

## Folder structure

```
veloceride-frontend/
  docs/                     analysis + specs (this folder)
  .env.example              VITE_API_BASE_URL, VITE_WS_URL
  src/
    api/                    apiClient.ts (axios), envelope/error types, tokenRefresh.ts
    services/               authService, userService, vehicleService, rideService, bookingService,
                            paymentService, reviewService, notificationService, locationService, adminService
    services/realtime/      stompClient.ts
    types/                  dto.ts (from API_MAPPING), enums.ts
    store/                  authStore.ts, uiStore.ts
    hooks/                  queries/ (useRide, useMyBookings…), useRideTracking, useLocationSharing,
                            useNotificationsStream, useDebounce
    routes/                 router.tsx, ProtectedRoute, GuestRoute, AdminRoute
    layouts/                PublicLayout, AppLayout, AdminLayout
    components/
      common/               Button, IconButton, Input, Select, Textarea, Field, Badge (StatusBadge),
                            Spinner, EmptyState, ErrorState, Pagination, Tabs, Avatar, Rating, Table/ResponsiveList
      layout/               Header, NavLinks, Footer, PageHeader
      dropdowns/            Dropdown (menu primitive), ProfileMenu, ModeSwitch, ActionsMenu (⋯)
      modals/               Modal, ConfirmDialog
      forms/                RideSearchForm, RideForm, VehicleForm, ReviewForm, PasswordFields
      maps/                 MapView, LocationPicker, LiveMarker
      feedback/             Toaster
      rides/ bookings/      RideRow, RideSummary, BookingRow…
    pages/                  auth/ dashboard/ passenger/ rider/ vehicles/ reviews/ notifications/ settings/ admin/ errors/
    utils/                  format (money INR, date/time without TZ conversion), errors (map ApiError → form), cn
    constants/              routes, query keys, enum labels
    styles/                 index.css (tokens, base)
    test/                   setup, msw handlers, render helpers
```

## API layer

- `apiClient` — `baseURL = import.meta.env.VITE_API_BASE_URL + '/api'`; request interceptor adds `Authorization: Bearer <access>`.
- Response interceptor **unwraps the envelope**: services return `data` (`T`) directly; 204 returns `void`.
- Errors normalised to `AppError { status, message, fieldErrors: Record<string,string> }` from `ApiResponse.errors`.
- **401 handling:** if the request is not under `/auth/` and has not been retried, run a **single-flight refresh**:
  - in-tab: one shared promise;
  - cross-tab: `navigator.locks.request('veloceride-refresh', …)` and re-read the stored refresh token inside the lock — if another tab already rotated it, reuse its result instead of presenting a stale (revoked) token, which would trigger the backend's revoke-all-sessions rule.
  - success → retry original request once; failure → clear session, redirect to `/login?returnTo=`.
- 403 → no refresh, surfaced as `AppError` (page shows forbidden state).
- Tokens: access + refresh in `localStorage` (needed for cross-tab sync and reload persistence; backend uses bearer tokens, not cookies). Never logged. `storage` event keeps tabs in sync; logout in one tab logs out all.
- No secrets in the frontend; only public URLs in `.env`.

## Routing & guards

- `GuestRoute` (login/register/forgot/reset): redirects authenticated users to `/dashboard`.
- `ProtectedRoute`: requires session; on boot, if a refresh token exists, calls `GET /users/me` (refreshing if needed) before rendering.
- `AdminRoute`: `user.role === 'ADMIN'` for navigation; backend still enforces (403 handled).
- Mode never gates routes.

## Design system

Tokens (CSS variables consumed by Tailwind theme):

| Token | Value |
|---|---|
| `--bg` | `#07111F` |
| `--bg-2` | `#0D1B2A` |
| `--surface` | `#12243A` |
| `--border` | `#1F334A` (subtle navy-grey) |
| `--accent` | `#18C7B7` (hover `#14B0A2`), text on accent `#04201D` |
| `--text` | `#F8FAFC` |
| `--text-2` | `#94A3B8` |
| `--success` | `#3FB68B` · `--warning` `#D9A441` · `--danger` `#E0675F` |

- Type scale: 12 / 14 (base) / 16 / 20 / 24; page titles 24 semibold; no display sizes inside the app.
- Spacing: 4-pt scale; page gutter 16 (mobile) / 24 / 32; content max-width 1120 (forms 560).
- Radius 8 (inputs, buttons), 12 (surfaces). One elevation level; borders preferred over shadows.
- Focus: 2px accent ring with offset on every interactive element.
- Status badges: subtle tinted text, no filled pills except for ACCEPTED/ONGOING emphasis.
- Lists over cards: ride results and bookings are divided rows on a single surface.

## Patterns

- Modals: booking confirm, cancel ride/booking, accept/reject, review, delete vehicle, deactivate, admin reject vehicle/moderate.
- Dropdowns: profile menu, mode switch, sort, filters, ⋯ actions.
- Toasts for mutation outcomes; inline errors for forms.
- Every query view renders Loading → Error (retry) → Empty → Content.
- Mutations invalidate affected query keys (e.g. accept booking → received-requests, ride, dashboard).

## Testing strategy

- Unit: Zod schemas vs backend rules, formatters, error mapping, refresh single-flight.
- Integration (RTL + MSW): login/register, protected redirects, search → details → book, publish ride,
  accept/reject, vehicle CRUD, review submit, notifications read.
- Realtime: stompClient with a mocked `Client` (subscribe/unsubscribe lifecycle, stop on `trackingActive:false`).
- Manual E2E against the running backend (Neon) at the end of each phase.
