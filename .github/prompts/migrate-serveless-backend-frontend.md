---
agent: Plan
---

- Preciso migrar a stack da pasta backend e frontend para o modelo serveless.

- Quarkus: Um Framework, Um Artefato, Zero Fricção
- O Quarkus é a cola que mantém tudo junto. Ele oferece:
Renderização server-side com Qute — Templates HTML que vivem dentro do JAR, sem build de front-end separado
- Suporte para AWS Lambda — O Quarkus tem uma extensão dedicada para Lambda que cuida do adaptador HTTP.
- Inicialização rápida — projetado desde o início para cargas de trabalho cloud-native e serverless
Uber-JAR único — toda sua aplicação (API REST, templates, CSS, JS) compila em um só artefato
Modo dev com hot reload — ./mvnw quarkus:dev dá feedback instantâneo durante o desenvolvimento
Vanilla JS: Sem Pipeline de Build para o Front-End
Com React ou Angular, você precisa de Node.js, npm, um bundler (Webpack/Vite) e um passo de build separado que produz assets estáticos que você depois precisa servir do S3 ou CloudFront. Preciso que a aplicacao banckend/frontend, a UI são templates Qute + vanilla JS e preciso migrar o design do front para usar Qute + vanilla JS. Está tudo dentro do JAR. Um build, um artefato, um deploy.
AWS Lambda: Computação Serverless Que Escala até Zero
Lambda significa:
Sem servidores para gerenciar — sem instâncias EC2, sem clusters ECS, sem Kubernetes
Pague apenas pelo que usar — cobrado por requisição e tempo de computação, ocioso = gratuito
Escalonamento automático — de zero a milhares de requisições concorrentes sem configuração
SAM torna declarativo — toda sua infraestrutura é definida em um único template.yaml
Lambda é a razão pela qual sam build && sam deploy é tudo que você precisa. Sem registry Docker, sem orquestração de containers, sem load balancers.
- DynamoDB: O Banco de Dados Que Combina com o Modelo do Lambda
Este é o grande diferencial. Usando PostgreSQL ou MySQL, eu precisaria:
Uma instância RDS (sempre rodando, sempre cobrando)
Configuração de VPC para o Lambda alcançar o banco de dados
Connection pooling (cold starts do Lambda + bancos relacionais = dor)
Migrações de banco a cada deploy
- O DynamoDB não tem nenhum desses problemas. Ele é:
Pay-per-request — custo zero quando ocioso
Sem conexões para gerenciar — é baseado em HTTP, perfeito para a natureza efêmera do Lambda
Sem VPC necessária — Lambda se comunica com DynamoDB pela API pública da AWS
Sem migrações — schema-free, então seu código é seu schema
Escala de zero ao infinito — assim como o próprio Lambda
Lambda + DynamoDB é a única combinação verdadeiramente serverless de computação + armazenamento na AWS. Todo o resto requer algo rodando 24/7.
As Quatro Peças Trabalhando Juntas
Eis por que essas quatro escolhas se potencializam:
Quarkus empacota seus templates e JS em um único JAR → sem deploy separado de front-end
Vanilla JS significa sem passo de build Node.js → esse JAR é tudo que você precisa buildar
Lambda roda esse JAR de forma serverless → sem infraestrutura para provisionar
DynamoDB não precisa de VPC ou conexões → Lambda se comunica com ele direto
- Após a migracao da infra estrutura, preciso substituir o design  web por as ferramentas migradas, e conectar ao app como esta hoje. ALém disso preciso documentar no readme e deixar o banckend, frontend funcional e comunicacao via app também funcional. A principio rodar local.