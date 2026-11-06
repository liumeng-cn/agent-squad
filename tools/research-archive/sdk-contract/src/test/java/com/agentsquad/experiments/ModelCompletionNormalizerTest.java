package com.agentsquad.experiments;

import com.agentsquad.squad.integration.agentscope.completion.ModelCompletionNormalizer;
import com.agentsquad.squad.integration.agentscope.completion.ModelCompletionStatus;
import com.agentsquad.squad.integration.agentscope.completion.ModelEvidenceIssue;
import com.agentsquad.squad.integration.agentscope.completion.ModelFinishReason;
import io.agentscope.core.session.SessionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型结束证据的保守判定验证
 * <p>
 * 直接测试业务适配层源码，覆盖重放、隔离、缺失、冲突与取消；人工事件用于边界而非SDK能力证明。
 */
class ModelCompletionNormalizerTest {
    private final ModelCompletionNormalizer normalizer = new ModelCompletionNormalizer();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void combinesReasonAndUsageFromIndependentEvents() throws Exception {
        var facts = List.of(dispatch(), chunk(2, Map.of("finishReason", "stop")),
                chunk(3, Map.of("usage", usage(7, 4))), end(4, "completed", Map.of("usage", usage(7, 4))));
        var result = normalizer.normalize("run", "turn", "call", facts);
        assertThat(result.hasConfirmedNormalStop()).isTrue();
        assertThat(result.hasTrustedUsage()).isTrue();
        assertThat(result.reasonSequence()).isEqualTo(2);
        assertThat(result.usageSequence()).isEqualTo(4);
        assertThat(result.usage().inputTokens()).isEqualTo(7);
        assertThat(result.usage().outputTokens()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"length", "content_filter", "tool_calls", "future_reason"})
    void nonStopReasonsNeverBecomeNormalSuccess(String reason) throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                chunk(2, Map.of("finishReason", reason)), end(3, "completed", Map.of())));
        assertThat(result.hasConfirmedNormalStop()).isFalse();
        assertThat(result.completionIssue()).isEqualTo(reason.equals("future_reason")
                ? ModelEvidenceIssue.UNKNOWN_REASON : ModelEvidenceIssue.NONE);
    }

    @Test
    void missingReasonDoesNotUseSdkCompletedAsSuccess() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(), end(2, "completed", Map.of())));
        assertThat(result.finishReason()).isEqualTo(ModelFinishReason.UNKNOWN);
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.MISSING_REASON);
        assertThat(result.hasConfirmedNormalStop()).isFalse();
    }

    @Test
    void conflictingReasonsAreUnknown() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                chunk(2, Map.of("finishReason", "stop")), end(3, "completed", Map.of("finishReason", "length"))));
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.CONFLICTING_REASON);
        assertThat(result.finishReason()).isEqualTo(ModelFinishReason.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelled", "failed", "future_status"})
    void knownStopCannotOverrideTechnicalFailure(String status) throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                chunk(2, Map.of("finishReason", "stop")), end(3, status, Map.of())));
        assertThat(result.hasConfirmedNormalStop()).isFalse();
        if (status.equals("future_status")) {
            assertThat(result.status()).isEqualTo(ModelCompletionStatus.UNKNOWN);
        }
    }

    @Test
    void duplicateAndOutOfOrderDeliveryIsIdempotent() throws Exception {
        var first = dispatch();
        var reason = chunk(2, Map.of("finishReason", "stop"));
        var last = end(3, "completed", Map.of("usage", usage(7, 4)));
        var ordered = normalizer.normalize("run", "turn", "call", List.of(first, reason, last));
        assertThat(normalizer.normalize("run", "turn", "call", List.of(last, first, reason, reason, first)))
                .isEqualTo(ordered);
    }

    @Test
    void missingEventsMustBeRecoveredBeforeClassification() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(), end(3, "completed", Map.of("finishReason", "stop"))));
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.EVENT_GAP);
        assertThat(result.hasConfirmedNormalStop()).isFalse();
    }

    @Test
    void sameSequenceDifferentContentIsAConflict() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                chunk(2, Map.of("finishReason", "stop")), chunk(2, Map.of("finishReason", "length"))));
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.EVENT_CONFLICT);
    }

    @Test
    void otherAttemptCannotSupplyMissingReason() throws Exception {
        var other = new SessionEvent(1, "other", 2, 1, "model/chunk", "old-run", "turn", false,
                json.writeValueAsString(Map.of("modelCallId", "call", "chunk", Map.of("finishReason", "stop"))));
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(), other, end(3, "completed", Map.of())));
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.MISSING_REASON);
    }

    @Test
    void otherModelCallCannotSupplyMissingReason() throws Exception {
        var other = event(2, "model/chunk", Map.of("modelCallId", "other-call", "chunk", Map.of("finishReason", "stop")));
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(), other, end(3, "completed", Map.of())));
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.MISSING_REASON);
    }

    @Test
    void incompleteLifecycleIsNotConfirmed() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(), chunk(2, Map.of("finishReason", "stop"))));
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.MISSING_END);
        assertThat(result.hasConfirmedNormalStop()).isFalse();
    }

    @Test
    void lateChunkAfterEndIsNotAValidLifecycle() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                end(2, "completed", Map.of()), chunk(3, Map.of("finishReason", "stop"))));
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.INVALID_LIFECYCLE);
    }

    @Test
    void usageIsNotSummedAcrossCumulativeChunks() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                chunk(2, Map.of("usage", usage(3, 1))),
                chunk(3, Map.of("finishReason", "stop", "usage", usage(7, 4))), end(4, "completed", Map.of())));
        assertThat(result.usage().inputTokens()).isEqualTo(7);
        assertThat(result.usage().outputTokens()).isEqualTo(4);
        assertThat(result.usageSequence()).isEqualTo(3);
    }

    @Test
    void conflictingFinalUsageCannotBeSettled() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                chunk(2, Map.of("finishReason", "stop", "usage", usage(7, 4))),
                end(3, "completed", Map.of("usage", usage(7, 5)))));
        assertThat(result.usageIssue()).isEqualTo(ModelEvidenceIssue.CONFLICTING_USAGE);
        assertThat(result.usage()).isNull();
        assertThat(result.hasTrustedUsage()).isFalse();
        assertThat(result.hasConfirmedNormalStop()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0.5", "2147483648"})
    void invalidNumericUsageIsNeverReplacedWithZero(String input) throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                end(2, "completed", Map.of("finishReason", "stop", "usage", usage(new java.math.BigDecimal(input), 4)))));
        assertThat(result.usageIssue()).isEqualTo(ModelEvidenceIssue.INVALID_USAGE);
        assertThat(result.hasTrustedUsage()).isFalse();
    }

    @Test
    void absentUsageDoesNotFabricateZeroTokens() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(), end(2, "completed", Map.of("finishReason", "stop"))));
        assertThat(result.hasConfirmedNormalStop()).isTrue();
        assertThat(result.usageIssue()).isEqualTo(ModelEvidenceIssue.MISSING_USAGE);
        assertThat(result.usage()).isNull();
    }

    @Test
    void missingDispatchCannotConfirmReasonOrUsage() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(end(2, "completed",
                Map.of("finishReason", "stop", "usage", usage(7, 4)))));
        assertThat(result.hasConfirmedNormalStop()).isFalse();
        assertThat(result.hasTrustedUsage()).isFalse();
    }

    @Test
    void malformedChunkCannotConfirmNormalStop() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                event(2, "model/chunk", Map.of("modelCallId", "call", "chunk", "invalid")),
                end(3, "completed", Map.of("finishReason", "stop", "usage", usage(7, 4)))));
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.INVALID_PAYLOAD);
        assertThat(result.hasConfirmedNormalStop()).isFalse();
        assertThat(result.hasTrustedUsage()).isFalse();
    }

    @Test
    void sameEventIdWithDifferentSequenceIsAConflict() throws Exception {
        SessionEvent first = dispatch();
        SessionEvent forged = new SessionEvent(1, first.eventId(), 2, 1, "model/end", "run", "turn", true,
                json.writeValueAsString(Map.of("modelCallId", "call", "status", "completed", "finishReason", "stop")));
        var result = normalizer.normalize("run", "turn", "call", List.of(first, forged));
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.EVENT_CONFLICT);
    }

    @Test
    void missingEndCannotConfirmIntermediateUsage() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                chunk(2, Map.of("finishReason", "stop", "usage", usage(7, 4)))));
        assertThat(result.usage()).isNull();
        assertThat(result.usageIssue()).isEqualTo(ModelEvidenceIssue.MISSING_END);
    }

    @Test
    void explicitZeroUsageIsDifferentFromMissingUsage() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                end(2, "completed", Map.of("finishReason", "stop", "usage", usage(0, 0)))));
        assertThat(result.hasTrustedUsage()).isTrue();
        assertThat(result.usage().inputTokens()).isZero();
    }

    @Test
    void numericStringUsageIsNotTrusted() throws Exception {
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(),
                end(2, "completed", Map.of("finishReason", "stop", "usage", Map.of("inputTokens", "7", "outputTokens", 4)))));
        assertThat(result.usageIssue()).isEqualTo(ModelEvidenceIssue.INVALID_USAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "\"invalid\""})
    void malformedNativeModelPayloadBecomesUnknown(String payload) throws Exception {
        var invalid = new SessionEvent(1, "invalid", 2, 1, "model/chunk", "run", "turn", true, payload);
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(), invalid,
                end(3, "completed", Map.of("finishReason", "stop"))));
        assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.INVALID_PAYLOAD);
        assertThat(result.hasConfirmedNormalStop()).isFalse();
    }

    @Test
    void unrelatedJournalPayloadIsNotParsedAsModelEvidence() throws Exception {
        var unrelated = new SessionEvent(1, "unrelated", 2, 1, "presentation/hint", "run", "turn", false, "[]");
        var result = normalizer.normalize("run", "turn", "call", List.of(dispatch(), unrelated,
                end(3, "completed", Map.of("finishReason", "stop"))));
        assertThat(result.hasConfirmedNormalStop()).isTrue();
    }

    private SessionEvent dispatch() throws Exception {
        return event(1, "model/dispatch", Map.of("modelCallId", "call"));
    }

    private SessionEvent chunk(long sequence, Map<String, Object> chunk) throws Exception {
        return event(sequence, "model/chunk", Map.of("modelCallId", "call", "chunk", chunk));
    }

    private SessionEvent end(long sequence, String status, Map<String, Object> extra) throws Exception {
        var data = new java.util.HashMap<String, Object>(extra);
        data.put("modelCallId", "call");
        data.put("status", status);
        return event(sequence, "model/end", data);
    }

    private SessionEvent event(long sequence, String type, Map<String, Object> data) throws Exception {
        return new SessionEvent(1, "event-" + sequence, sequence, 1, type, "run", "turn", true, json.writeValueAsString(data));
    }

    private Map<String, Object> usage(Number input, Number output) {
        return Map.of("inputTokens", input, "outputTokens", output);
    }
}
