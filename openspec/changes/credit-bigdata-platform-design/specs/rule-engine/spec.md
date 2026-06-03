## ADDED Requirements

### Requirement: JSON Rule Compilation

System SHALL compile rule definitions from JSON format into AST (Abstract Syntax Tree). Compiled rules SHALL be cached in Caffeine local cache. Compilation SHALL validate syntax, types, and detect circular dependencies.

#### Scenario: Successful rule compilation from JSON

WHEN a valid JSON rule definition is submitted for compilation
THEN the system SHALL parse the JSON into an AST representation
AND the compiled AST SHALL be stored in Caffeine local cache keyed by rule ID and version.

#### Scenario: Invalid rule syntax rejection

WHEN a JSON rule definition contains invalid syntax or type mismatches
THEN the system SHALL reject the compilation with a descriptive error message indicating the exact validation failure.

#### Scenario: Circular dependency detection

WHEN a rule definition references itself or forms a circular dependency chain
THEN the system SHALL detect the cycle during compilation and SHALL reject the rule with a clear circular dependency error.

---

### Requirement: AST Interpreter Execution

System SHALL execute compiled AST rules by traversing the tree and evaluating conditions. Engine overhead per decision SHALL be <50ms for up to 500 rules. Execution SHALL be thread-safe with per-request isolated ExecutionContext.

#### Scenario: AST rule execution within latency target

WHEN a compiled AST rule with up to 500 rules is executed against input data
THEN the engine SHALL complete the decision within 50ms
AND each request SHALL use an isolated ExecutionContext to ensure thread safety.

#### Scenario: Concurrent request isolation

WHEN multiple requests execute rules concurrently
THEN each request SHALL operate on its own ExecutionContext
AND no shared mutable state SHALL exist between concurrent executions.

---

### Requirement: Five Rule Types

System SHALL support 5 rule types: (a) Condition Rule - IF/THEN with AND/OR/NOT operators and comparison ops (GT/LT/GTE/LTE/EQ/NEQ/BETWEEN/IN), (b) Scorecard - multi-characteristic binning with weighted scoring, (c) Decision Table - 2D table with columns as conditions and rows as rules, (d) Decision Tree - nested condition tree, (e) Rule Set - container of condition rules with hit policy (FIRST_HIT/ALL/PRIORITY).

#### Scenario: Condition rule with logical and comparison operators

WHEN a Condition Rule is defined with AND/OR/NOT operators and comparison operations (GT, LT, GTE, LTE, EQ, NEQ, BETWEEN, IN)
THEN the engine SHALL correctly evaluate the boolean expression tree and return the THEN action when conditions are satisfied.

#### Scenario: Decision table evaluation

WHEN a Decision Table rule is defined with condition columns and rule rows
THEN the engine SHALL evaluate each row against the input and return matching row results.

#### Scenario: Decision tree traversal

WHEN a Decision Tree rule with nested condition nodes is executed
THEN the engine SHALL traverse the tree depth-first and return the leaf node result matching the input.

#### Scenario: Rule set with hit policy

WHEN a Rule Set is configured with FIRST_HIT, ALL, or PRIORITY hit policy
THEN the engine SHALL execute contained condition rules according to the specified hit policy and return the appropriate matched results.

---

### Requirement: Expression Engine (Aviator)

System SHALL use Aviator for condition evaluation. SHALL support custom functions (between, in, daysBetween, isInProvince, overdueCount). Expressions SHALL be compiled once and cached.

#### Scenario: Custom function invocation

WHEN a rule references a custom function such as between, in, daysBetween, isInProvince, or overdueCount
THEN the Aviator expression engine SHALL evaluate the function correctly against the provided arguments.

#### Scenario: Expression compilation and caching

WHEN an Aviator expression is encountered for the first time
THEN the system SHALL compile it and cache the compiled expression for subsequent reuse
AND subsequent evaluations SHALL use the cached compiled form without recompilation.

---

### Requirement: Hot Reload

System SHALL support rule hot-reload without service restart. Changes SHALL be broadcast via RocketMQ to all engine nodes. CopyOnWrite semantics: in-flight requests continue with old rules, new requests use new rules. Reload latency SHALL be <5 seconds.

#### Scenario: Rule update propagation across nodes

WHEN a rule is updated and published
THEN the system SHALL broadcast the change via RocketMQ to all engine nodes
AND each node SHALL reload the rule within 5 seconds without service restart.

#### Scenario: CopyOnWrite semantics during reload

WHEN a rule reload is in progress while requests are being processed
THEN in-flight requests SHALL continue executing with the old rule version
AND new requests arriving after reload completion SHALL use the new rule version.

---

### Requirement: Null Safety

System SHALL handle null values in rule conditions gracefully. Any comparison involving null SHALL return false.

#### Scenario: Null value in comparison

WHEN a rule condition evaluates a comparison where one or both operands are null
THEN the comparison SHALL return false without throwing an exception
AND the decision flow SHALL continue uninterrupted.
