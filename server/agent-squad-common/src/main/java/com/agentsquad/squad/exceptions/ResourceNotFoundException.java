package com.agentsquad.squad.exceptions;

/**
 * 目标资源不存在或对当前操作者不可见。
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
