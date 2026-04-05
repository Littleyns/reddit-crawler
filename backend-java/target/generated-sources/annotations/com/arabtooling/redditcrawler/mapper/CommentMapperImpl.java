package com.arabtooling.redditcrawler.mapper;

import com.arabtooling.redditcrawler.dto.CommentDTO;
import com.arabtooling.redditcrawler.entity.Comment;
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
public class CommentMapperImpl implements CommentMapper {

    @Override
    public CommentDTO toDTO(Comment comment) {
        if ( comment == null ) {
            return null;
        }

        CommentDTO commentDTO = new CommentDTO();

        commentDTO.setId( comment.getId() );
        commentDTO.setRedditId( comment.getRedditId() );
        commentDTO.setPostId( comment.getPostId() );
        commentDTO.setParentCommentId( comment.getParentCommentId() );
        commentDTO.setAuthor( comment.getAuthor() );
        commentDTO.setBody( comment.getBody() );
        commentDTO.setUpvotes( comment.getUpvotes() );
        commentDTO.setDepth( comment.getDepth() );
        commentDTO.setCreatedAt( comment.getCreatedAt() );

        return commentDTO;
    }

    @Override
    public Comment toEntity(CommentDTO dto) {
        if ( dto == null ) {
            return null;
        }

        Comment comment = new Comment();

        comment.setId( dto.getId() );
        comment.setRedditId( dto.getRedditId() );
        comment.setPostId( dto.getPostId() );
        comment.setParentCommentId( dto.getParentCommentId() );
        comment.setAuthor( dto.getAuthor() );
        comment.setBody( dto.getBody() );
        comment.setUpvotes( dto.getUpvotes() );
        comment.setDepth( dto.getDepth() );
        comment.setCreatedAt( dto.getCreatedAt() );

        return comment;
    }

    @Override
    public List<CommentDTO> toDTOList(List<Comment> comments) {
        if ( comments == null ) {
            return null;
        }

        List<CommentDTO> list = new ArrayList<CommentDTO>( comments.size() );
        for ( Comment comment : comments ) {
            list.add( toDTO( comment ) );
        }

        return list;
    }
}
