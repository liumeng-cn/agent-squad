package com.agentsquad.squad.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import com.agentsquad.squad.config.repository.SchemaInspectionMapper;
import org.springframework.dao.DataAccessException;
import java.util.List;

/**
 * 生产启动的数据库结构门禁。
 *
 * <p>生产禁止服务启动执行 DDL，只校验版本化迁移已应用到期望版本，且必需业务表存在。
 * 校验失败直接终止启动，避免在不完整结构上对外提供服务。</p>
 */
@Slf4j
@Component
@Profile("prod")
public class SchemaVersionGate implements InitializingBean {

    private static final String HISTORY_TABLE = "flyway_schema_history";
    private static final List<String> REQUIRED_TABLES = List.of(
            "sq_user", "sq_auth_session", "sq_auth_rate_bucket", "sq_auth_audit",
            "sq_runtime_admission_counter", "sq_runtime_admission_slot", "sq_final_delivery", "sq_runtime_event_inbox");

    @Resource
    private SchemaInspectionMapper schemaInspection;

    @Value("${agentsquad.schema.expected-version:6}")
    private int expectedVersion;

    @Value("${spring.sql.init.mode:always}")
    private String sqlInitMode;

    @Value("${agentsquad.identity.strict:false}")
    private boolean strictIdentity;

    @Value("${agentsquad.identity.enforce-user-status:false}")
    private boolean enforceUserStatus;

    @Value("${agentsquad.identity.allow-unsigned-local:false}")
    private boolean allowUnsignedLocal;

    /** 先拒绝危险配置，再由MyBatis只读核验受控迁移结果。 */
    @Override
    public void afterPropertiesSet() {
        if (!"never".equalsIgnoreCase(sqlInitMode) || !strictIdentity
                || !enforceUserStatus || allowUnsignedLocal) {
            throw new IllegalStateException("生产必须关闭启动 DDL 和本地身份，并强制签名及用户状态检查");
        }
        try {
            int appliedVersion = readAppliedVersion();
            if (appliedVersion < expectedVersion) {
                throw new IllegalStateException("生产数据库迁移版本落后: applied=" + appliedVersion
                        + ", expected=" + expectedVersion + "；请先执行 deploy/db/migrations 迁移");
            }
            int present = schemaInspection.countRequiredTables(REQUIRED_TABLES);
            if (present != REQUIRED_TABLES.size()) {
                throw new IllegalStateException("生产数据库缺少必需业务表: present=" + present
                        + "/" + REQUIRED_TABLES.size() + "；请先执行 deploy/db/migrations 全部受控迁移");
            }
            requireDeliveryBackfill();
            log.info("生产数据库结构校验通过，迁移版本 {}", appliedVersion);
        } catch (DataAccessException exception) {
            throw new IllegalStateException("生产数据库结构校验失败，请确认迁移已执行且账号具备只读校验权限", exception);
        }
    }

    private int readAppliedVersion() {
        try {
            int versionCount = schemaInspection.countAppliedVersions(expectedVersion);
            // 连续版本缺一不可，不能仅凭最大版本跳过中间迁移。
            return versionCount == expectedVersion ? expectedVersion : -1;
        } catch (DataAccessException exception) {
            throw new IllegalStateException("生产启动要求已执行版本化迁移（缺少 " + HISTORY_TABLE + "）", exception);
        }
    }

    /** 旧在途请求不能猜测终稿归属，缺位时阻止升级后启动。 */
    private void requireDeliveryBackfill() {
        if (schemaInspection.countPendingWithoutDelivery() > 0) {
            throw new IllegalStateException("存在缺少最终交付权的在途请求，请排空或核对回填后再启动");
        }
    }
}
