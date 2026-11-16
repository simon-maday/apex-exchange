# APEX — A Learning Journey Toward an Exchange-Grade Matching Engine

**APEX** is an exchange-engine project built incrementally with Java. Its goal is to explore and implement the engineering foundations of a reliable trading system: deterministic order matching, event sourcing, durable persistence, crash recovery, concurrency control, and eventually distributed production infrastructure.

This is both a serious software-engineering project and a practical exploration of advanced Java and JVM concepts. The implementation is developed in small, testable steps, with an emphasis on understanding *why* each design exists—not just making code compile.

> **Project status: actively under development.**
>
> APEX is not a production-ready exchange and must not be used to process real orders or funds. Some lifecycle, financial-risk, operational, and distributed-systems concerns remain on the roadmap.

## Goals

- Build a correct, deterministic price-time-priority matching engine.
- Keep the matching core independent of application frameworks and external infrastructure.
- Make accepted state transitions recoverable from a durable event log.
- Explore Java concurrency, the Java Memory Model, NIO, file locking, failure handling, and JVM behavior through real implementation work.
- Evolve the engine toward a production-grade architecture without hiding complexity behind frameworks.

## Current Capabilities

The current implementation includes:

- **Limit-order matching:** price-time-priority order-book behavior, partial fills, and maker-price execution.
- **Single-writer processing:** producers submit commands to a bounded queue; one dedicated matching thread owns mutable matching state.
- **Event-driven state changes:** matching decisions are represented as ordered domain events and applied separately from planning.
- **Deterministic recovery:** recovery replays persisted events rather than rerunning the matching algorithm.
- **Batched event persistence:** events are written in framed batches with a format version and CRC32 checksum.
- **Crash recovery:** incomplete trailing frames can be detected and repaired; corruption in a complete frame is treated as a fatal error rather than silently skipped.
- **Idempotent order retries:** stable order IDs prevent a retried request from creating a second accepted order after recovery.
- **Single-process ownership:** a separate Java NIO file lock prevents cooperating processes from opening the same event log concurrently.
- **Failure-oriented tests:** integration tests exercise injected failures, process termination, torn writes, recovery, and cross-process locking.

The feature list describes the current development state; it is not a claim of exchange-grade operational readiness.

## Architecture at a Glance

```text
                    Order producers
                           |
                           v
                  Bounded command queue
                           |
                           v
                 Single matching thread
                           |
             +-------------+-------------+
             |                           |
             v                           v
       Matching planner            Event batch factory
             |                           |
             +-------------+-------------+
                           |
                           v
                    Durable event log
                           |
                           v
                      Event applier
                           |
              +------------+------------+
              |                         |
              v                         v
          Order book                Trade history
              ^
              |
       Recovery replays
       persisted events
```

The key separation is:

- **Matching planner:** decides which trades should occur; it does not mutate live order-book state.
- **Event batch factory:** turns a command and its planned effects into an ordered event batch.
- **Event log:** persists the batch before the in-memory state is changed.
- **Event applier:** applies events to the in-memory projections; it does not match orders or append to the log.
- **Recovery:** rebuilds projections by replaying events in sequence.

This separation is intended to make the state transition auditable and recovery deterministic.

## Important Design Decisions

### Single-writer ownership

Only the matching thread mutates the order book and trade history. This avoids requiring fine-grained locking throughout the matching path and gives the engine one clear ordering point for commands.

A bounded queue provides an initial backpressure boundary. Admission, shutdown, and failure races are still being hardened.

### Persist before applying

For a command that produces events, APEX writes and forces the event batch before applying it to in-memory state. If the process fails after the durable append but before the in-memory application completes, the next process reconstructs the state by replaying the log.

A failure after the durable append is therefore an ambiguous outcome from the caller's perspective: the command may have committed even if the caller did not receive its acknowledgement. Stable order IDs and idempotent retry handling address this case.

### Recovery does not rerun matching

Recovery applies the historical events exactly as recorded. It does not invoke the current matching algorithm to decide what should have happened. This prevents a future algorithm change from rewriting the meaning of historical commands.

### One owner per event log

A separate lock file is used for process ownership. The lock is acquired before recovery and held until the event log is closed. The lock file is deliberately separate from the data file.

File locks coordinate cooperating processes on supported local filesystems; they are not a substitute for a distributed consensus or fencing protocol.

## Technology

- Java (the codebase uses modern Java language and library features; use the JDK version configured in `pom.xml`)
- Maven
- Java NIO (`FileChannel`, `FileLock`)
- `BlockingQueue`, `CompletableFuture`, and Java concurrency primitives
- JUnit 5 (tests)

Frameworks and infrastructure such as Spring Boot, PostgreSQL, Kafka, Redis, Docker, and Kubernetes are intentionally introduced later, when the core engine's boundaries and requirements justify them.

## Getting Started

### Prerequisites

- A JDK matching the Java version configured in `pom.xml`
- Maven

Check your environment:

```bash
java -version
mvn -version
```

### Run the test suite

From the project root:

```bash
mvn test
```

Build the project:

```bash
mvn package
```

The current focus is the engine and its automated tests. A stable end-user application/CLI entry point is not yet part of the project.

## Testing Strategy

Correctness and failure behavior are first-class concerns. The test suite includes scenarios such as:

- Price-time-priority matching and partial execution.
- Event encoding/decoding and invalid-frame rejection.
- Sequence continuity across event batches.
- Recovery from incomplete trailing headers and payloads.
- Rejection of complete-frame corruption.
- Idempotent retries across process restarts.
- Failure injection before and after durable append and event application.
- Abrupt child-process termination after a committed append.
- Same-process and cross-process event-log ownership.
- Worker interruption and command-future completion.

Tests are not proof of production readiness. Additional property-based, stress, performance, fault-injection, and operational testing is still required.

## Roadmap

The project is being developed through six major phases.

### 1. Core Java Matching Engine — Current Phase

- Matching and order-book correctness
- Event sourcing and durable event log
- Deterministic recovery and idempotency
- Single-writer concurrency and backpressure
- Lifecycle and failure propagation
- JVM investigation, profiling, and benchmarking

### 2. Financial Correctness and Risk

- Cancellation and modification
- Market orders and execution policies
- Decimal precision and instrument-specific rules
- Risk checks and balance reservations
- Double-entry ledger, fees, and limits

### 3. Application Layer and Relational Persistence

- Spring Boot application boundary
- REST and WebSocket APIs
- PostgreSQL and Flyway
- Accounts, configuration, ledger, and read projections
- Integration testing with Testcontainers

### 4. Distributed Architecture

- Event distribution and Kafka integration
- Reliable outbox/relay design
- Market-data distribution
- Carefully chosen caching and asynchronous boundaries
- Distributed consistency and failure analysis

### 5. Production Engineering

- Metrics, logs, tracing, and alerting
- Load, stress, and soak testing
- Profiling and JVM tuning
- Containerization and deployment automation
- Security, backup, disaster recovery, and operational procedures

### 6. Exchange-Grade Architecture

- Symbol partitioning and scaling strategy
- Replication and failover
- Snapshots, log compaction, and recovery-time objectives
- Market-data architecture
- Operational risk controls and recovery exercises

The matching core will remain independent of Spring Boot and external messaging systems. Infrastructure will be added around the core rather than placed inside the matching algorithm.

## Engineering Principles

1. **Correctness before throughput.** Optimize only after behavior is specified and measured.
2. **Explicit state transitions.** Important changes should be observable, ordered, and testable.
3. **Deterministic recovery.** Replaying history must reconstruct the same state.
4. **Failure is a design case.** Test crashes, partial writes, retries, and shutdown—not only the happy path.
5. **Measure performance.** Use appropriate benchmarks and profiling rather than assumptions.
6. **Keep boundaries honest.** Do not introduce infrastructure or abstractions without a concrete requirement.

## Contributing

This is currently an individual learning and engineering project. Questions, constructive feedback, and technical discussions are welcome. Before proposing a large change, please open an issue describing the problem and the intended design.

Any contribution should include tests for the behavior it changes. Please do not submit credentials, private keys, real customer data, or exchange secrets.

## License

APEX is licensed under the [MIT License](LICENSE).

The MIT License permits use, modification, distribution, and commercial use, provided the required copyright and license notices are retained. It does not require a public GitHub mention or a link to the original repository.

## Disclaimer

APEX is an educational and engineering project under active development. It is provided "as is", without warranties. It is not financial, investment, or trading advice, and it is not suitable for handling real funds or production trading.