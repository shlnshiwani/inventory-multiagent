# ai — Inventory Multi-Agent System

## Quick facts (never re-read files for these)

| Item | Value |
|------|-------|
| Build | Java 21 · Maven · `mvn package` → `target/inventory-multiagent-1.0.0.jar` (24 MB) |
| Run | `export GEMINI_API_KEY=<key>` then `mvn spring-boot:run` |
| LLM | Gemini 2.0 Flash (`gemini-2.0-flash`) via Google AI API |
| DB | H2 in-memory · JDBC URL `jdbc:h2:mem:inventorydb` · console at `/h2-console` |
| Base package | `com.multiagent` |
| Main class | `com.multiagent.MultiAgentApplication` |

## Dependency versions (pinned)

| Library | Version | GroupId / ArtifactId |
|---------|---------|----------------------|
| Spring Boot parent | `3.5.13` | `org.springframework.boot:spring-boot-starter-parent` |
| Spring Data JPA | managed by SB | `org.springframework.boot:spring-boot-starter-data-jpa` |
| LangChain4j core | `1.13.0` | `dev.langchain4j:langchain4j` (via BOM) |
| LangChain4j SB starter | `1.13.0-beta23` | `dev.langchain4j:langchain4j-spring-boot-starter` |
| LangChain4j Gemini | `1.13.0` | `dev.langchain4j:langchain4j-google-ai-gemini` (via BOM) |
| LangChain4j Gemini SB starter | `1.13.0-beta23` | `dev.langchain4j:langchain4j-google-ai-gemini-spring-boot-starter` |
| LangGraph4j core | `1.8.11` | `org.bsc.langgraph4j:langgraph4j-core` |
| LangGraph4j LangChain4j | `1.8.11` | `org.bsc.langgraph4j:langgraph4j-langchain4j` |
| Resilience4j SB3 | `2.2.0` | `io.github.resilience4j:resilience4j-spring-boot3` (**pinned** — not in SB BOM) |
| H2 | managed by SB | `com.h2database:h2` |

## File map

```
ai/
├── pom.xml
├── src/main/resources/
│   ├── application.properties          H2 · JPA/Hibernate · Gemini · Resilience4j config
│   ├── schema.sql                      Empty — Hibernate owns DDL (create-drop)
│   ├── data.sql                        Seed — 8 sample products (plain INSERT, runs after JPA)
│   └── logback-spring.xml              com.multiagent=INFO, rest=WARN
└── src/main/java/com/multiagent/
    ├── MultiAgentApplication.java      @SpringBootApplication
    ├── config/
    │   └── LlmConfig.java              @Bean ChatMemoryProvider (20-message window)
    ├── model/                          Lightweight domain records (DTOs)
    │   ├── Product.java                record(id,name,category,price,quantity)
    │   ├── Report.java                 record(id,title,content,createdAt)
    │   └── AgentExecution.java         record(id,sessionId,agentName,iteration,input,output,executedAt)
    ├── entity/                         JPA @Entity classes (Hibernate maps these to tables)
    │   ├── ProductEntity.java          @Entity @Table(products)
    │   ├── ReportEntity.java           @Entity @Table(reports) — @CreationTimestamp
    │   └── AgentExecutionEntity.java   @Entity @Table(agent_executions) — @Lob input/output
    ├── repository/                     Spring Data JPA interfaces (no implementation needed)
    │   ├── ProductRepository.java      JpaRepository<ProductEntity,Integer> + custom queries
    │   ├── ReportRepository.java       JpaRepository<ReportEntity,Integer>
    │   └── AgentExecutionJpaRepository.java  JpaRepository<AgentExecutionEntity,Integer>
    ├── db/                             Facade layer — translates entities ↔ records
    │   ├── InventoryRepository.java    @Repository — wraps ProductRepository + ReportRepository
    │   └── AgentExecutionRepository.java  @Repository — wraps AgentExecutionJpaRepository
    ├── service/
    │   └── AgentExecutionService.java  @Service — log(), getSharedContext(), getSummary()
    ├── tools/
    │   ├── InventoryTools.java         @Component — Tools 1,2,3
    │   ├── AnalyticsTools.java         @Component — Tools 4,5
    │   └── ReportTools.java            @Component — Tools 6,7
    ├── agents/
    │   ├── InventoryAgent.java         @Service — process(task,sessionId,iteration)
    │   ├── AnalyticsAgent.java         @Service — process(task,sessionId,iteration)
    │   └── ReportAgent.java            @Service — process(task,sessionId,iteration)
    ├── graph/
    │   ├── WorkflowState.java          extends AgentState — sessionId + task + history + ...
    │   ├── SupervisorNode.java         @Component implements NodeAction<WorkflowState>
    │   └── MultiAgentGraph.java        @Component — builds graph, generates sessionId per run
    └── runner/
        └── DemoRunner.java             CommandLineRunner — runs demo + prints DB execution log
```

## Database schema (3 tables — DDL owned by Hibernate)

Schema is generated from `@Entity` classes. `spring.jpa.hibernate.ddl-auto=create-drop`.

```
products            → ProductEntity.java
reports             → ReportEntity.java
agent_executions    → AgentExecutionEntity.java
```

| Table | Key columns |
|-------|-------------|
| `products` | `id` PK auto · `name` · `category` · `price` · `quantity` |
| `reports` | `id` PK auto · `title` · `content` @Lob · `created_at` @CreationTimestamp |
| `agent_executions` | `id` PK auto · `session_id` (idx) · `agent_name` (idx) · `iteration` · `input` @Lob · `output` @Lob · `executed_at` @CreationTimestamp |

Seed data (8 rows, `data.sql`): Laptop Pro 15, Wireless Mouse, Office Chair, Desk Lamp,
Java 21 Book, USB-C Hub, Standing Desk, Noise-Cancel Headphones.

## JPA layer overview

```
@Entity classes         Spring Data JPA repos              Facade repos (db/)
──────────────          ───────────────────────            ──────────────────
ProductEntity      ←──  ProductRepository                  InventoryRepository
ReportEntity       ←──  ReportRepository              ───► (used by tools)
AgentExecutionEntity ←── AgentExecutionJpaRepository       AgentExecutionRepository
                                                      ───► (used by AgentExecutionService)
```

- **Entity classes** live in `entity/` — Hibernate maps them to tables.
- **JPA repos** live in `repository/` — Spring Data generates all queries from method names.
  No SQL, no JPQL needed (except `@Query` for the `SUM` aggregate in `ProductRepository`).
- **Facade repos** in `db/` convert entities → records and are the only thing tools/services see.

### application.properties JPA settings

```properties
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.defer-datasource-initialization=true   # tables first, then data.sql
spring.sql.init.mode=always                        # run data.sql seed
```

> `spring.jpa.defer-datasource-initialization=true` is **required** so Hibernate
> creates tables before Spring runs `data.sql`.

## Agent execution tracking & inter-agent sharing

### How it works

Every `process(task, sessionId, iteration)` call on any agent:
1. Calls the Gemini LLM via the `AiServices` proxy
2. Immediately saves `(sessionId, agentName, iteration, input, output)` to `agent_executions`
3. Returns the output

### Session ID

`MultiAgentGraph.run(task)` generates a `UUID.randomUUID()` at the start of each
workflow. It is stored in `WorkflowState.sessionId` and passed to every agent call.
This groups all executions from one workflow run.

### Inter-agent output sharing

```java
// In AgentExecutionService — any agent can call this:
String sharedContext = executionService.getSharedContext(sessionId);
// Returns formatted text of ALL prior agent outputs in this session (from DB)

String latestAnalytics = executionService.getLatestOutput(sessionId, "AnalyticsAgent");
// Returns the most recent output from a specific agent
```

The **Report Agent** is wired to use `getSharedContext(sessionId)` as its prompt
context (sourced from DB), so it synthesises ALL prior agents' logged outputs —
not just the in-memory state — when writing its final report.

### Useful SQL to inspect a run

```sql
-- All executions for a session
SELECT agent_name, iteration, LENGTH(input) input_len, LENGTH(output) output_len, executed_at
FROM agent_executions
WHERE session_id = '<uuid>'
ORDER BY executed_at;

-- Full input/output for a specific agent
SELECT input, output FROM agent_executions
WHERE session_id = '<uuid>' AND agent_name = 'InventoryAgent';

-- All sessions summary
SELECT session_id, COUNT(*) calls, MIN(executed_at) started, MAX(executed_at) ended
FROM agent_executions GROUP BY session_id ORDER BY started DESC;
```

## Tools (7 total)

| # | Method | Class | Description |
|---|--------|-------|-------------|
| T1 | `addProduct(name,category,price,qty)` | `InventoryTools` | INSERT product row |
| T2 | `searchProducts(keyword)` | `InventoryTools` | LIKE search on name + category |
| T3 | `getLowStockProducts(threshold)` | `InventoryTools` | quantity ≤ threshold |
| T4 | `calculateInventoryValue()` | `AnalyticsTools` | SUM(price × quantity) |
| T5 | `getCategoryBreakdown()` | `AnalyticsTools` | per-category count/qty/value |
| T6 | `saveReport(title,content)` | `ReportTools` | INSERT report row |
| T7 | `listReports()` | `ReportTools` | SELECT all report titles |

## Agents

| Agent | Class constant | Tools | process() signature |
|-------|---------------|-------|---------------------|
| Inventory | `InventoryAgent.NAME` | T1 T2 T3 | `process(task, sessionId, iteration)` |
| Analytics | `AnalyticsAgent.NAME` | T3 T4 T5 | `process(task, sessionId, iteration)` |
| Report | `ReportAgent.NAME` | T6 T7 | `process(task, sessionId, iteration)` |

Each agent:
- `@Service` with inner `AiServices` interface (`@SystemMessage` + `@UserMessage`)
- Builds `AiServices` proxy in `@PostConstruct`: `AiServices.builder().chatModel(llm).tools(...).build()`
- Annotates `process()` with `@CircuitBreaker(name="gemini")` + `@Retry(name="gemini")`
- Has `fallback(task, sessionId, iteration, Throwable)` — also logs to DB
- `AgentExecutionService` injected for input/output logging

## AgentExecutionService API

```java
// Log one execution (called inside each agent)
AgentExecution log(String sessionId, String agentName, int iteration, String input, String output)

// Get all executions for a session (for display / audit)
List<AgentExecution> getExecutions(String sessionId)

// Get executions for one agent in a session
List<AgentExecution> getExecutionsByAgent(String sessionId, String agentName)

// Get the latest output from a specific agent (for targeted sharing)
String getLatestOutput(String sessionId, String agentName)

// Get all outputs formatted as a single context string (used by ReportAgent)
String getSharedContext(String sessionId)

// One-line summary per execution (for console display)
String getSummary(String sessionId)
```

## LangGraph4j state graph

```
START → supervisor ──[inventory]→ InventoryAgent ──┐
              │                                      ├→ supervisor → …
              │──[analytics]→ AnalyticsAgent ────────┤
              │──[report]───→ ReportAgent ────────────┘
              └──[END]──────────────────────────────→ END
```

**WorkflowState** fields & channels:

| Field | Channel | Default | Purpose |
|-------|---------|---------|---------|
| `sessionId` | `Channels.base(()→"")` | `""` | UUID per run — links DB rows |
| `task` | `Channels.base(()→"")` | `""` | original user task |
| `route` | `Channels.base(()→ROUTE_INVENTORY)` | `"inventory"` | next agent |
| `agentOutput` | `Channels.base(()→"")` | `""` | last agent's output |
| `history` | `Channels.appender(ArrayList::new)` | `[]` | in-memory accumulation |
| `iteration` | `Channels.base(()→0)` | `0` | supervisor decision count |
| `done` | `Channels.base(()→false)` | `false` | termination flag |

## LangGraph4j 1.8.11 API (correct usage)

| Wrong | Correct |
|-------|---------|
| `addNode(name, nodeAction)` sync | `addNode(name, node_async(nodeAction))` |
| `addConditionalEdges(from, fn)` | `addConditionalEdges(from, edge_async(fn), Map<key,dest>)` |
| `Channels.lastValue()` | `Channels.base(() -> defaultValue)` |
| `compiled.invoke().get()` | `compiled.invoke(Map)` → `Optional<State>` (synchronous) |
| groupId `io.github.bsorrentino` | correct groupId: `org.bsc.langgraph4j` |

## LangChain4j 1.x API (vs 0.x)

| Old (0.x) | New (1.x) |
|-----------|-----------|
| `ChatLanguageModel` | `ChatModel` (`dev.langchain4j.model.chat.ChatModel`) |
| `.chatLanguageModel(model)` | `.chatModel(model)` on AiServices builder |
| `model.generate(String)` | `model.chat(String)` → returns `String` |

## Resilience4j (application.properties)

Circuit breaker `gemini`: 50% failure threshold, sliding window 5, open wait 30s, half-open 2 calls.  
Retry `gemini`: max 3 attempts, 2s wait, exponential backoff ×2.  
`resilience4j-spring-boot3` is NOT managed by Spring Boot BOM — always pin version explicitly.  
`spring-boot-starter-aop` is required for annotation interception.

## How to add a new tool

1. Add `@Tool` method to appropriate `*Tools.java` (use `@P` for parameter docs)
2. Add DB method to `InventoryRepository` if it reads/writes the DB
3. Register the tool in the agent's `@PostConstruct` `.tools(...)` call
4. Update the Tool table in this file

## How to add a new agent

1. Create `agents/NewAgent.java` as `@Service` — same pattern as existing agents
2. `process(String task, String sessionId, int iteration)` with `@CircuitBreaker`/`@Retry`
3. Inject and call `executionService.log(sessionId, NAME, iteration, task, output)` inside `process()`
4. In `MultiAgentGraph.buildGraph()`:
   - Add `node_async(state → newAgent.process(..., state.sessionId(), state.iteration()))` 
   - `.addNode("new-agent", newNode)`
   - `.addEdge("new-agent", "supervisor")`
   - Add routing key to `WorkflowState` constants + `routingMap`
5. Update supervisor prompt in `SupervisorNode` to list the new agent
6. Update tables in this file

## Status

| Feature | Status |
|---------|--------|
| Spring Boot 3.5 scaffold | ✅ |
| H2 schema (Hibernate DDL create-drop) | ✅ |
| Seed data (data.sql, deferred) | ✅ |
| ProductEntity / ReportEntity / AgentExecutionEntity | ✅ |
| ProductRepository / ReportRepository / AgentExecutionJpaRepository | ✅ |
| InventoryRepository facade (JPA-backed) | ✅ |
| AgentExecutionRepository facade (JPA-backed) | ✅ |
| AgentExecutionService (log + share) | ✅ |
| InventoryTools (T1 T2 T3) | ✅ |
| AnalyticsTools (T4 T5) | ✅ |
| ReportTools (T6 T7) | ✅ |
| InventoryAgent (with DB logging) | ✅ |
| AnalyticsAgent (with DB logging) | ✅ |
| ReportAgent (with DB logging + shared context) | ✅ |
| WorkflowState (sessionId channel) | ✅ |
| SupervisorNode | ✅ |
| MultiAgentGraph (sessionId per run) | ✅ |
| DemoRunner (execution log display) | ✅ |
| Resilience4j @CircuitBreaker + @Retry | ✅ |
| Gemini auto-config via SB starter | ✅ |

## Session State

- Status: idle
- Last build: `mvn package` → success, 50 MB fat jar (Hibernate adds ~26 MB)
- No interrupted work
