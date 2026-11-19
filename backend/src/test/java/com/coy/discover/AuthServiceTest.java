package com.coy.discover;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:authtest;DB_CLOSE_DELAY=-1")
@Transactional
class AuthServiceTest {
    @Autowired AuthService auth;

    @Test void correctOtpLogsInAndTokenResolvesToUser() {
        String otp = auth.sendOtp("98765 43210").devOtp();
        String token = auth.verifyOtp("9876543210", otp);
        assertTrue(auth.userIdFor("Bearer " + token) > 0);
    }

    @Test void wrongOtpRejected() {
        auth.sendOtp("9876543210");
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> auth.verifyOtp("9876543210", "000000x")).getStatusCode().value());
    }

    @Test void otpIsSingleUse() {
        String otp = auth.sendOtp("9876543210").devOtp();
        auth.verifyOtp("9876543210", otp);
        assertThrows(ResponseStatusException.class, () -> auth.verifyOtp("9876543210", otp));
    }

    @Test void invalidPhoneAndMissingTokenRejected() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> auth.sendOtp("12")).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> auth.userIdFor(null)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> auth.userIdFor("Bearer nope")).getStatusCode().value());
    }

    @Test void sameNumberGetsSameUser() {
        String t1 = auth.verifyOtp("9876543210", auth.sendOtp("9876543210").devOtp());
        String t2 = auth.verifyOtp("9876543210", auth.sendOtp("9876543210").devOtp());
        assertEquals(auth.userIdFor("Bearer " + t1), auth.userIdFor("Bearer " + t2));
    }
}
