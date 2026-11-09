package com.agentsquad.squad.exceptions;

/**
 * 已认证但无目标操作权限。
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException() {
        super("无权限执行该操作");
    }
}
