package com.coy.discover;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chats")
public class ChatController {
    public record MessageRequest(String body) {}

    private final ChatService chats;
    private final AuthService auth;

    public ChatController(ChatService chats, AuthService auth) {
        this.chats = chats;
        this.auth = auth;
    }

    @GetMapping
    public List<ChatService.Summary> list(@RequestHeader(value = "Authorization", required = false) String h) {
        return chats.list(auth.userIdFor(h));
    }

    @GetMapping("/{id}")
    public ChatService.View view(@RequestHeader(value = "Authorization", required = false) String h, @PathVariable long id) {
        return chats.view(auth.userIdFor(h), id);
    }

    @PostMapping("/{id}/messages")
    public Map<String, Boolean> send(@RequestHeader(value = "Authorization", required = false) String h,
                                     @PathVariable long id, @RequestBody MessageRequest r) {
        chats.send(auth.userIdFor(h), id, r.body());
        return Map.of("sent", true);
    }

    @PostMapping("/{id}/reveal")
    public ChatService.View reveal(@RequestHeader(value = "Authorization", required = false) String h, @PathVariable long id) {
        return chats.reveal(auth.userIdFor(h), id);
    }
}
