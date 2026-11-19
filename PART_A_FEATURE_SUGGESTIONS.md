# Part A: Two core feature suggestions for Coy

Replace each "What I noticed" line with something you actually saw in the live app.

## 1. Confirm and undo window before a crush counts

**What it does:** When I add a crush, the app shows the number back to me and asks me to confirm it. After I confirm, the crush stays "pending" for a few minutes. During that time I can undo it, nobody is notified, and it cannot create a match.

**What I noticed:** [e.g. "When I added a crush I only typed a number. There was no confirmation step and no way to take it back."]

**Why it's required:**
- **Wrong numbers:** One mistyped digit can send a stranger a message that someone has a crush on them, or can burn one of my limited slots.
- **Regret:** People add a crush on impulse and change their mind. If it cannot be undone, they lose a slot, or the person finds out.
- **Trust:** A quiet undo protects Coy's promise that nobody finds out unless it is mutual. It also makes paid slots feel fairer, because a typo does not waste them.

**How I'd build it:** The crush row stores `added_at`. It counts as active only after the window ends, so mutual-match checks ignore pending crushes on both sides. `DELETE /crushes/{phone}` works only inside the window. I prototyped this in my Discover repo (`CrushService`, with tests).

## 2. Ghost chat: aliases first, identities only if both agree

**What it does:** After a mutual match, the two people chat for 48 hours under random aliases like "Quiet Cheetah" and "Midnight Fox". Each has a "Reveal myself" button. Real identities appear only when both press it. If the time runs out first, the chat ends and neither learns who the other was. The app never shows whether the other person pressed reveal.

**What I noticed:** [e.g. "After the match there was nothing to help the two people start talking, and the whole thing felt like a big moment with no gentle next step."]

**Why it's required:**
- **Lower pressure:** A match can feel like a shock. A short anonymous chat lets people see if there is a connection before they reveal themselves.
- **Privacy continues:** It extends Coy's promise past the match instead of ending it at "it's a match".
- **Safe exit:** If one person decides they are not interested, they can walk away without the other ever knowing who they were.
- **Retention:** Matched users have a reason to stay in Coy instead of moving to another app. Report and block buttons plus the 48-hour limit keep it safe.

**How I'd build it:** A `chats` table with an alias per side, an expiry time and a `reveal` flag per side, plus a `chat_messages` table. The API returns identities only when both flags are true, and it hides messages once an unrevealed chat expires. I prototyped this too (`ChatService`, with tests).
