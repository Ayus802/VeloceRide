# VeloceRide

A full-stack ride-sharing platform with a Java Spring Boot backend and a React + Vite frontend.

This project includes:
- Backend API and business logic in [rideshare-backend](rideshare-backend)
- Frontend app in [veloceride-frontend](veloceride-frontend)
- PostgreSQL + app container setup with Docker Compose

## Tech stack

### Backend
- Java 17
- Spring Boot 3.4.5
- Spring Security + JWT
- Spring Data JPA + Hibernate
- PostgreSQL
- Flyway migrations
- WebSockets / STOMP
- OpenAPI / Swagger

### Frontend
- React 19
- Vite
- TypeScript
- React Query
- Zustand
- Leaflet / maps

---

## Project structure

```text
pr/
├── rideshare-backend/        # Spring Boot API
│   ├── src/
│   ├── .env.example
│   ├── .env
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── pom.xml
│   └── mvnw
├── veloceride-frontend/      # React app
│   ├── src/
│   ├── package.json
│   ├── vite.config.ts
│   └── .env
└── README.md                 # this file
```

---

## Prerequisites

Before you start, make sure you have:

- Java 17 or later
- Maven wrapper is included, so you usually do not need a global Maven install
- Node.js 20+
- npm or pnpm
- Docker Desktop (for PostgreSQL and containerized runtime)

Optional but useful:
- Git
- VS Code with Java and JavaScript extensions

---

## 1) Clone and install

```bash
git clone <your-repo-url>
cd pr
```

Install frontend dependencies:

```bash
cd veloceride-frontend
npm install
```

The backend uses Maven wrapper, so no global installation is required:

```bash
cd ../rideshare-backend
./mvnw -v
```

On Windows PowerShell:

```powershell
cd rideshare-backend
.\mvnw.cmd -v
```

---

## 2) Environment configuration

### Backend
Create or update the backend environment file:

```bash
cd rideshare-backend
copy .env.example .env
```

Then set the values in `.env` using your own secrets and credentials. Do not commit this file to version control.

Example structure:

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
APP_PORT=8080

DB_URL=jdbc:postgresql://localhost:5432/your_db
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password

JWT_SECRET=your-strong-jwt-secret
FRONTEND_URL=http://localhost:5173

ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=your-admin-password

OTP_LOG_CODES=true

TWILIO_ACCOUNT_SID=your_twilio_account_sid
TWILIO_AUTH_TOKEN=your_twilio_auth_token
TWILIO_FROM_NUMBER=+12345678901

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email
MAIL_PASSWORD=your_email_password
MAIL_STARTTLS=true
MAIL_FROM=Your App <no-reply@example.com>

GOOGLE_CLIENT_ID=your_google_client_id
```

Important:
- Keep secrets in `.env`
- Do not share them in Git or screenshots
- Use values that match your local or cloud environment

### Frontend
Create the frontend `.env` file if it does not exist:

```bash
cd ../veloceride-frontend
copy .env.example .env
```

Use values like:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws
VITE_PLATFORM_TIME_ZONE=Asia/Kolkata
VITE_GOOGLE_CLIENT_ID=your_google_client_id
```

---

## 3) Run the backend

### Option A: Docker Compose
From the backend directory:

```bash
cd rideshare-backend
docker compose up --build
```

This starts:
- PostgreSQL database
- backend app on port `8080`

To run the app in the background:

```bash
docker compose up --build -d
```

To stop it:

```bash
docker compose down
```

### Option B: Local development run

```bash
cd rideshare-backend
./mvnw spring-boot:run
```

On Windows:

```powershell
cd rideshare-backend
.\mvnw.cmd spring-boot:run
```

The app will be available at:
- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html`

---

## 4) Run the frontend

From the frontend directory:

```bash
cd veloceride-frontend
npm run dev
```

The frontend usually runs at:
- `http://localhost:5173`

---

## 5) Build production artifacts

### Backend

```bash
cd rideshare-backend
./mvnw clean package
```

The jar will be generated in:

```text
rideshare-backend/target/rideshare-backend-1.0.0.jar
```

Then run it:

```bash
java -jar target/rideshare-backend-1.0.0.jar
```

### Frontend

```bash
cd veloceride-frontend
npm run build
```

The production build is generated in:

```text
veloceride-frontend/dist/
```

---

## 6) Useful backend commands

Run compile only:

```bash
./mvnw clean test-compile
```

Run all tests:

```bash
./mvnw clean test
```

Run on a specific profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

View logs from Docker:

```bash
docker compose logs -f app
```

---

## 7) Troubleshooting

### Backend does not start
- Check `.env` values
- Ensure the database is reachable
- Ensure Java 17 is installed and active
- Ensure Docker is running if using PostgreSQL container

### Frontend cannot reach the backend
- Confirm `VITE_API_BASE_URL` matches your backend URL
- Check CORS settings in the backend `.env`
- Confirm backend is running on port 8080

### SMS/Otp is not sending
- Verify the Twilio account credentials are valid
- Ensure the sender number is a real Twilio number in the correct account
- Ensure the recipient number is verified when using a trial account
- Keep `OTP_LOG_CODES=true` during testing if you want logs instead of live delivery

### Google sign-in fails
- Ensure `GOOGLE_CLIENT_ID` matches the client created in Google Cloud Console
- Ensure the frontend and backend use the same registered client ID

---

## 8) Security notes

- Never commit `.env` files
- Never expose raw credentials in docs, logs, screenshots, or issues
- Use environment variables or secret managers in real deployments
- Rotate tokens and passwords after setup if they were shared in a local environment

---

## 9) Recommended local workflow

For normal local development:

1. Start PostgreSQL via Docker or local DB
2. Set backend `.env`
3. Run backend with Spring Boot
4. Install frontend dependencies
5. Run frontend with Vite
6. Open `http://localhost:5173`

---

## 10) Default local URLs

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html`

---

## 11) Contributing

Use feature branches for changes and keep the setup instructions up to date when adding dependencies or env variables.

---

## License

This project is for local development and demonstration use unless otherwise specified by the repository owner.
