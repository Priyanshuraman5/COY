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
import java.util.UUID;

@Service
public class AuthService {
    public record OtpResponse(String message, String devOtp) {}

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long OTP_TTL_MS = 5 * 60 * 1000;

    private final JdbcTemplate db;
    private final boolean devOtpInResponse;
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcTemplate db, @Value("${app.dev-otp-in-response:true}") boolean devOtpInResponse) {
        this.db = db;
        this.devOtpInResponse = devOtpInResponse;
    }

    public static String normalize(String phone) {
        String p = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (!p.matches("\\+?[0-9]{10,13}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enter a valid phone number");
        }
        return p;
    }

    public OtpResponse sendOtp(String phone) {
        String p = normalize(phone);
        String code = String.format("%06d", random.nextInt(1_000_000));
        db.update("MERGE INTO otps(phone, code, expires_at) KEY(phone) VALUES (?,?,?)",
                p, code, System.currentTimeMillis() + OTP_TTL_MS);
        log.info("OTP for {} is {}", p, code); // stand-in for an SMS gateway
        return new OtpResponse("OTP sent", devOtpInResponse ? code : null);
    }

    /** Returns a session token. Creates the user on first login. */
    public String verifyOtp(String phone, String code) { return verifyOtp(phone, code, null); }

    public String verifyOtp(String phone, String code, String invite) {
        String p = normalize(phone);
        List<Map<String, Object>> rows = db.queryForList("SELECT code, expires_at FROM otps WHERE phone = ?", p);
        boolean ok = !rows.isEmpty()
                && ((Number) rows.get(0).get("expires_at")).longValue() > System.currentTimeMillis()
                && rows.get(0).get("code").equals(code);
        if (!ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or expired OTP");
        db.update("DELETE FROM otps WHERE phone = ?", p); // single use
        if (db.queryForList("SELECT id FROM users WHERE phone = ?", Long.class, p).isEmpty()) {
            Long inviter = null;
            if (invite != null && !invite.isBlank()) {
                List<Long> r = db.queryForList("SELECT id FROM users WHERE invite_code = ?", Long.class, invite);
                if (!r.isEmpty()) inviter = r.get(0);
            }
            db.update("INSERT INTO users(phone, referred_by) VALUES (?, ?)", p, inviter);
            // Invite reward: +1 crush slot for the inviter, only when a brand-new user signs up.
            if (inviter != null) db.update("UPDATE users SET bonus_slots = bonus_slots + 1 WHERE id = ?", inviter);
        }
        Long userId = db.queryForObject("SELECT id FROM users WHERE phone = ?", Long.class, p);
        String token = UUID.randomUUID().toString();
        db.update("INSERT INTO sessions(token, user_id) VALUES (?, ?)", token, userId);
        return token;
    }

    public long userIdFor(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        List<Long> ids = db.queryForList("SELECT user_id FROM sessions WHERE token = ?", Long.class,
                authHeader.substring(7));
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        return ids.get(0);
    }
}
