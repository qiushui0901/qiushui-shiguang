package com.quanshiguang.shiguang.search.biz.service;

import com.quanshiguang.framework.common.response.PageResponse;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.dto.RebuildNoteDocumentReqDTO;
import com.quanshiguang.shiguang.search.biz.model.vo.SearchNoteReqVO;
import com.quanshiguang.shiguang.search.biz.model.vo.SearchNoteRspVO;
import com.quanshiguang.shiguang.search.biz.model.vo.SuggestNoteReqVO;
import com.quanshiguang.shiguang.search.biz.model.vo.TrendingNoteReqVO;

import java.util.List;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: 笔记搜索业务
 **/
public interface NoteService {

    /**
     * 搜索笔记
     * @param searchNoteReqVO
     * @return
     */
    PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO);

    /**
     * 重建笔记文档
     * @param rebuildNoteDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO);

    /**
     * 搜索建议（自动补全）
     * @param suggestNoteReqVO
     * @return
     */
    Response<List<String>> suggest(SuggestNoteReqVO suggestNoteReqVO);

    /**
     * 热度排行（function_score + 时间衰减）
     * @param trendingNoteReqVO
     * @return
     */
    PageResponse<SearchNoteRspVO> trending(TrendingNoteReqVO trendingNoteReqVO);
}
