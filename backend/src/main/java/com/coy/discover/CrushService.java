package com.coy.discover;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Secret crush list. A crush is revealed only when it is mutual.
 * A new crush is "pending" for an undo window: it can be removed, and it cannot match, until the window ends.
 */
@Service
public class CrushService {
    public record CrushView(String phone, boolean mutual, boolean pending, long secondsLeft) {}
    public record AddResult(boolean mutual, boolean pending, long undoSeconds) {}

    private final JdbcTemplate db;
    private final AccountService accounts;
    private final long windowMs;

    public CrushService(JdbcTemplate db, AccountService accounts,
                        @Value("${app.undo-window-seconds:300}") long windowSeconds) {
        this.db = db;
        this.accounts = accounts;
        this.windowMs = windowSeconds * 1000;
    }

    @Transactional
    public AddResult add(long userId, String rawPhone) {
        if (!accounts.isEmailVerified(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "verify your email before adding a crush");
        }
        String phone = AuthService.normalize(rawPhone);
        String mine = db.queryForObject("SELECT phone FROM users WHERE id = ?", String.class, userId);
        if (phone.equals(mine)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "you can't add yourself");
        if (accounts.slots(userId).free() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no free slots left");
        }
        try {
            db.update("INSERT INTO crushes(user_id, phone, added_at) VALUES (?, ?, ?)",
                    userId, phone, System.currentTimeMillis());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already added");
        }
        // Nothing about whether this phone is registered is revealed unless it is mutual.
        return new AddResult(isMutual(userId, phone), windowMs > 0, windowMs / 1000);
    }

    /** Takes a crush back. Allowed only while the undo window is open; the other person never knows. */
    @Transactional
    public void undo(long userId, String rawPhone) {
        String phone = AuthService.normalize(rawPhone);
        List<Long> rows = db.queryForList("SELECT added_at FROM crushes WHERE user_id = ? AND phone = ?",
                Long.class, userId, phone);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "crush not found");
        if (rows.get(0) + windowMs <= System.currentTimeMillis()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "the undo window has passed");
        }
        db.update("DELETE FROM crushes WHERE user_id = ? AND phone = ?", userId, phone);
    }

    public List<CrushView> list(long userId) {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT phone, added_at FROM crushes WHERE user_id = ? ORDER BY phone", userId);
        return rows.stream().map(r -> {
            String phone = (String) r.get("phone");
            long activeAt = ((Number) r.get("added_at")).longValue() + windowMs;
            boolean pending = activeAt > now;
            return new CrushView(phone, !pending && isMutual(userId, phone), pending,
                    pending ? (activeAt - now + 999) / 1000 : 0);
        }).toList();
    }

    /** Mutual only if both crushes are past their undo window. Computed on read, never stored. */
    boolean isMutual(long userId, String crushPhone) {
        long now = System.currentTimeMillis();
        Integer n = db.queryForObject("""
            SELECT COUNT(*) FROM crushes mine
            JOIN users other ON other.phone = mine.phone
            JOIN crushes back ON back.user_id = other.id
            JOIN users me ON me.id = mine.user_id AND back.phone = me.phone
            WHERE mine.user_id = ? AND mine.phone = ?
              AND mine.added_at + ? <= ? AND back.added_at + ? <= ?""",
                Integer.class, userId, crushPhone, windowMs, now, windowMs, now);
        return n != null && n > 0;
    }
}
