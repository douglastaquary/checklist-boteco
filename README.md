# Checklist Boteco

Aplicativo de checklist para bares e restaurantes desenvolvido com **Kotlin Multiplatform (KMP)**, **Compose Multiplatform** e arquitetura **MVVM**.

## Funcionalidades

- **Login**: Autenticação por usuário e senha
- **Checklist por área**: Tabs para Atendimento, Cozinha, Estoque e Limpeza
- **Lista de atividades**: Nome, área e toggle para marcar conclusão
- **Captura de imagem**: Ao ativar o toggle, abre a câmera para registrar a conclusão
- **Confirmação**: Ao confirmar a foto, dados são salvos no banco local e o item fica desabilitado
- **Permissões**: Cada usuário acessa apenas suas áreas configuradas
- **Admin**: Gráficos de atividades realizadas e pendentes por área
- **Cadastro de atividades**: Admins podem adicionar novas atividades (nome, área, frequência)
- **Cadastro de novos usuários**: Fluxo público a partir da tela de login com nome, sobrenome, email, setor de trabalho em lista única, senha forte e confirmação
- **Setores de trabalho**: Atendimento, Cozinha, Serviços Gerais, Garçon, Cumim, Chefe de Cozinha, Gerente, Ajudante de Cozinha, Atendente e Barman
- **Permissões por funcionalidade**: Módulo apartado para admin configurar cadastro de novos funcionários, criação de atividades e edição de usuários
- **Administração de permissões**: Lista usuários por setor, mostra detalhes do cadastro e permite ao admin ajustar acessos gerenciais por usuário
- **Ponto de colaboradores**: Usuários comuns podem registrar entrada, almoço, descanso e saída com cálculo de horas trabalhadas, descanso e horas devidas

## Credenciais padrão

- **Usuário**: admin
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
- Ktor Server (backend Kotlin)
- SQLite (backend MVP)

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
./gradlew :backend:run
```

Admin web: `http://localhost:8080`

Guia completo de backend e deploy: [docs/backend-deploy.md](docs/backend-deploy.md)

Em produção, configure a API somente com HTTPS. O backend suporta deploy atrás de proxies TLS como Render, Railway e Cloud Run; para desenvolvimento local, use `http://localhost:8080`.

Para apontar o app Android para o backend, informe a URL da API no build:

```bash
./gradlew :composeApp:assembleDebug -PCHECKLIST_API_BASE_URL=https://sua-api.exemplo.com
```

O app rejeita `http://` fora de hosts locais de desenvolvimento.

### iOS

Abra o projeto no Xcode e execute no simulador ou dispositivo.

## Modelo de dados

- **Áreas**: Atendimento, Cozinha, Estoque, Limpeza
- **Setores**: Atendimento, Cozinha, Serviços Gerais, Garçon, Cumim, Chefe de Cozinha, Gerente, Ajudante de Cozinha, Atendente, Barman
- **Frequências**: Diário, Quinzenal, Mensal
- **Permissões**: Admin (acesso total), User (área derivada do setor) e permissões gerenciais por funcionalidade

## Cadastro e permissões

O botão **Novo usuário** fica abaixo do botão **Entrar** na tela de login. O cadastro exige:

- Nome e sobrenome normalizados com a primeira letra de cada nome em maiúscula
- Email com `@` e final `.com`
- Setor de trabalho selecionado em lista de escolha única
- Senha com no mínimo 8 caracteres, letra maiúscula, letra minúscula, número e caractere especial
- Confirmação de senha igual à senha informada

Usuários comuns recebem acesso às atividades vinculadas à área do seu setor de trabalho. Usuários admin recebem acesso total. A tela **Permissões por usuário** organiza os usuários por setor, abre os detalhes ao clicar no nome e permite ativar ou desativar permissões gerenciais.

O cadastro público sempre cria usuários comuns, sem permissões gerenciais. A concessão de permissões fica isolada no módulo **Permissões**, visível por padrão para o usuário admin. Somente admin pode alterar permissões específicas de cada usuário.

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
