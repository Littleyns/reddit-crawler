package com.redditcrawler.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a crawled Reddit post.
 */
@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subreddit;

    @Column(nullable = false, length = 1024)
    private String title;

    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String body;


    @Column(nullable = false, length = 256)
    private String author;

    @Column(nullable = false)
    private Integer upvotes;

    @Column(name = "comments_count", nullable = false)
    private Integer commentsCount;

    @Column(name = "created_utc", nullable = false)
    private Instant createdUtc;

    @Column(length = 2048)
    private String url;

    @Column(nullable = false, length = 1024)
    private String permalink;

    public Post() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubreddit() {
        return subreddit;
    }

    public void setSubreddit(String subreddit) {
        this.subreddit = subreddit;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Integer getUpvotes() {
        return upvotes;
    }

    public void setUpvotes(Integer upvotes) {
        this.upvotes = upvotes;
    }

    public Integer getCommentsCount() {
        return commentsCount;
    }

    public void setCommentsCount(Integer commentsCount) {
        this.commentsCount = commentsCount;
    }

    public Instant getCreatedUtc() {
        return createdUtc;
    }

    public void setCreatedUtc(Instant createdUtc) {
        this.createdUtc = createdUtc;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPermalink() {
        return permalink;
    }

    public void setPermalink(String permalink) {
        this.permalink = permalink;
    }
}
