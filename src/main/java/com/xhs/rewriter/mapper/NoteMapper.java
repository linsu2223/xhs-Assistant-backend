package com.xhs.rewriter.mapper;

import com.xhs.rewriter.domain.Note;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NoteMapper {
    @Select("select * from note order by updated_at desc limit 30")
    List<Note> findTop30ByUpdatedAtDesc();

    @Select("select * from note where id = #{id}")
    Note findById(Long id);

    @Insert("insert into note (title, note_url, original_content, image_urls_json, like_count, collect_count, comment_count, share_count, interaction_score, author_name, author_signature, publish_time, last_update_time, fetched_at, tags_json, analysis_json, rewrite_results_json, potential_label, remark, created_at, updated_at) " +
            "values (#{title}, #{noteUrl}, #{originalContent}, #{imageUrlsJson}, #{likeCount}, #{collectCount}, #{commentCount}, #{shareCount}, #{interactionScore}, #{authorName}, #{authorSignature}, #{publishTime}, #{lastUpdateTime}, #{fetchedAt}, #{tagsJson}, #{analysisJson}, #{rewriteResultsJson}, #{potentialLabel}, #{remark}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Note note);

    @Update("update note set title = #{title}, note_url = #{noteUrl}, original_content = #{originalContent}, image_urls_json = #{imageUrlsJson}, like_count = #{likeCount}, collect_count = #{collectCount}, comment_count = #{commentCount}, share_count = #{shareCount}, interaction_score = #{interactionScore}, author_name = #{authorName}, author_signature = #{authorSignature}, publish_time = #{publishTime}, last_update_time = #{lastUpdateTime}, fetched_at = #{fetchedAt}, tags_json = #{tagsJson}, analysis_json = #{analysisJson}, rewrite_results_json = #{rewriteResultsJson}, potential_label = #{potentialLabel}, remark = #{remark}, created_at = #{createdAt}, updated_at = #{updatedAt} where id = #{id}")
    int update(Note note);

    @Delete("delete from note where id = #{id}")
    int deleteById(Long id);

    @Select("select count(1) from note")
    int countAll();
}
