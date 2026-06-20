package com.checklistboteco.backend.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private final SecureRandom random = new SecureRandom();
    private final int iterations;
    public PasswordHasher() { this(120_000); }
    public PasswordHasher(int iterations) { this.iterations=iterations; }
    public String hash(String password) {
        byte[] salt=new byte[16]; random.nextBytes(salt);
        return "pbkdf2_sha256$"+iterations+"$"+b64(salt)+"$"+b64(derive(password,salt,iterations));
    }
    public boolean verify(String password, String encoded) {
        try {
            String[] parts=encoded.split("\\$");
            if(parts.length!=4 || !parts[0].equals("pbkdf2_sha256")) return false;
            byte[] salt=Base64.getDecoder().decode(parts[2]);
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),derive(password,salt,Integer.parseInt(parts[1])));
        } catch (RuntimeException exception) { return false; }
    }
    private byte[] derive(String value, byte[] salt, int count) {
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(value.toCharArray(),salt,count,256)).getEncoded(); }
        catch (Exception exception) { throw new IllegalStateException("Falha ao proteger senha",exception); }
    }
    private String b64(byte[] value) { return Base64.getEncoder().encodeToString(value); }
}
