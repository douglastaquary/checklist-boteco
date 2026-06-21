package com.checklistboteco.backend.web;

import com.checklistboteco.backend.model.Models.ApiError;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class IllegalArgumentMapper implements ExceptionMapper<IllegalArgumentException> {
    public Response toResponse(IllegalArgumentException error) {
        return Response.status(400)
            .entity(new ApiError(error.getMessage()))
            .type(MediaType.APPLICATION_JSON)
            .build();
    }
}
