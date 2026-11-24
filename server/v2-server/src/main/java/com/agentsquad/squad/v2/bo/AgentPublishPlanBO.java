package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.po.AgentPO;
import com.agentsquad.squad.v2.po.AgentReleaseSubagentPO;
import com.agentsquad.squad.v2.po.AgentReleaseToolPO;
import com.agentsquad.squad.v2.po.AgentVersionPO;
import com.agentsquad.squad.v2.vo.AgentPublishDependencyVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Master 发布解析计划
 * <p>
 * 承载一次发布预检或发布提交解析出的完整依赖图、待定格快照行、摘要指纹和阻断原因。
 */
@Data
public class AgentPublishPlanBO {
    private AgentPO master;

    private AgentVersionPO masterVersion;

    private Integer currentReleaseNo;

    private String dependencyDigest;

    private List<String> errors = new ArrayList<>();

    private List<AgentPublishDependencyVO> dependencies = new ArrayList<>();

    private List<AgentReleaseSubagentPO> releaseSubagents = new ArrayList<>();

    private List<AgentReleaseToolPO> releaseTools = new ArrayList<>();

    private Set<Long> toolIds = new TreeSet<>();

    private Set<Long> mcpServerIds = new TreeSet<>();

    private Set<Long> subagentIds = new TreeSet<>();

    public boolean isPublishable() {
        return errors.isEmpty();
    }
}
