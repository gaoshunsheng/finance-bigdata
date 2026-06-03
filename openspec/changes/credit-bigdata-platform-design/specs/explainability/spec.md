## ADDED Requirements

### Requirement: TraceEntry Recording

System SHALL record a TraceEntry for every DAG node execution. Each TraceEntry MUST include: nodeId, nodeType, startTime, endTime, durationMs, inputSnapshot (variable values), and outputSnapshot (result). For type-specific nodes, the TraceEntry MUST include additional details: scorecard breakdown for scorecard nodes, rule hits for rule nodes, and model output details for model nodes.

#### Scenario: Record TraceEntry for a scorecard node execution

WHEN the decision engine executes a scorecard node with ID "scorecard_01"
THEN the system SHALL record a TraceEntry containing nodeId="scorecard_01", nodeType="scorecard", startTime, endTime, durationMs, inputSnapshot with all input variable values, outputSnapshot with the computed score, and scorecard breakdown with per-characteristic bin scores.

#### Scenario: Record TraceEntry for a rule node execution

WHEN the decision engine executes a rule node
THEN the system SHALL record a TraceEntry containing nodeId, nodeType="rule", startTime, endTime, durationMs, inputSnapshot with evaluated variable values, outputSnapshot with hit/miss result, and rule hit details.

### Requirement: Four-Level Explainability

System SHALL provide 4 levels of explainability to serve different user personas:
- L1 Rule-level: hit rules, variable vs threshold comparison, scorecard score breakdown for business users.
- L2 Flow-level: DAG path visualization highlighting executed nodes for strategy analysts.
- L3 Model-level: SHAP values, feature importance, partial dependence plots (PDP) for model engineers.
- L4 Audit-level: complete input/output snapshots per step with timestamps for compliance officers.

#### Scenario: Business user views L1 Rule-level explanation

WHEN a business user queries the explanation for a completed decision
THEN the system SHALL return L1 explanation including which rules hit, each variable value compared against its threshold, and scorecard score breakdown by characteristic.

#### Scenario: Strategy analyst views L2 Flow-level explanation

WHEN a strategy analyst queries the explanation for a completed decision
THEN the system SHALL return L2 explanation including a DAG path visualization that highlights all executed nodes and edges traversed during the decision.

#### Scenario: Model engineer views L3 Model-level explanation

WHEN a model engineer queries the explanation for a decision that involved a model node
THEN the system SHALL return L3 explanation including SHAP values, feature importance rankings, and partial dependence plot data for the model.

#### Scenario: Compliance officer views L4 Audit-level explanation

WHEN a compliance officer queries the explanation for a completed decision
THEN the system SHALL return L4 explanation including complete input and output snapshots for every step with timestamps, enabling full audit trail reconstruction.

### Requirement: Decision Report Generation

System SHALL generate a complete decision report for each request. The report MUST include: decision ID, final result, score, reject reason/code (if applicable), full decision path, node execution details for every node in the path, experiment group assignment, and top contributing factors ranked by influence.

#### Scenario: Generate report for an approved decision

WHEN a credit decision request is approved with score 720
THEN the system SHALL generate a decision report containing decisionId, result="APPROVED", score=720, the full decision path from start to end, execution details for each node, experiment group, and top contributing factors.

#### Scenario: Generate report for a rejected decision

WHEN a credit decision request is rejected due to rule hit
THEN the system SHALL generate a decision report containing decisionId, result="REJECTED", reject reason, reject code, the decision path showing which rule caused rejection, and the contributing factors.

### Requirement: Asynchronous Log Writing

System SHALL write TraceEntry logs to Elasticsearch asynchronously via MQ. The log writing process SHALL NOT block the decision execution path. Log writing failure SHALL NOT affect decision results or return errors to the caller.

#### Scenario: Decision completes while MQ is temporarily unavailable

WHEN a decision is executed and the MQ broker is temporarily unavailable
THEN the decision SHALL complete normally and return the correct result to the caller without delay. The system SHALL retry writing the TraceEntry logs when MQ becomes available.

#### Scenario: TraceEntry logs written asynchronously after decision

WHEN a decision is executed successfully
THEN the system SHALL publish TraceEntry data to MQ after the decision result is returned. The consumer SHALL write the logs to Elasticsearch without impacting the decision response time.

### Requirement: Query API

System SHALL provide a REST API to query decision reports by decisionId. Reports SHALL be available within 5 seconds of decision completion. The API SHALL return the full decision report including all explainability data.

#### Scenario: Query report immediately after decision

WHEN a client queries the decision report API with a valid decisionId within 5 seconds of decision completion
THEN the system SHALL return the complete decision report with all node execution details, explainability data, and contributing factors.

#### Scenario: Query report with invalid decisionId

WHEN a client queries the decision report API with a decisionId that does not exist
THEN the system SHALL return a 404 response with an appropriate error message.
