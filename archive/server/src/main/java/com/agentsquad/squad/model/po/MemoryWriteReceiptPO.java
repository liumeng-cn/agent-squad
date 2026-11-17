package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 记忆工具调用记录，用于识别重复请求。
 * <p>
 * 相同执行和逻辑键必须具有相同请求摘要，重放不再次更改记忆。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_memory_write_receipt")
public class MemoryWriteReceiptPO {
    /**
     * 规范化工具输入摘要
     */
    private String payloadHash;
    /**
     * 工具持久收据，删除正文不在此重复保存
     */
    private String resultJson;
}
