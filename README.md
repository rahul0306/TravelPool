# TravelPool ✈️🏨💬
**TravelPool** is a collaborative trip planning and expense-splitting Android app built with **Jetpack Compose**, **Firebase (Auth + Firestore)**, and a **Cloudflare Worker** backend for travel search.

---

## Core Features

### Trips
- Create a trip with destination + date range + optional starting airport
- Invite others via **Join Code**
- Join trips using Join Code

### Itinerary
- Add itinerary items (flight/hotel/activity/etc.)
- Edit and delete items

### Pool (Shared Expenses)
- Add expenses and contributions
- Compute group costs and track who paid / who owes
- Settlements support

### Chat
- In-trip messaging so members can coordinate travel planning

### Notifications
- Per-user notifications

### Travel Search (Backend)
- A Cloudflare Worker powers endpoints for **flight + hotel** search
- API secrets are stored in Cloudflare environment variables (not committed)

---

## Tech Stack

**Android**
- Kotlin, Jetpack Compose (Material 3)
- Coroutines + Flow
- MVVM architecture (ViewModel + Repository pattern)

**Firebase**
- Firebase Authentication
- Cloud Firestore
- Firestore Security Rules (`firestore.rules`)
- Firestore Indexes (`firestore.indexes.json`)

**Backend**
- Cloudflare Workers (TypeScript) in `search-worker/`
- External travel APIs (Amadeus)
- Credentials stored as Cloudflare secrets/env vars
