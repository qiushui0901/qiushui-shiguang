package com.quanshiguang.shiguang.count.biz.domain.mapper;

import com.quanshiguang.shiguang.count.biz.domain.dataobject.NoteCountDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteCountDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteCountDO record);

    int insertSelective(NoteCountDO record);

    /**
     * 添加记录或更新笔记点赞数
     * @param count
     * @param noteId
     * @return
     */
    int insertOrUpdateLikeTotalByNoteId(@Param("count") Integer count, @Param("noteId") Long noteId);

    /**
     * 添加记录或更新笔记收藏数
     * @param count
     * @param noteId
     * @return
     */
    int insertOrUpdateCollectTotalByNoteId(@Param("count") Integer count, @Param("noteId") Long noteId);

    /**
     * 添加记录或更新笔记评论数
     * @param count
     * @param noteId
     * @return
     */
    int insertOrUpdateCommentTotalByNoteId(@Param("count") int count, @Param("noteId") Long noteId);

    NoteCountDO selectByPrimaryKey(Long id);

    /**
     * 根据笔记 ID 批量查询
     * @param noteIds
     * @return
     */
    List<NoteCountDO> selectByNoteIds(@Param("noteIds") List<Long> noteIds);

    int updateByPrimaryKeySelective(NoteCountDO record);

    int updateByPrimaryKey(NoteCountDO record);
}