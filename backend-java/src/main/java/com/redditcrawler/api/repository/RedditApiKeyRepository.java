package com.redditcrawler.api.repository;

import com.redditcrawler.api.model.RedditApiKeyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Reddit API key configuration entries.
 */
@Repository
public interface RedditApiKeyRepository extends JpaRepository<RedditApiKeyConfig, Long> {

    List<RedditApiKeyConfig> findByActiveTrueOrderByRotationOrderAsc();

    Optional<RedditApiKeyConfig> findByAlias(String alias);

    @Query("SELECT r FROM RedditApiKeyConfig r WHERE r.active = true AND " +
           "r.tokenExpiresAt <= :now ORDER BY r.rotationOrder ASC")
    List<RedditApiKeyConfig> findInvalidTokens(@Param("now") java.time.LocalDateTime now);

    @Modifying
    @Query("UPDATE RedditApiKeyConfig r SET r.accessToken = :token, " +
           "r.refreshToken = :refreshToken, r.tokenExpiresAt = :expiresAt " +
           "WHERE r.id = :id")
    int updateTokens(@Param("id") Long id,
                     @Param("token") String token,
                     @Param("refreshToken") String refreshToken,
                     @Param("expiresAt") java.time.LocalDateTime expiresAt);

    @Modifying
    @Query("UPDATE RedditApiKeyConfig r SET r.active = :active WHERE r.id = :id")
    int setActive(@Param("id") Long id, @Param("active") boolean active);

    List<RedditApiKeyConfig> findAllByOrderByRotationOrderAsc();
}
