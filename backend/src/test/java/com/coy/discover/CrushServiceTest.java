package com.coy.discover;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:crushtest;DB_CLOSE_DELAY=-1", "app.undo-window-seconds=0"})
@Transactional
class CrushServiceTest {
    @Autowired CrushService crushes;
    @Autowired AccountService accounts;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate db;
    long a, b;

    long user(String phone, boolean verified) {
        db.update("INSERT INTO users(phone, email_verified) VALUES (?, ?)", phone, verified);
        return db.queryForObject("SELECT id FROM users WHERE phone = ?", Long.class, phone);
    }

    int status(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(ResponseStatusException.class, e).getStatusCode().value();
    }

    @BeforeEach void setUp() { a = user("9000000001", true); b = user("9000000002", true); }

    @Test void oneSidedCrushStaysSecretEvenIfTheyAreRegistered() {
        assertFalse(crushes.add(a, "9000000002").mutual());
        assertFalse(crushes.list(a).get(0).mutual());
        assertTrue(crushes.list(b).isEmpty()); // b cannot see that a added them
    }

    @Test void mutualCrushIsRevealedToBothSides() {
        crushes.add(a, "9000000002");
        assertTrue(crushes.add(b, "9000000001").mutual());
        assertTrue(crushes.list(a).get(0).mutual());
    }

    @Test void emailMustBeVerifiedBeforeAddingCrush() {
        long c = user("9000000003", false);
        assertEquals(403, status(() -> crushes.add(c, "9000000009")));
    }

    @Test void slotLimitIsEnforced() {
        crushes.add(a, "9100000001"); crushes.add(a, "9100000002"); crushes.add(a, "9100000003");
        assertEquals(409, status(() -> crushes.add(a, "9100000004")));
    }

    @Test void duplicateAndSelfCrushRejected() {
        crushes.add(a, "9100000001");
        assertEquals(409, status(() -> crushes.add(a, "9100000001")));
        assertEquals(400, status(() -> crushes.add(a, "9000000001")));
    }

    @Test void emailVerificationFlow() {
        long c = user("9000000004", false);
        assertEquals(401, status(() -> accounts.verifyEmail(c, "c@x.com", "000000")));
        String code = accounts.sendEmailCode(c, "C@X.com").devOtp();
        accounts.verifyEmail(c, "c@x.com", code);
        assertTrue(accounts.isEmailVerified(c));
        assertEquals(400, status(() -> accounts.sendEmailCode(c, "not-an-email")));
    }

    @Test void inviteGivesInviterOneExtraSlotOnlyForNewUsers() {
        String code = accounts.invite(a).code();
        assertEquals(3, accounts.slots(a).total());
        auth.verifyOtp("9200000001", auth.sendOtp("9200000001").devOtp(), code);   // new user joins via link
        assertEquals(4, accounts.slots(a).total());
        assertEquals(1, accounts.invite(a).joined());
        auth.verifyOtp("9200000001", auth.sendOtp("9200000001").devOtp(), code);   // same user logging in again
        assertEquals(4, accounts.slots(a).total());
    }

    @Test void newCrushIsPendingAndUndoableWhileWindowIsOpen() {
        crushes.add(a, "9300000001");
        db.update("UPDATE crushes SET added_at = ? WHERE user_id = ?", System.currentTimeMillis() + 600_000, a); // open window
        assertTrue(crushes.list(a).get(0).pending());
        crushes.undo(a, "9300000001");
        assertTrue(crushes.list(a).isEmpty());
        assertEquals(3, accounts.slots(a).free()); // slot freed
    }

    @Test void pendingCrushNeverCountsAsMutual() {
        crushes.add(b, "9000000001");
        crushes.add(a, "9000000002");
        db.update("UPDATE crushes SET added_at = ? WHERE user_id = ?", System.currentTimeMillis() + 600_000, a);
        assertFalse(crushes.list(a).get(0).mutual());
        assertFalse(crushes.list(b).get(0).mutual()); // b learns nothing while a can still undo
    }

    @Test void undoAfterWindowAndUnknownCrushRejected() {
        crushes.add(a, "9300000002");
        assertEquals(409, status(() -> crushes.undo(a, "9300000002")));
        assertEquals(404, status(() -> crushes.undo(a, "9300000009")));
    }
}
