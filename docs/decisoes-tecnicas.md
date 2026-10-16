# Decisões técnicas — registro completo

[← Voltar ao README](../README.md)

Cada entrada registra a escolha, o motivo e a alternativa descartada. O registro foi alimentado etapa a etapa, durante o desenvolvimento, e não escrito no fim — são 21 decisões, agrupadas por tema. O README traz uma seleção com as que definem o projeto; a decisão central, a constraint de exclusão, está no próprio [README](../README.md#o-problema-central).

## Sumário

- [Modelagem do domínio](#modelagem-do-domínio)
- [Regras de negócio e tempo](#regras-de-negócio-e-tempo)
- [Segurança](#segurança)
- [Erros e respostas](#erros-e-respostas)
- [Persistência e migrations](#persistência-e-migrations)
- [Estratégia de testes](#estratégia-de-testes)
- [Empacotamento, CI e deploy](#empacotamento-ci-e-deploy)
- [Armadilhas do Spring Boot 4](#armadilhas-do-spring-boot-4)

---

## Modelagem do domínio

### Valor congelado na reserva

O valor é gravado no momento da reserva, não derivado do preço atual da quadra. Se o clube reajustar a tabela amanhã, quem reservou hoje continua devendo o que combinou. Tudo em `BigDecimal` — dinheiro nunca em `double`.

### Cancelar não apaga a linha

`DELETE /api/reservas/{id}` muda o status para `CANCELADA`. Histórico de reserva é informação contábil e operacional; apagar destruiria a trilha de quem reservou o quê.

### Desativar quadra não cancela reservas existentes

Desativar impede **novas** reservas. Cancelar em massa as já confirmadas é uma decisão administrativa separada, e silenciosamente cancelar a agenda de dezenas de clientes como efeito colateral de um toggle seria péssimo comportamento.

---

## Regras de negócio e tempo

### O `Clock` é um bean injetado

Nenhuma regra chama `Instant.now()` diretamente. "Não reservar no passado" e "cancelar com 6h de antecedência" dependem de tempo, e regra que depende de tempo só é testável se o tempo for controlável. Nos testes o bean vira `Clock.fixed(...)`, e as asserções valem em qualquer máquina, em qualquer dia.

### Fuso de negócio explícito, separado do fuso do servidor

Instantes são persistidos em `TIMESTAMPTZ` e a aplicação roda em UTC. Mas *"a quadra abre às 8h"* é uma afirmação sobre a hora **local** do clube. Sem um `reservas.fuso-horario` explícito, a mesma reserva passaria ou falharia na validação de funcionamento conforme o fuso da máquina — exatamente o tipo de bug que só aparece em produção.

### Validação em duas camadas

Bean Validation nos DTOs cuida de formato (campo obrigatório, e-mail válido, preço não negativo). Regra de negócio fica no serviço, onde há acesso ao relógio, à configuração e ao agregado. Misturar as duas produziria anotações que precisam de banco para decidir.

### `ValidadorDeReserva` isolado do serviço

As regras de criação vivem numa classe sem banco, sem transação e sem HTTP — só `Clock`, configuração e a quadra. Isso torna cada regra testável por teste unitário puro, e a suite roda em milissegundos. Repare no que ele **não** faz: verificar se o horário está ocupado. Essa checagem não pode ser feita ali de forma confiável, pelo motivo explicado em [O problema central](../README.md#o-problema-central).

---

## Segurança

### JWT stateless, com o papel como claim

Token opaco com sessão em banco seria mais simples de revogar, mas custaria uma consulta por requisição. O JWT assinado (HS256) dispensa o banco tanto na autenticação quanto na autorização. O preço é a revogação — um token roubado vale até expirar, por isso a expiração é curta e configurável, e mudança de papel só vale no próximo login.

O segredo vem de variável de ambiente e **nunca** entra no repositório. A aplicação se recusa a subir com um segredo menor que 32 caracteres: HS256 exige 256 bits, e é melhor não subir do que subir com token forjável.

### Registro público sempre cria `CLIENTE`

Se o papel viesse no corpo da requisição, qualquer um se autopromoveria a `ADMIN`.

### A aplicação recusa subir mal configurada

`PropriedadesJwt` exige um segredo de pelo menos 32 caracteres — HS256 pede 256 bits, e um segredo curto significa token forjável. A checagem roda no boot: sem `JWT_SECRET`, ou com um valor fraco, a aplicação **não sobe**, e o log aponta a propriedade e o motivo. A alternativa — subir e funcionar errado em silêncio — é pior justamente porque não parece um problema.

O detalhe que faz isso funcionar é o `@Validated` na classe de propriedades. Sem ele o Spring Boot liga os valores e **ignora** `@NotBlank` e `@Size`: as anotações viram decoração e a aplicação sobe com um segredo de cinco caracteres. É uma pegadinha silenciosa, porque o código *parece* validado.

---

## Erros e respostas

### Erros em Problem Details (RFC 9457)

Toda resposta de erro é `application/problem+json` com um `type` estável que o cliente pode tratar programaticamente. Erros de segurança acontecem na cadeia de filtros, antes do `@RestControllerAdvice` — por isso existe um `EscritorDeProblema` dedicado, senão um 401 sairia como página HTML do container e quebraria o contrato.

### 404 em vez de 403 para reserva de outro cliente

Quando um `CLIENTE` pede uma reserva que não é dele, a resposta é 404. Responder 403 confirmaria que aquela reserva existe, o que já é vazamento de informação.

---

## Persistência e migrations

### Repositórios de domínio atrás de interface própria

O domínio depende de `ReservaRepositorio`, não do Spring Data. O adaptador vive em `infra`. Além de deixar a camada de serviço testável com dublês, isso dá um lugar natural para a tradução da violação de constraint — que é detalhe de persistência e não deve vazar para o serviço.

### `spring.jpa.hibernate.ddl-auto=validate`

O schema é responsabilidade exclusiva do Flyway. O Hibernate apenas confere se o mapeamento bate com o que existe — deixá-lo criar tabelas tornaria as migrations decorativas e o schema de produção imprevisível.

---

## Estratégia de testes

### Testcontainers com PostgreSQL real, não H2

A garantia central do projeto é uma `EXCLUDE USING gist` sobre `tstzrange` com `btree_gist` — recursos que só o PostgreSQL tem. Testar contra um banco em memória validaria um sistema diferente do que roda em produção, justamente na parte que mais importa.

### Surefire e Failsafe separados

`mvn test` roda só os unitários — rápidos, sem dependência externa. `mvn verify` roda também os `*IT`, que exigem Docker. O ciclo de desenvolvimento continua rápido sem abrir mão da suite completa no CI.
---

## Empacotamento, CI e deploy

### Banco no Neon, aplicação no Render

O plano gratuito de PostgreSQL do Render é apagado 30 dias depois de criado. Num projeto de portfólio isso significa o link ao vivo morrendo todo mês, provavelmente sem aviso, provavelmente na semana em que alguém for olhar. O banco fica então no Neon, cujo plano gratuito é permanente.

Isso exige duas concessões, ambas visíveis no [`render.yaml`](../render.yaml): a `DB_URL` precisa terminar em `?sslmode=require`, e o `DB_CONNECTION_TIMEOUT` sobe de 3s para 10s. Os 3 segundos do padrão partem da premissa de um banco sempre acordado — um banco que hiberna quebra essa premissa, e a primeira conexão depois da suspensão precisa esperar ele acordar.

### Infraestrutura em arquivo, não no painel

O [`render.yaml`](../render.yaml) descreve o serviço junto do código, pelo mesmo motivo do `compose.yaml`: quem lê o repositório vê o que está no ar, e uma mudança de configuração passa por commit em vez de acontecer num formulário que ninguém mais viu.

Duas escolhas dentro dele valem nota. O `autoDeployTrigger: checksPass` faz o deploy esperar o CI — um push que quebra os testes não chega em produção. E o `JWT_SECRET` usa `generateValue: true`: o Render gera 256 bits aleatórios na criação e nunca os exibe, que é exatamente o que se quer de uma chave de assinatura. Ele fica estável entre deploys; se fosse regenerado, todos os tokens emitidos morreriam a cada subida.

### Uma página inicial estática, sem framework

A raiz devolvia 401 — correto para uma API, ruim para quem abre o link do portfólio sem ler o README, porque 401 lê como site quebrado. É um arquivo HTML servido de `src/main/resources/static`, sem build e sem dependência nova: o que interessa aqui é o backend, e a página só precisa deixá-lo demonstrável sem abrir um terminal.

Ela consome a própria API para provar o ponto do projeto, e trata o caso em que outra pessoa já rodou a demonstração no mesmo horário — aí o primeiro `POST` já devolve 409, o que continua sendo exatamente o que se queria mostrar.

### Grupo de perfis em vez de arquivo duplicado

O perfil `docker` já baixa o nível de log e esconde os detalhes do `/actuator/health`, e isso vale igualmente em produção. Em vez de repetir essas linhas num `application-prod.properties`, o `spring.profiles.group.prod=docker` faz o `prod` herdar o `docker`. No log da subida aparecem os dois: `The following 2 profiles are active: "prod", "docker"`.

### `server.port` respeita a variável `PORT`

Plataformas de deploy injetam a porta por variável de ambiente. Sem `server.port=${PORT:8080}`, o serviço sobe na 8080, a plataforma procura em outra porta, o health check falha e o deploy é revertido — com a aplicação funcionando perfeitamente por dentro.

---

## Armadilhas do Spring Boot 4

Boa parte do conteúdo disponível online ainda descreve o Spring Boot 3. Onde este projeto diverge do que a maioria dos tutoriais mostra:

| O que você vai ver por aí | O que o Spring Boot 4 usa |
|---|---|
| `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** |
| `spring-boot-starter-test` | Starters modulares: `-webmvc-test`, `-validation-test`, `-security-test`, `-data-jpa-test`... |
| `flyway-core` como dependência direta | **`spring-boot-starter-flyway`** |
| `com.fasterxml.jackson.databind.ObjectMapper` | **`tools.jackson.databind.ObjectMapper`** — o Boot 4 usa Jackson 3, que mudou de pacote. As *anotações* (`@JsonFormat` e afins) continuam em `com.fasterxml.jackson.annotation`. |
| `org.testcontainers.containers.PostgreSQLContainer<?>` | **`org.testcontainers.postgresql.PostgreSQLContainer`** — o Testcontainers 2.x mudou o pacote e a classe deixou de ser genérica. |
| JUnit 5 | JUnit 6 |
| `springdoc-openapi` 2.x | **springdoc-openapi 3.x** — a 2.x é para Boot 3 e Jackson 2 e não sobe aqui. |

### Três armadilhas que custaram tempo aqui

Não são específicas do Boot 4, mas as três apareceram durante a construção deste projeto e vale registrar:

**1. `hibernate.jdbc.time_zone` corrompe `LocalTime`.** Essa propriedade parece obrigatória em qualquer projeto que leve fuso a sério, e é o que quase todo guia recomenda. O problema é que ela manda o Hibernate converter **todo** valor temporal para o fuso indicado na gravação — inclusive os `LocalTime` de horário de funcionamento, que são hora de parede e não um ponto na linha do tempo. Com a JVM em UTC−3, "fecha às 23h" virava `02:00` no banco, e a check constraint `fechamento > abertura` estourava. Ela não faz falta: os instantes são `Instant` em `TIMESTAMPTZ`, e o driver já registra o ponto correto qualquer que seja o fuso da JVM.

**2. `application.properties` em `src/test/resources` substitui o de produção — não se soma a ele.** Ambos ocupam o mesmo caminho no classpath, e o de teste ganha. Resultado: todas as propriedades de negócio voltaram ao default do `record` (zero), e os testes de integração falharam com "reservas só podem ser feitas com até **0** dias de antecedência" — um erro que não tem nada a ver com o que estava sendo testado. A correção é usar um perfil: `application-test.properties` + `@ActiveProfiles("test")`, aí o arquivo do main carrega normalmente e o de teste apenas sobrepõe o que precisa.

Corolário: `target/test-classes` não é limpo automaticamente quando um recurso de teste é renomeado ou removido. Depois de mexer nesses arquivos, `./mvnw clean` antes de concluir que a correção não funcionou.

**3. O Mockito precisa ser carregado como agente Java explícito.** Sem isso ele se auto-anexa, emite aviso, e deixará de funcionar em versões futuras do JDK — quebrando o CI num upgrade de Java. A configuração (via `maven-dependency-plugin:properties` + `argLine` do surefire) está no `pom.xml`.

[← Voltar ao README](../README.md)
