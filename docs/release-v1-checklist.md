# Checklist de publicação — v1.0

Status em 8 de julho de 2026: **apta para piloto interno após configurar o ambiente AWS; ainda não apta para publicação nas lojas**.

## Validações concluídas

- backend: 28 testes aprovados, pacote Quarkus e `target/function.zip` gerados;
- infraestrutura: template SAM válido e `sam build` aprovado;
- Android: testes, lint vital e `assembleRelease` aprovados;
- iOS: build `Release` para simulador aprovado e layout validado em iPhone SE e iPhone 14 Pro;
- DynamoDB: tabelas `PAY_PER_REQUEST`, retenção e recuperação point-in-time habilitadas;
- branch de release alinhado com `origin/main` sem conflitos.

## Bloqueios antes do piloto interno

- executar `sam deploy --guided` com valores fortes para `JwtSecret`, `PurchasesMcpToken` e `InitialAdminPassword`;
- confirmar o health check e o primeiro login na URL AWS;
- substituir `api.checklistboteco.example` em `iosApp/Config/Release.xcconfig` pela URL real;
- gerar o Android release com `CHECKLIST_API_BASE_URL` apontando para a URL real;
- criar e guardar um keystore Android; o APK atual é `composeApp-release-unsigned.apk`;
- trocar a senha inicial do administrador após o primeiro login e guardar os segredos fora do Git.

## Bloqueios para App Store e Google Play

- configurar assinatura Android release e gerar AAB assinado;
- obter certificado/perfil Apple Distribution e validar um Archive em dispositivo genérico;
- configurar metadados, ícones, política de privacidade e testes internos das lojas;
- substituir a exposição do código de verificação por entrega real via email, SMS ou autenticador;
- restringir `CORS_ORIGINS` e concluir a estratégia de autenticação de produção antes de acesso público amplo.

## Critério de go/no-go

- **Go para piloto interno:** backend AWS saudável, login real aprovado, sincronização offline/online verificada em um Android e um iPhone, backup point-in-time confirmado.
- **No-go para lojas ou público externo:** enquanto assinatura, endpoint definitivo e fluxo seguro de verificação de dispositivo estiverem pendentes.

