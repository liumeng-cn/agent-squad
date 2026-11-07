ALTER TABLE sq_execution_scope
    ADD COLUMN traceparent varchar(55) DEFAULT NULL COMMENT '受理请求的W3C父追踪标识',
    ADD COLUMN tracestate varchar(512) DEFAULT NULL COMMENT '受理请求的W3C追踪状态';
