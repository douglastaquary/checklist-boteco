package com.checklistboteco.backend.web;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class AdminResource {
    @Inject Template admin;
    @GET @Produces(MediaType.TEXT_HTML) public TemplateInstance index(){ return admin.data("title","Xocoalho"); }
}
