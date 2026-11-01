# FraudShield — Analyst Dashboard (UI)

React + Vite frontend for the FraudShield fraud-detection platform. Runs entirely on **mock data** — no backend required.

## Run

```bash
cd UI
npm install
npm run dev
```

Opens at http://localhost:5173

## Demo accounts

| Email | Password | Role | Can do |
|---|---|---|---|
| `HKadmin@fraudshield.io` | `Admin@123` | ADMIN | Everything (toggle rules) |
| `HKanalyst@fraudshield.io` | `Analyst@123` | ANALYST | Review + override verdicts |
| `HKsuperadmin@fraudshield.io` | `SuperAdmin@123` | SUPER_ADMIN | Everything |

## Features

- **Login** with role-based access (session persisted in localStorage)
- **Dashboard** — KPI stat cards + recent flagged transactions
- **Transactions** — searchable/filterable table, detail drawer showing triggered fraud rules, override verdict (analysts+)
- **Fraud Rules** — enable/disable rules (admins only; analysts see a 403-style notice)
- **Audit Log** — immutable timeline of analyst actions
- **Dark / light theme** toggle, responsive layout

## Structure

```
src/
  main.jsx          entry
  App.jsx           routes + auth guard
  auth.jsx          mock auth context
  mockData.js       users, transactions, rules, audit log
  styles.css        design system (matches the FraudShield brand)
  components/Layout.jsx
  pages/Login.jsx Dashboard.jsx Transactions.jsx Rules.jsx Audit.jsx
```

## Build

```bash
npm run build      # outputs to dist/
npm run preview
```
