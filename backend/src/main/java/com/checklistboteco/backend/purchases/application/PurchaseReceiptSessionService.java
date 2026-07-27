package com.checklistboteco.backend.purchases.application;

import static com.checklistboteco.backend.purchases.domain.PurchaseModels.*;

import com.checklistboteco.backend.model.Models.User;
import com.checklistboteco.backend.purchases.persistence.PurchaseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@ApplicationScoped
public class PurchaseReceiptSessionService {
    @Inject PurchaseRepository repository;

    public ReceiptSessionSubmitResponse submit(User actor, ReceiptSessionSubmitRequest request) {
        require(request != null, "Payload inválido");
        require(request.items != null && !request.items.isEmpty(), "Nenhum item na sessão de comprovantes");
        String dataset = request.datasetId == null || request.datasetId.isBlank() ? "purchases" : request.datasetId.trim();
        String location = request.location == null || request.location.isBlank() ? "Beco da Praia" : request.location.trim();
        LocalDate purchaseDate = request.purchaseDate != null ? request.purchaseDate : LocalDate.now();
        String sessionId = UUID.randomUUID().toString();

        ReceiptSessionSubmitResponse response = new ReceiptSessionSubmitResponse();
        response.sessionId = sessionId;
        long total = 0L;
        int index = 0;
        for (ReceiptSessionItem item : request.items) {
            index++;
            try {
                Purchase purchase = toPurchase(sessionId, dataset, purchaseDate, location, request, item, index);
                if (repository.saveIfAbsent(purchase)) {
                    response.importedRows++;
                    total += purchase.totalInCents;
                } else {
                    response.duplicateRows++;
                }
            } catch (IllegalArgumentException e) {
                response.rejectedRows++;
                response.errors.add(new ImportError(index, "item", e.getMessage()));
            }
        }
        response.totalInCents = total;
        response.status = response.importedRows == 0 && response.rejectedRows > 0 ? "FAILED" : "COMMITTED";
        return response;
    }

    private Purchase toPurchase(
        String sessionId,
        String dataset,
        LocalDate purchaseDate,
        String location,
        ReceiptSessionSubmitRequest request,
        ReceiptSessionItem item,
        int index
    ) {
        require(item != null, "Item inválido");
        String description = required(item.description, "Descrição vazia");
        String category = required(item.category, "Categoria vazia");
        require(item.totalInCents > 0, "Valor total inválido");
        Purchase purchase = new Purchase();
        purchase.id = UUID.randomUUID().toString();
        purchase.datasetId = dataset;
        purchase.importId = sessionId;
        purchase.importedAt = Instant.now();
        purchase.purchaseDate = purchaseDate;
        purchase.description = description;
        purchase.category = category;
        purchase.location = location;
        purchase.supplier = nullable(request.supplier);
        purchase.documentNumber = nullable(request.documentNumber);
        purchase.unit = "UN";
        purchase.quantity = item.quantity == null || item.quantity.signum() <= 0 ? BigDecimal.ONE : item.quantity;
        purchase.unitPriceInCents = item.unitPriceInCents;
        purchase.totalInCents = item.totalInCents;
        if (request.paymentMethod != null && !request.paymentMethod.isBlank()) {
            purchase.attributes.put("forma_pagamento", request.paymentMethod.trim());
        }
        purchase.attributes.put("origem", "receipt_session");
        purchase.rowHash = hash(dataset + "|" + sessionId + "|" + index + "|" + description + "|" + item.totalInCents);
        return purchase;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
