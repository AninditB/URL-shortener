# System Design Document — ShortLink

A retrospective, whole-system explainer: what's actually built, how it fits together, and why. Unlike the per-stage plans in `docs_I/planning/` (local only, not in the repo), this document describes the system as it exists today, not as a build sequence.

Organized as a tree, grouped by category, instead of one flat file:

```
docs/
├── 00-index.md                          (this file)
├── 01-overview.md
├── requirements/
│   ├── functional.md
│   └── non-functional.md
├── design/
│   ├── high-level.md
│   └── low-level.md
├── implementation/
│   ├── code.md
│   ├── database.md
│   └── caching.md
├── not-yet-built.md
└── appendix/
    └── running-it-locally.md
```

## Contents

1. [Overview](01-overview.md)
2. Requirements
   - [Functional](requirements/functional.md)
   - [Non-Functional](requirements/non-functional.md)
3. Design
   - [High-Level Design](design/high-level.md)
   - [Low-Level Design](design/low-level.md)
4. Implementation
   - [Code](implementation/code.md)
   - [Database](implementation/database.md)
   - [Caching](implementation/caching.md)
5. [Not Yet Built](not-yet-built.md)
6. [Appendix: Running It Locally](appendix/running-it-locally.md)
