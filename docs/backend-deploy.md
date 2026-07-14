# Backend serverless e Web Admin

O backend agora é uma aplicação **Quarkus 3**. A API REST, o endpoint MCP, o template Qute, o CSS e o JavaScript vanilla são empacotados no mesmo JAR. Em desenvolvimento o estado fica em memória; no perfil de produção a aplicação usa tabelas DynamoDB separadas para a operação, compras e vendas.

## Arquitetura

```text
App Android ─┐
             ├─ HTTP API ─ Quarkus/Lambda ─ DynamoDB
Admin Qute ──┘              │
                       HTML + CSS + JS
                       no mesmo artefato
```

- `dev` e `test`: `LocalStore`, sem Docker e sem credenciais AWS.
- `prod`: `DynamoDbStore`, selecionado no build e configurado por variáveis.
- AWS: API Gateway HTTP API encaminha todas as rotas à Lambda.
- As tabelas usam `PAY_PER_REQUEST`; não há VPC nem pool de conexões. Compras usam `pk` + `sk` para manter o histórico isolado.

### Pontos importantes da infraestrutura

- **HTTP API (API Gateway v2):** produz URLs limpas, sem o prefixo de stage `/Prod` usado normalmente pelas REST APIs v1.
- **Cobrança `PAY_PER_REQUEST`:** o DynamoDB cobra conforme o uso, sem capacidade provisionada enquanto a aplicação está ociosa.
- **Roteamento proxy:** `/{proxy+}` captura todas as rotas internas, como `/api/auth/login`, e as encaminha ao Quarkus. O evento separado para `/` garante que a página inicial Qute também seja atendida.
- **Tabelas automáticas:** os recursos `ChecklistTable`, `PurchasesTable` e `SalesTable` são criados pelo CloudFormation durante `sam deploy`; não há configuração manual do banco.
- **Sem rede privada:** a Lambda não declara VPC, subnets ou security groups. Ela acessa o DynamoDB pela API gerenciada da AWS usando a permissão criada pelo SAM.

## Rodar localmente

Pré-requisitos: JDK 17+. O Maven é baixado pelo wrapper na primeira execução.

```bash
cd backend
./mvnw quarkus:dev
```

Abra:

- Admin: `http://localhost:8181`
- Health check: `http://localhost:8181/api/health`

Credenciais seed: `admin@checklistboteco.com` / `admin123`. No primeiro login, a tela mostra o código de confirmação do dispositivo. O store local é recriado quando o processo reinicia.

Observação atualizada para módulos de compras e vendas:

- os uploads confirmados de CSV em `Compras` e `Vendas` são persistidos localmente em arquivos JSON estáveis no diretório `backend/.data/`;
- isso mantém os dados disponíveis após reiniciar o backend local;
- o MCP continua lendo esses dados persistidos normalmente.

Arquivos usados no dev local:

- `backend/.data/purchases-local.json`
- `backend/.data/sales-local.json`

## Web Admin: equipe, permissões e senha

A aba `Equipe` agora suporta o ciclo completo de gestão de acessos:

- criar usuário;
- editar nome, email, setor e perfil;
- remover usuário;
- resetar senha quando um colaborador perder o acesso.

Regras de acesso:

- `ADMIN` sempre pode acessar todas as funcionalidades do painel.
- A permissão `canRegisterUsers` libera a visualização da aba `Equipe` e o cadastro de novos usuários.
- A permissão `canEditUsers` libera edição, remoção e reset de senha de usuários existentes.
- Apenas `ADMIN` pode alterar os checkboxes de permissões dos usuários.
- A permissão `canCreateActivities` libera a aba `Atividades` e o cadastro de novas atividades.

Comportamentos importantes:

- reset de senha invalida dispositivos confiáveis e desafios de verificação pendentes daquele usuário;
- não é permitido remover o último administrador do sistema;
- ao promover um usuário para `ADMIN`, ele recebe todas as permissões automaticamente;
- ao rebaixar um `ADMIN` para `USER`, as permissões delegadas são zeradas por segurança e podem ser reatribuídas manualmente por um administrador.

## Módulos admin de compras e vendas

As abas `Compras` e `Vendas` ficam disponíveis somente para administradores.

- `Compras` importa CSVs de abastecimento/compras e normaliza os campos mais úteis para consulta.
- `Vendas` importa CSVs de relatório de vendas e exige como mapeamento mínimo `data`, `produto`, `local` e `quantidade`.
- Ambas preservam colunas extras como atributos dinâmicos, úteis para filtros e leitura por agentes.
- A auditoria `vendido x abastecido` cruza os datasets para apontar extravio potencial, consumo sem registro, venda sem entrada correspondente e perdas.

Rotas principais:

- `POST /api/purchases/imports/preview`
- `POST /api/purchases/imports/{id}/commit`
- `POST /api/purchases/query`
- `POST /api/purchases/aggregate`
- `POST /api/sales/imports/preview`
- `POST /api/sales/imports/{id}/commit`
- `POST /api/sales/query`
- `POST /api/sales/aggregate`
- `POST /api/sales/audit/stock`

Importações de vendas são idempotentes: cada linha válida gera uma chave única normalizada por data da venda, produto, local, quantidade, valores, unidade/tipo preço e documento quando disponível. Uploads diários, semanais e mensais podem se sobrepor; linhas já importadas são contabilizadas como duplicadas e não são gravadas novamente. O preview/commit retorna cobertura de datas (`coverageFrom`, `coverageTo`) e contadores como `newRows`, `duplicateRows`, `inFileDuplicateRows`, `existingDuplicateRows`, `missingDateRows` e `rejectedRows`.

O endpoint MCP local continua em `POST /mcp` e agora expõe ferramentas de compras e vendas somente leitura.

Para perguntas por produto, o MCP expõe a tool `sales_by_product`, útil para frases como:

- `quantas águas vendeu?`
- `qual a quantidade de amstel 600ml vendida no beco?`
- `quanto vendeu de batata frita no período?`

## Conectar o app

O contrato de `/api` foi preservado. Para Android Emulator, a máquina host é `10.0.2.2`:

```bash
./gradlew :composeApp:installDebug -PCHECKLIST_API_BASE_URL=http://10.0.2.2:8080
```

Em aparelho físico, use o IP da máquina na rede local. Fora de `localhost`, `127.0.0.1` e `10.0.2.2`, o cliente exige HTTPS.

No Android, o build `debug` permite HTTP claro somente para `10.0.2.2`, `127.0.0.1` e `localhost` por meio de `network_security_config`. Isso destrava o ambiente local sem abrir exceção ampla para produção.

## Testar e empacotar

```bash
cd backend
./mvnw test
./mvnw package
java -jar target/checklist-boteco-serverless-2.0.0-runner.jar
```

O build também produz `target/function.zip`, consumido pelo SAM.

## Deploy AWS SAM

Pré-requisitos: AWS CLI configurada e AWS SAM CLI.

```bash
cd backend
./build-lambda.sh
sam build --template-file template.yaml
sam deploy
```

No primeiro deploy use `sam deploy --guided`. Informe `JwtSecret` e `PurchasesMcpToken` com 24 ou mais caracteres e uma senha inicial exclusiva de ao menos 12 caracteres em `InitialAdminPassword`. O repositório não mantém valores padrão para esses parâmetros de produção.

O template cria Lambda Java 17 ARM64, HTTP API e **três** tabelas DynamoDB (`ChecklistTable`, `PurchasesTable`, `SalesTable`), aplica permissão CRUD à função e mantém as tabelas quando a stack é removida.

**Produção:** deploy somente via SAM/Lambda. O arquivo `Dockerfile.deprecated` não deve ser usado.

Roadmap arquitetural (Cognito, lazy DynamoDB, SnapStart): [architecture-serverless.md](architecture-serverless.md)

Depois do deploy, use o output `ApiUrl` no app:

```bash
./gradlew :composeApp:assembleDebug -PCHECKLIST_API_BASE_URL=https://id.execute-api.regiao.amazonaws.com
```

Quando o ambiente dev estiver validado, gere a variante Android apontando para a URL HTTPS da AWS. O app já está preparado para isso e continua bloqueando HTTP fora dos hosts locais de desenvolvimento.

Variáveis da Lambda definidas pelo template:

- `JWT_SECRET`: segredo HMAC dos tokens.
- `INITIAL_ADMIN_PASSWORD`: senha do primeiro administrador, criada somente quando a tabela operacional está vazia.
- `CHECKLIST_TABLE`: nome da tabela criada.
- `PURCHASES_TABLE`: tabela separada de compras e importações.
- `SALES_TABLE`: tabela separada de vendas e importações.
- `PURCHASES_MCP_TOKEN`: token de serviço enviado por clientes MCP com `Authorization: Bearer`.
- `AWS_REGION`: fornecida pelo runtime da Lambda.
- `EXPOSE_DEVICE_CODE`: por padrão `true` para o MVP continuar funcional; defina `false` quando integrar o envio do código por email, SMS ou autenticador.

Opcionalmente, `CORS_ORIGINS` restringe as origens da interface web. O valor padrão é permissivo para o MVP.

## Contratos mantidos

- `POST /api/auth/login`
- `POST /api/auth/verify-device`
- `GET /api/me`
- `GET|POST /api/users`
- `PUT /api/users/{id}`
- `DELETE /api/users/{id}`
- `POST /api/users/{id}/reset-password`
- `PATCH /api/users/{id}/permissions`
- `GET|POST /api/activities`
- `GET /api/completions`
- `GET /api/admin/dashboard`
- `GET /api/sync/pull?since=...`
- `POST /api/sync/push`
- `POST /api/purchases/imports/preview`
- `POST /api/purchases/imports/{id}/commit`
- `GET /api/purchases/schema`
- `POST /api/purchases/query`
- `POST /api/purchases/aggregate`
- `POST /api/sales/imports/preview`
- `POST /api/sales/imports/{id}/commit`
- `GET /api/sales/schema`
- `POST /api/sales/query`
- `POST /api/sales/aggregate`
- `POST /api/sales/audit/stock`
- `POST /mcp` (MCP Streamable HTTP, tools de compras e vendas somente leitura)

As marcações de ponto continuam filtradas pelo `userId` do token, impedindo que um colaborador envie ponto em nome de outro.

## Teste local com MCP

Suba o backend:

```bash
cd backend
./mvnw quarkus:dev
```

Depois conecte um cliente MCP HTTP apontando para:

- URL: `http://localhost:8080/mcp`
- Header: `Authorization: Bearer local-purchases-token`

Exemplo de configuração para Cursor/Codex:

```json
{
  "mcpServers": {
    "checklist-boteco-analytics": {
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer local-purchases-token"
      }
    }
  }
}
```

Arquivos prontos no repositório:

- Cursor: [.cursor/mcp.json](/Users/douglastaquary/ChecklistBoteco/.cursor/mcp.json)
- Codex: [.codex/mcp.json](/Users/douglastaquary/ChecklistBoteco/.codex/mcp.json)

Passo a passo de uso via chat:

- [docs/mcp-local-test.md](/Users/douglastaquary/ChecklistBoteco/docs/mcp-local-test.md)
