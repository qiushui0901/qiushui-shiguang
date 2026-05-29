package com.quanshiguang.shiguang.note.biz.convert;

import com.quanshiguang.shiguang.note.biz.domain.dataobject.NoteDO;
import com.quanshiguang.shiguang.note.biz.model.dto.PublishNoteDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author: 犬小哈
 * @date: 2025/4/15 15:02
 * @version: v1.0.0
 * @description: 实体类转换
 **/
@Mapper
public interface NoteConvert {

    /**
     * 初始化 convert 实例
     */
    NoteConvert INSTANCE = Mappers.getMapper(NoteConvert.class);

    /**
     * 将 DO 转化为 DTO
     * @param bean
     * @return
     */
    PublishNoteDTO convertDO2DTO(NoteDO bean);

    /**
     * 将 DTO 转化为 DO
     * @param bean
     * @return
     */
    NoteDO convertDTO2DO(PublishNoteDTO bean);
}
