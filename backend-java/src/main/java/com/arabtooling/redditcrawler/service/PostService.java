package com.arabtooling.redditcrawler.service;

import com.arabtooling.redditcrawler.dto.PostDTO;
import com.arabtooling.redditcrawler.entity.Post;
import com.arabtooling.redditcrawler.mapper.PostMapper;
import com.arabtooling.redditcrawler.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;

    public List<PostDTO> getAllPosts(Pageable pageable) {
        Page<Post> posts = postRepository.findAll(pageable);
        return posts.getContent().stream()
                .map(postMapper::toDTO)
                .toList();
    }

    public PostDTO getPostById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found: " + id));
        return postMapper.toDTO(post);
    }

    public List<PostDTO> getPostsBySubreddit(String subreddit, Pageable pageable) {
        Page<Post> posts = postRepository.findBySubreddit(subreddit, pageable);
        return posts.getContent().stream()
                .map(postMapper::toDTO)
                .toList();
    }

    public List<PostDTO> getPostsBySession(Long sessionId, Pageable pageable) {
        Page<Post> posts = postRepository.findBySessionId(sessionId, pageable);
        return posts.getContent().stream()
                .map(postMapper::toDTO)
                .toList();
    }

    public PostDTO getPostByRedditId(String redditId) {
        Page<Post> posts = postRepository.findByRedditId(redditId, org.springframework.data.domain.PageRequest.of(0, 1));
        if (posts.isEmpty()) {
            throw new RuntimeException("Post with redditId not found: " + redditId);
        }
        return postMapper.toDTO(posts.getContent().get(0));
    }

    public PostDTO createPost(PostDTO dto) {
        Post post = postMapper.toEntity(dto);
        Post saved = postRepository.save(post);
        log.info("Post created with ID: {}", saved.getId());
        return postMapper.toDTO(saved);
    }

    public List<PostDTO> searchPosts(String query, Pageable pageable) {
        Page<Post> posts = postRepository.findByTitleContainingOrContentContaining(query, query, pageable);
        return posts.getContent().stream()
                .map(postMapper::toDTO)
                .toList();
    }
}
