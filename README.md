# Reserva de Quadras

[![CI](https://github.com/alcantarajv/reserva-quadras/actions/workflows/ci.yml/badge.svg)](https://github.com/alcantarajv/reserva-quadras/actions/workflows/ci.yml)
[![licenca MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-green.svg)](LICENSE)

API REST para reserva de quadras esportivas, construída em Java 21 com Spring Boot 4 e PostgreSQL.

O problema que este projeto resolve de verdade não é o CRUD de reservas — é o que acontece quando **duas pessoas clicam em "reservar" na mesma quadra, no mesmo horário, no mesmo instante**. A resposta está em [O problema central](#o-problema-central), e há um teste multi-thread que a comprova.

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
- [Decisões técnicas](#decisões-técnicas)
  - [Registro completo das decisões](docs/decisoes-tecnicas.md)
- [Domínio e regras de negócio](#domínio-e-regras-de-negócio)
- [API](#api)
- [Como rodar](#como-rodar)
- [Testes](#testes)
- [Stack](#stack)
- [Projetos relacionados](#projetos-relacionados)
- [Autor](#autor)
- [Licença](#licença)

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

### A solução: uma constraint de exclusão

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

O projeto registrou 21 decisões ao longo do desenvolvimento, além da constraint de exclusão acima, cada uma com o motivo e a alternativa descartada. As que estão abaixo são as que definem o projeto; o registro completo, agrupado por tema e com as armadilhas do Spring Boot 4 que apareceram no caminho, está em **[docs/decisoes-tecnicas.md](docs/decisoes-tecnicas.md)**.

### Domínio e tempo

**O valor fica congelado na reserva.** Ele é gravado no momento da reserva, não derivado do preço atual da quadra: se o clube reajustar a tabela amanhã, quem reservou hoje continua devendo o que combinou. Tudo em `BigDecimal` — dinheiro nunca em `double`.

**Cancelar não apaga a linha.** O `DELETE` muda o status para `CANCELADA`. Histórico de reserva é informação contábil e operacional, e é a cláusula `WHERE` da constraint que libera o horário.

**O `Clock` é um bean injetado.** Nenhuma regra chama `Instant.now()` diretamente. "Não reservar no passado" e "cancelar com 6h de antecedência" só são testáveis se o tempo for controlável; nos testes, o bean vira `Clock.fixed(...)`.

**Fuso de negócio explícito, separado do fuso do servidor.** Os instantes ficam em `TIMESTAMPTZ` e a aplicação roda em UTC, mas *"a quadra abre às 8h"* é uma afirmação sobre a hora local do clube. Sem o `reservas.fuso-horario`, a mesma reserva passaria ou falharia conforme o fuso da máquina.

### Segurança e erros

**JWT stateless, com o papel como claim.** O token assinado dispensa o banco na autenticação e na autorização. O preço é a revogação — um token roubado vale até expirar —, por isso a expiração é curta e configurável.

**A aplicação recusa subir mal configurada.** Sem `JWT_SECRET`, ou com menos de 32 caracteres, ela não sobe: HS256 pede 256 bits, e segredo curto é token forjável. O detalhe que faz isso funcionar é o `@Validated` na classe de propriedades; sem ele, o Spring Boot ignora o `@NotBlank` e o `@Size` em silêncio.

**404, e não 403, para a reserva de outro cliente.** Responder 403 confirmaria que aquela reserva existe, o que já é vazamento de informação.

### Testes e entrega

**PostgreSQL real nos testes, não H2.** A garantia central é uma `EXCLUDE USING gist` sobre `tstzrange` com `btree_gist`, recursos que só o PostgreSQL tem. Testar contra um banco em memória validaria um sistema diferente do que roda em produção, justamente na parte que mais importa.

**Banco no Neon, e o deploy espera o CI.** O PostgreSQL gratuito do Render é apagado em 30 dias, e o do Neon é permanente. O `autoDeployTrigger: checksPass` faz o Render só publicar se o CI passar, e o `JWT_SECRET` é gerado pelo próprio Render, que nunca o exibe.

---

## Domínio e regras de negócio

### Modelo de domínio

| Entidade | Papel |
|---|---|
| `Usuario` | Credenciais e papel (`CLIENTE` ou `ADMIN`) |
| `Quadra` | Nome, modalidade, preço/hora, ativa ou não |
| `HorarioFuncionamento` | Janela de funcionamento por quadra e dia da semana |
| `Reserva` | Usuário, quadra, intervalo, status e valor |

Ciclo de vida da reserva: `CONFIRMADA` → `CONCLUIDA` ou `CANCELADA`.

A `Quadra` é raiz do agregado que carrega seus horários: só faz sentido falar em "abre às 8h na segunda" no contexto de uma quadra, e as duas coisas mudam juntas.

### Regras de negócio

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

---

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

---

## Testes

**43 testes: 36 unitários e 7 de integração.**

Só os de unidade, sem Docker, em cerca de 6 segundos:

```bash
./mvnw test
```

A suíte completa, incluindo os testes de integração com Testcontainers, em cerca de 25 segundos:

```bash
./mvnw verify
```

Os testes de integração precisam do Docker rodando: eles sobem um PostgreSQL real, porque a constraint de exclusão só existe nele.

| Teste | O que comprova |
|---|---|
| [`ReservaConcorrenteIT`](src/test/java/com/joaoalcantara/reservas/reserva/ReservaConcorrenteIT.java) | 16 threads no mesmo horário: uma reserva vence, 15 recebem conflito e o banco tem uma única reserva ativa; reservas em horários que não colidem seguem em paralelo |
| [`ConstraintDeSobreposicaoIT`](src/test/java/com/joaoalcantara/reservas/reserva/ConstraintDeSobreposicaoIT.java) | as bordas da constraint: blocos encostados, sobreposição parcial, intervalo contido, cancelamento e quadras diferentes |

### Na integração contínua

Cada push e cada pull request para `main` disparam o workflow [`ci.yml`](.github/workflows/ci.yml), que roda o `./mvnw verify` completo e, se ele passar, constrói a imagem Docker. Os relatórios de teste ficam disponíveis para download na página da execução, inclusive quando a suíte falha.

---

## Stack

Java 21 · Spring Boot 4.1 · Spring Security · PostgreSQL 17 · Flyway · JPA/Hibernate · JJWT · springdoc-openapi 3 · JUnit 6 · AssertJ · Mockito · Testcontainers 2 · Docker · GitHub Actions · Render · Neon

---

## Projetos relacionados

Este é o segundo de uma série de quatro projetos, e cada um ataca um problema de backend que só aparece em produção:

1. [encurtador-links](https://github.com/alcantarajv/encurtador-links) — **latência**: cache com Redis, rate limiting, processamento assíncrono
2. **reserva-quadras** — **concorrência**: constraint de exclusão do PostgreSQL e teste multi-thread
3. [api-pedidos](https://github.com/alcantarajv/api-pedidos) — **consistência entre sistemas**: idempotência e padrão outbox
4. [triagem-chamados](https://github.com/alcantarajv/triagem-chamados) — **resiliência a uma dependência externa lenta, cara e instável**: fallback entre provedores de IA, circuit breaker, validação da resposta e controle de custo

---

## Autor

**João Vitor Alcântara Corrêa**
[joaoalcantara.dev](https://joaoalcantara.dev) · [GitHub](https://github.com/alcantarajv) · [LinkedIn](https://linkedin.com/in/joaovalcantara)

---

## Licença

[MIT](LICENSE).
