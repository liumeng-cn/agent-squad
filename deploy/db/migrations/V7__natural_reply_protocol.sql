-- 保留旧在途协议，新受理由Mapper明确写入NATURAL；仅供显式执行。
ALTER TABLE sq_response_execution
    ADD COLUMN completion_protocol varchar(16) NOT NULL DEFAULT 'PROPOSAL' COMMENT '回复完成协议[PROPOSAL-旧候选,NATURAL-自然输出]';
