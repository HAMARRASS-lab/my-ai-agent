package com.hamza.foodordringsystem.myaiagent.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AppUserRepository users) {
        this.users = users;
    }

    public record RegisterRequest(String username, String email, String fullName, String password) {
    }

    public record LoginRequest(String login, String password) {
    }

    @Transactional
    public AppUser register(RegisterRequest request) {
        String username = trim(request.username());
        String email = trim(request.email()).toLowerCase();
        String fullName = trim(request.fullName());
        String password = request.password() == null ? "" : request.password();

        if (!USERNAME.matcher(username).matches()) {
            throw badRequest("Username must be 3-30 characters: letters, digits, '.', '_' or '-'.");
        }
        if (!EMAIL.matcher(email).matches()) {
            throw badRequest("Please enter a valid email address.");
        }
        if (!StringUtils.hasText(fullName) || fullName.length() > 100) {
            throw badRequest("Full name is required (max 100 characters).");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw badRequest("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This username is already taken.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        return users.save(new AppUser(username, email, fullName, passwordEncoder.encode(password)));
    }

    // Accepts either the username or the email as the login.
    public AppUser login(LoginRequest request) {
        String login = trim(request.login());
        String password = request.password() == null ? "" : request.password();

        Optional<AppUser> user = login.contains("@")
                ? users.findByEmailIgnoreCase(login)
                : users.findByUsernameIgnoreCase(login);

        // Same message for unknown user and wrong password so logins can't be probed.
        return user.filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password."));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
