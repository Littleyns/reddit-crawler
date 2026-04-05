package com.arabtooling.redditcrawler.mapper;

import com.arabtooling.redditcrawler.entity.Post;
import com.arabtooling.redditcrawler.dto.PostDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PostMapper {

    @Mapping(target = "scrapingSessionId", source = "scrapingSession.id")
    PostDTO toDTO(Post post);
    
    @Mapping(target = "scrapingSessionId", ignore = true)
    Post toEntity(PostDTO dto);
    
    List<PostDTO> toDTOList(List<Post> posts);
}
