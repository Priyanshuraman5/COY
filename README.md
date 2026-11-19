# Coy Discover (Spring Boot + React)

Swipe-based "Discover" tab. Java 17 / Spring Boot backend (H2 file DB), React + Vite frontend.

## Run
    cd backend && mvn spring-boot:run      # http://localhost:8080
    cd frontend && npm install && npm run dev   # http://localhost:5173
    cd backend && mvn test

## Login
Phone OTP login. `POST /auth/send-otp {phone}` then `POST /auth/verify-otp {phone, otp}` returns a session token (sent as `Authorization: Bearer <token>`).
The first login creates the user. OTPs are 6 digits, single-use, and expire after 5 minutes.
No SMS provider is wired up, so in demo mode the OTP is printed in the server log and shown on screen (`app.dev-otp-in-response=true`). Set it to `false` and plug in an SMS gateway for production.
Swipes and matches are stored per user. The seeded profiles that "already liked you" like every new user.
If you ran an earlier version, delete `backend/data/` first because the schema changed.

## Crushes tab (extras beyond Part B)
Home stays blank and Discover is unchanged. A third "Crushes" tab adds:
- Email verification: code sent to the email (printed in the server log and shown on screen in demo mode). Verifying is required before adding a crush.
- Adding a crush by phone number: 3 free slots. The response never says whether that number is registered. A crush is revealed only if the other person also added you, and that is computed on read, never stored.
- Invite link: `/?invite=CODE`. When a brand-new user signs up through it, the inviter gets +1 slot.
- Extra-slots screen: shows plans, but `POST /slots/purchase` always returns 402 (no payments).
- Undo window: a new crush is "pending" for `app.undo-window-seconds` (30s for demos). While pending it can be removed with `DELETE /crushes/{phone}`, nobody can learn about it, and it cannot create a match. The UI asks you to confirm the number first.
- Ghost chat: once two people are a mutual crush, `/chats` opens a chat for `app.chat-hours` (48h) under random aliases. Each can press "Reveal myself". Verified emails are shown only if both press it; otherwise the chat ends and shows nothing. The API never says whether the other side pressed reveal. To demo it, use two browsers (or one normal and one private window) with two phone numbers that add each other.
Endpoints: GET /me, POST /email/send, POST /email/verify, GET/POST /crushes, DELETE /crushes/{phone}, GET /chats, GET /chats/{id}, POST /chats/{id}/messages, POST /chats/{id}/reveal, GET /invite, GET /slots, POST /slots/purchase.

## API
All endpoints below need the token.
- GET /feed: unswiped profiles only
- POST /swipe {profileId, direction: "LIKE"|"PASS"}: returns {match, profile}; profile is set only on a mutual match
- GET /matches: mutual matches

## Privacy
Seeded "already liked you" rows live in `incoming_likes`, which no endpoint reads except the match check inside `POST /swipe`.
The `Profile` response type has no "likes you" field, and feed order is a fixed shuffle that does not group likers.

## Behaviour
Duplicate swipe -> 409, unknown profile -> 404, bad direction -> 400. A PASS never creates a match. State persists in `backend/data/`.
