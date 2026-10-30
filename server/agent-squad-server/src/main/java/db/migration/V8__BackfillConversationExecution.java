package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 旧任务的消息与执行归属迁移。
 * <p>
 * 将已有用户输入、公开答案及预算引用转入新账本，不创建命令或修改 SDK 身份；DML 失败整体回滚。
 */
public class V8__BackfillConversationExecution extends BaseJavaMigration {

    @Override
    public Integer getChecksum() {
        return 2026102801;
    }

    @Override
    public void migrate(Context context) throws Exception {
        new LegacyConversationBackfill(context.getConnection()).migrate();
    }
}
