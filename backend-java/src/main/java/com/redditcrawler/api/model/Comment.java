package com.redditcrawler.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA entity representing a crawled Reddit comment linked to a post.
 */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String redditId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // Note: parent_comment_id column exists in migration but is deprecated.
    // Use a dedicated parent reference field if needed later (requires FK).
    @Deprecated(forRemoval = true)
    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Deprecated(forRemoval = true)
    private String parentAuthor;

    @Column(nullable = false, length = 100)
    private String author;

    @Lob
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Reddit comment score (upvotes - downvotes) */
    @Column(nullable = false)
    private Integer upvotes;

    /** Nesting depth in the comment tree — top-level comment = 0 */
    @Column(nullable = false)
    private Integer depth;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    public Comment() {}

    // ── getters / setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRedditId() { return redditId; }
    public void setRedditId(String redditId) { this.redditId = redditId; }

    public Post getPost() { return post; }
    public void setPost(Post post) { this.post = post; }

    @Deprecated(forRemoval = true)
    public Long getParentCommentId() { return parentCommentId; }
    @Deprecated(forRemoval = true)
    public void setParentCommentId(Long parentCommentId) { this.parentCommentId = parentCommentId; }

    @Deprecated(forRemoval = true)
    public String getParentAuthor() { return parentAuthor; }
    @Deprecated(forRemoval = true)
    public void setParentAuthor(String parentAuthor) { this.parentAuthor = parentAuthor; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Integer getUpvotes() { return upvotes; }
    public void setUpvotes(Integer upvotes) { this.upvotes = upvotes; }

    public Integer getDepth() { return depth; }
    public void setDepth(Integer depth) { this.depth = depth; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
