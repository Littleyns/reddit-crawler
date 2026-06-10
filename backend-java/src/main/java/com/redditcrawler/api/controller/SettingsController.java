package com.redditcrawler.api.controller;

import com.redditcrawler.api.entity.AppSettings;
import com.redditcrawler.api.repository.AppSettingsRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for system settings endpoints.
 * Persisted key-value store with sensible defaults on first run.
 */
@RestController
@RequestMapping("/api")
public class SettingsController {

    private final AppSettingsRepository repository;

    public SettingsController(AppSettingsRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void initDefaults() {
        // Populate defaults if table is empty
        if (repository.count() == 0) {
            defaultKeys().forEach(it -> repository.save(
                new AppSettings(it.key, it.value, it.description)
            ));
        } else {
            // Ensure any key missing from a fresh deploy still exists
            for (DefaultsEntry de : defaultKeys()) {
                repository.findByKey(de.key).orElseGet(() -> repository.save(
                    new AppSettings(de.key, de.value, de.description)
                ));
            }
        }
    }

    private java.util.List<DefaultsEntry> defaultKeys() {
        return java.util.List.of(
            // LLM / AI settings
            new DefaultsEntry("llm.provider", "ollama", "LLM inference provider (openai, ollama, claude)"),
            new DefaultsEntry("llm.model", "qwen3.6:35b", "LLM model identifier"),
            new DefaultsEntry("llm.apiKey", "", "API key for LLM provider (if required)"),

            // Proxy settings
            new DefaultsEntry("proxy.enabled", "false", "Proxy enabled flag"),
            new DefaultsEntry("proxy.host", "", "Proxy host address"),
            new DefaultsEntry("proxy.port", "8080", "Proxy port"),
            new DefaultsEntry("proxy.authUsername", "", "Proxy auth username"),
            new DefaultsEntry("proxy.authPassword", "", "Proxy auth password"),

            // Crawler defaults
            new DefaultsEntry("crawler.defaultSubreddit", "machinelearning", "Default subreddit")
        );
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        Map<String, Object> settings = repository.findAll().stream()
            .collect(java.util.stream.Collectors.toMap(
                AppSettings::getKey,
                s -> parseValue(s.getKey(), s.getValue()),
                (a, b) -> a, // dedup, should not happen
                LinkedHashMap::new
            ));

        // Fill any keys that exist in defaults but not in db yet
        for (DefaultsEntry de : defaultKeys()) {
            settings.putIfAbsent(de.key, parseValue(de.key, de.value));
        }

        return ResponseEntity.ok(settings);
    }

    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> payload) {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            // Skip meta / non-settings fields
            String key = entry.getKey();
            if (!key.contains(".") && !defaultKeys().stream().anyMatch(d -> d.key.equals(key))) continue;

            @SuppressWarnings("unchecked")
            Optional<AppSettings> existing = repository.findByKey(key);
            String value = entry.getValue() == null ? "" : escapeValue(entry.getValue());
            existing.ifPresentOrElse(
                s -> { s.setValue(value); repository.save(s); },
                () -> repository.save(new AppSettings(key, value, "persisted via updateSetting"))
            );
        }
        // Return the settings we actually touched (for quick round-trip confirmation)
        Map<String, Object> response = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            if (!key.contains(".") && !defaultKeys().stream().anyMatch(d -> d.key.equals(key))) continue;
            // Return current persisted value
            repository.findByKey(key).ifPresentOrElse(
                s -> response.put(key, parseValue(key, s.getValue())),
                () -> response.put(key, entry.getValue())
            );
        }
        return ResponseEntity.ok(response);
    }

    // Helpers ---------------------------------------------------------------

    /** Convert String value from DB into proper Java type */
    private Object parseValue(String key, String raw) {
        if (raw == null || raw.isEmpty()) return (defaultKeys().stream()
            .filter(d -> d.key.equals(key)).findFirst()
            .map(d -> d.value == null ? "" : d.value));

        // Boolean keys
        if (key.contains(".enabled")) {
            return Boolean.parseBoolean(raw);
        }
        // Integer keys
        if (raw.matches("-?\\d+")) return Integer.parseInt(raw);
        // Everything else
        return raw;
    }

    /** Turn arbitrary JSON-like object into safe DB string */
    private String escapeValue(Object v) {
        if (v == null) return "";
        if (v instanceof Boolean b) return String.valueOf(b); // "true"/"false"
        return String.valueOf(v);
    }

    // Inner record ----------------------------------------------------------
    record DefaultsEntry(String key, String value, String description) {}
}
