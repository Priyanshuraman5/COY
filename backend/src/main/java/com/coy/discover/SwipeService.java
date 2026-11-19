package com.coy.discover;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@Service
public class SwipeService {
    public record SwipeResult(boolean match, Profile profile) {}

    private static final RowMapper<Profile> MAPPER = (rs, i) -> new Profile(
            rs.getInt("id"), rs.getString("name"), rs.getInt("age"), rs.getString("photo"),
            rs.getString("bio"), rs.getString("city"),
            Arrays.asList(rs.getString("interests").split(",")));

    private final JdbcTemplate db;

    public SwipeService(JdbcTemplate db) { this.db = db; }

    /** Unswiped profiles only. Order is a fixed shuffle so it never hints at who liked the user. */
    public List<Profile> feed(long userId) {
        return db.query("""
            SELECT * FROM profiles
            WHERE id NOT IN (SELECT target_id FROM swipes WHERE user_id = ?)
            ORDER BY MOD(id * 11, 31)""", MAPPER, userId);
    }

    @Transactional
    public SwipeResult swipe(long userId, int profileId, String direction) {
        if (!"LIKE".equals(direction) && !"PASS".equals(direction)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direction must be LIKE or PASS");
        }
        List<Profile> found = db.query("SELECT * FROM profiles WHERE id = ?", MAPPER, profileId);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "profile not found");
        try {
            db.update("INSERT INTO swipes(user_id, target_id, direction) VALUES (?, ?, ?)", userId, profileId, direction);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already swiped");
        }
        if ("LIKE".equals(direction)) {
            Integer liked = db.queryForObject(
                    "SELECT COUNT(*) FROM incoming_likes WHERE profile_id = ?", Integer.class, profileId);
            if (liked != null && liked > 0) {
                db.update("INSERT INTO matches(user_id, profile_id) VALUES (?, ?)", userId, profileId);
                return new SwipeResult(true, found.get(0)); // revealed only now, because it is mutual
            }
        }
        return new SwipeResult(false, null);
    }

    public List<Profile> matches(long userId) {
        return db.query("SELECT p.* FROM profiles p JOIN matches m ON m.profile_id = p.id WHERE m.user_id = ? ORDER BY p.id", MAPPER, userId);
    }
}
