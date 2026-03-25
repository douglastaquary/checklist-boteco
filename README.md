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

### iOS

Abra o projeto no Xcode e execute no simulador ou dispositivo.

## Modelo de dados

- **Áreas**: Atendimento, Cozinha, Estoque, Limpeza
- **Frequências**: Diário, Quinzenal, Mensal
- **Permissões**: Admin (acesso total) ou User (áreas específicas)

## Próximos passos sugeridos

- Cadastro de novos usuários: **Implementado** - acesse pelo ícone de usuários no menu (admin)
- Implementação completa da câmera no iOS
- Sincronização com servidor (opcional)
- Notificações para atividades pendentes
