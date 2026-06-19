package com.checklistboteco.backend.purchases.mcp;

import static com.checklistboteco.backend.purchases.domain.PurchaseModels.*;

import com.checklistboteco.backend.purchases.application.PurchaseImportService;
import com.checklistboteco.backend.purchases.application.PurchaseQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Path("/mcp")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PurchaseMcpResource {
    private static final Logger LOG=Logger.getLogger(PurchaseMcpResource.class);
    @Inject PurchaseQueryService queries;
    @Inject PurchaseImportService imports;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="purchases.mcp.token") String expectedToken;

    public static class RpcRequest { public String jsonrpc="2.0",method; public Object id; public Map<String,Object> params=new LinkedHashMap<>(); }

    @POST public Response handle(@HeaderParam("Authorization") String authorization,RpcRequest request){
        authenticate(authorization); Object id=request==null?null:request.id;
        try {
            if(request==null||request.method==null) return rpcError(id,-32600,"Requisição MCP inválida");
            Object result=switch(request.method){
                case "initialize" -> Map.of("protocolVersion","2025-03-26","capabilities",Map.of("tools",Map.of("listChanged",false)),"serverInfo",Map.of("name","checklist-boteco-purchases","version","1.0.0"));
                case "notifications/initialized" -> null;
                case "ping" -> Map.of();
                case "tools/list" -> Map.of("tools",tools());
                case "tools/call" -> callTool(request.params);
                default -> throw new NoSuchMethodException(request.method);
            };
            if("notifications/initialized".equals(request.method)) return Response.status(Response.Status.ACCEPTED).build();
            return Response.ok(Map.of("jsonrpc","2.0","id",id==null?"":id,"result",result)).build();
        } catch(NoSuchMethodException e){ return rpcError(id,-32601,"Método não encontrado: "+e.getMessage()); }
        catch(Exception e){ LOG.warnf("MCP purchases call failed method=%s message=%s",request==null?"unknown":request.method,e.getMessage()); return rpcError(id,-32602,e.getMessage()); }
    }

    private Object callTool(Map<String,Object> params) throws Exception {
        String name=Objects.toString(params==null?null:params.get("name"),""); Map<String,Object> args=params!=null&&params.get("arguments") instanceof Map<?,?> map?(Map<String,Object>)map:Map.of();
        long started=System.nanoTime(); Object data=switch(name){
            case "purchases_get_schema" -> queries.schema(Objects.toString(args.get("datasetId"),"purchases"));
            case "purchases_list" -> queries.query(Objects.toString(args.get("datasetId"),"purchases"),mapper.convertValue(args,PurchaseQuery.class));
            case "purchases_aggregate" -> queries.aggregate(Objects.toString(args.get("datasetId"),"purchases"),mapper.convertValue(args,AggregateRequest.class));
            case "purchases_get_imports" -> imports.list();
            default -> throw new NoSuchMethodException("Tool desconhecida: "+name);
        };
        LOG.infof("MCP audit tool=%s durationMs=%d",name,(System.nanoTime()-started)/1_000_000);
        String json=mapper.writeValueAsString(data); return Map.of("content",List.of(Map.of("type","text","text",json)),"structuredContent",data,"isError",false);
    }

    private List<Map<String,Object>> tools(){
        Map<String,Object> periodProps=Map.ofEntries(Map.entry("from",Map.of("type","string","format","date")),Map.entry("to",Map.of("type","string","format","date")),Map.entry("datasetId",Map.of("type","string")),Map.entry("text",Map.of("type","string")),Map.entry("categories",Map.of("type","array","items",Map.of("type","string"))),Map.entry("locations",Map.of("type","array","items",Map.of("type","string"))),Map.entry("suppliers",Map.of("type","array","items",Map.of("type","string"))),Map.entry("minTotalInCents",Map.of("type","integer")),Map.entry("maxTotalInCents",Map.of("type","integer")),Map.entry("page",Map.of("type","integer","minimum",0)),Map.entry("pageSize",Map.of("type","integer","minimum",1,"maximum",200)));
        return List.of(
            tool("purchases_get_schema","Descobre campos, atributos dinâmicos, tipos e cobertura dos dados",Map.of("type","object","properties",Map.of("datasetId",Map.of("type","string")))),
            tool("purchases_list","Lista compras com período obrigatório, filtros e paginação",Map.of("type","object","properties",periodProps,"required",List.of("from","to"))),
            tool("purchases_aggregate","Soma compras e agrupa por categoria, fornecedor, mercadoria, período ou atributo dinâmico",Map.of("type","object","properties",merge(periodProps,Map.of("groupBy",Map.of("type","string"))),"required",List.of("from","to","groupBy"))),
            tool("purchases_get_imports","Lista lotes importados e sua cobertura",Map.of("type","object","properties",Map.of()))
        );
    }
    private static Map<String,Object> tool(String name,String description,Map<String,Object> schema){ return Map.of("name",name,"description",description,"inputSchema",schema); }
    private static Map<String,Object> merge(Map<String,Object> left,Map<String,Object> right){ Map<String,Object> result=new LinkedHashMap<>(left); result.putAll(right); return result; }
    private void authenticate(String authorization){
        String token=authorization!=null&&authorization.startsWith("Bearer ")?authorization.substring(7).trim():"";
        if(!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),expectedToken.getBytes(StandardCharsets.UTF_8))) throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("message","Token MCP inválido ou ausente")).build());
    }
    private static Response rpcError(Object id,int code,String message){ return Response.ok(Map.of("jsonrpc","2.0","id",id==null?"":id,"error",Map.of("code",code,"message",Objects.toString(message,"Erro MCP")))).build(); }
}
