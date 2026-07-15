package com.checklistboteco.backend.sales.web;

import static com.checklistboteco.backend.sales.domain.SalesModels.*;

import com.checklistboteco.backend.sales.application.SalesAuditService;
import com.checklistboteco.backend.sales.application.SalesImportService;
import com.checklistboteco.backend.sales.application.SalesQueryService;
import com.checklistboteco.backend.security.AdminGuard;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.LocalDate;
import java.util.List;

@Path("/api/sales")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SalesResource {
    @Inject AdminGuard guard;
    @Inject SalesImportService imports;
    @Inject SalesQueryService queries;
    @Inject SalesAuditService audits;

    @POST @Path("/imports/preview") public ImportBatch preview(@HeaderParam("Authorization") String auth,PreviewRequest request){ return imports.preview(guard.requireApplyDailyAuditAccess(auth),request); }
    @POST @Path("/imports/{id}/commit") public ImportBatch commit(@HeaderParam("Authorization") String auth,@PathParam("id") String id,CommitRequest request){ return imports.commit(guard.requireApplyDailyAuditAccess(auth),id,request); }
    @GET @Path("/imports/{id}") public ImportBatch importStatus(@HeaderParam("Authorization") String auth,@PathParam("id") String id){ guard.requireAdmin(auth); return imports.get(id); }
    @GET @Path("/imports") public List<ImportBatch> importList(@HeaderParam("Authorization") String auth){ guard.requireAdmin(auth); return imports.list(); }
    @GET @Path("/schema") public ImportSchema schema(@HeaderParam("Authorization") String auth,@QueryParam("datasetId") String dataset){ guard.requireAdmin(auth); return queries.schema(dataset); }
    @GET public SalePage list(@HeaderParam("Authorization") String auth,@QueryParam("datasetId") String dataset,@QueryParam("from") LocalDate from,@QueryParam("to") LocalDate to,
        @QueryParam("category") List<String> categories,@QueryParam("location") List<String> locations,@QueryParam("seller") List<String> sellers,@QueryParam("text") String text,@QueryParam("page") @DefaultValue("0") int page,@QueryParam("size") @DefaultValue("50") int size){
        guard.requireAdmin(auth); SaleQuery query=new SaleQuery(); query.from=from; query.to=to; query.categories=categories; query.locations=locations; query.sellers=sellers; query.text=text; query.page=page; query.pageSize=size; return queries.query(dataset,query);
    }
    @POST @Path("/query") public SalePage query(@HeaderParam("Authorization") String auth,@QueryParam("datasetId") String dataset,SaleQuery request){ guard.requireAdmin(auth); return queries.query(dataset,request); }
    @POST @Path("/aggregate") public AggregateResponse aggregate(@HeaderParam("Authorization") String auth,@QueryParam("datasetId") String dataset,AggregateRequest request){ guard.requireAdmin(auth); return queries.aggregate(dataset,request); }
    @POST @Path("/audit/stock") public SalesAuditResponse audit(@HeaderParam("Authorization") String auth,SalesAuditRequest request){ guard.requireAdmin(auth); return audits.audit(request); }
}
