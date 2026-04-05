package com.arabtooling.redditcrawler.controller;

import com.arabtooling.redditcrawler.dto.CommentDTO;
import com.arabtooling.redditcrawler.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/data/comments")
@Tag(name = "Comments", description = "Comment data endpoints")
@RequiredArgsConstructor
public class CommentsController {

    private final CommentService commentService;

    @GetMapping
    @Operation(summary = "Get all comments with pagination", description = "Returns paginated list of all comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CommentDTO>> getAllComments(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        List<CommentDTO> comments = commentService.getAllComments(pageable);
        return ResponseEntity.ok(comments);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get comment by ID", description = "Returns a specific comment by its database ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentDTO> getCommentById(@PathVariable Long id) {
        CommentDTO comment = commentService.getCommentById(id);
        return ResponseEntity.ok(comment);
    }

    @GetMapping("/reddit/{redditId}")
    @Operation(summary = "Get comment by Reddit ID", description = "Returns a comment by its Reddit identifier")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentDTO> getCommentByRedditId(@PathVariable String redditId) {
        CommentDTO comment = commentService.getCommentByRedditId(redditId);
        return ResponseEntity.ok(comment);
    }

    @GetMapping("/post/{postId}")
    @Operation(summary = "Get comments by post", description = "Returns all comments for a specific post")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CommentDTO>> getCommentsByPost(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        List<CommentDTO> comments = commentService.getCommentsByPost(postId, pageable);
        return ResponseEntity.ok(comments);
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "Get comments by session", description = "Returns comments from a specific crawl session")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CommentDTO>> getCommentsBySession(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        List<CommentDTO> comments = commentService.getCommentsBySession(sessionId, pageable);
        return ResponseEntity.ok(comments);
    }
}
