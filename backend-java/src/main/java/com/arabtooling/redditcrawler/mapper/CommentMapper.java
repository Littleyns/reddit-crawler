package com.arabtooling.redditcrawler.mapper;

import com.arabtooling.redditcrawler.entity.Comment;
import com.arabtooling.redditcrawler.dto.CommentDTO;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    CommentDTO toDTO(Comment comment);
    
    Comment toEntity(CommentDTO dto);
    
    List<CommentDTO> toDTOList(List<Comment> comments);
}
