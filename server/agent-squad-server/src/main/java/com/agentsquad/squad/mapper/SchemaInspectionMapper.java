package com.agentsquad.squad.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 生产启动结构检查的
 * <p>
 * 仅以只读SQL核验迁移历史、必需表和在途交付位，不执行DDL或历史回填。
 */
@Mapper
public interface SchemaInspectionMapper {
    int countAppliedVersions(@Param("expectedVersion") int expectedVersion);

    int countRequiredTables(@Param("tableNames") List<String> tableNames);

    long countPendingWithoutDelivery();
}
