package com.redditcrawler.api.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory user store for the crawler backend. Stores usernames and BCrypt-hashed passwords.
 */
@Service
public class UserService {

    private final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public boolean register(String username, String password) {
        if (users.containsKey(username)) return false;
        users.putIfAbsent(username, encoder.encode(password));
        return true;
    }

    public Optional<String> findByUsername(String username) {
        return Optional.ofNullable(users.get(username)); // present means user exists
    }
}
