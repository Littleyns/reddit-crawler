package com.arabtooling.redditcrawler.mapper;

import com.arabtooling.redditcrawler.dto.ScrapingSessionDTO;
import com.arabtooling.redditcrawler.entity.Post;
import com.arabtooling.redditcrawler.entity.ScrapingSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ScrapingSessionMapper {

    ScrapingSessionDTO toDTO(ScrapingSession session);
    
    ScrapingSession toEntity(ScrapingSessionDTO dto);
    
    @Mapping(target = "postIds", expression = "java(session.getPosts().stream().map(post -> post.getId()).toList())")
    ScrapingSessionDTO toDTOWithPostIds(ScrapingSession session);
    
    default List<ScrapingSessionDTO> toDTOList(List<ScrapingSession> sessions) {
        return sessions.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    @Mapping(target = "id", ignore = true)
    void updateFromDTO(ScrapingSessionDTO dto, @MappingTarget ScrapingSession entity);
}
