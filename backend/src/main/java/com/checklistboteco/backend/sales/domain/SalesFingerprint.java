package com.checklistboteco.backend.sales.domain;

import com.checklistboteco.backend.sales.domain.SalesModels.Sale;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public final class SalesFingerprint {
    private SalesFingerprint() {}

    public static String of(Sale sale){
        String canonical=String.join("|",
            value(sale.datasetId),
            value(sale.saleDate),
            key(sale.description),
            key(sale.location),
            decimal(sale.quantity),
            String.valueOf(sale.totalInCents),
            String.valueOf(sale.unitPriceInCents),
            key(sale.unit),
            key(sale.documentNumber)
        );
        return sha256(canonical);
    }

    public static String existingOrComputed(Sale sale){
        if(sale.saleFingerprint!=null&&!sale.saleFingerprint.isBlank()) return sale.saleFingerprint;
        return of(sale);
    }

    private static String value(Object value){ return Objects.toString(value,"").trim(); }
    private static String decimal(BigDecimal value){ return value==null?"0":value.stripTrailingZeros().toPlainString(); }
    private static String key(String value){
        String normalized=Normalizer.normalize(Objects.toString(value,"").trim(),Normalizer.Form.NFD)
            .replaceAll("\\p{M}","")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+","_")
            .replaceAll("^_+|_+$","");
        return normalized;
    }
    private static String sha256(String value){
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){ throw new IllegalStateException(e); }
    }
}
