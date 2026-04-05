package com.arabtooling.redditcrawler.mapper;

import com.arabtooling.redditcrawler.dto.ScrapingSessionDTO;
import com.arabtooling.redditcrawler.entity.ScrapingSession;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-05T15:18:07+0200",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11-ea (Debian)"
)
@Component
public class ScrapingSessionMapperImpl implements ScrapingSessionMapper {

    @Override
    public ScrapingSessionDTO toDTO(ScrapingSession session) {
        if ( session == null ) {
            return null;
        }

        ScrapingSessionDTO scrapingSessionDTO = new ScrapingSessionDTO();

        scrapingSessionDTO.setId( session.getId() );
        scrapingSessionDTO.setSubreddit( session.getSubreddit() );
        scrapingSessionDTO.setSort( session.getSort() );
        scrapingSessionDTO.setPostLimit( session.getPostLimit() );
        scrapingSessionDTO.setDepth( session.getDepth() );
        scrapingSessionDTO.setIncludeComments( session.getIncludeComments() );
        scrapingSessionDTO.setKeywords( session.getKeywords() );
        scrapingSessionDTO.setStatus( session.getStatus() );
        scrapingSessionDTO.setStartedAt( session.getStartedAt() );
        scrapingSessionDTO.setFinishedAt( session.getFinishedAt() );
        scrapingSessionDTO.setErrorMessage( session.getErrorMessage() );

        return scrapingSessionDTO;
    }

    @Override
    public ScrapingSession toEntity(ScrapingSessionDTO dto) {
        if ( dto == null ) {
            return null;
        }

        ScrapingSession scrapingSession = new ScrapingSession();

        scrapingSession.setId( dto.getId() );
        scrapingSession.setSubreddit( dto.getSubreddit() );
        scrapingSession.setSort( dto.getSort() );
        scrapingSession.setPostLimit( dto.getPostLimit() );
        scrapingSession.setDepth( dto.getDepth() );
        scrapingSession.setIncludeComments( dto.getIncludeComments() );
        scrapingSession.setKeywords( dto.getKeywords() );
        scrapingSession.setStatus( dto.getStatus() );
        scrapingSession.setStartedAt( dto.getStartedAt() );
        scrapingSession.setFinishedAt( dto.getFinishedAt() );
        scrapingSession.setErrorMessage( dto.getErrorMessage() );

        return scrapingSession;
    }

    @Override
    public ScrapingSessionDTO toDTOWithPostIds(ScrapingSession session) {
        if ( session == null ) {
            return null;
        }

        ScrapingSessionDTO scrapingSessionDTO = new ScrapingSessionDTO();

        scrapingSessionDTO.setId( session.getId() );
        scrapingSessionDTO.setSubreddit( session.getSubreddit() );
        scrapingSessionDTO.setSort( session.getSort() );
        scrapingSessionDTO.setPostLimit( session.getPostLimit() );
        scrapingSessionDTO.setDepth( session.getDepth() );
        scrapingSessionDTO.setIncludeComments( session.getIncludeComments() );
        scrapingSessionDTO.setKeywords( session.getKeywords() );
        scrapingSessionDTO.setStatus( session.getStatus() );
        scrapingSessionDTO.setStartedAt( session.getStartedAt() );
        scrapingSessionDTO.setFinishedAt( session.getFinishedAt() );
        scrapingSessionDTO.setErrorMessage( session.getErrorMessage() );

        scrapingSessionDTO.setPostIds( session.getPosts().stream().map(post -> post.getId()).toList() );

        return scrapingSessionDTO;
    }

    @Override
    public void updateFromDTO(ScrapingSessionDTO dto, ScrapingSession entity) {
        if ( dto == null ) {
            return;
        }

        entity.setSubreddit( dto.getSubreddit() );
        entity.setSort( dto.getSort() );
        entity.setPostLimit( dto.getPostLimit() );
        entity.setDepth( dto.getDepth() );
        entity.setIncludeComments( dto.getIncludeComments() );
        entity.setKeywords( dto.getKeywords() );
        entity.setStatus( dto.getStatus() );
        entity.setStartedAt( dto.getStartedAt() );
        entity.setFinishedAt( dto.getFinishedAt() );
        entity.setErrorMessage( dto.getErrorMessage() );
    }
}
