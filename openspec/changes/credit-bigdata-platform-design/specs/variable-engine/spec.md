## ADDED Requirements

### Requirement: Four-Layer Variable Model

System SHALL organize variables into 4 layers: Layer 0 (Input, from request, ~50 vars, 0ms), Layer 1 (External, from API calls, ~200 vars, 200-800ms), Layer 2 (Cached, from Redis/HBase via Flink pre-computation, ~1500 vars, 5-20ms), Layer 3 (Derived, computed from L0-L2 via Aviator expressions, ~300 vars, <1ms).

#### Scenario: Variable resolution by layer

WHEN a variable is requested during decision execution
THEN the system SHALL determine its layer (L0, L1, L2, or L3) and resolve it using the appropriate source
AND Layer 0 variables SHALL be read from the request context with 0ms overhead
AND Layer 1 variables SHALL be fetched from external APIs
AND Layer 2 variables SHALL be fetched from Redis/HBase cache
AND Layer 3 variables SHALL be computed from L0-L2 values via Aviator expressions.

---

### Requirement: Variable Registry

System SHALL maintain a variable registry in MySQL storing var_id, name, category, layer, source, data_type, expression, dependencies (JSON), version, status. System SHALL support CRUD operations and versioning.

#### Scenario: Variable registration and retrieval

WHEN a new variable is registered with its metadata (name, category, layer, source, data_type, expression, dependencies)
THEN the system SHALL store it in the MySQL registry with a unique var_id and version
AND the variable SHALL be retrievable by name or var_id.

#### Scenario: Variable versioning

WHEN a variable definition is updated
THEN the system SHALL create a new version while preserving the previous version
AND the version history SHALL be queryable for audit purposes.

---

### Requirement: Lazy Loading

System SHALL implement lazy variable resolution - variables SHALL only be fetched when referenced by a rule or node.

#### Scenario: On-demand variable resolution

WHEN a rule or node references a variable that has not yet been resolved
THEN the system SHALL fetch the variable value on demand
AND variables that are never referenced during a decision flow SHALL NOT be fetched.

---

### Requirement: Prefetch Optimization

System SHALL statically analyze DAG at compile time to collect all potentially needed variables. At request time, variables SHALL be prefetched in parallel by layer: Layer 1 external APIs called concurrently (total time = max), Layer 2 Redis/HBase batch-fetched.

#### Scenario: Compile-time variable collection

WHEN a DAG is compiled
THEN the system SHALL statically analyze all nodes and edges to collect the complete set of potentially needed variables
AND the collected set SHALL be stored with the CompiledDAG for runtime prefetch.

#### Scenario: Parallel prefetch by layer at request time

WHEN a decision flow request begins execution
THEN the system SHALL prefetch all needed Layer 1 variables by calling external APIs concurrently so total latency equals the maximum individual call time
AND Layer 2 variables SHALL be batch-fetched from Redis/HBase in a single round trip.

---

### Requirement: Dependency Analysis

System SHALL track derived variable dependencies automatically. When a derived variable's dependencies change, the system SHALL flag affected rules/scorecards.

#### Scenario: Automatic dependency tracking

WHEN a derived variable (Layer 3) is defined with an Aviator expression referencing other variables
THEN the system SHALL automatically parse the expression and record all dependent variable references.

#### Scenario: Impact analysis on dependency change

WHEN a variable that other derived variables depend on is modified
THEN the system SHALL identify and flag all rules, scorecards, and derived variables affected by the change.

---

### Requirement: Null Handling

System SHALL provide configurable default values for variables that cannot be resolved. Variable resolution failure SHALL NOT crash the decision flow.

#### Scenario: Variable resolution failure with default value

WHEN a variable cannot be resolved (e.g., external API timeout, cache miss)
THEN the system SHALL substitute a configurable default value for that variable
AND the decision flow SHALL continue execution without crashing.

#### Scenario: Missing default value configuration

WHEN a variable cannot be resolved and no default value is configured
THEN the system SHALL use null as the value
AND downstream rules SHALL handle the null per the null safety requirement.
