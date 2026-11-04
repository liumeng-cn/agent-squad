CREATE TABLE IF NOT EXISTS `sq_final_delivery`
(
    `id`                   bigint      NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `delivery_id`          char(36)    NOT NULL COMMENT '最终答复记录业务ID',
    `user_id`              char(36)    NOT NULL COMMENT '归属用户ID',
    `source_message_id`    char(36)    NOT NULL COMMENT '原始用户消息ID',
    `delivery_generation` bigint      NOT NULL COMMENT '用户明确要求重新生成答复时的版本号',
    `owner_type`           varchar(16) NOT NULL COMMENT '负责发布答复的类型[RESPONSE-回复执行,TASK-目标任务]',
    `owner_id`             char(36)    NOT NULL COMMENT '负责发布答复的回复执行ID或目标任务ID',
    `state`                varchar(16) NOT NULL COMMENT '答复状态[OWNED-已指定负责人,PUBLISHED-已发布,CLOSED-已关闭]',
    `published_message_id` char(36)    NULL COMMENT '已发布的最终答复消息ID',
    `creator_id`           varchar(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name`         varchar(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time`          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id`      varchar(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name`    varchar(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time`     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag`             char(1)     NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_final_delivery` (`delivery_id`),
    UNIQUE KEY `uk_final_delivery_generation` (`user_id`, `source_message_id`, `delivery_generation`),
    UNIQUE KEY `uk_final_delivery_owner` (`user_id`, `owner_type`, `owner_id`),
    UNIQUE KEY `uk_final_delivery_message` (`published_message_id`)
) ENGINE = InnoDB COMMENT ='用户请求的最终答复负责人及发布记录';
