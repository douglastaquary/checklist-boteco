package com.checklistboteco.backend.ai;

import com.checklistboteco.backend.ai.AiModels.*;
import com.checklistboteco.backend.model.Models.User;
import com.checklistboteco.backend.purchases.mcp.PurchaseMcpResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ServiceUnavailableException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OpenAiChatService {
    private static final String INSTRUCTIONS = """
        Você é o analista operacional do Beco da Praia. Responda em português brasileiro, de forma curta e objetiva.
        Para números do estabelecimento, use somente as ferramentas fornecidas. Nunca invente dados.
        Beco, beco da praia e o estabelecimento significam Beco da Praia. Informe período e unidade monetária.
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
        if(apiKey.isBlank()) throw new ServiceUnavailableException("Chat de IA ainda não configurado");
        validate(request);
        UsageSummary current=usageService.summary(null);
        if(current.blocked) throw new jakarta.ws.rs.WebApplicationException("Limite mensal de IA atingido",429);
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
        } catch(jakarta.ws.rs.WebApplicationException e){ throw e; }
        catch(Exception e){ throw new IllegalStateException("Não foi possível concluir a consulta de IA",e); }
    }
    private ObjectNode baseRequest(ChatRequest request){
        ObjectNode body=mapper.createObjectNode(); body.put("model",model); body.put("instructions",INSTRUCTIONS); body.put("store",false); body.put("max_output_tokens",usageService.budget().maxOutputTokens); body.put("prompt_cache_key","checklist-boteco-ai-v1");
        ArrayNode input=body.putArray("input"); request.messages.stream().skip(Math.max(0,request.messages.size()-4)).forEach(message->{ ObjectNode item=input.addObject(); item.put("role",safeRole(message.role)); item.put("content",message.text.trim()); });
        ArrayNode tools=body.putArray("tools"); Set<String> allowed=allowedTools(request);
        analytics.tools().stream().filter(tool->allowed.contains(tool.get("name"))).forEach(tool->{ ObjectNode value=tools.addObject(); value.put("type","function"); value.put("name",tool.get("name").toString()); value.put("description",tool.get("description").toString()); value.set("parameters",mapper.valueToTree(tool.get("inputSchema"))); value.put("strict",false); });
        body.put("tool_choice","auto"); return body;
    }
    public Set<String> allowedToolsForTesting(ChatRequest request){ return allowedTools(request); }
    private Set<String> allowedTools(ChatRequest request){
        String text=request.messages.get(request.messages.size()-1).text.toLowerCase(Locale.ROOT); LinkedHashSet<String> names=new LinkedHashSet<>();
        if(has(text,"venda","vendeu","vendemos","vendida","vendidas","vendido","vendidos","fatur","produto","quantas","quanto","heineken","cerveja")) names.addAll(List.of("sales_by_product","sales_quantity_by_product_in_period","sales_aggregate","sales_get_imports"));
        if(has(text,"compra","gasto","fornecedor","mercadoria","custo")) names.addAll(List.of("purchases_aggregate","purchases_list","purchases_get_imports"));
        if(has(text,"estoque","contagem","extravio","perda","abastec")) names.addAll(List.of("inventory_daily_audit","inventory_count_sessions","sales_audit_stock"));
        if(has(text,"ponto","hora extra","jornada","falta","escala")) names.addAll(List.of("work_clock_summary","work_clock_entries","work_clock_schedule"));
        if(names.isEmpty()) names.addAll(List.of("sales_aggregate","purchases_aggregate","inventory_daily_audit","work_clock_summary"));
        return names;
    }
    private JsonNode send(ObjectNode body,String apiKey) throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl+"/responses")).timeout(Duration.ofSeconds(timeoutSeconds)).header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()>=400) throw new IllegalStateException("OpenAI respondeu HTTP "+response.statusCode());
        return mapper.readTree(response.body());
    }
    private Usage readUsage(JsonNode response){ Usage u=new Usage(); JsonNode usage=response.path("usage"); u.inputTokens=usage.path("input_tokens").asInt(); u.cachedInputTokens=usage.path("input_tokens_details").path("cached_tokens").asInt(); u.outputTokens=usage.path("output_tokens").asInt(); u.totalTokens=usage.path("total_tokens").asInt(); return u; }
    private String answer(JsonNode response){ for(JsonNode item:response.path("output")) if("message".equals(item.path("type").asText())) for(JsonNode content:item.path("content")) if("output_text".equals(content.path("type").asText())) return content.path("text").asText(); return "Não encontrei informações suficientes para responder."; }
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
