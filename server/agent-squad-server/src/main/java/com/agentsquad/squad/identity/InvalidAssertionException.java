package com.agentsquad.squad.identity;

/**
 * 内部身份断言格式或内容不合法。
 *
 * <p>仅在业务服务内部用于区分具体拒绝原因，对外统一映射为 401，不返回签名或密钥细节。</p>
 */
final class InvalidAssertionException extends RuntimeException {

    InvalidAssertionException(String reason) {
        super(reason, null, false, false);
    }
}
