package com.quanshiguang.shiguang.search.biz.controller;

import com.quanshiguang.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanshiguang.framework.common.response.PageResponse;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.dto.RebuildNoteDocumentReqDTO;
import com.quanshiguang.shiguang.search.biz.model.vo.SearchNoteReqVO;
import com.quanshiguang.shiguang.search.biz.model.vo.SearchNoteRspVO;
import com.quanshiguang.shiguang.search.biz.model.vo.SuggestNoteReqVO;
import com.quanshiguang.shiguang.search.biz.model.vo.TrendingNoteReqVO;
import com.quanshiguang.shiguang.search.biz.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * @author: 犬小哈
 * @date: 2024/4/4 13:22
 * @version: v1.0.0
 * @description: 笔记搜索
 **/
@RestController
@RequestMapping("/search")
@Slf4j
public class NoteController {

    @Resource
    private NoteService noteService;

    @PostMapping("/note")
    @ApiOperationLog(description = "搜索笔记")
    public PageResponse<SearchNoteRspVO> searchNote(@RequestBody @Validated SearchNoteReqVO searchNoteReqVO) {
        return noteService.searchNote(searchNoteReqVO);
    }

    @PostMapping("/note/suggest")
    @ApiOperationLog(description = "搜索建议")
    public Response<List<String>> suggest(@RequestBody @Validated SuggestNoteReqVO suggestNoteReqVO) {
        return noteService.suggest(suggestNoteReqVO);
    }

    @PostMapping("/note/trending")
    @ApiOperationLog(description = "热度排行")
    public PageResponse<SearchNoteRspVO> trending(@RequestBody @Validated TrendingNoteReqVO trendingNoteReqVO) {
        return noteService.trending(trendingNoteReqVO);
    }

    // ===================================== 对其他服务提供的接口 =====================================
    @PostMapping("/note/document/rebuild")
    @ApiOperationLog(description = "用户文档重建")
    public Response<Long> rebuildDocument(@Validated @RequestBody RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO) {
        return noteService.rebuildDocument(rebuildNoteDocumentReqDTO);
    }

}
