# Tally Ledger Dashboard

A simple utility to fetch all ledgers from Tally ERP 9, display them in a dashboard with balances and groups, and allow assigning tasks and adding comments with dates.

## Prerequisites

- Java 11 or higher
- Maven
- Node.js and npm
- Tally ERP 9 running and accessible via HTTP/XML API (default: localhost:9000)

## Setup

### Backend (Java)

1. Navigate to `backend` directory.
2. Install dependencies: `mvn install`
3. Run the server: `mvn exec:java -Dexec.mainClass="com.tally.App"`

### Frontend (React)

1. Navigate to `frontend` directory.
2. Install dependencies: `npm install`
3. Start the development server: `npm run dev`

## Usage

1. Ensure Tally is running and accessible.
2. Start the backend server.
3. Start the frontend server.
4. Open the frontend in browser (usually http://localhost:5173).
5. Click "Refresh from Tally" to fetch ledgers.
6. Edit assigned to and comments in the table; changes are saved automatically.

## Configuration

- Tally URL: Set `TALLY_URL` environment variable if Tally is not on `http://localhost:9000`. Example: `set TALLY_URL=http://localhost:9002`
- Backend port: Set `BACKEND_PORT` before starting both backend and frontend if port 8080 is unavailable. Example:
  - `set BACKEND_PORT=8090`
  - Start backend and frontend in the same terminal session
- Data storage: Ledgers are stored in `backend/ledgers.json`.

## Notes

- Balances are fetched from Trial Balance report.
- Groups are from Masters.
- Only ledgers with non-zero balances are shown.