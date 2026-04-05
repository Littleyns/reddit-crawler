package com.arabtooling.redditcrawler.mapper;

import com.arabtooling.redditcrawler.dto.PostDTO;
import com.arabtooling.redditcrawler.entity.Post;
import com.arabtooling.redditcrawler.entity.ScrapingSession;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-05T15:18:06+0200",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11-ea (Debian)"
)
@Component
public class PostMapperImpl implements PostMapper {

    @Override
    public PostDTO toDTO(Post post) {
        if ( post == null ) {
            return null;
        }

        PostDTO postDTO = new PostDTO();

        postDTO.setScrapingSessionId( postScrapingSessionId( post ) );
        postDTO.setId( post.getId() );
        postDTO.setRedditId( post.getRedditId() );
        postDTO.setTitle( post.getTitle() );
        postDTO.setAuthor( post.getAuthor() );
        postDTO.setSubreddit( post.getSubreddit() );
        postDTO.setUpvotes( post.getUpvotes() );
        postDTO.setUrl( post.getUrl() );
        postDTO.setContent( post.getContent() );
        postDTO.setCreatedAt( post.getCreatedAt() );

        return postDTO;
    }

    @Override
    public Post toEntity(PostDTO dto) {
        if ( dto == null ) {
            return null;
        }

        Post post = new Post();

        post.setId( dto.getId() );
        post.setRedditId( dto.getRedditId() );
        post.setTitle( dto.getTitle() );
        post.setAuthor( dto.getAuthor() );
        post.setSubreddit( dto.getSubreddit() );
        post.setUpvotes( dto.getUpvotes() );
        post.setUrl( dto.getUrl() );
        post.setContent( dto.getContent() );
        post.setCreatedAt( dto.getCreatedAt() );

        return post;
    }

    @Override
    public List<PostDTO> toDTOList(List<Post> posts) {
        if ( posts == null ) {
            return null;
        }

        List<PostDTO> list = new ArrayList<PostDTO>( posts.size() );
        for ( Post post : posts ) {
            list.add( toDTO( post ) );
        }

        return list;
    }

    private Long postScrapingSessionId(Post post) {
        if ( post == null ) {
            return null;
        }
        ScrapingSession scrapingSession = post.getScrapingSession();
        if ( scrapingSession == null ) {
            return null;
        }
        Long id = scrapingSession.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
