## ADDED Requirements

### Requirement: Rule Editor

System SHALL provide a visual rule editor (Vue 3 frontend) where business users who are non-developers can configure condition rules via drag-and-drop interaction using field, operator, and value components, without writing code. The editor SHALL support AND, OR, and NOT logical composition and all standard comparison operators (equals, not equals, greater than, less than, greater than or equal, less than or equal, in, not in, between, is null, is not null, contains, starts with).

#### Scenario: Business user creates a compound rule via drag-and-drop

WHEN a business user drags field "age", operator ">", value "25" and combines with field "income", operator ">=", value "50000" using AND logic
THEN the system SHALL create a rule node representing: age > 25 AND income >= 50000, without requiring the user to write any code.

#### Scenario: Rule editor supports all logical operators

WHEN a user creates a rule with nested AND/OR/NOT logic
THEN the system SHALL correctly represent and evaluate the nested logical composition in the rule engine.

### Requirement: Scorecard Editor

System SHALL provide a visual scorecard editor where users can define characteristics, configure bins with ranges and corresponding scores, and set cutoff thresholds. The editor SHALL show a real-time score distribution preview as bins are configured.

#### Scenario: User configures a scorecard characteristic with bins

WHEN a user defines a characteristic "age" with bins [18-25: -10, 26-35: 5, 36-50: 15, 51+: 0] and sets a cutoff threshold of 30
THEN the system SHALL save the scorecard configuration and display a real-time score distribution preview based on sample data.

#### Scenario: User adjusts cutoff threshold

WHEN a user changes the cutoff threshold in the scorecard editor
THEN the system SHALL update the pass/fail rate preview in real-time to reflect the new threshold.

### Requirement: Decision Table Editor

System SHALL provide an Excel-style decision table editor using Handsontable where users edit conditions in columns and results in rows. The editor SHALL support wildcard (*) matching for flexible condition values and SHALL support Excel import and export for offline editing.

#### Scenario: User edits decision table conditions and results

WHEN a user opens the decision table editor and enters condition columns (age_range, income_level) and a result column (decision)
THEN the system SHALL display an Excel-style grid where the user can type conditions and results directly into cells using Handsontable.

#### Scenario: User imports decision table from Excel

WHEN a user imports an Excel file containing a decision table
THEN the system SHALL parse the Excel file and populate the decision table editor with the imported conditions and results.

#### Scenario: User exports decision table to Excel

WHEN a user clicks export on a decision table
THEN the system SHALL generate and download an Excel file containing the current decision table data with conditions and results.

#### Scenario: Decision table supports wildcard matching

WHEN a user enters "*" as a condition value in a decision table cell
THEN the system SHALL interpret that condition as matching any value for that field.

### Requirement: Flow Designer (DAG Canvas)

System SHALL provide a DAG flow designer using AntV X6 where users drag node types (rule set, scorecard, model, decision, action) onto the canvas and connect them with directed edges. Edge conditions SHALL be configurable via an expression editor. Flow validation SHALL detect cycles and missing connections before save.

#### Scenario: User designs a decision flow on the DAG canvas

WHEN a user drags a rule set node, a scorecard node, and a decision node onto the canvas and connects them with directed edges
THEN the system SHALL render the flow as a DAG with the configured nodes and edges using AntV X6.

#### Scenario: User configures edge condition via expression editor

WHEN a user clicks on an edge in the DAG canvas
THEN the system SHALL open an expression editor where the user can define the condition that must be true for execution to follow that edge.

#### Scenario: Flow validation detects cycle

WHEN a user creates a cycle in the DAG flow
THEN the system SHALL display a validation error indicating that cycles are not allowed and SHALL prevent saving the flow until the cycle is removed.

#### Scenario: Flow validation detects missing connection

WHEN a user saves a flow where a decision node has no incoming edges
THEN the system SHALL display a validation error indicating the disconnected node and SHALL prevent saving until the connection is made.

### Requirement: Version Management

System SHALL provide strategy version management with full lifecycle: Draft -> Test (sandbox) -> Review (approval) -> Grayscale (5% -> 50% -> 100%) -> Release. The system SHALL support one-click rollback to any historical version.

#### Scenario: Strategy progresses through full lifecycle

WHEN a strategy is created
THEN it starts in Draft status. It can be promoted to Test for sandbox testing, then to Review for approval, then to Grayscale starting at 5% traffic, then increased to 50% and 100%, then finally Released to full production.

#### Scenario: One-click rollback to previous version

WHEN an admin clicks rollback on a previous released version
THEN the system SHALL immediately switch production traffic to the selected historical version without requiring re-approval of that version.

#### Scenario: Grayscale traffic increment

WHEN a strategy in Grayscale phase at 5% is promoted
THEN the system SHALL increase the traffic allocation to 50% for the new version while maintaining 50% on the current version.

### Requirement: Sandbox Testing

System SHALL support sandbox testing where users submit test inputs (as JSON or via form) against draft rules and view execution results and full trace without affecting production data or decisions.

#### Scenario: User tests a draft strategy with JSON input

WHEN a user submits a JSON test input against a Draft strategy in sandbox mode
THEN the system SHALL execute the strategy against the test input and return the decision result along with the full execution trace, without affecting any production data.

#### Scenario: User tests a draft strategy with form input

WHEN a user fills in a test form with field values and submits against a Draft strategy
THEN the system SHALL execute the strategy and display the decision result and node-by-node trace in the UI.

### Requirement: Decision Analytics Dashboard

System SHALL provide dashboards displaying: decision pass rate trends over time, rule hit rate ranking (most-hit rules), score distribution histograms, channel comparison metrics, and experiment group comparison.

#### Scenario: User views decision pass rate trend

WHEN a user opens the Decision Analytics Dashboard
THEN the system SHALL display a time-series chart showing decision pass rate trends with configurable time range (daily, weekly, monthly).

#### Scenario: User views rule hit rate ranking

WHEN a user navigates to the rule hit rate section
THEN the system SHALL display a ranked list of rules ordered by hit frequency, showing the rule name and hit count.

#### Scenario: User compares experiment groups

WHEN a user selects an experiment for comparison
THEN the system SHALL display side-by-side metrics for each experiment group including pass rate, average score, and reject reason distribution.

### Requirement: RBAC Permission Control

System SHALL enforce role-based access control with the following roles: viewer (read-only access to all resources), editor (create and modify rules, scorecards, decision tables, and flows), approver (approve or reject changes in Review status), admin (full access including user management and system configuration).

#### Scenario: Viewer attempts to modify a rule

WHEN a user with the viewer role attempts to save changes to a rule
THEN the system SHALL deny the operation and display an access denied message.

#### Scenario: Editor creates and submits a rule for approval

WHEN a user with the editor role creates a new rule and submits it
THEN the system SHALL save the rule and transition it to Review status, making it available for approvers.

#### Scenario: Approver approves a strategy change

WHEN a user with the approver role reviews a strategy in Review status and clicks approve
THEN the system SHALL transition the strategy to Grayscale status.

#### Scenario: Admin manages user roles

WHEN a user with the admin role accesses user management
THEN the system SHALL allow the admin to assign and modify roles for any user in the system.
