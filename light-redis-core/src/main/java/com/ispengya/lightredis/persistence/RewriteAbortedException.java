package com.ispengya.lightredis.persistence;

/**
 * AOF 重写被主动中止时抛出，用于区分正常失败。
 */
final class RewriteAbortedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    RewriteAbortedException() {
        super("AOF rewrite aborted");
    }
}
