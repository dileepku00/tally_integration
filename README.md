# Tally Integration Utility

This utility connects to a running Tally server, lets the user enter the Tally IP and port, loads the running firms, and shows a firm-wise ledger dashboard with pendency tracking.

## What Changed

- Tally connection is now configurable from the UI using host and port.
- Tally can be launched from the UI using the installed executable path.
- Tally connection can be tested from the UI before loading firms.
- Firms can be fetched from the running Tally instance and selected from a dropdown.
- Account team status is controlled through a fixed dropdown:
  - `work in progress`
  - `pending for approval`
  - `closed`
  - `balance reconcilled`
- Pendency is shown with summary cards and a cleaner chart-style view.
- The Java backend now serves the built frontend, which makes Windows setup simpler.

## Prerequisites

- Java 11 or higher
- Node.js and npm for building the frontend
- Tally running with XML/HTTP enabled

## First-Time Setup

### 1. Build the frontend

```powershell
cd "d:\git hub\tally_integration\frontend"
npm install
npm run build
```

### 2. Start the dashboard

```powershell
cd "d:\git hub\tally_integration"
start-dashboard.bat
```

Then open `http://localhost:8090`.

## Development Mode

### Backend

```powershell
cd "d:\git hub\tally_integration\backend"
run.bat
```

### Frontend

```powershell
cd "d:\git hub\tally_integration\frontend"
npm run dev
```

The Vite frontend proxies `/api` calls to the backend.

### One-click development start

```powershell
cd "d:\git hub\tally_integration"
start-dev.bat
```

This opens two Windows terminals:
- backend server
- frontend Vite server

Open the localhost URL printed by the frontend terminal, usually `http://localhost:5173`.
The dev script starts the backend on port `8090` to avoid conflicts with older local servers.

## Data Files

- `backend/settings.json`
  - stores Tally host, port, and selected firm
- `backend/ledger_meta.json`
  - stores editable follow-up data per firm and date
- `backend/ledgers.json`
  - legacy fallback data if metadata has not been created yet

## Notes

- If Tally is unavailable, the backend falls back to saved data, then sample data.
- The firm list depends on what the running Tally instance returns for company export.
- The dashboard is optimized for Windows users who want a simpler start flow.
