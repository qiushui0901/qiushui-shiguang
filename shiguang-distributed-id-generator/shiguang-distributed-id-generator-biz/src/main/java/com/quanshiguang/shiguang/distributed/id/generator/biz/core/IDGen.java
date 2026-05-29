package com.quanshiguang.shiguang.distributed.id.generator.biz.core;

import com.quanshiguang.shiguang.distributed.id.generator.biz.core.common.Result;

public interface IDGen {
    Result get(String key);
    boolean init();
}
