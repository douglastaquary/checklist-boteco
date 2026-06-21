# Exemplos — ChecklistBoteco serverless

## 1. Novo endpoint REST

**Cenário:** expor `GET /api/inventory/admin-stock/balances` (já existente — use como referência).

**Service** (`inventory/application/InventoryService.java`):

```java
public List<AdminStockBalance> listAdminStockBalances() {
    return adminStockRepository.listBalances();
}
```

**Resource** (`inventory/web/InventoryResource.java`):

```java
@GET @Path("/admin-stock/balances")
public List<AdminStockBalance> adminStockBalances(
        @HeaderParam("Authorization") String auth) {
    guard.requireAdministrativeStockAccess(auth);
    return service.listAdminStockBalances();
}
```

**Guard** (`security/AdminGuard.java`):

```java
public User requireAdministrativeStockAccess(String authorization) {
    User user = requireUser(authorization);
    if (user.permissionLevel == PermissionLevel.ADMIN
            || (user.permissions != null && user.permissions.canManageAdministrativeStock)) {
        return user;
    }
    fail(FORBIDDEN, "Permissão para contagem administrativa de estoque necessária");
    return user;
}
```

**Teste** (`InventoryResourceTest.java`):

```java
@Test void administrativeStockAddsBalance() {
    String admin = login("admin@checklistboteco.com", "admin123");
    // criar usuário com canManageAdministrativeStock
    // POST /api/inventory/admin-stock/counts
    // GET /api/inventory/admin-stock/balances → quantity
}
```

---

## 2. Nova seção admin (Qute + vanilla JS)

**Cenário:** toggle Abertura / Estoque admin na aba Contagens.

**HTML** (`templates/admin.html`):

```html
<article>
  <div class="count-mode-toggle">
    <button id="countModeDaily" type="button" class="secondary active">Abertura</button>
    <button id="countModeAdmin" type="button" class="secondary">Estoque admin</button>
  </div>
</article>
```

**JS** (`assets/admin.js`):

```javascript
let inventoryCountMode = localStorage.getItem('inventory-count-mode') || 'daily';

function setInventoryCountMode(mode) {
  inventoryCountMode = mode;
  localStorage.setItem('inventory-count-mode', mode);
  const adminMode = mode === 'admin';
  const endpoint = adminMode ? '/api/inventory/admin-stock/counts' : '/api/inventory/counts';
  // atualizar UI, submitCount usa endpoint
}

$('countModeDaily').addEventListener('click', () => setInventoryCountMode('daily'));
$('countModeAdmin').addEventListener('click', () => setInventoryCountMode('admin'));
```

**Permissão na tabela Equipe:**

```javascript
['canManageAdministrativeStock', ...].map(key => /* checkbox */)
```

**CSS** (`assets/admin.css`):

```css
.count-mode-toggle { display: flex; gap: 8px; }
.count-mode-toggle button.active { background: var(--green); color: #fff; }
```

---

## 3. Novo repositório Dynamo + local

**Cenário:** persistir estoque administrativo separado da contagem diária.

**Interface:**

```java
public interface AdminStockRepository {
    void saveSession(AdminStockSession session);
    List<AdminStockBalance> listBalances();
    AdminStockBalance adjustBalance(String productKey, String productName,
            String location, BigDecimal delta);
    Optional<Instant> appliedAuditAt(LocalDate date, String location);
    void markAuditApplied(LocalDate date, String location, Instant appliedAt, String appliedBy);
}
```

**Local** (`@UnlessBuildProfile("prod")`):

```java
@ApplicationScoped
@UnlessBuildProfile("prod")
public class LocalAdminStockRepository implements AdminStockRepository {
    @ConfigProperty(name = "inventory.admin-stock.file")
    String fileName;
    // JSON snapshot: sessions, balances, appliedAudits
}
```

**Dynamo** (`@IfBuildProfile("prod")`):

```java
@ApplicationScoped
@IfBuildProfile("prod")
public class DynamoAdminStockRepository implements AdminStockRepository {
    // pk ADMIN_STOCK#SNAPSHOT, kind ADMIN_STOCK, payload JSON
}
```

**Service** — injeta interface, não implementação:

```java
@Inject AdminStockRepository adminStockRepository;

public ApplyDailyAuditResponse applyDailyAudit(User user, DailyAuditRequest request) {
    // para cada item com soldQuantity > 0:
    adminStockRepository.adjustBalance(productKey, item.product, location, item.soldQuantity.negate());
}
```

**Regra:** em código novo Dynamo, evitar `scan()` na `@PostConstruct`. Preferir get/put por chave ou lazy load no primeiro acesso.
