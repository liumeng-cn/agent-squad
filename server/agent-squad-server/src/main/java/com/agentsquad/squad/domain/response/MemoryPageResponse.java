package com.agentsquad.squad.domain.response;

import com.agentsquad.squad.domain.dto.MemoryView;

/**
 * 用户记忆分页。
 * <p>
 * 以稳定存储游标分页，仅包含当前用户的显式记忆。
 */
public record MemoryPageResponse(java.util.List<MemoryView> items, String nextCursor, long memoryRevision, int affectedScopes) {
}
