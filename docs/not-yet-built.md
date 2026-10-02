← [Index](00-index.md)

## Not Yet Built

Named plainly, no phase numbers — these are real gaps, not a to-do list ordering:

- Observability: no metrics, structured tracing, or dashboards (Micrometer/Prometheus/Grafana/OpenTelemetry).
- Resilience: no circuit breakers, bulkheads, or graceful degradation — a Redis or Postgres outage fails the dependent feature outright rather than degrading.
- Containerized deployment / CI-CD: no Dockerfile for the app itself, no automated build→test→deploy pipeline.
- High availability: single instance of the app, Redis, Postgres, and Kafka each — no replicas, no failover.
- Kubernetes / horizontal scaling: the app isn't stateless in a way that survives multiple writers cleanly yet (see the Base62/auto-increment note in [Code](implementation/code.md)).
- Disaster recovery: no defined RPO/RTO, backup, or restore procedure.

See `docs_I/planning/ROADMAP.md` (local only, not in the repo) for the staged plan these gaps map to.
