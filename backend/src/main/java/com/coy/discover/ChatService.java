package com.coy.discover;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * Ghost chat: two mutual crushes talk under aliases for a limited time.
 * Identities (verified emails) are shared only if BOTH press reveal. If the time runs out first, the chat ends
 * and neither learns anything. The API never tells a user whether the other side has pressed reveal.
 */
@Service
public class ChatService {
    public record Message(boolean mine, String body, long sentAt) {}
    public record Summary(long id, String alias, long expiresAt, boolean ended, boolean revealed, String email) {}
    public record View(long id, String myAlias, String alias, long expiresAt, boolean ended,
                       boolean iRevealed, boolean revealed, String email, List<Message> messages) {}

    private static final String[] ADJECTIVES = {"Quiet", "Midnight", "Velvet", "Sunny", "Misty", "Golden", "Silent", "Gentle"};
    private static final String[] ANIMALS = {"Cheetah", "Fox", "Otter", "Panda", "Falcon", "Koala", "Lynx", "Heron"};

    private final JdbcTemplate db;
    private final CrushService crushes;
    private final long chatMs;
    private final SecureRandom random = new SecureRandom();

    public ChatService(JdbcTemplate db, CrushService crushes, @Value("${app.chat-hours:48}") long chatHours) {
        this.db = db;
        this.crushes = crushes;
        this.chatMs = chatHours * 3600_000L;
    }

    /** Opens a chat for every mutual crush that doesn't have one yet, then lists this user's chats. */
    public List<Summary> list(long userId) {
        for (CrushService.CrushView c : crushes.list(userId)) {
            if (!c.mutual()) continue;
            List<Long> other = db.queryForList("SELECT id FROM users WHERE phone = ?", Long.class, c.phone());
            if (!other.isEmpty()) ensure(userId, other.get(0));
        }
        return db.queryForList("SELECT * FROM chats WHERE user_a = ? OR user_b = ? ORDER BY id", userId, userId)
                .stream().map(c -> {
                    View v = toView(userId, c);
                    return new Summary(v.id(), v.alias(), v.expiresAt(), v.ended(), v.revealed(), v.email());
                }).toList();
    }

    public View view(long userId, long chatId) { return toView(userId, row(userId, chatId)); }

    public void send(long userId, long chatId, String body) {
        Map<String, Object> c = row(userId, chatId);
        View v = toView(userId, c);
        if (v.ended()) throw new ResponseStatusException(HttpStatus.CONFLICT, "this chat has ended");
        if (!v.revealed() && v.expiresAt() <= System.currentTimeMillis()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "this chat has ended");
        }
        if (body == null || body.isBlank() || body.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must be 1 to 500 characters");
        }
        db.update("INSERT INTO chat_messages(chat_id, sender_id, body, sent_at) VALUES (?,?,?,?)",
                chatId, userId, body.trim(), System.currentTimeMillis());
    }

    public View reveal(long userId, long chatId) {
        Map<String, Object> c = row(userId, chatId);
        if (toView(userId, c).ended()) throw new ResponseStatusException(HttpStatus.CONFLICT, "this chat has ended");
        String col = isA(userId, c) ? "reveal_a" : "reveal_b";
        db.update("UPDATE chats SET " + col + " = TRUE WHERE id = ?", chatId);
        return view(userId, chatId);
    }

    // ---- helpers ----
    private boolean isA(long userId, Map<String, Object> c) { return ((Number) c.get("user_a")).longValue() == userId; }

    private Map<String, Object> row(long userId, long chatId) {
        List<Map<String, Object>> r = db.queryForList(
                "SELECT * FROM chats WHERE id = ? AND (user_a = ? OR user_b = ?)", chatId, userId, userId);
        if (r.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "chat not found");
        return r.get(0);
    }

    private View toView(long userId, Map<String, Object> c) {
        boolean a = isA(userId, c);
        long id = ((Number) c.get("id")).longValue();
        long expiresAt = ((Number) c.get("expires_at")).longValue();
        boolean mineRevealed = Boolean.TRUE.equals(c.get(a ? "reveal_a" : "reveal_b"));
        boolean both = Boolean.TRUE.equals(c.get("reveal_a")) && Boolean.TRUE.equals(c.get("reveal_b"));
        boolean ended = expiresAt <= System.currentTimeMillis() && !both;
        long otherId = ((Number) c.get(a ? "user_b" : "user_a")).longValue();
        String email = both ? db.queryForObject("SELECT email FROM users WHERE id = ?", String.class, otherId) : null;
        List<Message> messages = ended ? List.of() : db.queryForList(
                "SELECT sender_id, body, sent_at FROM chat_messages WHERE chat_id = ? ORDER BY id", id).stream()
                .map(m -> new Message(((Number) m.get("sender_id")).longValue() == userId,
                        (String) m.get("body"), ((Number) m.get("sent_at")).longValue())).toList();
        return new View(id, (String) c.get(a ? "alias_a" : "alias_b"), (String) c.get(a ? "alias_b" : "alias_a"),
                expiresAt, ended, mineRevealed, both, email, messages);
    }

    private String alias() {
        return ADJECTIVES[random.nextInt(ADJECTIVES.length)] + " " + ANIMALS[random.nextInt(ANIMALS.length)];
    }

    private void ensure(long x, long y) {
        long a = Math.min(x, y), b = Math.max(x, y);
        Integer n = db.queryForObject("SELECT COUNT(*) FROM chats WHERE user_a = ? AND user_b = ?", Integer.class, a, b);
        if (n != null && n > 0) return;
        String aliasA = alias(), aliasB = alias();
        while (aliasB.equals(aliasA)) aliasB = alias();
        try {
            db.update("INSERT INTO chats(user_a, user_b, alias_a, alias_b, expires_at) VALUES (?,?,?,?,?)",
                    a, b, aliasA, aliasB, System.currentTimeMillis() + chatMs);
        } catch (DuplicateKeyException ignored) { /* the other user opened it first */ }
    }
}
