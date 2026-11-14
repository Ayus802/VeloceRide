# VeloceRide Frontend

React + TypeScript client for the `rideshare-backend` Spring Boot API.

## Run

```bash
cp .env.example .env        # VITE_API_BASE_URL=http://localhost:8080
npm install
npm run dev                 # http://localhost:5173
```

The backend must allow this origin: `FRONTEND_URL=http://localhost:5173` in `rideshare-backend/.env`.

## Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Dev server |
| `npm run build` | Type-check and production build to `dist/` |
| `npm run lint` | ESLint |
| `npm test` | Vitest (unit + component tests, MSW stands in for the API) |

## Structure

```
src/
  api/          axios client (envelope unwrap, errors, single-flight token refresh), query client + keys
  services/     one module per backend area; realtime/stompClient.ts for STOMP
  store/        auth session, toasts (Zustand)
  hooks/        realtime session, mode switch, live tracking, location sharing
  components/   common UI kit, layout, dropdowns, modals, forms, maps, rides, bookings, reviews
  pages/        route components (public, auth, dashboard, profile, passenger, rider, vehicles, admin)
  routes/       router + guards
  utils/        formatting, validation schemas (mirror backend), error helpers
docs/           backend analysis, API/WebSocket mapping, gaps, page spec, architecture, progress
```

## Notes

- The backend is authoritative for permissions, seats, prices and statuses; the UI mode (Passenger/Rider) is only a preference.
- Ride dates/times are shown exactly as stored (platform time zone `Asia/Kolkata`); timestamps are shown in the browser's zone.
- Payments use the backend's **simulated** provider — no real money moves.
