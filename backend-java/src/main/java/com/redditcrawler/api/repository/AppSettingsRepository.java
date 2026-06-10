package com.redditcrawler.api.repository;

import com.redditcrawler.api.entity.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
    Optional<AppSettings> findByKey(String key);

    @Query("SELECT s.key FROM AppSettings s")
    java.util.List<String> findAllKeys();
}
