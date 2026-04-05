package com.arabtooling.redditcrawler.controller;

import com.arabtooling.redditcrawler.dto.PostDTO;
import com.arabtooling.redditcrawler.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/data/posts")
@Tag(name = "Posts", description = "Post data endpoints")
@RequiredArgsConstructor
public class PostsController {

    private final PostService postService;

    @GetMapping
    @Operation(summary = "Get all posts with pagination", description = "Returns paginated list of all posts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PostDTO>> getAllPosts(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {
        
        Sort sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Order.asc(sort) : Sort.Order.desc(sort);
        Pageable pageable = PageRequest.of(page, size, sortDirection);
        List<PostDTO> posts = postService.getAllPosts(pageable);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get post by ID", description = "Returns a specific post by its database ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostDTO> getPostById(@PathVariable Long id) {
        PostDTO post = postService.getPostById(id);
        return ResponseEntity.ok(post);
    }

    @GetMapping("/reddit/{redditId}")
    @Operation(summary = "Get post by Reddit ID", description = "Returns a post by its Reddit identifier")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostDTO> getPostByRedditId(@PathVariable String redditId) {
        PostDTO post = postService.getPostByRedditId(redditId);
        return ResponseEntity.ok(post);
    }

    @GetMapping("/subreddit/{subreddit}")
    @Operation(summary = "Get posts by subreddit", description = "Returns paginated posts for a specific subreddit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PostDTO>> getPostsBySubreddit(
            @PathVariable String subreddit,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        List<PostDTO> posts = postService.getPostsBySubreddit(subreddit, pageable);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "Get posts by session", description = "Returns posts from a specific crawl session")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PostDTO>> getPostsBySession(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        List<PostDTO> posts = postService.getPostsBySession(sessionId, pageable);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/search")
    @Operation(summary = "Search posts", description = "Searches posts by title or content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PostDTO>> searchPosts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        List<PostDTO> posts = postService.searchPosts(query, pageable);
        return ResponseEntity.ok(posts);
    }
}
