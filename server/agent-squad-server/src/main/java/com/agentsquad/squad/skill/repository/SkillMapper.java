package com.agentsquad.squad.skill.repository;

import com.agentsquad.squad.domain.entity.SkillVersionDO;
import com.agentsquad.squad.domain.entity.SkillTextFileDO;
import com.agentsquad.squad.domain.entity.SkillGrantDO;
import com.agentsquad.squad.domain.entity.SkillReferenceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 个人技能存储映射。
 * <p>
 * 保留用户归属、版本锁和授权比较更新；不承载业务准入规则。
 */
@Mapper
public interface SkillMapper {
    SkillVersionDO find(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillVersionDO lockVersion(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillVersionDO findNamedVersion(@Param("userId") String userId, @Param("name") String name, @Param("version") String version);

    SkillVersionDO findNamedVersionCurrent(@Param("userId") String userId, @Param("name") String name, @Param("version") String version);

    List<SkillVersionDO> list(@Param("userId") String userId, @Param("afterId") long afterId, @Param("limit") int limit);

    int insertVersion(@Param("userId") String userId, @Param("versionId") String versionId, @Param("name") String name, @Param("version") String version, @Param("description") String description, @Param("contentHash") String contentHash, @Param("requiredToolsJson") String requiredToolsJson);

    int insertFile(@Param("fileId") String fileId, @Param("userId") String userId, @Param("versionId") String versionId, @Param("file") SkillTextFileDO file);

    List<SkillTextFileDO> files(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillTextFileDO file(@Param("userId") String userId, @Param("versionId") String versionId, @Param("path") String path);

    int insertGrant(@Param("grantId") String grantId, @Param("userId") String userId, @Param("versionId") String versionId);

    SkillGrantDO grant(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillGrantDO lockGrant(@Param("userId") String userId, @Param("versionId") String versionId);

    int changeGrant(@Param("userId") String userId, @Param("versionId") String versionId, @Param("expectedVersion") long expectedVersion, @Param("enabled") boolean enabled);

    int revoke(@Param("userId") String userId, @Param("versionId") String versionId);

    List<String> catalogExists(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int insertCatalog(@Param("catalogId") String catalogId, @Param("userId") String userId, @Param("scopeId") String scopeId);

    int insertReference(@Param("referenceId") String referenceId, @Param("userId") String userId, @Param("scopeId") String scopeId, @Param("skill") SkillVersionDO skill);

    List<SkillReferenceDO> references(@Param("userId") String userId, @Param("scopeId") String scopeId);

    List<SkillVersionDO> lockVersions(@Param("userId") String userId);

}
