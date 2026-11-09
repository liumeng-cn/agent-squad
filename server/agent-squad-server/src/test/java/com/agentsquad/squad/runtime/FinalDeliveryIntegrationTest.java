package com.agentsquad.squad.runtime;

import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.bo.GoalTaskCreateBO;
import com.agentsquad.squad.dto.MessageCreateDTO;
import com.agentsquad.squad.vo.MessageReceiptVO;
import com.agentsquad.squad.service.FinalDeliveryService;
import com.agentsquad.squad.service.GoalTaskCreationService;
import com.agentsquad.squad.service.MessageAcceptanceService;
import com.agentsquad.squad.service.ResponseExecutionService;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.bo.NaturalReplyEvidenceBO;
import com.agentsquad.squad.enums.ResponseExecutionStatus;
import com.agentsquad.squad.bo.NativeEventViewBO;
import com.agentsquad.squad.service.TaskEventPublishingService;
import com.agentsquad.squad.agent.MasterAgentIdentity;
import com.agentsquad.squad.bo.ProjectionTargetBO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 同源最终交付的确定性MySQL回归。
 * <p>
 * 故意让委派后的回复仍提交完整正文，验证平台裁决、事件重放、独立问题及事务恢复边界。
 */
class FinalDeliveryIntegrationTest {
    @Test
    void delegatedResponseCannotPublishSecondEssayAndReplayKeepsIdentity(@TempDir Path directory) {
        try (var context = start(directory)) {
            String user = UUID.randomUUID().toString();
            var receipt = message(context, user, null, "first");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            var goals = context.getBean(GoalTaskCreationService.class);
            var request = new GoalTaskCreateBO("goal", "两篇作文", "协作写两篇完整作文", receipt.messageId());
            var task = goals.create(user, session(jdbc, receipt), request);
            assertThat(goals.create(user, session(jdbc, receipt), request).taskId()).isEqualTo(task.taskId());
            complete(context, user, receipt, "不应公开的两篇完整作文");
            assertThat(answers(jdbc, user)).isZero();
            transaction(context).executeWithoutResult(status -> context.getBean(TaskEventPublishingService.class)
                    .insertProjected(user, task.taskId(), 2, "final", "answer.completed",
                            "{\"text\":\"权威两篇作文\",\"revision\":0}", LocalDateTime.now()));
            transaction(context).executeWithoutResult(status -> context.getBean(TaskEventPublishingService.class)
                    .insertProjected(user, task.taskId(), 3, "replay", "answer.completed",
                            "{\"text\":\"权威两篇作文\",\"revision\":0}", LocalDateTime.now()));
            assertThat(answers(jdbc, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT content FROM sq_message WHERE user_id=? AND role='ASSISTANT'", String.class, user))
                    .isEqualTo("权威两篇作文");
            assertThat(jdbc.queryForObject("SELECT published_message_id FROM sq_final_delivery WHERE user_id=?", String.class, user))
                    .isEqualTo(jdbc.queryForObject("SELECT message_id FROM sq_message WHERE user_id=? AND role='ASSISTANT'", String.class, user));
            var independent = message(context, user, receipt.conversationId(), "independent");
            complete(context, user, independent, "独立问题答案");
            assertThat(answers(jdbc, user)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT status FROM sq_goal_task WHERE task_id=?", String.class, task.taskId())).isEqualTo("QUEUED");
        }
    }

    @Test
    void directAnswerAndIdempotentAcceptanceHaveOneDelivery(@TempDir Path directory) {
        try (var context = start(directory)) {
            String user = UUID.randomUUID().toString();
            var receipt = message(context, user, null, "direct");
            assertThat(message(context, user, null, "direct").executionId()).isEqualTo(receipt.executionId());
            complete(context, user, receipt, "直接写两篇作文");
            complete(context, user, receipt, "直接写两篇作文");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(answers(jdbc, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task WHERE user_id=?", Long.class, user)).isZero();
            assertThatThrownBy(() -> context.getBean(GoalTaskCreationService.class).create(user, session(jdbc, receipt),
                    new GoalTaskCreateBO("late", "迟到目标", "重复交付", receipt.messageId())))
                    .isInstanceOf(RuntimeBusinessException.class);
        }
    }

    @Test
    void rollbackAndNewGenerationRejectLateOwners(@TempDir Path directory) {
        try (var context = start(directory)) {
            String user = UUID.randomUUID().toString();
            var receipt = message(context, user, null, "rollback");
            var delivery = context.getBean(FinalDeliveryService.class);
            var tx = transaction(context);
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                delivery.claimPublication(user, receipt.messageId(), "RESPONSE", receipt.executionId());
                throw new IllegalStateException("模拟发布事务崩溃");
            })).isInstanceOf(IllegalStateException.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT state FROM sq_final_delivery WHERE user_id=?", String.class, user)).isEqualTo("OWNED");
            String next = UUID.randomUUID().toString();
            tx.executeWithoutResult(status -> delivery.retry(user, receipt.messageId(), receipt.executionId(), next));
            assertThat(tx.<String>execute(status -> delivery.claimPublication(user, receipt.messageId(), "RESPONSE", receipt.executionId()))).isNull();
            String published = tx.execute(status -> delivery.claimPublication(user, receipt.messageId(), "RESPONSE", next));
            assertThat(tx.<String>execute(status -> delivery.claimPublication(user, receipt.messageId(), "RESPONSE", next))).isEqualTo(published);
            tx.executeWithoutResult(status -> delivery.close(user, receipt.messageId(), "RESPONSE", receipt.executionId()));
            assertThat(jdbc.queryForObject("SELECT state FROM sq_final_delivery WHERE user_id=? ORDER BY delivery_generation DESC LIMIT 1", String.class, user))
                    .isEqualTo("PUBLISHED");
        }
    }

    @Test
    void transferAndCompletionRaceHaveExactlyOneOwner(@TempDir Path directory) throws Exception {
        try (var context = start(directory); var workers = Executors.newFixedThreadPool(2)) {
            String user = UUID.randomUUID().toString();
            var receipt = message(context, user, null, "race");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            var gate = new CountDownLatch(1);
            var completion = workers.submit(() -> {
                gate.await();
                complete(context, user, receipt, "普通完成候选");
                return true;
            });
            var transfer = workers.submit(() -> {
                gate.await();
                try {
                    return context.getBean(GoalTaskCreationService.class).create(user, session(jdbc, receipt),
                            new GoalTaskCreateBO("race-goal", "竞态目标", "任务交付", receipt.messageId())).taskId();
                } catch (RuntimeBusinessException conflict) {
                    return null;
                }
            });
            gate.countDown();
            assertThat(completion.get()).isTrue();
            String taskId = transfer.get();
            if (taskId != null) {
                transaction(context).executeWithoutResult(status -> context.getBean(TaskEventPublishingService.class)
                        .insertProjected(user, taskId, 2, "race-final", "answer.completed",
                                "{\"text\":\"任务交付\"}", LocalDateTime.now()));
            }
            assertThat(answers(jdbc, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_final_delivery WHERE user_id=? AND state='PUBLISHED'", Long.class, user)).isEqualTo(1);
        }
    }

    @Test
    void cancelledTaskRejectsLateResultWithoutRevivingOriginalResponse(@TempDir Path directory) {
        try (var context = start(directory)) {
            String user = UUID.randomUUID().toString();
            var receipt = message(context, user, null, "cancelled");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String taskId = context.getBean(GoalTaskCreationService.class).create(user, session(jdbc, receipt),
                    new GoalTaskCreateBO("cancel-goal", "取消目标", "完成工作", receipt.messageId())).taskId();
            transaction(context).executeWithoutResult(status -> {
                jdbc.update("UPDATE sq_goal_task SET status='CANCELLED' WHERE task_id=?", taskId);
                context.getBean(TaskEventPublishingService.class).insertProjected(user, taskId, 2, "cancel", "task.completed",
                        "{\"status\":\"CANCELLED\"}", LocalDateTime.now());
            });
            complete(context, user, receipt, "委派后不应公开的结果");
            transaction(context).executeWithoutResult(status -> context.getBean(TaskEventPublishingService.class)
                    .insertProjected(user, taskId, 3, "late-final", "answer.completed",
                            "{\"text\":\"取消后迟到的结果\"}", LocalDateTime.now()));
            assertThat(answers(jdbc, user)).isZero();
            assertThat(jdbc.queryForObject("SELECT state FROM sq_final_delivery WHERE user_id=?", String.class, user)).isEqualTo("CLOSED");
            assertThat(jdbc.queryForObject("SELECT status FROM sq_goal_task WHERE task_id=?", String.class, taskId)).isEqualTo("CANCELLED");
        }
    }

    @Test
    void failedTaskKeepsFailureResultAndDoesNotRestoreResponseOwnership(@TempDir Path directory) {
        try (var context = start(directory)) {
            String user = UUID.randomUUID().toString();
            var receipt = message(context, user, null, "failed");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String taskId = context.getBean(GoalTaskCreationService.class).create(user, session(jdbc, receipt),
                    new GoalTaskCreateBO("failed-goal", "失败目标", "完成工作", receipt.messageId())).taskId();
            transaction(context).executeWithoutResult(status -> {
                jdbc.update("UPDATE sq_goal_task SET status='FAILED' WHERE task_id=?", taskId);
                context.getBean(TaskEventPublishingService.class).insertProjected(user, taskId, 2, "failed-final", "answer.completed",
                        "{\"text\":\"任务未完成，已保留输入\"}", LocalDateTime.now());
                context.getBean(TaskEventPublishingService.class).insertProjected(user, taskId, 3, "failed-completed", "task.completed",
                        "{\"status\":\"FAILED\"}", LocalDateTime.now());
            });
            complete(context, user, receipt, "不应作为另一份结果公开");
            assertThat(answers(jdbc, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT content FROM sq_message WHERE user_id=? AND role='ASSISTANT'", String.class, user))
                    .isEqualTo("任务未完成，已保留输入");
            assertThatThrownBy(() -> transaction(context).executeWithoutResult(status -> context.getBean(FinalDeliveryService.class)
                    .retry(user, receipt.messageId(), receipt.executionId(), UUID.randomUUID().toString())))
                    .isInstanceOf(RuntimeBusinessException.class);
        }
    }

    @Test
    void replayedCompletionAndStaleRevisionCannotReplaceCurrentAnswer(@TempDir Path directory) {
        try (var context = start(directory)) {
            String user = UUID.randomUUID().toString();
            var receipt = message(context, user, null, "revision");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String taskId = context.getBean(GoalTaskCreationService.class).create(user, session(jdbc, receipt),
                    new GoalTaskCreateBO("revision-goal", "修订目标", "完成工作", receipt.messageId())).taskId();
            var projector = context.getBean(TaskEventPublishingService.class);
            var tx = transaction(context);
            tx.executeWithoutResult(status -> projector.insertProjected(user, taskId, 2, "original", "answer.completed",
                    "{\"text\":\"原终稿\"}", LocalDateTime.now()));
            jdbc.update("UPDATE sq_goal_task SET answer_revision=2 WHERE task_id=?", taskId);
            tx.executeWithoutResult(status -> projector.insertProjected(user, taskId, 3, "revision-two", "answer.replaced",
                    "{\"text\":\"修订终稿\",\"revision\":2}", LocalDateTime.now()));
            long eventCount = jdbc.queryForObject("SELECT COUNT(*) FROM sq_conversation_event WHERE user_id=? AND event_type='message.completed'", Long.class, user);
            tx.executeWithoutResult(status -> projector.insertProjected(user, taskId, 4, "completion-replayed", "answer.completed",
                    "{\"text\":\"原终稿\"}", LocalDateTime.now()));
            tx.executeWithoutResult(status -> projector.insertProjected(user, taskId, 5, "stale-revision", "answer.replaced",
                    "{\"text\":\"旧修订\",\"revision\":1}", LocalDateTime.now()));
            tx.executeWithoutResult(status -> projector.insertProjected(user, taskId, 6, "same-revision", "answer.replaced",
                    "{\"text\":\"同版本不同正文\",\"revision\":2}", LocalDateTime.now()));
            assertThat(jdbc.queryForObject("SELECT content FROM sq_message WHERE user_id=? AND role='ASSISTANT'", String.class, user)).isEqualTo("修订终稿");
            assertThat(jdbc.queryForObject("SELECT revision FROM sq_message WHERE user_id=? AND role='ASSISTANT'", Long.class, user)).isEqualTo(2);
            var snapshot = context.getBean(com.agentsquad.squad.service.TaskSnapshotService.class).snapshot(user, taskId);
            assertThat(snapshot.answer().text()).isEqualTo("修订终稿");
            assertThat(snapshot.answer().revision()).isEqualTo(2);
            // 历史完成事件缺修订号时，只能按已发布同正文消息的确切版本恢复。
            jdbc.update("UPDATE sq_task_event SET payload_json='{\"text\":\"修订终稿\"}' WHERE user_id=? AND producer_key='revision-two'", user);
            var historical = context.getBean(com.agentsquad.squad.service.TaskSnapshotService.class).snapshot(user, taskId);
            assertThat(historical.answer().text()).isEqualTo("修订终稿");
            assertThat(historical.answer().revision()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_conversation_event WHERE user_id=? AND event_type='message.completed'", Long.class, user)).isEqualTo(eventCount);
        }
    }

    private void complete(ConfigurableApplicationContext context, String user, MessageReceiptVO receipt, String answer) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
        String scopeId = jdbc.queryForObject("SELECT scope_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
        var scope = context.getBean(ExecutionScopeMapper.class).findOwned(user, scopeId);
        var service = context.getBean(ResponseExecutionService.class);
        String turn = UUID.randomUUID().toString();
        // 此处只验证交付归属竞争，使用自然回复适配器的可信输出；SDK 日志映射由独立集成测试验证。
        var output = new NaturalReplyEvidenceBO(
                ResponseExecutionStatus.SUCCEEDED, answer, null);
        service.accept(scope, new ProjectionTargetBO(user, null, binding, MasterAgentIdentity.AGENT_ID, session(jdbc, receipt), 0),
                new NativeEventViewBO(1, "end", "run/end", "run", turn, "completed", 0, java.util.Map.of("naturalOutput", output)));
    }

    private MessageReceiptVO message(ConfigurableApplicationContext context, String user, String conversation, String key) {
        return context.getBean(MessageAcceptanceService.class).accept(user, conversation, key,
                new MessageCreateDTO("写两篇作文", List.of()));
    }

    private long answers(JdbcTemplate jdbc, String user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE user_id=? AND role='ASSISTANT'", Long.class, user);
    }

    private String session(JdbcTemplate jdbc, MessageReceiptVO receipt) {
        return jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=(SELECT binding_id FROM sq_response_execution WHERE execution_id=?)", String.class, receipt.executionId());
    }

    private TransactionTemplate transaction(ConfigurableApplicationContext context) {
        return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    private ConfigurableApplicationContext start(Path directory) {
        return MessageAcceptanceHttpTest.start(com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory),
                com.agentsquad.squad.testsupport.MySqlTestDatabase.username(),
                com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), UUID.randomUUID().toString());
    }
}
