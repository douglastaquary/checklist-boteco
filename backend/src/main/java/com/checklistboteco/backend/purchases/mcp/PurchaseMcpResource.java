package com.checklistboteco.backend.purchases.mcp;

import static com.checklistboteco.backend.purchases.domain.PurchaseModels.*;

import com.checklistboteco.backend.purchases.application.PurchaseImportService;
import com.checklistboteco.backend.purchases.application.PurchaseQueryService;
import com.checklistboteco.backend.sales.application.SalesAuditService;
import com.checklistboteco.backend.sales.application.SalesImportService;
import com.checklistboteco.backend.sales.application.SalesQueryService;
import com.checklistboteco.backend.sales.domain.SalesModels.ProductSearchRequest;
import com.checklistboteco.backend.sales.domain.SalesModels.SaleQuery;
import com.checklistboteco.backend.sales.domain.SalesModels.SalesAuditRequest;
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
    private static final String DEFAULT_LOCATION="Beco da Praia";
    @Inject PurchaseQueryService purchaseQueries;
    @Inject PurchaseImportService purchaseImports;
    @Inject SalesQueryService salesQueries;
    @Inject SalesImportService salesImports;
    @Inject SalesAuditService salesAudits;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="purchases.mcp.token") String expectedToken;

    public static class RpcRequest { public String jsonrpc="2.0",method; public Object id; public Map<String,Object> params=new LinkedHashMap<>(); }

    @POST public Response handle(@HeaderParam("Authorization") String authorization,RpcRequest request){
        authenticate(authorization); Object id=request==null?null:request.id;
        try {
            if(request==null||request.method==null) return rpcError(id,-32600,"Requisição MCP inválida");
            Object result=switch(request.method){
                case "initialize" -> Map.of("protocolVersion","2025-03-26","capabilities",Map.of("tools",Map.of("listChanged",false)),"serverInfo",Map.of("name","checklist-boteco-analytics","version","1.2.0","defaultLocation",DEFAULT_LOCATION));
                case "notifications/initialized" -> null;
                case "ping" -> Map.of();
                case "tools/list" -> Map.of("tools",tools());
                case "tools/call" -> callTool(request.params);
                default -> throw new NoSuchMethodException(request.method);
            };
            if("notifications/initialized".equals(request.method)) return Response.status(Response.Status.ACCEPTED).build();
            return Response.ok(Map.of("jsonrpc","2.0","id",id==null?"":id,"result",result)).build();
        } catch(NoSuchMethodException e){ return rpcError(id,-32601,"Método não encontrado: "+e.getMessage()); }
        catch(Exception e){ LOG.warnf("MCP analytics call failed method=%s message=%s",request==null?"unknown":request.method,e.getMessage()); return rpcError(id,-32602,e.getMessage()); }
    }

    private Object callTool(Map<String,Object> params) throws Exception {
        String name=Objects.toString(params==null?null:params.get("name"),""); Map<String,Object> args=params!=null&&params.get("arguments") instanceof Map<?,?> map?(Map<String,Object>)map:Map.of();
        long started=System.nanoTime(); Object data=switch(name){
            case "purchases_get_schema" -> purchaseQueries.schema(Objects.toString(args.get("datasetId"),"purchases"));
            case "purchases_list" -> purchaseQueries.query(Objects.toString(args.get("datasetId"),"purchases"),mapper.convertValue(args,PurchaseQuery.class));
            case "purchases_aggregate" -> purchaseQueries.aggregate(Objects.toString(args.get("datasetId"),"purchases"),mapper.convertValue(args,com.checklistboteco.backend.purchases.domain.PurchaseModels.AggregateRequest.class));
            case "purchases_get_imports" -> purchaseImports.list();
            case "sales_get_schema" -> salesQueries.schema(Objects.toString(args.get("datasetId"),"sales"));
            case "sales_list" -> salesQueries.query(Objects.toString(args.get("datasetId"),"sales"),mapper.convertValue(normalizeSalesArgs(args),SaleQuery.class));
            case "sales_aggregate" -> salesQueries.aggregate(Objects.toString(args.get("datasetId"),"sales"),mapper.convertValue(normalizeSalesArgs(args),com.checklistboteco.backend.sales.domain.SalesModels.AggregateRequest.class));
            case "sales_by_product" -> salesQueries.byProduct(Objects.toString(args.get("datasetId"),"sales"),mapper.convertValue(normalizeSalesArgs(args),ProductSearchRequest.class));
            case "sales_quantity_by_product_in_period" -> salesQueries.byProduct(Objects.toString(args.get("datasetId"),"sales"),mapper.convertValue(normalizeSalesArgs(args),ProductSearchRequest.class));
            case "sales_get_imports" -> salesImports.list();
            case "sales_audit_stock" -> salesAudits.audit(mapper.convertValue(normalizeSalesArgs(args),SalesAuditRequest.class));
            default -> throw new NoSuchMethodException("Tool desconhecida: "+name);
        };
        LOG.infof("MCP audit tool=%s durationMs=%d",name,(System.nanoTime()-started)/1_000_000);
        String json=mapper.writeValueAsString(data); return Map.of("content",List.of(Map.of("type","text","text",json)),"structuredContent",data,"isError",false);
    }

    private List<Map<String,Object>> tools(){
        Map<String,Object> purchasePeriodProps=Map.ofEntries(Map.entry("from",Map.of("type","string","format","date")),Map.entry("to",Map.of("type","string","format","date")),Map.entry("datasetId",Map.of("type","string")),Map.entry("text",Map.of("type","string")),Map.entry("categories",Map.of("type","array","items",Map.of("type","string"))),Map.entry("locations",Map.of("type","array","items",Map.of("type","string"))),Map.entry("suppliers",Map.of("type","array","items",Map.of("type","string"))),Map.entry("minTotalInCents",Map.of("type","integer")),Map.entry("maxTotalInCents",Map.of("type","integer")),Map.entry("page",Map.of("type","integer","minimum",0)),Map.entry("pageSize",Map.of("type","integer","minimum",1,"maximum",200)));
        Map<String,Object> salesPeriodProps=Map.ofEntries(Map.entry("from",Map.of("type","string","format","date")),Map.entry("to",Map.of("type","string","format","date")),Map.entry("datasetId",Map.of("type","string")),Map.entry("text",Map.of("type","string")),Map.entry("categories",Map.of("type","array","items",Map.of("type","string"))),Map.entry("locations",Map.of("type","array","items",Map.of("type","string"))),Map.entry("minTotalInCents",Map.of("type","integer")),Map.entry("maxTotalInCents",Map.of("type","integer")),Map.entry("page",Map.of("type","integer","minimum",0)),Map.entry("pageSize",Map.of("type","integer","minimum",1,"maximum",200)));
        return List.of(
            tool("purchases_get_schema","Descobre campos, atributos dinâmicos, tipos e cobertura dos dados de compras",Map.of("type","object","properties",Map.of("datasetId",Map.of("type","string")))),
            tool("purchases_list","Lista compras com período obrigatório, filtros e paginação",Map.of("type","object","properties",purchasePeriodProps,"required",List.of("from","to"))),
            tool("purchases_aggregate","Soma compras e agrupa por categoria, fornecedor, mercadoria, período ou atributo dinâmico",Map.of("type","object","properties",merge(purchasePeriodProps,Map.of("groupBy",Map.of("type","string"))),"required",List.of("from","to","groupBy"))),
            tool("purchases_get_imports","Lista lotes importados de compras e sua cobertura",Map.of("type","object","properties",Map.of())),
            tool("sales_get_schema","Descobre campos, atributos dinâmicos, tipos e cobertura dos dados de vendas",Map.of("type","object","properties",Map.of("datasetId",Map.of("type","string")))),
            tool("sales_list","Lista vendas do Beco da Praia com período obrigatório, filtros e paginação. Quando o local não for informado, assuma Beco da Praia.",Map.of("type","object","properties",salesPeriodProps,"required",List.of("from","to"))),
            tool("sales_aggregate","Soma vendas do Beco da Praia e agrupa por categoria, produto, local, período ou atributo dinâmico",Map.of("type","object","properties",merge(salesPeriodProps,Map.of("groupBy",Map.of("type","string"))),"required",List.of("from","to","groupBy"))),
            tool("sales_by_product","Busca vendas por produto no Beco da Praia e retorna quantidade vendida, valor total e quebra por produto/local. Use para perguntas como 'quantas cervejas vendeu?' ou 'quanto vendeu no beco?'",Map.of("type","object","properties",merge(salesPeriodProps,Map.of("product",Map.of("type","string"),"limit",Map.of("type","integer","minimum",1,"maximum",100))),"required",List.of("product"))),
            tool("sales_quantity_by_product_in_period","Retorna a quantidade vendida e o total em reais de um produto no Beco da Praia em um período específico. Use para perguntas como 'quantas heinekens vendeu em maio no beco?'",Map.of("type","object","properties",merge(salesPeriodProps,Map.of("product",Map.of("type","string"),"limit",Map.of("type","integer","minimum",1,"maximum",100))),"required",List.of("product","from","to"))),
            tool("sales_get_imports","Lista lotes importados de vendas e sua cobertura",Map.of("type","object","properties",Map.of())),
            tool("sales_audit_stock","Cruza quantidade vendida x quantidade abastecida para apontar extravio, venda sem entrada registrada e perdas. Aceita filtro textual por produto.",Map.of("type","object","properties",Map.of("purchaseDatasetId",Map.of("type","string"),"salesDatasetId",Map.of("type","string"),"from",Map.of("type","string","format","date"),"to",Map.of("type","string","format","date"),"locations",Map.of("type","array","items",Map.of("type","string")),"text",Map.of("type","string")),"required",List.of("from","to")))
        );
    }
    private static Map<String,Object> normalizeSalesArgs(Map<String,Object> args){
        Map<String,Object> normalized=new LinkedHashMap<>(args==null?Map.of():args);
        Object locationsValue=normalized.get("locations");
        if(locationsValue instanceof Collection<?> values){
            LinkedHashSet<String> locations=new LinkedHashSet<>();
            for(Object value:values){
                String current=Objects.toString(value,"").trim();
                if(current.isBlank()) continue;
                locations.add(current);
                if(isBecoAlias(current)) locations.add(DEFAULT_LOCATION);
            }
            normalized.put("locations",new ArrayList<>(locations));
        } else if(!normalized.containsKey("locations")){
            normalized.put("locations",List.of(DEFAULT_LOCATION));
        }
        return normalized;
    }
    private static boolean isBecoAlias(String value){
        String current=Objects.toString(value,"").trim();
        if(current.isBlank()) return false;
        String key=current.toLowerCase(Locale.ROOT)
            .replace('ã','a')
            .replace('á','a')
            .replace('â','a')
            .replace('é','e')
            .replace('ê','e')
            .replace('í','i')
            .replace('ó','o')
            .replace('ô','o')
            .replace('õ','o')
            .replace('ú','u')
            .replace('ç','c');
        return key.equals("beco")||key.equals("beco da praia")||key.equals("beco_da_praia");
    }
    private static Map<String,Object> tool(String name,String description,Map<String,Object> schema){ return Map.of("name",name,"description",description,"inputSchema",schema); }
    private static Map<String,Object> merge(Map<String,Object> left,Map<String,Object> right){ Map<String,Object> result=new LinkedHashMap<>(left); result.putAll(right); return result; }
    private void authenticate(String authorization){
        String token=authorization!=null&&authorization.startsWith("Bearer ")?authorization.substring(7).trim():"";
        if(!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),expectedToken.getBytes(StandardCharsets.UTF_8))) throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("message","Token MCP inválido ou ausente")).build());
    }
    private static Response rpcError(Object id,int code,String message){ return Response.ok(Map.of("jsonrpc","2.0","id",id==null?"":id,"error",Map.of("code",code,"message",Objects.toString(message,"Erro MCP")))).build(); }
}
