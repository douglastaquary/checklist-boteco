package com.checklistboteco.backend.inventory.web;

import static com.checklistboteco.backend.inventory.domain.InventoryModels.*;
import com.checklistboteco.backend.inventory.application.InventoryService;
import com.checklistboteco.backend.security.AdminGuard;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.LocalDate;
import java.util.List;

@Path("/api/inventory") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class InventoryResource {
    @Inject AdminGuard guard; @Inject InventoryService service;
    @POST @Path("/counts") public Response submit(@HeaderParam("Authorization") String auth,SubmitCountRequest request){ return Response.status(Response.Status.CREATED).entity(service.submit(guard.requireInventoryCountAccess(auth),request)).build(); }
    @GET @Path("/counts") public List<CountSession> list(@HeaderParam("Authorization") String auth,@QueryParam("from") LocalDate from,@QueryParam("to") LocalDate to){ guard.requireInventoryInsightsAccess(auth); return service.list(from,to); }
    @DELETE @Path("/counts/{id}") public Response delete(@HeaderParam("Authorization") String auth,@PathParam("id") String id){ guard.requireAdmin(auth); service.delete(id); return Response.noContent().build(); }
    @POST @Path("/audit/daily") public DailyAuditResponse audit(@HeaderParam("Authorization") String auth,DailyAuditRequest request){ guard.requireInventoryInsightsAccess(auth); return service.audit(request); }
    @POST @Path("/audit/daily/apply") public ApplyDailyAuditResponse applyDailyAudit(@HeaderParam("Authorization") String auth,DailyAuditRequest request){ return service.applyDailyAudit(guard.requireApplyDailyAuditAccess(auth),request); }
    @POST @Path("/admin-stock/counts") public Response submitAdminStock(@HeaderParam("Authorization") String auth,SubmitCountRequest request){ return Response.status(Response.Status.CREATED).entity(service.submitAdminStock(guard.requireAdministrativeStockAccess(auth),request)).build(); }
    @GET @Path("/admin-stock/counts") public List<AdminStockSession> listAdminStock(@HeaderParam("Authorization") String auth,@QueryParam("from") LocalDate from,@QueryParam("to") LocalDate to){ guard.requireAdministrativeStockAccess(auth); return service.listAdminStock(from,to); }
    @GET @Path("/admin-stock/balances") public List<AdminStockBalance> adminStockBalances(@HeaderParam("Authorization") String auth){ guard.requireAdministrativeStockAccess(auth); return service.listAdminStockBalances(); }
}
