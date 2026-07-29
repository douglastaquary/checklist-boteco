package com.checklistboteco.backend.web;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Cognito implicit flow lands here with #id_token in the fragment (client-side only). */
@Path("/auth/callback")
public class AuthCallbackResource {
    @Inject Template authCallback;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance callback() {
        return authCallback.data("title", "Xocoalho");
    }
}
