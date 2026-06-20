package com.checklistboteco.backend.purchases.web;

import static com.checklistboteco.backend.purchases.domain.PurchaseModels.*;

import com.checklistboteco.backend.purchases.application.PurchaseImportService;
import com.checklistboteco.backend.purchases.application.PurchaseQueryService;
import com.checklistboteco.backend.security.AdminGuard;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.LocalDate;
import java.util.List;

@Path("/api/purchases")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PurchaseResource {
    @Inject AdminGuard guard;
    @Inject PurchaseImportService imports;
    @Inject PurchaseQueryService queries;

    @POST @Path("/imports/preview") public ImportBatch preview(@HeaderParam("Authorization") String auth,PreviewRequest request){ return imports.preview(guard.requireAdmin(auth),request); }
    @POST @Path("/imports/{id}/commit") public ImportBatch commit(@HeaderParam("Authorization") String auth,@PathParam("id") String id,CommitRequest request){ return imports.commit(guard.requireAdmin(auth),id,request); }
    @GET @Path("/imports/{id}") public ImportBatch importStatus(@HeaderParam("Authorization") String auth,@PathParam("id") String id){ guard.requireAdmin(auth); return imports.get(id); }
    @GET @Path("/imports") public List<ImportBatch> importList(@HeaderParam("Authorization") String auth){ guard.requireAdmin(auth); return imports.list(); }
    @GET @Path("/schema") public ImportSchema schema(@HeaderParam("Authorization") String auth,@QueryParam("datasetId") String dataset){ guard.requireAdmin(auth); return queries.schema(dataset); }
    @GET public PurchasePage list(@HeaderParam("Authorization") String auth,@QueryParam("datasetId") String dataset,@QueryParam("from") LocalDate from,@QueryParam("to") LocalDate to,
        @QueryParam("category") List<String> categories,@QueryParam("location") List<String> locations,@QueryParam("supplier") List<String> suppliers,@QueryParam("text") String text,@QueryParam("page") @DefaultValue("0") int page,@QueryParam("size") @DefaultValue("50") int size){
        guard.requireAdmin(auth); PurchaseQuery query=new PurchaseQuery(); query.from=from; query.to=to; query.categories=categories; query.locations=locations; query.suppliers=suppliers; query.text=text; query.page=page; query.pageSize=size; return queries.query(dataset,query);
    }
    @POST @Path("/query") public PurchasePage query(@HeaderParam("Authorization") String auth,@QueryParam("datasetId") String dataset,PurchaseQuery request){ guard.requireAdmin(auth); return queries.query(dataset,request); }
    @POST @Path("/aggregate") public AggregateResponse aggregate(@HeaderParam("Authorization") String auth,@QueryParam("datasetId") String dataset,AggregateRequest request){ guard.requireAdmin(auth); return queries.aggregate(dataset,request); }
}
