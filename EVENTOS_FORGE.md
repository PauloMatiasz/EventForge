# EventForge — Plataforma de Criação, Venda, Reserva e Simulação de Eventos

> Documento vivo. Regra de ouro: **nada entra no código antes de estar (ou ser discutido) aqui.**
> Este arquivo é o contrato entre você e o "eu arquiteto" — quando eu propuser um trecho de código,
> vou sempre apontar pra qual seção deste documento aquela decisão pertence.

---

## 0. Contexto e como vamos trabalhar

- Você é pleno, entende POO e lógica, vem de **Python (Flask/FastAPI)** e **React**. Não vamos explicar "o que é uma classe" — vamos explicar **por que o Java/Spring resolve os mesmos problemas de um jeito estruturalmente diferente**, e por que isso importa em produção.
- Formato de trabalho: eu implemento **um pedaço pequeno e coeso por vez** (ex: só o domínio do Evento, sem controller ainda), explico a decisão, você lê, questiona, e só então avançamos.
- Toda decisão de design registrada aqui deve responder três perguntas: **o que isso resolve, o que eu perco ao escolher isso, e qual seria a alternativa.**
- Este projeto é deliberadamente mais difícil que o necessário para "funcionar" — o objetivo é aprender a tomar a decisão, não só ter um sistema rodando.

---

## 1. Domínio do negócio (o que o sistema faz)

Plataforma onde **organizadores** criam eventos com sessões e lotes de ingresso, **compradores** reservam
temporariamente (hold), simulam pagamento e confirmam a compra — sem gateway de pagamento real.

### Atores
- **Organizador**: cria/edita eventos, sessões, lotes, acompanha vendas.
- **Comprador**: navega catálogo, reserva, "paga" (simulado), cancela/reembolsa.
- **Admin**: audita, cancela eventos, vê métricas globais.

### Funcionalidades obrigatórias (MVP)
1. CRUD de Evento → Sessão → Lote de ingresso (com quantidade disponível).
2. Consulta pública de catálogo (busca, filtro por data/cidade/categoria).
3. **Hold** (reserva temporária) de N ingressos de um lote, com **TTL** (ex: 10 min) — enquanto o hold existe, o estoque fica reservado e não pode ser vendido a outro comprador.
4. Expiração automática do hold (sem cron manual — TTL nativo do Redis) devolvendo o estoque.
5. Simulação de pagamento: estados `PENDING → APPROVED | DECLINED`, com "gateways simulados" diferentes (cartão aprova 90%, pix aprova 99%, boleto fica pendente até confirmação manual via endpoint de teste).
6. Confirmação da compra → gera Pedido + Ticket(s) com código único, **de forma idempotente** (reprocessar o mesmo pedido não duplica ticket).
7. Cancelamento com reembolso simulado, devolvendo estoque se dentro da política.
8. Notificação assíncrona (pode ser só um log estruturado "e-mail enviado" — o objetivo é o fluxo assíncrono, não o e-mail em si).

### Regras de negócio inegociáveis (é aqui que mora o desafio real)
- **Nunca vender acima do estoque** (nem sob concorrência — 50 pessoas comprando o último ingresso ao mesmo tempo).
- Hold expira sozinho e devolve estoque — sem job varrendo tabela de tempos em tempos como única fonte de verdade.
- Nenhuma mensagem do RabbitMQ pode, se reentregue, duplicar efeito (ticket duplicado, e-mail duplicado, estoque decrementado duas vezes).
- Se `sales-simulation-service` cair no meio de um fluxo, o `reservation-service` não pode travar o hold do usuário para sempre.

---

## 2. Arquitetura de alto nível — os 3 microsserviços

| Serviço | Banco | Responsabilidade | Por que esse banco |
|---|---|---|---|
| `event-catalog-service` | **PostgreSQL** | Dono de Evento, Sessão, Lote, Venue. Fonte da verdade do estoque "base". | Dados fortemente relacionais (Evento 1-N Sessão 1-N Lote), precisa de FK, transação ACID ao decrementar estoque. |
| `reservation-service` | **PostgreSQL** (transacional) + **Redis** (hold/TTL, lock, cache) | Orquestra a saga de compra: cria hold, aguarda pagamento, confirma ou cancela. Dono da idempotência do fluxo de compra. | Postgres pelo mesmo motivo de consistência forte (Pedido, Reserva). Redis porque hold é *efêmero por natureza* — TTL nativo é a ferramenta certa, não "gambiarra" de coluna `expires_at` + cron. |
| `sales-simulation-service` | **MongoDB** | Simula gateways de pagamento; guarda tentativas de pagamento com payload variável por método (cartão ≠ pix ≠ boleto). | Esse é o caso real de schema flexível: cada "gateway simulado" retorna um payload de formato diferente. Modelar isso em tabelas relacionais forçaria colunas nulas ou uma tabela EAV — o documento do Mongo resolve isso de forma honesta, não por modismo. |

> **Nota importante sobre o Mongo**: se ao implementar você achar que o payload de pagamento não é
> variável o bastante para justificar um banco a mais, isso é uma discussão válida — o documento existe
> para você **questionar**, não só aceitar. Registre a decisão final (manter Mongo ou trocar por Postgres
> com uma coluna `JSONB`) na seção de Decisões de Arquitetura (ADR) do repositório.

### Peça de infraestrutura opcional (não conta como um dos 3 domínios de negócio)
- **API Gateway** (Spring Cloud Gateway): ponto único de entrada, valida JWT antes de rotear, evita repetir lógica de auth nos 3 serviços. Marcado como **estágio 2** — comece com os serviços expostos diretamente e adicione o gateway quando o fluxo básico estiver estável.

### Comunicação entre serviços — regra de decisão
- **Síncrona (REST/WebClient)**: só quando o chamador **precisa da resposta imediatamente** para decidir o próximo passo (ex: `reservation-service` perguntando a disponibilidade real na hora de criar o hold).
- **Assíncrona (RabbitMQ)**: para **fatos que já aconteceram** e outros serviços reagem a eles (ex: `PagamentoAprovado`, `ReservaExpirada`, `PedidoConfirmado`). Se o serviço consumidor estiver fora do ar, a mensagem espera na fila — ninguém trava.
- Regra prática: **pergunta = síncrono, aviso = assíncrono.**

### Fluxo de compra (visão de mensageria)

```
Comprador → reservation-service: POST /holds (lote, quantidade)
reservation-service → event-catalog-service: reserva estoque (síncrono, transacional)
reservation-service → Redis: grava hold com TTL 10min
reservation-service → RabbitMQ (exchange: reservation.events): publica HoldCreated

Comprador → reservation-service: POST /holds/{id}/checkout
reservation-service → sales-simulation-service: solicita pagamento (síncrono)
sales-simulation-service → RabbitMQ (exchange: payment.events): publica PaymentApproved | PaymentDeclined

reservation-service consome PaymentApproved:
  - confirma reserva (idempotente via Inbox)
  - publica PedidoConfirmado
event-catalog-service consome PedidoConfirmado:
  - decrementa estoque definitivo (some do hold temporário)

Se hold expira no Redis (keyspace notification) sem checkout:
  reservation-service consome expiração → publica HoldExpired
  event-catalog-service devolve estoque reservado
```

---

## 3. Idempotência e resiliência a falhas (sua preocupação principal)

Isso é tratado como requisito de primeira classe, não como detalhe de implementação.

### Problema 1 — "minha mensagem pode chegar duas vezes"
RabbitMQ com confirmação manual de ack garante **at-least-once delivery** — ou seja, reentrega é *esperada*,
não é bug. Solução: **padrão Inbox**.
- Toda mensagem recebida carrega um `messageId` único.
- Antes de processar, o consumidor tenta inserir esse `messageId` numa tabela `processed_messages`
  com **constraint UNIQUE**, dentro da **mesma transação** que aplica o efeito de negócio.
- Se a inserção falhar por duplicidade → a mensagem já foi processada → **ack e descarta**, sem reaplicar o efeito.

### Problema 2 — "gravei no banco mas caí antes de publicar o evento" (dual write problem)
Solução: **padrão Outbox**.
- Em vez de `salvar no banco` + `publicar no RabbitMQ` como duas operações separadas (que podem falhar entre uma e outra), você grava a mudança de estado **e** o evento a ser publicado **na mesma transação/tabela `outbox`**.
- Um processo separado (poller ou Debezium, dependendo do quão fundo você quiser ir) lê a tabela `outbox` e publica no RabbitMQ, marcando como enviado.
- Resultado: é fisicamente impossível salvar o estado sem eventualmente publicar o evento.

### Problema 3 — "requisição HTTP idêntica enviada duas vezes pelo Angular" (double-click, retry de rede)
Solução: **Idempotency-Key** no header de requisições que causam efeito (`POST /holds/{id}/checkout`).
- Chave gerada pelo cliente (Angular), guardada em Redis com o resultado da primeira execução.
- Segunda requisição com a mesma chave devolve a resposta cacheada, não reexecuta.

### Concorrência no estoque (evitar overselling)
- Decremento de estoque com `SELECT ... FOR UPDATE` (lock pessimista) dentro de transação curta no Postgres, **ou**
- Decremento otimista com coluna `version` (`@Version` do JPA) + retry em caso de conflito.
- Discussão obrigatória no ADR: qual dos dois você escolheu e por quê (dica: depende do nível de contenção esperado).

---

## 4. Arquitetura interna de cada serviço — Clean Architecture

Cada um dos 3 serviços segue as mesmas camadas (consistência entre serviços é uma decisão deliberada
de aprendizado: o padrão se repete, só o conteúdo muda).

```
domain/            -> Entidades de negócio puras, Value Objects, regras. ZERO import de Spring/JPA/Jackson.
application/        -> Casos de uso (o "o que o sistema faz"). Interfaces de Porta (Repository, Publisher).
infrastructure/     -> Implementação das portas: JPA, RabbitMQ, Redis, clientes HTTP.
interfaces/web/      -> Controllers, DTOs de entrada/saída, tratamento de exceção HTTP.
```

### Por que não um "MVC simples" como Flask?
No Flask, é comum a rota chamar direto o SQLAlchemy. Funciona para apps pequenos, mas mistura
"regra de negócio" com "como eu guardo isso". Clean Architecture força a pergunta: **se eu trocar
Postgres por outra coisa amanhã, quanto código de negócio eu preciso tocar?** Resposta esperada aqui: zero,
fora da pasta `infrastructure/`.

### Mapeamento Flask/FastAPI/React → Java/Spring (pra você não traduzir "no escuro")

| Python / React | Java / Spring | Observação |
|---|---|---|
| Blueprint / APIRouter | `@RestController` | Rota + método HTTP declarativo. |
| Pydantic model / Marshmallow | `record` (DTO) + Bean Validation (`@NotNull`, `@Positive`) | Java 21 `record` é imutável por padrão — igual um Pydantic `frozen=True`. |
| SQLAlchemy Model | `@Entity` (JPA) | Mas o `@Entity` fica em `infrastructure/`, não é o modelo de domínio. |
| SQLAlchemy Session/query manual | `JpaRepository<Entity, Id>` (Spring Data) | Spring gera a implementação em runtime a partir da interface. |
| Injeção manual / app factory | IoC container do Spring (`@Service`, `@Component`, `@Autowired`/construtor) | Você declara, o container resolve o grafo de dependências. |
| Celery task | `@RabbitListener` | Consumidor assíncrono orientado a evento, não a fila de tarefas agendadas. |
| Flask-Caching | `@Cacheable` + Redis | Anotação declarativa em cima do método. |
| Flask-JWT-Extended | Spring Security + filtro JWT customizado | Mais verboso, mas o pipeline de filtros é explícito e testável. |
| pytest | JUnit 5 | `@Test`, `@ParameterizedTest`. |
| pytest-mock / unittest.mock | Mockito | `@Mock`, `when(...).thenReturn(...)`. |
| Alembic | Flyway | Migração versionada, `V1__criar_tabela_evento.sql`. |
| .env / python-dotenv | `application.yml` + profiles (`dev`, `docker`, `test`) | |
| React component + hook | Angular component + service | Angular já vem com DI, parecido com o Spring. |
| Axios + interceptor | `HttpClient` + `HttpInterceptor` | Mesmo conceito, sintaxe diferente. |
| Context/Redux | Service Angular com `BehaviorSubject` (ou NgRx, se quiser desafio extra) | |

### Quando usar `interface`, `abstract class` ou `record` (regra prática, não dogma)

- **`interface`**: quando existe (ou vai existir) mais de uma implementação, **ou** quando você quer
  inverter a dependência entre camadas (ex: `application` define `EventoRepositoryPort`, `infrastructure`
  implementa com JPA — o domínio não sabe que Postgres existe). Também use para contratos que serão mockados em teste.
- **`abstract class`**: só quando há **comportamento compartilhado de verdade** entre poucas classes
  próximas (Template Method). Se a "classe abstrata" só tem métodos abstratos, ela deveria ser uma interface.
- **`record`** (Java 21): para DTOs, Value Objects e qualquer dado imutável — substitui boa parte do
  boilerplate de getters/equals/hashCode que você faria "na mão" em Python com `dataclass(frozen=True)`.
- **`sealed interface`**: para modelar um conjunto **fechado e conhecido** de estados (ex: `ReservationStatus`
  como sealed com `Pending`, `Confirmed`, `Cancelled`, `Expired`) — o compilador te obriga a tratar todos os
  casos num `switch`, evitando o "esqueci de tratar esse status" que em Python só apareceria em runtime.

---

## 5. Estrutura de pastas (proposta, por serviço)

```
reservation-service/
├── src/main/java/com/eventforge/reservation/
│   ├── domain/
│   │   ├── model/           # Reserva, Hold, ReservationStatus (sealed)
│   │   └── exception/       # DomainException e subtipos
│   ├── application/
│   │   ├── usecase/         # CriarHoldUseCase, ConfirmarReservaUseCase
│   │   └── port/
│   │       ├── in/          # interfaces que os controllers chamam
│   │       └── out/         # interfaces que infra implementa (Repository, Publisher)
│   ├── infrastructure/
│   │   ├── persistence/     # @Entity JPA + implementação do port de repositório
│   │   ├── messaging/       # publishers/listeners RabbitMQ, outbox/inbox
│   │   └── cache/           # implementação Redis (hold, lock, idempotency-key)
│   └── interfaces/web/
│       ├── controller/
│       ├── dto/
│       └── exceptionhandler/
├── src/test/java/...        # espelha a estrutura acima
├── src/main/resources/
│   ├── db/migration/        # Flyway
│   └── application.yml
└── Dockerfile
```

> A pasta `application/port/in` e `application/port/out` é o ponto que mais estranha quem vem do Flask:
> lá, "a função da rota" e "o que ela faz" costumam ser a mesma coisa. Aqui, separamos **o contrato**
> (interface) **do consumidor** (interface) **da implementação** (infra) — é o que permite trocar Postgres
> por outra coisa sem tocar em regra de negócio, e o que permite mockar em teste unitário sem subir banco nenhum.

---

## 6. Segurança

- Spring Security + JWT (access token curto + refresh token).
- Papéis: `ORGANIZER`, `CUSTOMER`, `ADMIN` (RBAC via `@PreAuthorize`).
- Decisão a discutir: validar JWT em cada serviço (mais resiliente, mais repetitivo) vs só no Gateway
  (mais simples, ponto único de falha) — registrar escolha no ADR.

## 7. Observabilidade

- Logs estruturados em JSON, com `correlationId`/`traceId` propagado entre os 3 serviços via header
  HTTP e via header de mensagem no RabbitMQ (para você conseguir seguir uma compra inteira nos logs).
- **Grafana + Loki** (agregação de logs) + **Prometheus + Micrometer** (métricas: latência, taxa de erro,
  profundidade de fila) — dashboards por serviço.
- `Spring Boot Actuator` para health checks (`/actuator/health`) e métricas expostas ao Prometheus.

## 8. Testes

Pirâmide, não achatamento:
1. **Unitário** (JUnit 5 + Mockito): regras de domínio e casos de uso, sem Spring context, sem banco.
2. **Integração** (Testcontainers): sobe Postgres/Mongo/RabbitMQ/Redis reais em container só para o teste —
   valida que a implementação de infra realmente conversa com a tecnologia escolhida.
3. **Contrato/API** (coleção Postman versionada, rodável via Newman): valida o comportamento HTTP observável.
4. **Ponta a ponta** (opcional, estágio avançado): fluxo completo de compra passando pelos 3 serviços via Docker Compose.

Regra: todo Pull Request que altera `application/` ou `domain/` precisa de teste unitário. Todo PR que
altera `infrastructure/` precisa de teste com Testcontainers.

## 9. Infraestrutura local

- Um `docker-compose.yml` na raiz do monorepo subindo: Postgres, Mongo, Redis, RabbitMQ (com painel de
  management), Grafana, Loki, Prometheus, os 3 serviços e o Angular.
- Cada serviço com `Dockerfile` multi-stage (build com Maven/Gradle, runtime só com o JAR).
- Profiles Spring: `dev` (tudo local sem Docker), `docker` (nomes de host = nome do serviço no compose), `test` (Testcontainers).

## 10. Frontend — Angular

- Angular LTS, organizado por *feature modules* (`catalog/`, `checkout/`, `account/`), não por tipo de arquivo.
- `HttpInterceptor` para anexar JWT e tratar 401/refresh.
- Sobre **microfrontend**: só faz sentido quando times diferentes precisam **deployar independentemente**
  partes da UI. Para este projeto (uma pessoa aprendendo), microfrontend real é *over-engineering* —
  proposta: comece com Angular monolítico modular; se depois de dominar o básico você quiser o desafio
  extra de Module Federation, isso vira um item explícito no roadmap da seção 12, não um requisito do MVP.

## 11. Gestão do projeto

- Board Jira/Kanban com colunas: `Backlog → Em andamento → Em revisão (com você validando) → Testado → Feito`.
- Cada card (story) só fecha com **Definição de Pronto**: código + teste unitário + teste de integração
  (quando aplicável) + coleção Postman atualizada + Swagger atualizado + log estruturado no fluxo.
- Conventional Commits (`feat:`, `fix:`, `refactor:`) para manter o histórico legível.

## 12. Roadmap incremental sugerido

1. **Fase 0** — `event-catalog-service` sozinho: domínio + CRUD + Postgres + Flyway + testes. Sem mensageria ainda.
2. **Fase 1** — `reservation-service`: hold em Redis com TTL, comunicação síncrona com o catálogo.
3. **Fase 2** — RabbitMQ entra: eventos de hold criado/expirado, padrão Outbox no catálogo.
4. **Fase 3** — `sales-simulation-service` com Mongo, fluxo completo de pagamento simulado, padrão Inbox no reservation.
5. **Fase 4** — Segurança (JWT) fim a fim.
6. **Fase 5** — Observabilidade (Grafana/Loki/Prometheus) + Angular consumindo tudo.
7. **Fase 6 (desafio extra, opcional)** — API Gateway, Resilience4j (circuit breaker), rate limiting, Module Federation no front, Virtual Threads (Java 21) no `reservation-service` para lidar com pico de concorrência no checkout.

## 13. Stack técnica confirmada

```
Backend:  Java 21 · Spring Boot 3 · Maven (mesma ferramenta nos 3 serviços)
          Spring Web · Spring Validation · Spring Data JPA · Spring Data MongoDB
          Spring AMQP (RabbitMQ) · Spring Cache + Redis · Spring Security + JWT
          Flyway · springdoc-openapi (Swagger) · JUnit 5 · Mockito · Testcontainers
Infra:    Docker Compose · PostgreSQL · MongoDB · Redis · RabbitMQ · Grafana + Loki + Prometheus
Frontend: Angular (LTS)
Testes de API: Postman/Newman
Gestão: Jira/Kanban
```

## 14. Registro de Decisões de Arquitetura (ADR) — a criar

Todo desvio ou confirmação de uma escolha feita aqui vira um arquivo em `/docs/adr/000X-titulo.md` com:
**Contexto → Decisão → Consequências → Alternativas consideradas**. Isso é o que separa "copiei um
tutorial" de "sei por que meu sistema é assim" — e é exatamente o que você pediu no início.

---

### Próximo passo sugerido
Validar/ajustar este documento (nomes, escopo, se o Mongo se justifica pra você) e então começarmos pela
**Fase 0**: modelar o domínio de `event-catalog-service` — só as classes de domínio, sem Spring, sem banco,
discutindo cada decisão de `record` vs `class` vs `sealed interface` antes de tocar em qualquer anotação.
