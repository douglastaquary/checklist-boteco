package com.checklistboteco.backend.ai;

import com.checklistboteco.backend.ai.AiModels.*;
import com.checklistboteco.backend.model.Models.ApiError;
import com.checklistboteco.backend.model.Models.User;
import com.checklistboteco.backend.purchases.mcp.PurchaseMcpResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OpenAiChatService {
    private static final Logger LOG = Logger.getLogger(OpenAiChatService.class);
    private static final String INSTRUCTIONS = """
        Você é o analista operacional do Beco da Praia. Responda em português brasileiro, de forma curta e objetiva.
        Para números do estabelecimento, use somente as ferramentas fornecidas. Nunca invente dados.
        Beco, beco da praia e o estabelecimento significam Beco da Praia. Informe período e unidade monetária.
        Perguntas sobre quantidade vendida de um produto (ex.: quantas/quantos tainhas, tábuas, porção de carne de sol, cerveja)
        devem chamar sales_by_product ou sales_quantity_by_product_in_period com from/to resolvidos a partir da data atual.
        "último mês até hoje" / "ultimo mes ate o dia de hoje" significa os últimos 30 dias até a data atual (não o mês calendário anterior).
        Se os dados forem insuficientes, diga exatamente o que falta. Ferramentas são somente leitura.
        """;
    @Inject ObjectMapper mapper;
    @Inject PurchaseMcpResource analytics;
    @Inject AiUsageService usageService;
    @ConfigProperty(name="ai.openai.api-key") Optional<String> configuredApiKey;
    @ConfigProperty(name="ai.openai.model") String model;
    @ConfigProperty(name="ai.openai.base-url") String baseUrl;
    @ConfigProperty(name="ai.openai.timeout-seconds") int timeoutSeconds;
    @ConfigProperty(name="ai.pricing.input-microdollars-per-million") long inputPrice;
    @ConfigProperty(name="ai.pricing.cached-input-microdollars-per-million") long cachedPrice;
    @ConfigProperty(name="ai.pricing.output-microdollars-per-million") long outputPrice;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String,CachedToolResult> toolCache=new ConcurrentHashMap<>();

    public ChatResponse chat(User user,ChatRequest request) {
        String apiKey=configuredApiKey.orElse("");
        if(apiKey.isBlank()) {
            throw fail(Response.Status.SERVICE_UNAVAILABLE,
                "Chat de IA ainda não configurado. Defina OPENAI_API_KEY no backend (.env.local) e reinicie o Quarkus.");
        }
        validate(request);
        UsageSummary current=usageService.summary(null);
        if(current.blocked) throw fail(Response.Status.TOO_MANY_REQUESTS,"Limite mensal de IA atingido");
        long started=System.currentTimeMillis(); String requestId=UUID.randomUUID().toString();
        List<String> consulted=new ArrayList<>();
        try {
            ObjectNode body=baseRequest(request);
            JsonNode response=send(body,apiKey);
            ArrayNode functionOutputs=mapper.createArrayNode();
            ArrayNode priorOutput=(ArrayNode)response.path("output");
            for(JsonNode item:priorOutput) if("function_call".equals(item.path("type").asText())) {
                String name=item.path("name").asText();
                if(!allowedTools(request).contains(name)) throw new IllegalArgumentException("Ferramenta não permitida: "+name);
                Map<String,Object> args=mapper.readValue(item.path("arguments").asText("{}"),Map.class);
                Object result=executeCached(name,args); consulted.add(name);
                ObjectNode output=mapper.createObjectNode(); output.put("type","function_call_output"); output.put("call_id",item.path("call_id").asText()); output.put("output",mapper.writeValueAsString(result)); functionOutputs.add(output);
            }
            Usage total=readUsage(response);
            if(!functionOutputs.isEmpty()) {
                ObjectNode followup=baseRequest(request); ArrayNode input=(ArrayNode)followup.get("input");
                priorOutput.forEach(input::add); functionOutputs.forEach(input::add);
                response=send(followup,apiKey); merge(total,readUsage(response));
            }
            total.estimatedCostMicros=cost(total);
            ChatResponse result=new ChatResponse(); result.requestId=requestId; result.answer=answer(response); result.consultedTools=consulted; result.usage=total;
            AuditRecord audit=new AuditRecord(); audit.id=requestId; audit.userId=user.id; audit.month=YearMonth.now().toString(); audit.model=model; audit.createdAt=System.currentTimeMillis(); audit.latencyMs=audit.createdAt-started; audit.inputTokens=total.inputTokens; audit.cachedInputTokens=total.cachedInputTokens; audit.outputTokens=total.outputTokens; audit.estimatedCostMicros=total.estimatedCostMicros; audit.tools=consulted;
            usageService.record(audit); result.budget=usageService.summary(null); return result;
        } catch(WebApplicationException e){ throw e; }
        catch(IllegalArgumentException e){ throw fail(Response.Status.BAD_REQUEST, e.getMessage()); }
        catch(HttpTimeoutException e){
            LOG.warnf(e, "AI chat timeout requestId=%s", requestId);
            throw fail(Response.Status.GATEWAY_TIMEOUT, "A consulta de IA demorou demais. Tente novamente em instantes.");
        }
        catch(Exception e){
            LOG.errorf(e, "AI chat failed requestId=%s", requestId);
            String detail = rootMessage(e);
            throw fail(Response.Status.BAD_GATEWAY,
                detail==null||detail.isBlank()
                    ? "Não foi possível concluir a consulta de IA. Tente novamente."
                    : "Não foi possível concluir a consulta de IA: "+detail);
        }
    }

    private static WebApplicationException fail(Response.Status status,String message){
        return new WebApplicationException(Response.status(status).entity(new ApiError(message)).type(MediaType.APPLICATION_JSON).build());
    }
    private ObjectNode baseRequest(ChatRequest request){
        ObjectNode body=mapper.createObjectNode(); body.put("model",model); body.put("instructions",INSTRUCTIONS+"\nData atual em America/Fortaleza: "+LocalDate.now(ZoneId.of("America/Fortaleza"))+". Resolva datas relativas como ontem antes de chamar ferramentas."); body.put("store",false); body.put("max_output_tokens",usageService.budget().maxOutputTokens); body.put("prompt_cache_key","checklist-boteco-ai-v1");
        ArrayNode input=body.putArray("input"); request.messages.stream().skip(Math.max(0,request.messages.size()-4)).forEach(message->{ ObjectNode item=input.addObject(); item.put("role",safeRole(message.role)); item.put("content",message.text.trim()); });
        ArrayNode tools=body.putArray("tools"); Set<String> allowed=allowedTools(request);
        analytics.tools().stream().filter(tool->allowed.contains(tool.get("name"))).forEach(tool->{ ObjectNode value=tools.addObject(); value.put("type","function"); value.put("name",tool.get("name").toString()); value.put("description",tool.get("description").toString()); value.set("parameters",mapper.valueToTree(tool.get("inputSchema"))); value.put("strict",false); });
        body.put("tool_choice","auto"); return body;
    }
    public Set<String> allowedToolsForTesting(ChatRequest request){ return allowedTools(request); }
    private Set<String> allowedTools(ChatRequest request){
        String text=request.messages.get(request.messages.size()-1).text.toLowerCase(Locale.ROOT); LinkedHashSet<String> names=new LinkedHashSet<>();
        if(has(text,"venda","vendeu","vendemos","vendida","vendidas","vendido","vendidos","fatur","produto","quantas","quantos","quanto","heineken","cerveja","porcao","porção","tabua","tábua","carne","tainha","ultimo mes","último mês","ultimo mês","último mes")) names.addAll(List.of("sales_by_product","sales_quantity_by_product_in_period","sales_aggregate","sales_get_imports"));
        if(has(text,"usuario","usuário","vendedor","garçom","garcom","atendente","operador","funcionario","funcionário","joão","joao","10%","gorjeta","serviço","servico","forró","forro")) names.addAll(List.of("sales_by_seller","sales_aggregate","sales_get_imports"));
        if(has(text,"compra","gasto","fornecedor","mercadoria","custo")) names.addAll(List.of("purchases_aggregate","purchases_list","purchases_get_imports"));
        if(has(text,"estoque","contagem","extravio","perda","abastec")) names.addAll(List.of("inventory_daily_audit","inventory_count_sessions","sales_audit_stock"));
        if(has(text,"ponto","hora extra","jornada","escala")) names.addAll(List.of("work_clock_summary","work_clock_entries","work_clock_schedule"));
        if(has(text,"falta","faltas","ausência","ausencias","ausência","ausências","nao veio","não veio")) names.addAll(List.of("work_clock_absences","work_clock_summary","work_clock_entries","work_clock_schedule"));
        if(names.isEmpty()) names.addAll(List.of("sales_aggregate","purchases_aggregate","inventory_daily_audit","work_clock_summary"));
        return names;
    }
    private JsonNode send(ObjectNode body,String apiKey) throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl+"/responses")).timeout(Duration.ofSeconds(timeoutSeconds)).header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()>=400) {
            String detail=openaiErrorMessage(response.body());
            int status=response.statusCode()==429?429:502;
            throw fail(Response.Status.fromStatusCode(status),
                detail==null||detail.isBlank()
                    ?"OpenAI respondeu HTTP "+response.statusCode()
                    :detail);
        }
        return mapper.readTree(response.body());
    }

    private String openaiErrorMessage(String body){
        try {
            JsonNode error=mapper.readTree(body).path("error");
            String message=error.path("message").asText("");
            String code=error.path("code").asText("");
            if(!message.isBlank()&&"insufficient_quota".equals(code)) {
                return "A cota da OpenAI acabou. Verifique plano e billing em platform.openai.com.";
            }
            return message.isBlank()?null:message;
        } catch(Exception ignored){ return null; }
    }
    private Usage readUsage(JsonNode response){ Usage u=new Usage(); JsonNode usage=response.path("usage"); u.inputTokens=usage.path("input_tokens").asInt(); u.cachedInputTokens=usage.path("input_tokens_details").path("cached_tokens").asInt(); u.outputTokens=usage.path("output_tokens").asInt(); u.totalTokens=usage.path("total_tokens").asInt(); return u; }
    private String answer(JsonNode response){
        StringBuilder text=new StringBuilder();
        for(JsonNode item:response.path("output")) if("message".equals(item.path("type").asText())) {
            for(JsonNode content:item.path("content")) if("output_text".equals(content.path("type").asText())) {
                String part=content.path("text").asText("");
                if(!part.isBlank()) {
                    if(text.length()>0) text.append('\n');
                    text.append(part);
                }
            }
        }
        if(text.length()>0) return text.toString().trim();
        String status=response.path("status").asText("");
        if("incomplete".equals(status)) {
            return "A resposta da IA ficou incompleta. Tente perguntar de novo de forma mais direta.";
        }
        return "Não encontrei informações suficientes para responder.";
    }
    private static String rootMessage(Throwable error){
        Throwable current=error;
        while(current.getCause()!=null && current.getCause()!=current) current=current.getCause();
        String message=current.getMessage();
        if(message==null||message.isBlank()) return null;
        return message.length()>220?message.substring(0,217)+"...":message;
    }
    private long cost(Usage u){ long uncached=Math.max(0,u.inputTokens-u.cachedInputTokens); return Math.round((uncached*inputPrice+u.cachedInputTokens*cachedPrice+u.outputTokens*outputPrice)/1_000_000.0); }
    private Object executeCached(String name,Map<String,Object> args) throws Exception {
        String key=name+":"+mapper.writeValueAsString(new TreeMap<>(args)); long now=System.currentTimeMillis(); CachedToolResult cached=toolCache.get(key);
        if(cached!=null&&cached.expiresAt>now) return cached.value;
        Object raw=analytics.callTool(Map.of("name",name,"arguments",args));
        Object value=raw instanceof Map<?,?> map&&map.containsKey("structuredContent")?map.get("structuredContent"):raw;
        toolCache.put(key,new CachedToolResult(value,now+300_000)); return value;
    }
    private static void merge(Usage a,Usage b){ a.inputTokens+=b.inputTokens; a.cachedInputTokens+=b.cachedInputTokens; a.outputTokens+=b.outputTokens; a.totalTokens+=b.totalTokens; }
    private static String safeRole(String role){ return "assistant".equals(role)?"assistant":"user"; }
    private static boolean has(String text,String... values){ return Arrays.stream(values).anyMatch(text::contains); }
    private static void validate(ChatRequest request){ if(request==null||request.messages==null||request.messages.isEmpty()) throw new IllegalArgumentException("Envie uma pergunta"); if(request.messages.size()>4) request.messages=request.messages.subList(request.messages.size()-4,request.messages.size()); for(ChatMessage message:request.messages) if(message==null||message.text==null||message.text.isBlank()||message.text.length()>2000) throw new IllegalArgumentException("Mensagem inválida ou muito longa"); }
    private record CachedToolResult(Object value,long expiresAt) {}
}
