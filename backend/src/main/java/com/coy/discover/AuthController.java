package com.coy.discover;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    public record SendOtpRequest(String phone) {}
    public record VerifyRequest(String phone, String otp, String invite) {}

    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/send-otp")
    public AuthService.OtpResponse sendOtp(@RequestBody SendOtpRequest req) { return auth.sendOtp(req.phone()); }

    @PostMapping("/verify-otp")
    public Map<String, String> verify(@RequestBody VerifyRequest req) {
        return Map.of("token", auth.verifyOtp(req.phone(), req.otp(), req.invite()));
    }
}
