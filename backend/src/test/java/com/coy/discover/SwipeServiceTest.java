package com.coy.discover;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
@Transactional
class SwipeServiceTest {
    @Autowired SwipeService service;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    long uid;

    @org.junit.jupiter.api.BeforeEach void user() {
        db.update("INSERT INTO users(phone) VALUES ('9999999999')");
        uid = db.queryForObject("SELECT id FROM users WHERE phone = '9999999999'", Long.class);
    }

    @Test void swipesAreIsolatedPerUser() {
        service.swipe(uid, 1, "PASS");
        db.update("INSERT INTO users(phone) VALUES ('8888888888')");
        long other = db.queryForObject("SELECT id FROM users WHERE phone = '8888888888'", Long.class);
        assertTrue(service.feed(other).stream().anyMatch(p -> p.id() == 1));
    }

    @Test void likeOnProfileThatLikedUsCreatesMatch() {
        var r = service.swipe(uid, 3, "LIKE"); // ids divisible by 3 are seeded likers
        assertTrue(r.match());
        assertEquals(1, service.matches(uid).size());
    }

    @Test void likeOnNonLikerDoesNotMatch() {
        assertFalse(service.swipe(uid, 1, "LIKE").match());
        assertTrue(service.matches(uid).isEmpty());
    }

    @Test void passNeverMatchesEvenIfTheyLikedUs() {
        assertFalse(service.swipe(uid, 3, "PASS").match());
        assertTrue(service.matches(uid).isEmpty());
    }

    @Test void swipedProfileLeavesFeed() {
        int before = service.feed(uid).size();
        service.swipe(uid, 1, "PASS");
        assertEquals(before - 1, service.feed(uid).size());
        assertTrue(service.feed(uid).stream().noneMatch(p -> p.id() == 1));
    }

    @Test void duplicateSwipeRejected() {
        service.swipe(uid, 1, "LIKE");
        var e = assertThrows(ResponseStatusException.class, () -> service.swipe(uid, 1, "LIKE"));
        assertEquals(409, e.getStatusCode().value());
    }

    @Test void unknownProfileAndBadDirectionRejected() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.swipe(uid, 999, "LIKE")).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.swipe(uid, 1, "MAYBE")).getStatusCode().value());
    }

    @Test void feedNeverExposesLikerInfo() {
        assertTrue(java.util.Arrays.stream(Profile.class.getRecordComponents())
                .noneMatch(c -> c.getName().toLowerCase().contains("like")));
    }
}
