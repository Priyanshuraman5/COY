package com.coy.discover;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class DiscoverController {
    public record SwipeRequest(int profileId, String direction) {}

    private final SwipeService service;
    private final AuthService auth;

    public DiscoverController(SwipeService service, AuthService auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping("/feed")
    public List<Profile> feed(@RequestHeader(value = "Authorization", required = false) String h) {
        return service.feed(auth.userIdFor(h));
    }

    @PostMapping("/swipe")
    public SwipeService.SwipeResult swipe(@RequestHeader(value = "Authorization", required = false) String h,
                                          @RequestBody SwipeRequest req) {
        return service.swipe(auth.userIdFor(h), req.profileId(), req.direction());
    }

    @GetMapping("/matches")
    public List<Profile> matches(@RequestHeader(value = "Authorization", required = false) String h) {
        return service.matches(auth.userIdFor(h));
    }
}
