package com.hamza.foodordringsystem.myaiagent.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String SESSION_USER_ID = "userId";

    private final AuthService authService;
    private final AppUserRepository users;

    public AuthController(AuthService authService, AppUserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    public record Profile(Long id, String username, String email, String fullName, Instant createdAt) {
        static Profile of(AppUser user) {
            return new Profile(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getCreatedAt());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Profile> register(@RequestBody AuthService.RegisterRequest request, HttpServletRequest http) {
        AppUser user = authService.register(request);
        startSession(http, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(Profile.of(user));
    }

    @PostMapping("/login")
    public Profile login(@RequestBody AuthService.LoginRequest request, HttpServletRequest http) {
        AppUser user = authService.login(request);
        startSession(http, user);
        return Profile.of(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<Profile> me(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (!(userId instanceof Long id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return users.findById(id)
                .map(user -> ResponseEntity.ok(Profile.of(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private static void startSession(HttpServletRequest http, AppUser user) {
        http.getSession(true);
        // New session id on login to prevent session fixation.
        http.changeSessionId();
        http.getSession().setAttribute(SESSION_USER_ID, user.getId());
    }
}
