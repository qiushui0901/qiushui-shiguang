package com.quanshiguang.shiguang.distributed.id.generator.biz.core.snowflake.exception;

public class CheckLastTimeException extends RuntimeException {
    public CheckLastTimeException(String msg){
        super(msg);
    }
}
