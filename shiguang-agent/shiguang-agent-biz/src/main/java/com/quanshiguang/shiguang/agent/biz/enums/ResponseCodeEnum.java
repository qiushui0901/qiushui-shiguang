package com.quanshiguang.shiguang.agent.biz.enums;

import com.quanshiguang.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    SYSTEM_ERROR("AGENT-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("AGENT-10001", "参数错误"),
    ;

    private final String errorCode;
    private final String errorMessage;
}
