---
agent: Plan
---

- Preciso criar um módulo para os colaboradores da empresa "bater o ponto" ao chegar no trabalho, horario almoço, descanso e saida.
- O modulo de Ponto deve aparecer para todo novo colaborador cadastrado. 
- Usuarios admin nao tem acesso a funcionalidade de ponto.
- A tela de ponto deve ser um mapa na parte superior da tela, ocupando 40% da tela indicando a localização do usuario no mapa, nome, caracteristica da marcação (entrada ou saida), dia e hora com minutos e segundos e o botão no bottom da tela para confirmar a marcação. Além disso é necessario ter um botão que ao clicar mostre em detalhes as marcações do dia. Além disso na tela de detalhe das marcações deve conter o total de horas trabalhadas (40 horas semanais), o total de horas devidas (ou seja não trabalhou as 8h por dia no dia que fez a primeira maracacao.)
- O app deve calcular e atualizar sempre a quantidade de horas trabalhadas, faltantes e horario de almoço além de 60 min. Será tolerado 10 min de atraso. Após esse tempo, a marcação vai com a tag de atraso.
- O usuario pode sair antes das 8h trabalhadas. As jornadas de 12 horas trabalhadas no dia, devem ter 2h de descanso, sendo maracadas separadamente dividida em 1h cada ou as 2 horas simultaneas. 
- Não é permitido editar a marcação. Uma vez marcada, não tem volta, inicialmente no mvp. 
- O botão de marcação so deve ser habilitado se o usuario estiver logado, e em um raio de 5 metros do local de trabalho: Av. Vicente de Carvalho, 761 Centro - Bertioga (Beco da Praia).
- Preciso refatorar o app para que os usuarios comuns vejam somente as atividades/checklist que pertencem a sua area e a tab de ponto ao logarem no app.