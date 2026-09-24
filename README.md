# Reserva de Quadras

[![CI](https://github.com/alcantarajv/reserva-quadras/actions/workflows/ci.yml/badge.svg)](https://github.com/alcantarajv/reserva-quadras/actions/workflows/ci.yml)
[![licenca MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-green.svg)](LICENSE)

API REST para reserva de quadras esportivas, construída em Java 21 com Spring Boot 4 e PostgreSQL.

O problema que este projeto resolve de verdade não é o CRUD de reservas — é o que acontece quando **duas pessoas clicam em "reservar" na mesma quadra, no mesmo horário, no mesmo instante**. A resposta está em [Controle de concorrência](#controle-de-concorrência), e há um teste multi-thread que a comprova.

---

## No ar

**<https://reserva-quadras.onrender.com>**

[Demonstração ao vivo](https://reserva-quadras.onrender.com) · [Swagger UI](https://reserva-quadras.onrender.com/swagger-ui.html) · [Health](https://reserva-quadras.onrender.com/actuator/health) · [Quadras](https://reserva-quadras.onrender.com/api/quadras)

A raiz traz uma página que executa a demonstração do conflito ao vivo: ela cria uma conta, reserva um horário e tenta reservar **o mesmo horário** de novo, mostrando o 409 vindo da constraint. É o argumento central do projeto rodando no navegador, sem precisar de terminal.

O serviço roda no plano gratuito do Render e hiberna quando ocioso: a primeira requisição depois de um tempo parado leva cerca de um minuto para responder — as seguintes são imediatas.

---

## Sumário

- [No ar](#no-ar)
- [O problema central](#o-problema-central)
- [Controle de concorrência](#controle-de-concorrência)
- [Decisões técnicas](#decisões-técnicas)
  - [Modelagem do domínio](#modelagem-do-domínio)
  - [Regras de negócio e tempo](#regras-de-negócio-e-tempo)
  - [Segurança](#segurança)
  - [Erros e respostas](#erros-e-respostas)
  - [Persistência e migrations](#persistência-e-migrations)
  - [Estratégia de testes](#estratégia-de-testes)
  - [Empacotamento, CI e deploy](#empacotamento-ci-e-deploy)
- [Modelo de domínio](#modelo-de-domínio)
- [Regras de negócio](#regras-de-negócio)
- [API](#api)
- [Como rodar](#como-rodar)
- [Testes](#testes)
- [Armadilhas do Spring Boot 4](#armadilhas-do-spring-boot-4)
- [Stack](#stack)

---

## O problema central

A tentação, num sistema de reservas, é escrever isto:

```java
if (repositorio.existeReservaNoPeriodo(quadraId, inicio, fim)) {
    throw new HorarioIndisponivelException();
}
repositorio.salvar(novaReserva);   // <-- errado
```

Esse código parece correto e passa em qualquer teste sequencial. Ele está errado.

Entre o `SELECT` e o `INSERT` existe uma janela. Duas transações simultâneas consultam, **as duas veem o horário livre** — porque uma não enxerga a linha ainda não commitada da outra — e as duas inserem. O resultado é uma quadra com duas reservas para o mesmo horário e dois clientes irritados no portão.

A janela não fecha aumentando a "velocidade" da checagem. Ela é estrutural: é a distância entre ler e escrever.

## Controle de concorrência

A garantia foi colocada no único lugar que não tem janela de corrida: **o próprio banco, no momento do `INSERT`**.

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservas
    ADD CONSTRAINT reservas_sem_sobreposicao
    EXCLUDE USING gist (
        quadra_id WITH =,
        tstzrange(inicio, fim, '[)') WITH &&
    )
    WHERE (status <> 'CANCELADA');
```

Lendo em português: *não podem existir duas linhas em que a quadra seja a mesma **e** os intervalos de tempo se sobreponham, ignorando as reservas canceladas.*

Três detalhes importam:

| Detalhe | Por quê |
|---|---|
| `btree_gist` | Um índice GiST não sabe comparar `bigint` com `=` por padrão. A extensão ensina isso, e é o que permite combinar igualdade (quadra) com sobreposição (tempo) no mesmo índice. |
| `'[)'` | Intervalo semiaberto: inclui o início, exclui o fim. É o que faz 09:00–10:00 e 10:00–11:00 **não** conflitarem. Com `'[]'` elas colidiriam no instante das 10:00, e a agenda perderia um slot a cada duas reservas. |
| `WHERE (status <> 'CANCELADA')` | Reserva cancelada libera o horário. Sem essa cláusula, cancelar bloquearia o horário para sempre. |

### Por que não as alternativas

**Lock otimista (`@Version`)** — não se aplica. Ele detecta duas escritas concorrentes na **mesma linha**. Aqui o conflito é entre linhas **diferentes** cujos intervalos se cruzam; não existe uma versão em comum para comparar.

**Lock pessimista na quadra (`SELECT ... FOR UPDATE`)** — funciona, mas serializa todas as reservas daquela quadra. Duas pessoas reservando às 9h e às 15h não conflitam entre si e mesmo assim ficariam em fila. Numa quadra movimentada isso vira gargalo por um problema que quase nunca acontece.

**Constraint de exclusão** — resolve a corrida sem serializar o que não colide. Inserts em horários distintos seguem em paralelo; só os que realmente se sobrepõem são barrados. É a garantia mais forte pelo menor custo de contenção.

### Como o erro chega ao cliente

A aplicação nunca vaza erro de banco. O adaptador de persistência ([`ReservaRepositorioJpa`](src/main/java/com/joaoalcantara/reservas/reserva/infra/ReservaRepositorioJpa.java)) é o único ponto do sistema que conhece o nome da constraint, e traduz a violação em `HorarioIndisponivelException` → **409 Conflict** em Problem Details.

Um detalhe fácil de errar: o método usa `saveAndFlush`, não `save`. Com `save`, o `INSERT` só chegaria ao banco no commit — **depois** que o método transacional retornou — e a violação estouraria fora do `try`, virando um 500 opaco em vez de um 409.

### O teste que comprova

[`ReservaConcorrenteIT`](src/test/java/com/joaoalcantara/reservas/reserva/ReservaConcorrenteIT.java) dispara 16 threads contra o mesmo horário, todas liberadas ao mesmo tempo por um `CountDownLatch`, e verifica:

- exatamente **uma** reserva vence;
- as outras 15 recebem conflito de horário — nenhuma falha por outro motivo;
- o **banco** contém uma única reserva ativa (contador em memória poderia mentir; a tabela não).

Um segundo teste verifica o outro lado da moeda: seis reservas em horários que não colidem são criadas em paralelo sem nenhuma falhar — provando que a solução não serializou a quadra.

[`ConstraintDeSobreposicaoIT`](src/test/java/com/joaoalcantara/reservas/reserva/ConstraintDeSobreposicaoIT.java) cobre as bordas: blocos encostados convivem, sobreposição parcial é barrada, intervalo contido é barrado, cancelar libera o horário, e quadras diferentes não interferem entre si.

---

## Decisões técnicas

### Modelagem do domínio

#### Valor congelado na reserva

O valor é gravado no momento da reserva, não derivado do preço atual da quadra. Se o clube reajustar a tabela amanhã, quem reservou hoje continua devendo o que combinou. Tudo em `BigDecimal` — dinheiro nunca em `double`.

#### Cancelar não apaga a linha

`DELETE /api/reservas/{id}` muda o status para `CANCELADA`. Histórico de reserva é informação contábil e operacional; apagar destruiria a trilha de quem reservou o quê.

#### Desativar quadra não cancela reservas existentes

Desativar impede **novas** reservas. Cancelar em massa as já confirmadas é uma decisão administrativa separada, e silenciosamente cancelar a agenda de dezenas de clientes como efeito colateral de um toggle seria péssimo comportamento.

### Regras de negócio e tempo

#### O `Clock` é um bean injetado

Nenhuma regra chama `Instant.now()` diretamente. "Não reservar no passado" e "cancelar com 6h de antecedência" dependem de tempo, e regra que depende de tempo só é testável se o tempo for controlável. Nos testes o bean vira `Clock.fixed(...)`, e as asserções valem em qualquer máquina, em qualquer dia.

#### Fuso de negócio explícito, separado do fuso do servidor

Instantes são persistidos em `TIMESTAMPTZ` e a aplicação roda em UTC. Mas *"a quadra abre às 8h"* é uma afirmação sobre a hora **local** do clube. Sem um `reservas.fuso-horario` explícito, a mesma reserva passaria ou falharia na validação de funcionamento conforme o fuso da máquina — exatamente o tipo de bug que só aparece em produção.

#### Validação em duas camadas

Bean Validation nos DTOs cuida de formato (campo obrigatório, e-mail válido, preço não negativo). Regra de negócio fica no serviço, onde há acesso ao relógio, à configuração e ao agregado. Misturar as duas produziria anotações que precisam de banco para decidir.

#### `ValidadorDeReserva` isolado do serviço

As regras de criação vivem numa classe sem banco, sem transação e sem HTTP — só `Clock`, configuração e a quadra. Isso torna cada regra testável por teste unitário puro, e a suite roda em milissegundos. Repare no que ele **não** faz: verificar se o horário está ocupado. Essa checagem não pode ser feita ali de forma confiável, pelo motivo explicado acima.

### Segurança

#### JWT stateless, com o papel como claim

Token opaco com sessão em banco seria mais simples de revogar, mas custaria uma consulta por requisição. O JWT assinado (HS256) dispensa o banco tanto na autenticação quanto na autorização. O preço é a revogação — um token roubado vale até expirar, por isso a expiração é curta e configurável, e mudança de papel só vale no próximo login.

O segredo vem de variável de ambiente e **nunca** entra no repositório. A aplicação se recusa a subir com um segredo menor que 32 caracteres: HS256 exige 256 bits, e é melhor não subir do que subir com token forjável.

#### Registro público sempre cria `CLIENTE`

Se o papel viesse no corpo da requisição, qualquer um se autopromoveria a `ADMIN`.

#### A aplicação recusa subir mal configurada

`PropriedadesJwt` exige um segredo de pelo menos 32 caracteres — HS256 pede 256 bits, e um segredo curto significa token forjável. A checagem roda no boot: sem `JWT_SECRET`, ou com um valor fraco, a aplicação **não sobe**, e o log aponta a propriedade e o motivo. A alternativa — subir e funcionar errado em silêncio — é pior justamente porque não parece um problema.

O detalhe que faz isso funcionar é o `@Validated` na classe de propriedades. Sem ele o Spring Boot liga os valores e **ignora** `@NotBlank` e `@Size`: as anotações viram decoração e a aplicação sobe com um segredo de cinco caracteres. É uma pegadinha silenciosa, porque o código *parece* validado.

### Erros e respostas

#### Erros em Problem Details (RFC 9457)

Toda resposta de erro é `application/problem+json` com um `type` estável que o cliente pode tratar programaticamente. Erros de segurança acontecem na cadeia de filtros, antes do `@RestControllerAdvice` — por isso existe um `EscritorDeProblema` dedicado, senão um 401 sairia como página HTML do container e quebraria o contrato.

#### 404 em vez de 403 para reserva de outro cliente

Quando um `CLIENTE` pede uma reserva que não é dele, a resposta é 404. Responder 403 confirmaria que aquela reserva existe, o que já é vazamento de informação.

### Persistência e migrations

#### Repositórios de domínio atrás de interface própria

O domínio depende de `ReservaRepositorio`, não do Spring Data. O adaptador vive em `infra`. Além de deixar a camada de serviço testável com dublês, isso dá um lugar natural para a tradução da violação de constraint — que é detalhe de persistência e não deve vazar para o serviço.

#### `spring.jpa.hibernate.ddl-auto=validate`

O schema é responsabilidade exclusiva do Flyway. O Hibernate apenas confere se o mapeamento bate com o que existe — deixá-lo criar tabelas tornaria as migrations decorativas e o schema de produção imprevisível.

### Estratégia de testes

#### Testcontainers com PostgreSQL real, não H2

A garantia central do projeto é uma `EXCLUDE USING gist` sobre `tstzrange` com `btree_gist` — recursos que só o PostgreSQL tem. Testar contra um banco em memória validaria um sistema diferente do que roda em produção, justamente na parte que mais importa.

#### Surefire e Failsafe separados

`mvn test` roda só os unitários — rápidos, sem dependência externa. `mvn verify` roda também os `*IT`, que exigem Docker. O ciclo de desenvolvimento continua rápido sem abrir mão da suite completa no CI.
### Empacotamento, CI e deploy

#### Banco no Neon, aplicação no Render

O plano gratuito de PostgreSQL do Render é apagado 30 dias depois de criado. Num projeto de portfólio isso significa o link ao vivo morrendo todo mês, provavelmente sem aviso, provavelmente na semana em que alguém for olhar. O banco fica então no Neon, cujo plano gratuito é permanente.

Isso exige duas concessões, ambas visíveis no [`render.yaml`](render.yaml): a `DB_URL` precisa terminar em `?sslmode=require`, e o `DB_CONNECTION_TIMEOUT` sobe de 3s para 10s. Os 3 segundos do padrão partem da premissa de um banco sempre acordado — um banco que hiberna quebra essa premissa, e a primeira conexão depois da suspensão precisa esperar ele acordar.

#### Infraestrutura em arquivo, não no painel

O [`render.yaml`](render.yaml) descreve o serviço junto do código, pelo mesmo motivo do `compose.yaml`: quem lê o repositório vê o que está no ar, e uma mudança de configuração passa por commit em vez de acontecer num formulário que ninguém mais viu.

Duas escolhas dentro dele valem nota. O `autoDeployTrigger: checksPass` faz o deploy esperar o CI — um push que quebra os testes não chega em produção. E o `JWT_SECRET` usa `generateValue: true`: o Render gera 256 bits aleatórios na criação e nunca os exibe, que é exatamente o que se quer de uma chave de assinatura. Ele fica estável entre deploys; se fosse regenerado, todos os tokens emitidos morreriam a cada subida.

#### Uma página inicial estática, sem framework

A raiz devolvia 401 — correto para uma API, ruim para quem abre o link do portfólio sem ler o README, porque 401 lê como site quebrado. É um arquivo HTML servido de `src/main/resources/static`, sem build e sem dependência nova: o que interessa aqui é o backend, e a página só precisa deixá-lo demonstrável sem abrir um terminal.

Ela consome a própria API para provar o ponto do projeto, e trata o caso em que outra pessoa já rodou a demonstração no mesmo horário — aí o primeiro `POST` já devolve 409, o que continua sendo exatamente o que se queria mostrar.

#### Grupo de perfis em vez de arquivo duplicado

O perfil `docker` já baixa o nível de log e esconde os detalhes do `/actuator/health`, e isso vale igualmente em produção. Em vez de repetir essas linhas num `application-prod.properties`, o `spring.profiles.group.prod=docker` faz o `prod` herdar o `docker`. No log da subida aparecem os dois: `The following 2 profiles are active: "prod", "docker"`.

#### `server.port` respeita a variável `PORT`

Plataformas de deploy injetam a porta por variável de ambiente. Sem `server.port=${PORT:8080}`, o serviço sobe na 8080, a plataforma procura em outra porta, o health check falha e o deploy é revertido — com a aplicação funcionando perfeitamente por dentro.

---

## Modelo de domínio

| Entidade | Papel |
|---|---|
| `Usuario` | Credenciais e papel (`CLIENTE` ou `ADMIN`) |
| `Quadra` | Nome, modalidade, preço/hora, ativa ou não |
| `HorarioFuncionamento` | Janela de funcionamento por quadra e dia da semana |
| `Reserva` | Usuário, quadra, intervalo, status e valor |

Ciclo de vida da reserva: `CONFIRMADA` → `CONCLUIDA` ou `CANCELADA`.

A `Quadra` é raiz do agregado que carrega seus horários: só faz sentido falar em "abre às 8h na segunda" no contexto de uma quadra, e as duas coisas mudam juntas.

## Regras de negócio

- Não é possível reservar horário no passado
- A reserva precisa caber **inteira em uma** janela de funcionamento daquela quadra naquele dia — uma reserva que atravessa o intervalo de almoço é rejeitada
- Início em múltiplos de 30 minutos, e duração múltipla da grade
- Duração mínima de 60 e máxima de 180 minutos
- Antecedência máxima de 30 dias
- Quadra inativa não aceita novas reservas
- Cancelamento exige 6 horas de antecedência — um `ADMIN` pode cancelar a qualquer momento, porque precisa poder liberar a agenda em caso de imprevisto
- Um `CLIENTE` só enxerga e cancela as próprias reservas; um `ADMIN` gerencia quadras e vê todas

Todos os limites acima são configuráveis (prefixo `reservas.*`) — são política comercial, não lei da natureza.

**Limitação conhecida e deliberada:** reservas que atravessam a meia-noite local são rejeitadas. O horário de funcionamento é modelado como um par de `LocalTime` dentro de um dia da semana; suportar a virada exigiria janelas que "vazam" para o dia seguinte — complexidade que uma quadra fechando às 23h não justifica.

## API

| Método | Rota | Acesso |
|---|---|---|
| `POST` | `/api/auth/registrar` | público |
| `POST` | `/api/auth/login` | público |
| `GET` | `/api/auth/eu` | autenticado |
| `GET` | `/api/quadras` | público |
| `GET` | `/api/quadras/{id}` | público |
| `POST` | `/api/quadras` | `ADMIN` |
| `PUT` | `/api/quadras/{id}` | `ADMIN` |
| `PUT` | `/api/quadras/{id}/situacao` | `ADMIN` |
| `POST` | `/api/reservas` | autenticado |
| `GET` | `/api/reservas` | autenticado (cliente vê as suas; admin vê todas) |
| `GET` | `/api/reservas/{id}` | autenticado |
| `DELETE` | `/api/reservas/{id}` | autenticado |
| `GET` | `/swagger-ui.html` | público |
| `GET` | `/v3/api-docs` | público |

Exemplo de conflito de horário:

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://api.reservas.dev/erros/horario-indisponivel",
  "title": "Conflito",
  "status": 409,
  "detail": "Este horario ja foi reservado para esta quadra. Escolha outro.",
  "ocorridoEm": "2026-09-01T13:00:00Z"
}
```

---

## Como rodar

Pré-requisitos: Java 21, Docker. Maven não é necessário — o projeto usa o Maven Wrapper.

Suba o banco:

```bash
docker compose up -d
```

Defina o segredo do JWT e rode a aplicação:

```bash
JWT_SECRET=troque-por-um-segredo-longo-de-no-minimo-32-caracteres ./mvnw spring-boot:run
```

No PowerShell:

```bash
$env:JWT_SECRET = "troque-por-um-segredo-longo-de-no-minimo-32-caracteres"; ./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`. As migrations rodam sozinhas no boot.

Veja [`.env.example`](.env.example) para a lista completa de variáveis.

### O primeiro administrador

Há um problema de partida em todo sistema com papéis: o registro público cria apenas `CLIENTE` — se o papel viesse no corpo da requisição, qualquer um se autopromoveria — e um `ADMIN` só poderia ser criado por outro `ADMIN`. Sem um ponto de partida, o sistema sobe sem nenhum administrador e as rotas de gestão de quadras ficam inalcançáveis.

A saída é um bootstrap por configuração, o mesmo padrão de Grafana, Keycloak e Jenkins:

```bash
JWT_SECRET=troque-por-um-segredo-longo-de-no-minimo-32-caracteres ADMIN_INICIAL_EMAIL=admin@reservas.dev ADMIN_INICIAL_SENHA=troque-esta-senha ./mvnw spring-boot:run
```

Duas salvaguardas o tornam seguro: **não há valor padrão** — sem as duas variáveis definidas nada acontece, e o sistema nunca sobe com credencial de administrador conhecida — e ele é **idempotente**, então a senha não é sobrescrita a cada deploy e a variável pode continuar definida sem efeito colateral.

### Swagger UI

Com a aplicação no ar:

```
http://localhost:8080/swagger-ui.html
```

O spec cru fica em `/v3/api-docs`. Ambos são públicos — documentação que exige token para ser lida não serve para nada.

Para testar as rotas protegidas: faça login em `POST /api/auth/login`, copie o valor de `token` da resposta, clique em **Authorize** no topo da página e cole **apenas o token**, sem o prefixo `Bearer`. Ele passa a valer para todas as chamadas seguintes.

As rotas públicas aparecem sem cadeado — registro, login e os dois `GET` de quadras. Isso não é cosmético: o esquema de segurança é declarado globalmente em [`OpenApiConfig`](src/main/java/com/joaoalcantara/reservas/comum/config/OpenApiConfig.java) e as públicas são marcadas com `@SecurityRequirements`, o que faz o springdoc gerar `"security": []` nelas. Sem essa declaração global, o botão Authorize simplesmente não existiria, cada chamada protegida voltaria 401 e a página seria só uma lista bonita.

### Testando a API na mão

Fluxo mínimo, do registro ao conflito de horário:

```bash
curl -s -X POST http://localhost:8080/api/auth/registrar -H "Content-Type: application/json" -d '{"nome":"Joao","email":"joao@teste.dev","senha":"senha12345"}'
```

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"joao@teste.dev","senha":"senha12345"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
```

Criar uma quadra exige o token do admin (`ADMIN` no login acima). Com uma quadra criada, reserve duas vezes o mesmo horário — a segunda devolve **409**:

```bash
curl -s -X POST http://localhost:8080/api/reservas -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"quadraId":1,"inicio":"2026-08-29T14:00:00-03:00","fim":"2026-08-29T15:00:00-03:00"}'
```

Os instantes vão em ISO-8601 **com fuso**. Exigir o fuso do cliente elimina a ambiguidade de "14h" — a API nunca precisa adivinhar de onde o horário veio.

## Testes

Só os unitários (rápidos, sem Docker):

```bash
./mvnw test
```

Suite completa, incluindo os testes de integração com Testcontainers:

```bash
./mvnw verify
```

O teste de concorrência precisa de Docker rodando — ele sobe um PostgreSQL real.

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

---

## Stack

Java 21 · Spring Boot 4.1 · Spring Security · PostgreSQL 17 · Flyway · JPA/Hibernate · JJWT · springdoc-openapi · JUnit 6 · AssertJ · Mockito · Testcontainers 2 · Docker

---
## Projetos relacionados

Este é o segundo de uma série, e cada um ataca um problema diferente de backend:

1. [encurtador-links](https://github.com/alcantarajv/encurtador-links) — **latência**: cache com Redis, rate limiting, processamento assíncrono
2. **reserva-quadras** — **concorrência**: constraint de exclusão do PostgreSQL e teste multi-thread
3. [api-pedidos](https://github.com/alcantarajv/api-pedidos) — **consistência entre sistemas**: idempotência e padrão outbox

---

## Autor

**João Vitor Alcântara Corrêa**
[GitHub](https://github.com/alcantarajv) · [LinkedIn](https://linkedin.com/in/joaovalcantara)

---

## Licença

[MIT](LICENSE).
