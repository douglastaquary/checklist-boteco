package com.checklistboteco.backend.workclock.web;

import com.checklistboteco.backend.model.Models.WorkClockEntry;
import com.checklistboteco.backend.security.AdminGuard;
import com.checklistboteco.backend.workclock.application.WorkClockService;
import com.checklistboteco.backend.workclock.domain.WorkClockModels.UserWorkSchedule;
import com.checklistboteco.backend.workclock.domain.WorkClockModels.WorkClockSummaryRow;
import com.checklistboteco.backend.workclock.domain.WorkClockModels.WorksiteInfo;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.List;

@Path("/api/work-clock")
public class WorkClockResource {
    @Inject AdminGuard guard;
    @Inject WorkClockService service;

    @GET
    @Path("/summary")
    @Produces(MediaType.APPLICATION_JSON)
    public List<WorkClockSummaryRow> summary(
        @HeaderParam("Authorization") String auth,
        @QueryParam("from") LocalDate from,
        @QueryParam("to") LocalDate to,
        @QueryParam("userId") String userId
    ) {
        guard.requireAdmin(auth);
        LocalDate start = from != null ? from : LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        LocalDate end = to != null ? to : start.plusDays(6);
        return service.summary(start, end, userId);
    }

    @GET
    @Path("/entries")
    @Produces(MediaType.APPLICATION_JSON)
    public List<WorkClockEntry> entries(
        @HeaderParam("Authorization") String auth,
        @QueryParam("userId") String userId,
        @QueryParam("from") LocalDate from,
        @QueryParam("to") LocalDate to
    ) {
        guard.requireAdmin(auth);
        if (userId == null || userId.isBlank()) {
            throw badRequest("userId obrigatório");
        }
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start;
        return service.entries(userId, start, end);
    }

    @GET
    @Path("/schedule/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public UserWorkSchedule getSchedule(
        @HeaderParam("Authorization") String auth,
        @PathParam("userId") String userId
    ) {
        guard.requireAdmin(auth);
        return service.getSchedule(userId);
    }

    @PUT
    @Path("/schedule/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserWorkSchedule saveSchedule(
        @HeaderParam("Authorization") String auth,
        @PathParam("userId") String userId,
        UserWorkSchedule schedule
    ) {
        guard.requireAdmin(auth);
        return service.saveSchedule(userId, schedule);
    }

    @GET
    @Path("/worksite")
    @Produces(MediaType.APPLICATION_JSON)
    public WorksiteInfo worksite(@HeaderParam("Authorization") String auth) {
        guard.requireToken(auth);
        return service.worksite();
    }

    @GET
    @Path("/export.csv")
    @Produces("text/csv; charset=UTF-8")
    public Response exportCsv(
        @HeaderParam("Authorization") String auth,
        @QueryParam("year") @DefaultValue("0") int year,
        @QueryParam("month") @DefaultValue("0") int month
    ) {
        guard.requireAdmin(auth);
        int resolvedYear = year > 0 ? year : LocalDate.now().getYear();
        int resolvedMonth = month > 0 ? month : LocalDate.now().getMonthValue();
        byte[] body = service.exportCsv(resolvedYear, resolvedMonth);
        return Response.ok(body)
            .header("Content-Disposition", "attachment; filename=\"ponto-" + resolvedYear + "-" + resolvedMonth + ".csv\"")
            .build();
    }

    @GET
    @Path("/export.pdf")
    @Produces("application/pdf")
    public Response exportPdf(
        @HeaderParam("Authorization") String auth,
        @QueryParam("year") @DefaultValue("0") int year,
        @QueryParam("month") @DefaultValue("0") int month
    ) {
        guard.requireAdmin(auth);
        int resolvedYear = year > 0 ? year : LocalDate.now().getYear();
        int resolvedMonth = month > 0 ? month : LocalDate.now().getMonthValue();
        byte[] body = service.exportPdf(resolvedYear, resolvedMonth);
        return Response.ok(body)
            .header("Content-Disposition", "attachment; filename=\"ponto-" + resolvedYear + "-" + resolvedMonth + ".pdf\"")
            .build();
    }

    private static jakarta.ws.rs.WebApplicationException badRequest(String message) {
        return new jakarta.ws.rs.WebApplicationException(
            Response.status(Response.Status.BAD_REQUEST).entity(java.util.Map.of("message", message)).build()
        );
    }
}
