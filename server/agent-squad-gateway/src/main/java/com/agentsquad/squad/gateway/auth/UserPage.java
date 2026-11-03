package com.agentsquad.squad.gateway.auth;

import java.util.List;

public record UserPage(List<UserSummary> items, String nextCursor) {
}
