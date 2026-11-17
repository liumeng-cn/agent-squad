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
    default SkillVersionPO find(Long userId, Long versionId) {
        return selectOne(new QueryWrapper<SkillVersionPO>()
                .eq("user_id", userId)
                .eq("skill_version_id", versionId)
                .eq("del_flag", "0"));
    }

    SkillVersionPO lockVersion(@Param("userId") Long userId, @Param("versionId") Long versionId);

    SkillVersionPO findNamedVersion(@Param("userId") Long userId, @Param("name") String name, @Param("version") String version);

    SkillVersionPO findNamedVersionCurrent(@Param("userId") Long userId, @Param("name") String name, @Param("version") String version);

    List<SkillVersionPO> list(@Param("userId") Long userId, @Param("afterId") long afterId, @Param("limit") int limit);

    int insertVersion(@Param("userId") Long userId, @Param("versionId") Long versionId, @Param("name") String name, @Param("version") String version, @Param("description") String description, @Param("contentHash") String contentHash, @Param("requiredToolsJson") String requiredToolsJson);

    int insertFile(@Param("fileId") Long fileId, @Param("userId") Long userId, @Param("versionId") Long versionId, @Param("file") SkillTextFilePO file);

    List<SkillTextFilePO> files(@Param("userId") Long userId, @Param("versionId") Long versionId);

    SkillTextFilePO file(@Param("userId") Long userId, @Param("versionId") Long versionId, @Param("path") String path);

    int insertGrant(@Param("grantId") Long grantId, @Param("userId") Long userId, @Param("versionId") Long versionId);

    SkillGrantPO grant(@Param("userId") Long userId, @Param("versionId") Long versionId);

    SkillGrantPO lockGrant(@Param("userId") Long userId, @Param("versionId") Long versionId);

    int changeGrant(@Param("userId") Long userId, @Param("versionId") Long versionId, @Param("expectedVersion") long expectedVersion, @Param("enabled") boolean enabled);

    int revoke(@Param("userId") Long userId, @Param("versionId") Long versionId);

    List<Long> catalogExists(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int insertCatalog(@Param("catalogId") Long catalogId, @Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int insertReference(@Param("referenceId") Long referenceId, @Param("userId") Long userId, @Param("scopeId") Long scopeId, @Param("skill") SkillVersionPO skill);

    List<SkillReferenceBO> references(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    List<SkillVersionPO> lockVersions(@Param("userId") Long userId);

}
