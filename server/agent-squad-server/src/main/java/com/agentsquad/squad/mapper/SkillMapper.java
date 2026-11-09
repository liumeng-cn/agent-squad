package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.SkillVersionPO;
import com.agentsquad.squad.model.po.SkillTextFilePO;
import com.agentsquad.squad.model.po.SkillGrantPO;
import com.agentsquad.squad.model.bo.SkillReferenceBO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 个人技能存储映射。
 * <p>
 * 保留用户归属、版本锁和授权比较更新；不承载业务准入规则。
 */
@Mapper
public interface SkillMapper extends BaseMapper<SkillVersionPO> {

    /**
     * 查询用户可访问的技能版本，排除已删除记录
     */
    default SkillVersionPO find(String userId, String versionId) {
        return selectOne(new QueryWrapper<SkillVersionPO>()
                .eq("user_id", userId)
                .eq("skill_version_id", versionId)
                .eq("del_flag", "0"));
    }

    SkillVersionPO lockVersion(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillVersionPO findNamedVersion(@Param("userId") String userId, @Param("name") String name, @Param("version") String version);

    SkillVersionPO findNamedVersionCurrent(@Param("userId") String userId, @Param("name") String name, @Param("version") String version);

    List<SkillVersionPO> list(@Param("userId") String userId, @Param("afterId") long afterId, @Param("limit") int limit);

    int insertVersion(@Param("userId") String userId, @Param("versionId") String versionId, @Param("name") String name, @Param("version") String version, @Param("description") String description, @Param("contentHash") String contentHash, @Param("requiredToolsJson") String requiredToolsJson);

    int insertFile(@Param("fileId") String fileId, @Param("userId") String userId, @Param("versionId") String versionId, @Param("file") SkillTextFilePO file);

    List<SkillTextFilePO> files(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillTextFilePO file(@Param("userId") String userId, @Param("versionId") String versionId, @Param("path") String path);

    int insertGrant(@Param("grantId") String grantId, @Param("userId") String userId, @Param("versionId") String versionId);

    SkillGrantPO grant(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillGrantPO lockGrant(@Param("userId") String userId, @Param("versionId") String versionId);

    int changeGrant(@Param("userId") String userId, @Param("versionId") String versionId, @Param("expectedVersion") long expectedVersion, @Param("enabled") boolean enabled);

    int revoke(@Param("userId") String userId, @Param("versionId") String versionId);

    List<String> catalogExists(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int insertCatalog(@Param("catalogId") String catalogId, @Param("userId") String userId, @Param("scopeId") String scopeId);

    int insertReference(@Param("referenceId") String referenceId, @Param("userId") String userId, @Param("scopeId") String scopeId, @Param("skill") SkillVersionPO skill);

    List<SkillReferenceBO> references(@Param("userId") String userId, @Param("scopeId") String scopeId);

    List<SkillVersionPO> lockVersions(@Param("userId") String userId);

}
