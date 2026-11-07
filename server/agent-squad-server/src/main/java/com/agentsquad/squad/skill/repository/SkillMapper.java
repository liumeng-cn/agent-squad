package com.agentsquad.squad.skill.repository;

import com.agentsquad.squad.domain.entity.SkillVersionRow;
import com.agentsquad.squad.domain.entity.SkillTextFile;
import com.agentsquad.squad.domain.entity.SkillGrantRow;
import com.agentsquad.squad.domain.entity.SkillReference;
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
    SkillVersionRow find(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillVersionRow lockVersion(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillVersionRow findNamedVersion(@Param("userId") String userId, @Param("name") String name, @Param("version") String version);

    SkillVersionRow findNamedVersionCurrent(@Param("userId") String userId, @Param("name") String name, @Param("version") String version);

    List<SkillVersionRow> list(@Param("userId") String userId, @Param("afterId") long afterId, @Param("limit") int limit);

    int insertVersion(@Param("userId") String userId, @Param("versionId") String versionId, @Param("name") String name, @Param("version") String version, @Param("description") String description, @Param("contentHash") String contentHash, @Param("requiredToolsJson") String requiredToolsJson);

    int insertFile(@Param("fileId") String fileId, @Param("userId") String userId, @Param("versionId") String versionId, @Param("file") SkillTextFile file);

    List<SkillTextFile> files(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillTextFile file(@Param("userId") String userId, @Param("versionId") String versionId, @Param("path") String path);

    int insertGrant(@Param("grantId") String grantId, @Param("userId") String userId, @Param("versionId") String versionId);

    SkillGrantRow grant(@Param("userId") String userId, @Param("versionId") String versionId);

    SkillGrantRow lockGrant(@Param("userId") String userId, @Param("versionId") String versionId);

    int changeGrant(@Param("userId") String userId, @Param("versionId") String versionId, @Param("expectedVersion") long expectedVersion, @Param("enabled") boolean enabled);

    int revoke(@Param("userId") String userId, @Param("versionId") String versionId);

    List<String> catalogExists(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int insertCatalog(@Param("catalogId") String catalogId, @Param("userId") String userId, @Param("scopeId") String scopeId);

    int insertReference(@Param("referenceId") String referenceId, @Param("userId") String userId, @Param("scopeId") String scopeId, @Param("skill") SkillVersionRow skill);

    List<SkillReference> references(@Param("userId") String userId, @Param("scopeId") String scopeId);

    List<SkillVersionRow> lockVersions(@Param("userId") String userId);

}
