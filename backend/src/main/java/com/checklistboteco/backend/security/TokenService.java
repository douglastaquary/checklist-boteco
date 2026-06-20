package com.checklistboteco.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.config.ConfigMapping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@ApplicationScoped
public class TokenService {
    @ConfigMapping(prefix="checklist.jwt") public interface JwtConfig { String secret(); }
    public static class Payload { public String userId; public boolean isAdmin; public long expiresAt; }
    @Inject ObjectMapper mapper;
    @Inject JwtConfig config;
    public String issue(String userId, boolean admin) {
        try {
            var payload=new Payload(); payload.userId=userId; payload.isAdmin=admin; payload.expiresAt=System.currentTimeMillis()+86_400_000L;
            String body=Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(payload));
            return body+"."+sign(body);
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    public Payload verify(String token) {
        try {
            String[] parts=token.split("\\.");
            if(parts.length!=2 || !constantTime(sign(parts[0]),parts[1])) return null;
            Payload payload=mapper.readValue(Base64.getUrlDecoder().decode(parts[0]),Payload.class);
            return payload.expiresAt>System.currentTimeMillis()?payload:null;
        } catch(Exception e) { return null; }
    }
    private String sign(String value) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(config.secret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
    private boolean constantTime(String left,String right) { return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),right.getBytes(StandardCharsets.UTF_8)); }
}
