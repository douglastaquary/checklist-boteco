package com.checklistboteco.backend.ai;

import com.checklistboteco.backend.ai.AiModels.*;
import com.checklistboteco.backend.model.Models.User;
import com.checklistboteco.backend.security.AdminGuard;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/ai")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AiResource {
    @Inject AdminGuard guard;
    @Inject OpenAiChatService chat;
    @Inject AiUsageService usage;

    @POST @Path("/chat") public ChatResponse chat(@HeaderParam("Authorization") String auth,ChatRequest request){ User admin=guard.requireAdmin(auth); return chat.chat(admin,request); }
    @GET @Path("/usage") public UsageSummary usage(@HeaderParam("Authorization") String auth,@QueryParam("month") String month){ guard.requireAdmin(auth); return usage.summary(month); }
    @PUT @Path("/budget") public Budget budget(@HeaderParam("Authorization") String auth,BudgetUpdate request){ guard.requireAdmin(auth); return usage.update(request); }
}
