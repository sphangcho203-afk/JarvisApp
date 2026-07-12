# Phase 9.0 — Ten-Node Cortex Mesh

## Mission

Turn Jarvis from a single fragile cloud endpoint into a health-aware distributed inference layer without restoring localhost or Termux brain dependencies.

## Provider Layout

- `GEMINI 01` through `GEMINI 06`
- `GROQ 01` through `GROQ 04`

Each node stores a provider, model ID, encrypted API key, enable state, priority, success and failure counts, failure streak, last latency, last use time, cooldown time, last HTTP status, and redacted error text.

## Request Flow

```text
Voice or text input
      ↓
Android deterministic command router
      ↓ when no local command applies
Task classifier
      ↓
Cortex node scoring
      ↓
Best healthy node
      ↓ failure
Cooldown and health update
      ↓
Next eligible node
```

## Scoring

```text
score = stability × (
    0.38 × task_fit
  + 0.22 × reliability
  + 0.16 × latency_utility
  + 0.14 × freshness
  + 0.10 × priority
)
```

Reliability uses the posterior mean of a Beta(1,1) model:

```text
reliability = (successes + 1) / (successes + failures + 2)
```

Latency, freshness, and failure stability use bounded exponential curves. This prevents a single slow or failed request from permanently dominating node selection.

## Failure Policy

- `200–299`: success, clear failure streak and cooldown
- `401/403`: disable node until credentials are corrected
- `429`: cooldown using `Retry-After` when available
- `5xx`: temporary cooldown
- timeout/network failure: short cooldown and failover

## Security Boundary

The mesh is a resilience and specialization layer, not a mechanism for violating provider quotas or terms. Only credentials owned by the operator should be configured.
