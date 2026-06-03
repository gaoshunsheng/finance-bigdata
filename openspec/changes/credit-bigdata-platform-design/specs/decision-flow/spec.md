## ADDED Requirements

### Requirement: DAG Definition

System SHALL support decision flow definition as a directed acyclic graph in JSON, with nodes (9 types: DATA_PREP, RULE_SET, SCORECARD, MODEL, DECISION, SUB_FLOW, AB_SPLIT, ACTION, SCRIPT) and edges (with optional Aviator conditions).

#### Scenario: Valid DAG definition parsing

WHEN a JSON DAG definition is provided with nodes of supported types and connecting edges
THEN the system SHALL parse and validate the definition including node types, edge references, and optional Aviator conditions on edges.

#### Scenario: DAG with all nine node types

WHEN a DAG definition includes all 9 node types (DATA_PREP, RULE_SET, SCORECARD, MODEL, DECISION, SUB_FLOW, AB_SPLIT, ACTION, SCRIPT)
THEN the system SHALL accept and compile the definition without error.

---

### Requirement: DAG Compilation

System SHALL compile DAG JSON into CompiledDAG with topological ordering. Circular dependency detection SHALL fail compilation with clear error.

#### Scenario: Successful DAG compilation with topological sort

WHEN a valid DAG definition is compiled
THEN the system SHALL produce a CompiledDAG with nodes arranged in topological order
AND independent nodes at the same topological level SHALL be identifiable for parallel execution.

#### Scenario: Circular dependency detection

WHEN a DAG definition contains a cycle between nodes
THEN compilation SHALL fail with a clear error message identifying the nodes involved in the cycle.

---

### Requirement: Parallel Execution

System SHALL execute independent DAG nodes in parallel using CompletableFuture. Nodes at the same topological level with no data dependencies SHALL execute concurrently.

#### Scenario: Concurrent node execution at same level

WHEN the DAG executor encounters multiple independent nodes at the same topological level
THEN those nodes SHALL execute concurrently using CompletableFuture
AND the executor SHALL wait for all parallel nodes to complete before proceeding to the next level.

#### Scenario: Sequential execution for dependent nodes

WHEN a node has a data dependency on a previous node's output
THEN the dependent node SHALL only begin execution after the prerequisite node has completed.

---

### Requirement: Conditional Branching

System SHALL support conditional edges evaluated via Aviator expressions. DECISION nodes SHALL route to different branches based on expression evaluation. SWITCH-like routing based on node return values SHALL be supported.

#### Scenario: Conditional edge evaluation

WHEN a DAG edge has an associated Aviator condition expression
THEN the engine SHALL evaluate the expression against the current execution context
AND SHALL only traverse the edge if the expression evaluates to true.

#### Scenario: DECISION node branch routing

WHEN a DECISION node evaluates its condition expression
THEN the engine SHALL route execution to the branch corresponding to the evaluated result
AND branches not matching the condition SHALL be skipped.

---

### Requirement: Sub-flow Nesting

System SHALL support SUB_FLOW nodes that invoke other decision flows, enabling reuse and modularity.

#### Scenario: Sub-flow invocation

WHEN a SUB_FLOW node is encountered during DAG execution
THEN the engine SHALL load and execute the referenced decision flow as a nested execution
AND the sub-flow result SHALL be returned to the parent flow as the node output.

---

### Requirement: Node Timeout

System SHALL support configurable per-node timeout. Timeout SHALL abort node execution and trigger fallback behavior.

#### Scenario: Node execution exceeds timeout

WHEN a node execution exceeds its configured timeout duration
THEN the engine SHALL abort the node execution
AND SHALL trigger fallback behavior (such as returning a default result or following an error edge).

---

### Requirement: Performance

Complete DAG execution overhead (excluding external data fetch) SHALL be <50ms for a flow with 20 nodes.

#### Scenario: DAG execution within latency target

WHEN a decision flow with 20 nodes is executed (excluding external data fetch time)
THEN the total engine overhead for DAG orchestration SHALL complete within 50ms.
