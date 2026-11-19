package com.coy.discover;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/** Email verification, invite links and crush slots. */
@Service
public class AccountService {
    public static final int FREE_SLOTS = 3;
    public record Slots(int used, int total, int free, int bonus) {}
    public record Me(String phone, String email, boolean emailVerified, Slots slots) {}
    public record Invite(String code, String link, int joined) {}

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final JdbcTemplate db;
    private final boolean devOtp;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public AccountService(JdbcTemplate db, @Value("${app.dev-otp-in-response:true}") boolean devOtp,
                          @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.db = db;
        this.devOtp = devOtp;
        this.frontendUrl = frontendUrl;
    }

    public Me me(long userId) {
        Map<String, Object> u = db.queryForMap("SELECT phone, email, email_verified FROM users WHERE id = ?", userId);
        return new Me((String) u.get("phone"), (String) u.get("email"),
                Boolean.TRUE.equals(u.get("email_verified")), slots(userId));
    }

    public boolean isEmailVerified(long userId) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT email_verified FROM users WHERE id = ?", Boolean.class, userId));
    }

    public Slots slots(long userId) {
        int used = db.queryForObject("SELECT COUNT(*) FROM crushes WHERE user_id = ?", Integer.class, userId);
        int bonus = db.queryForObject("SELECT bonus_slots FROM users WHERE id = ?", Integer.class, userId);
        int total = FREE_SLOTS + bonus;
        return new Slots(used, total, Math.max(0, total - used), bonus);
    }

    // ---- email verification ----
    public AuthService.OtpResponse sendEmailCode(long userId, String email) {
        if (email == null || !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enter a valid email");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        db.update("MERGE INTO email_otps(user_id, email, code, expires_at) KEY(user_id) VALUES (?,?,?,?)",
                userId, email.trim().toLowerCase(), code, System.currentTimeMillis() + 10 * 60 * 1000);
        log.info("Email code for {} is {}", email, code); // stand-in for an email provider
        return new AuthService.OtpResponse("Code sent", devOtp ? code : null);
    }

    public void verifyEmail(long userId, String email, String code) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT email, code, expires_at FROM email_otps WHERE user_id = ?", userId);
        boolean ok = !rows.isEmpty()
                && rows.get(0).get("email").equals(email == null ? "" : email.trim().toLowerCase())
                && rows.get(0).get("code").equals(code)
                && ((Number) rows.get(0).get("expires_at")).longValue() > System.currentTimeMillis();
        if (!ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or expired code");
        db.update("UPDATE users SET email = ?, email_verified = TRUE WHERE id = ?", email.trim().toLowerCase(), userId);
        db.update("DELETE FROM email_otps WHERE user_id = ?", userId);
    }

    // ---- invite link ----
    public Invite invite(long userId) {
        String code = db.queryForObject("SELECT invite_code FROM users WHERE id = ?", String.class, userId);
        if (code == null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            code = sb.toString();
            db.update("UPDATE users SET invite_code = ? WHERE id = ?", code, userId);
        }
        int joined = db.queryForObject("SELECT COUNT(*) FROM users WHERE referred_by = ?", Integer.class, userId);
        return new Invite(code, frontendUrl + "/?invite=" + code, joined);
    }
}
