package com.arabtooling.redditcrawler.repository;

import com.arabtooling.redditcrawler.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByRedditId(String redditId, Pageable pageable);
    Page<Comment> findByPostId(Long postId, Pageable pageable);
    
    List<Comment> findByParentCommentId(Long parentCommentId);
    Page<Comment> findByPostIdAndParentCommentIdIsNull(Long postId, Pageable pageable);
}
