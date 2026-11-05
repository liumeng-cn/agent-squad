-- 旧回复保留NULL并沿用原运行路径，新受理固定模型与思考模式
ALTER TABLE sq_response_execution
    ADD COLUMN model_selection_json json DEFAULT NULL COMMENT '受理时固定模型引用、配置指纹及推理模式，不含密钥';
