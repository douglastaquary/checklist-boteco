# Padrões UI/UX mobile — Checklist Boteco

Este documento orienta ajustes e novas telas nos apps Android e iOS.

## Plataformas

- Android: Kotlin Multiplatform/Compose em `composeApp/`.
- iOS: SwiftUI nativo em `iosApp/` e `Packages/`.
- As plataformas devem manter o mesmo comportamento, hierarquia visual e textos principais, mesmo quando a implementação for nativa.

## Design visual

- Cor primária: preto/tom escuro definido pelos tokens do design system.
- Botões primários: formato capsule/pill, texto centralizado, alto contraste.
- Estados positivos: verde/primary quando a regra foi satisfeita.
- Estados de erro: vermelho do sistema/tema, sempre com texto explicativo.
- Campos de texto: borda/superfície clara, cantos arredondados e label objetiva.
- Telas devem respeitar safe area e evitar sobreposição com status bar/notch.

## Formulários

- Validações devem aparecer abaixo do campo que originou o erro.
- Evite alert modal para erro de digitação; prefira feedback inline.
- Botões de envio ficam desabilitados até os campos mínimos estarem válidos.
- Mensagens devem ser acionáveis: informar exatamente qual regra falta.

## Primeiro acesso / troca obrigatória de senha

- Aplica-se somente aos apps mobile.
- Após login remoto, se `user.mustChangePassword == true`, o app deve abrir a tela de criação de nova senha antes da navegação principal.
- A senha deve conter:
  - ao menos 6 caracteres;
  - ao menos uma letra maiúscula;
  - ao menos um número;
  - ao menos um caractere especial.
- A tela deve conter dois campos:
  - `Nova senha`;
  - `Confirmar senha`.
- A validação deve exibir cada regra abaixo do campo de nova senha e feedback verde quando a senha estiver OK.
- A confirmação deve exibir feedback próprio abaixo do segundo campo.
- A senha temporária/anterior deve ficar apenas em memória durante o fluxo. Se a sessão for restaurada sem essa senha em memória, o usuário deve fazer login novamente.

## Navegação

- Android: adicionar novas telas no sealed class `Screen` e manter callbacks explícitos no `App.kt`.
- iOS: adicionar novos estados em `AuthScreen` e manter fluxo no `RootView`.
- Fluxos bloqueantes, como troca obrigatória de senha, devem impedir acesso à `Main` até conclusão.

## Checklist antes de finalizar UI

- Android compila com `./gradlew :composeApp:test`.
- iOS compila com `xcodebuild` do app; não usar `swift test` em `Packages/`.
- Campos respeitam safe area.
- Estado loading/erro não quebra navegação.
- Textos e regras são equivalentes nas duas plataformas.
