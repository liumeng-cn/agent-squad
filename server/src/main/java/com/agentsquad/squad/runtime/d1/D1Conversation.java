package com.agentsquad.squad.runtime.d1;

/** D1 会话归属投影。 */
public class D1Conversation {

    private final String id;
    private final String userId;
    private final String title;
    private volatile String activeTaskId;

    public D1Conversation(String id, String userId, String title) {
        this.id = id;
        this.userId = userId;
        this.title = title;
    }

    public String id() { return id; }
    public String userId() { return userId; }
    public String title() { return title; }
    public String activeTaskId() { return activeTaskId; }
    public void setActiveTaskId(String activeTaskId) { this.activeTaskId = activeTaskId; }
}
