package com.coy.discover;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:chattest;DB_CLOSE_DELAY=-1", "app.undo-window-seconds=0"})
@Transactional
class ChatServiceTest {
    @Autowired ChatService chats;
    @Autowired CrushService crushes;
    @Autowired JdbcTemplate db;
    long a, b, c, chatId;

    long user(String phone, String email) {
        db.update("INSERT INTO users(phone, email, email_verified) VALUES (?, ?, TRUE)", phone, email);
        return db.queryForObject("SELECT id FROM users WHERE phone = ?", Long.class, phone);
    }

    int status(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(ResponseStatusException.class, e).getStatusCode().value();
    }

    @BeforeEach void setUp() {
        a = user("9500000001", "a@x.com"); b = user("9500000002", "b@x.com"); c = user("9500000003", "c@x.com");
        crushes.add(a, "9500000002");
        crushes.add(b, "9500000001");
        chatId = chats.list(a).get(0).id();
    }

    @Test void chatExistsOnlyBetweenMutualCrushesWithMirroredAliases() {
        assertEquals(1, chats.list(b).size());
        assertTrue(chats.list(c).isEmpty());
        var va = chats.view(a, chatId);
        var vb = chats.view(b, chatId);
        assertNotEquals(va.myAlias(), va.alias());
        assertEquals(va.myAlias(), vb.alias());
    }

    @Test void nonParticipantCannotReadOrWrite() {
        assertEquals(404, status(() -> chats.view(c, chatId)));
        assertEquals(404, status(() -> chats.send(c, chatId, "hi")));
    }

    @Test void messagesAreSharedAndMarkedMine() {
        chats.send(a, chatId, "hi");
        assertFalse(chats.view(b, chatId).messages().get(0).mine());
        assertTrue(chats.view(a, chatId).messages().get(0).mine());
    }

    @Test void identityIsHiddenUntilBothReveal() {
        chats.reveal(a, chatId);
        var va = chats.view(a, chatId);
        assertTrue(va.iRevealed());
        assertFalse(va.revealed());
        assertNull(va.email());
        assertNull(chats.view(b, chatId).email());
        chats.reveal(b, chatId);
        assertEquals("b@x.com", chats.view(a, chatId).email());
        assertEquals("a@x.com", chats.view(b, chatId).email());
    }

    @Test void viewNeverExposesWhetherTheOtherSideRevealed() {
        assertTrue(Arrays.stream(ChatService.View.class.getRecordComponents())
                .noneMatch(f -> f.getName().toLowerCase().contains("other")));
        chats.reveal(a, chatId);
        assertFalse(chats.view(b, chatId).iRevealed());
        assertFalse(chats.view(b, chatId).revealed());
    }

    @Test void expiredChatWithoutRevealEndsAndShowsNothing() {
        chats.send(a, chatId, "hi");
        db.update("UPDATE chats SET expires_at = ?", System.currentTimeMillis() - 1000);
        var v = chats.view(a, chatId);
        assertTrue(v.ended());
        assertTrue(v.messages().isEmpty());
        assertNull(v.email());
        assertEquals(409, status(() -> chats.send(a, chatId, "late")));
        assertEquals(409, status(() -> chats.reveal(a, chatId)));
    }

    @Test void emptyAndTooLongMessagesRejected() {
        assertEquals(400, status(() -> chats.send(a, chatId, "  ")));
        assertEquals(400, status(() -> chats.send(a, chatId, "x".repeat(501))));
    }
}
