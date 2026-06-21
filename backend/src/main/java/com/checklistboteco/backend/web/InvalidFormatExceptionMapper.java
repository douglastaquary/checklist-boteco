package com.checklistboteco.backend.web;

import com.checklistboteco.backend.model.Models.ApiError;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidFormatExceptionMapper implements ExceptionMapper<InvalidFormatException> {
    @Override
    public Response toResponse(InvalidFormatException error) {
        String field = "campo";
        if (!error.getPath().isEmpty()) {
            field = error.getPath().get(error.getPath().size() - 1).getFieldName();
        }
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiError("Valor inválido para " + field + "."))
            .type(MediaType.APPLICATION_JSON)
            .build();
    }
}
