# Checklist Boteco

Documentação do módulo de contagem e auditoria diária: [docs/inventory-counting-module.md](docs/inventory-counting-module.md).

Aplicativo de checklist para bares e restaurantes com app **Kotlin Multiplatform**, backend **Quarkus serverless** e administração web em **Qute + JavaScript vanilla**.

## Direcionamento backend/web (Cursor)

Para features de API, painel admin, persistência ou deploy AWS, use a skill de projeto:

**[`.cursor/skills/quarkus-serverless-qute/SKILL.md`](.cursor/skills/quarkus-serverless-qute/SKILL.md)**

Roadmap de alinhamento: [`docs/architecture-serverless.md`](docs/architecture-serverless.md)

## Funcionalidades

- **Login**: Autenticação por usuário e senha
- **Checklist por área**: Tabs para Atendimento, Cozinha, Estoque e Limpeza
- **Lista de atividades**: Nome, área e toggle para marcar conclusão
- **Captura de imagem**: Ao ativar o toggle, abre a câmera para registrar a conclusão
- **Confirmação**: Ao confirmar a foto, dados são salvos no banco local e o item fica desabilitado
- **Permissões**: Cada usuário acessa apenas suas áreas configuradas
- **Admin**: Gráficos de atividades realizadas e pendentes por área
- **Cadastro de atividades**: Admins podem adicionar novas atividades (nome, área, frequência)
- **Gestão de usuários no Web Admin**: Aba Equipe com criação, edição, remoção e reset de senha
- **Setores de trabalho**: Atendimento, Cozinha, Serviços Gerais, Garçon, Cumim, Chefe de Cozinha, Gerente, Ajudante de Cozinha, Atendente e Barman
- **Permissões por funcionalidade**: Módulo apartado para admin configurar cadastro de novos funcionários, criação de atividades e edição de usuários
- **Administração de permissões**: Admin pode delegar cadastro de usuários, gestão de atividades e edição de usuários para perfis não-admin
- **Ponto de colaboradores**: Usuários comuns podem registrar entrada, almoço, descanso e saída com cálculo de horas trabalhadas, descanso e horas devidas
- **Compras**: Importação CSV com mapeamento dinâmico, consulta e exposição via MCP somente leitura
- **Vendas e auditoria**: Importação CSV de vendas, cruzamento vendido x abastecido e exposição via MCP somente leitura para agentes

## Credenciais padrão

- **Usuário**: admin@checklistboteco.com
- **Senha**: admin123

## Plataformas suportadas

- **Android** (API 26+)
- **Desktop** (JVM)
- **iOS** (estrutura preparada)

## Tecnologias

- Kotlin Multiplatform
- Compose Multiplatform (UI compartilhada)
- SQLDelight (banco de dados local)
- MVVM
- Coroutines & Flow
- Quarkus REST + Qute (API e web no mesmo JAR)
- JavaScript vanilla (sem Node.js ou bundler)
- AWS Lambda + API Gateway HTTP API
- DynamoDB on-demand em produção

## Estrutura do projeto

```
composeApp/
├── src/
│   ├── commonMain/          # Código compartilhado
│   │   ├── kotlin/          # Lógica, ViewModels, UI
│   │   └── sqldelight/      # Schema do banco
│   ├── androidMain/         # Implementações Android (câmera, etc)
│   ├── desktopMain/         # Implementações Desktop
│   └── iosMain/             # Implementações iOS
backend/
├── src/main/java/            # API, segurança e stores
├── src/main/resources/       # Qute, CSS, JS e configuração
├── pom.xml                    # Build único do backend + web
└── template.yaml              # Lambda, HTTP API e DynamoDB (SAM)
```

## Como executar

### Pré-requisitos

- JDK 17+
- Android Studio ou IntelliJ IDEA com plugin KMP
- (Opcional) Xcode para iOS

### Android

```bash
./gradlew :composeApp:installDebug
```

Ou execute pela IDE: Run > composeApp (Android)

### Desktop

```bash
./gradlew :composeApp:runDesktop
```

### Backend local

```bash
cd backend
./mvnw quarkus:dev
```

Admin web: `http://localhost:8080`

Guia completo de backend e deploy: [docs/backend-deploy.md](docs/backend-deploy.md)

Plano do módulo de compras com importação CSV e MCP: [docs/finance-module-plan.md](docs/finance-module-plan.md)

Plano de sincronização Android offline-first com o backend Quarkus: [docs/android-quarkus-sync-plan.md](docs/android-quarkus-sync-plan.md)

Guia do módulo de auditoria de vendas x abastecimento: [docs/sales-audit-module.md](docs/sales-audit-module.md)

O perfil local usa memória e não precisa de Docker ou AWS. Em produção, o template SAM publica o mesmo artefato em Lambda com DynamoDB.

Em desenvolvimento, os uploads confirmados de `Compras` e `Vendas` passam a ser persistidos em arquivos locais estáveis:

- [backend/.data/purchases-local.json](/Users/douglastaquary/ChecklistBoteco/backend/.data/purchases-local.json)
- [backend/.data/sales-local.json](/Users/douglastaquary/ChecklistBoteco/backend/.data/sales-local.json)

Assim, os dados importados continuam disponíveis após reiniciar o backend local e seguem acessíveis via MCP.

Para apontar o app Android para o backend, informe a URL da API no build:

```bash
./gradlew :composeApp:installDebug -PCHECKLIST_API_BASE_URL=http://10.0.2.2:8080
```

`10.0.2.2` aponta o Android Emulator para o backend da máquina. Para AWS, troque pela URL HTTPS exibida no output `ApiUrl` do SAM. O app rejeita HTTP fora dos hosts locais de desenvolvimento.

No Android, o build `debug` libera tráfego HTTP claro apenas para `10.0.2.2`, `127.0.0.1` e `localhost`, facilitando testes locais. Builds destinados à AWS continuam usando HTTPS.

### iOS

Abra o projeto no Xcode e execute no simulador ou dispositivo.

## Modelo de dados

- **Áreas**: Atendimento, Cozinha, Estoque, Limpeza
- **Setores**: Atendimento, Cozinha, Serviços Gerais, Garçon, Cumim, Chefe de Cozinha, Gerente, Ajudante de Cozinha, Atendente, Barman
- **Frequências**: Diário, Quinzenal, Mensal
- **Permissões**: Admin (acesso total), User (área derivada do setor) e permissões gerenciais por funcionalidade

## Cadastro e permissões

O gerenciamento de acessos acontece na aba **Equipe** do painel web.

- `ADMIN` tem acesso total ao sistema e aos checkboxes de permissões
- `canRegisterUsers` permite visualizar a aba Equipe e criar novos usuários
- `canEditUsers` permite editar usuários existentes, remover usuários e resetar senha
- `canCreateActivities` permite acessar a aba **Atividades** e cadastrar novas atividades

No cadastro e edição de usuários, os campos obrigatórios são:

- Nome
- Email válido
- Setor de trabalho
- Perfil (`USER` ou `ADMIN`)

Na criação, a senha também é obrigatória e deve ter no mínimo 8 caracteres. No reset de senha, os dispositivos confiáveis e desafios pendentes do usuário são invalidados, forçando um novo fluxo de confirmação no próximo acesso.

Usuários comuns recebem acesso às atividades vinculadas à área derivada do seu setor. Usuários `ADMIN` recebem acesso total às áreas e permissões gerenciais.

## Módulos administrativos de compras e vendas

Os módulos `Compras` e `Vendas` ficam disponíveis apenas para usuários `ADMIN`.

- `Compras`: importa CSV de abastecimento/compras, normaliza campos principais, preserva colunas dinâmicas e expõe consultas por REST e MCP.
- `Vendas`: importa CSV de relatório de vendas, exige mapeamento mínimo de `produto` e `quantidade`, preenche data/local automaticamente quando o relatório não trouxer esses campos, preserva colunas dinâmicas e expõe consultas por REST e MCP.
- `Auditoria vendido x abastecido`: cruza os datasets de compras e vendas para destacar itens com venda sem entrada correspondente, venda acima do abastecido e abastecimento sem saída registrada.

Ferramentas MCP disponíveis localmente em `http://localhost:8080/mcp`:

- `purchases_get_schema`
- `purchases_list`
- `purchases_aggregate`
- `purchases_get_imports`
- `sales_get_schema`
- `sales_list`
- `sales_aggregate`
- `sales_by_product`
- `sales_quantity_by_product_in_period`
- `sales_get_imports`
- `sales_audit_stock`

Exemplo de configuração MCP para Cursor/Codex em ambiente local:

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

Arquivos já prontos no projeto:

- Cursor: [.cursor/mcp.json](/Users/douglastaquary/ChecklistBoteco/.cursor/mcp.json)
- Codex: [.codex/mcp.json](/Users/douglastaquary/ChecklistBoteco/.codex/mcp.json)

Guia de uso e testes via chat:

- [docs/mcp-local-test.md](docs/mcp-local-test.md)

Snippets de instruções para agentes:

- Cursor: [.cursor/checklist-boteco-agent-instructions.md](/Users/douglastaquary/ChecklistBoteco/.cursor/checklist-boteco-agent-instructions.md)
- Codex: [.codex/checklist-boteco-agent-instructions.md](/Users/douglastaquary/ChecklistBoteco/.codex/checklist-boteco-agent-instructions.md)

## Ponto de colaboradores

O módulo **Ponto** aparece para colaboradores comuns e não aparece para usuários admin. A tela principal mostra um mapa operacional do local de trabalho, dados da próxima marcação, dia e hora, distância do local configurado e botão para confirmar a marcação.

Regras do MVP:

- Não existe escala fixa diária; o app calcula a partir das marcações realizadas no dia
- A jornada esperada é de 8h trabalhadas por dia e 40h por semana
- O descanso esperado é de 1h por dia
- Jornadas de 12h trabalhadas exigem 2h de descanso
- A tela de detalhes mostra marcações do dia, horas trabalhadas, descanso, descanso devido/excedente e horas devidas
- Marcações não podem ser editadas após confirmação
- O local de trabalho configurado é `Av. Vicente de Carvalho, 761 Centro - Bertioga (Beco da Praia)`, com raio permitido de 5 metros

## Desenvolvimento assistido por IA e PDD

Esta funcionalidade foi conduzida por **PDD (Prompt Driven Development)**: o arquivo `.github/prompts/new-user-feature.md` descreveu o comportamento esperado e orientou a implementação. O desenvolvimento assistido por IA foi usado para transformar o prompt em mudanças de modelo, banco local, telas Compose, viewmodels, validações compartilhadas, testes unitários e documentação.

## Próximos passos sugeridos

- Implementação completa da câmera no iOS
- Sincronização com servidor (opcional)
- Notificações para atividades pendentes
