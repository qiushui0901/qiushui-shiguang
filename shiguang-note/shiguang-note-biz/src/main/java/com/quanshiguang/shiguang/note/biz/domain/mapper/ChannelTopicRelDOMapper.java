package com.quanshiguang.shiguang.note.biz.domain.mapper;

import com.quanshiguang.shiguang.note.biz.domain.dataobject.ChannelTopicRelDO;

public interface ChannelTopicRelDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(ChannelTopicRelDO record);

    int insertSelective(ChannelTopicRelDO record);

    ChannelTopicRelDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(ChannelTopicRelDO record);

    int updateByPrimaryKey(ChannelTopicRelDO record);
}