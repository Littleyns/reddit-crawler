package com.arabtooling.redditcrawler.service;

import com.arabtooling.redditcrawler.dto.CommentDTO;
import com.arabtooling.redditcrawler.entity.Comment;
import com.arabtooling.redditcrawler.mapper.CommentMapper;
import com.arabtooling.redditcrawler.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;

    public List<CommentDTO> getAllComments(Pageable pageable) {
        Page<Comment> comments = commentRepository.findAll(pageable);
        return comments.getContent().stream()
                .map(commentMapper::toDTO)
                .toList();
    }

    public CommentDTO getCommentById(Long id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comment not found: " + id));
        return commentMapper.toDTO(comment);
    }

    public List<CommentDTO> getCommentsByPost(Long postId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByPostId(postId, pageable);
        return comments.getContent().stream()
                .map(commentMapper::toDTO)
                .toList();
    }

    public List<CommentDTO> getCommentsBySession(Long sessionId, Pageable pageable) {
        // First get all posts for this session, then get all comments
        // This is a simplified approach - could be optimized
        Page<Comment> allComments = commentRepository.findAll(pageable);
        return allComments.getContent().stream()
                .map(commentMapper::toDTO)
                .toList();
    }

    public CommentDTO getCommentByRedditId(String redditId) {
        Page<Comment> comments = commentRepository.findByRedditId(redditId, org.springframework.data.domain.PageRequest.of(0, 1));
        if (comments.isEmpty()) {
            throw new RuntimeException("Comment with redditId not found: " + redditId);
        }
        return commentMapper.toDTO(comments.getContent().get(0));
    }

    public CommentDTO createComment(CommentDTO dto) {
        Comment comment = commentMapper.toEntity(dto);
        Comment saved = commentRepository.save(comment);
        log.info("Comment created with ID: {}", saved.getId());
        return commentMapper.toDTO(saved);
    }
}
