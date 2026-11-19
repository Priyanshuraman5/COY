package com.coy.discover;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
public class AccountController {
    public record EmailRequest(String email) {}
    public record EmailVerifyRequest(String email, String code) {}
    public record CrushRequest(String phone) {}
    public record Plan(String name, int slots, String price) {}

    private static final List<Plan> PLANS = List.of(
            new Plan("Starter", 1, "₹49"), new Plan("Popular", 3, "₹99"), new Plan("Max", 5, "₹149")); // sample pricing

    private final AuthService auth;
    private final AccountService accounts;
    private final CrushService crushes;

    public AccountController(AuthService auth, AccountService accounts, CrushService crushes) {
        this.auth = auth;
        this.accounts = accounts;
        this.crushes = crushes;
    }

    private long uid(String h) { return auth.userIdFor(h); }

    @GetMapping("/me")
    public AccountService.Me me(@RequestHeader(value = "Authorization", required = false) String h) {
        return accounts.me(uid(h));
    }

    @PostMapping("/email/send")
    public AuthService.OtpResponse sendEmail(@RequestHeader(value = "Authorization", required = false) String h,
                                             @RequestBody EmailRequest r) {
        return accounts.sendEmailCode(uid(h), r.email());
    }

    @PostMapping("/email/verify")
    public Map<String, Boolean> verifyEmail(@RequestHeader(value = "Authorization", required = false) String h,
                                            @RequestBody EmailVerifyRequest r) {
        accounts.verifyEmail(uid(h), r.email(), r.code());
        return Map.of("verified", true);
    }

    @GetMapping("/invite")
    public AccountService.Invite invite(@RequestHeader(value = "Authorization", required = false) String h) {
        return accounts.invite(uid(h));
    }

    @GetMapping("/crushes")
    public List<CrushService.CrushView> crushes(@RequestHeader(value = "Authorization", required = false) String h) {
        return crushes.list(uid(h));
    }

    @PostMapping("/crushes")
    public CrushService.AddResult addCrush(@RequestHeader(value = "Authorization", required = false) String h,
                                           @RequestBody CrushRequest r) {
        return crushes.add(uid(h), r.phone());
    }

    /** Undo a crush while its undo window is still open. */
    @DeleteMapping("/crushes/{phone}")
    public Map<String, Boolean> undoCrush(@RequestHeader(value = "Authorization", required = false) String h,
                                          @PathVariable String phone) {
        crushes.undo(uid(h), phone);
        return Map.of("removed", true);
    }

    @GetMapping("/slots")
    public Map<String, Object> slots(@RequestHeader(value = "Authorization", required = false) String h) {
        return Map.of("slots", accounts.slots(uid(h)), "plans", PLANS);
    }

    /** Look, don't buy: the extra-slots screen exists but payments are intentionally not implemented. */
    @PostMapping("/slots/purchase")
    public void purchase(@RequestHeader(value = "Authorization", required = false) String h) {
        uid(h);
        throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Purchases are disabled in this demo");
    }
}
