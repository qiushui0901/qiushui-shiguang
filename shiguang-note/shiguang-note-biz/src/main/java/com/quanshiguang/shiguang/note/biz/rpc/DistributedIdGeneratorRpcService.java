package com.quanshiguang.shiguang.note.biz.rpc;

import com.quanshiguang.shiguang.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class DistributedIdGeneratorRpcService {

    private static final String BIZ_TAG_NOTE_ID = "leaf-segment-note-id";

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    public String getNoteId() {
        return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_NOTE_ID);
    }
}
