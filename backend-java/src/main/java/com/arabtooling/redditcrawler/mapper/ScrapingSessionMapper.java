package com.arabtooling.redditcrawler.dto;

import com.arabtooling.redditcrawler.entity.ScrapingSession;
import com.arabtooling.redditcrawler.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ScrapingSessionMapper {

    ScrapingSessionDTO toDTO(ScrapingSession session);
    
    ScrapingSession toEntity(ScrapingSessionDTO dto);
    
    @Mapping(target = "postIds", expression = "java(session.getPosts().stream().map(Post::getId).toList())")
    ScrapingSessionDTO toDTOWithPostIds(ScrapingSession session);
    
    List<ScrapingSessionDTO> toDTOList(List<ScrapingSession> sessions);
    
    @Mapping(target = "id", ignore = true)
    void updateFromDTO(ScrapingSessionDTO dto, @MappingTarget ScrapingSession entity);
}
