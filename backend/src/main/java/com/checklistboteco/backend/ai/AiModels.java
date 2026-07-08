package com.checklistboteco.backend.ai;

import java.util.ArrayList;
import java.util.List;

public final class AiModels {
    private AiModels() {}

    public static class ChatMessage { public String role, text; }
    public static class ChatRequest {
        public String clientRequestId;
        public List<ChatMessage> messages = new ArrayList<>();
    }
    public static class Usage {
        public int inputTokens, cachedInputTokens, outputTokens, totalTokens;
        public long estimatedCostMicros;
    }
    public static class Budget {
        public long monthlyLimitCents = 500;
        public int maxOutputTokens = 700;
    }
    public static class BudgetUpdate {
        public long monthlyLimitCents;
        public int maxOutputTokens;
    }
    public static class UsageSummary {
        public String month;
        public long requests, estimatedCostMicros, monthlyLimitCents;
        public int inputTokens, cachedInputTokens, outputTokens;
        public boolean blocked;
    }
    public static class AuditRecord {
        public String id, userId, month, model;
        public long createdAt, latencyMs, estimatedCostMicros;
        public int inputTokens, cachedInputTokens, outputTokens;
        public List<String> tools = new ArrayList<>();
    }
    public static class ChatResponse {
        public String requestId, answer, interpretedLocation = "Beco da Praia";
        public List<String> consultedTools = new ArrayList<>();
        public Usage usage = new Usage();
        public UsageSummary budget;
    }
}
