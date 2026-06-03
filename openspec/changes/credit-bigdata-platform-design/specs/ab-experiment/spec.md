## ADDED Requirements

### Requirement: AB Split Node

System SHALL provide an AB_SPLIT node type in DAG that routes requests to different branches based on experiment configuration.

#### Scenario: AB split node in DAG execution

WHEN the DAG executor encounters an AB_SPLIT node
THEN the node SHALL determine the appropriate experiment bucket for the request
AND SHALL route execution to the branch associated with that bucket.

---

### Requirement: Traffic Splitting

System SHALL use consistent hashing on a configurable traffic key (e.g., applicationId) to deterministically assign requests to experiment buckets. Same key SHALL always route to same bucket.

#### Scenario: Deterministic bucket assignment

WHEN a request with a specific traffic key value passes through the AB_SPLIT node
THEN the system SHALL use consistent hashing on the key to assign the request to a bucket
AND the same traffic key value SHALL always be assigned to the same bucket across all requests.

#### Scenario: Traffic ratio enforcement

WHEN an experiment is configured with an 80/20 traffic split
THEN approximately 80% of requests SHALL be routed to the first bucket and 20% to the second
AND the distribution SHALL remain stable over time due to consistent hashing.

---

### Requirement: Experiment Configuration

System SHALL support configuring experiment name, traffic ratio per bucket (e.g., 80%/20%), start/end dates, and linked strategy versions.

#### Scenario: Experiment lifecycle management

WHEN an experiment is configured with a start date and end date
THEN the AB_SPLIT node SHALL only apply the experiment routing during the configured date range
AND requests before the start date or after the end date SHALL route to the default (champion) branch.

#### Scenario: Multiple bucket configuration

WHEN an experiment is configured with multiple buckets each having a traffic percentage and linked strategy version
THEN the system SHALL validate that percentages sum to 100%
AND SHALL route traffic to each bucket according to the configured ratios.

---

### Requirement: Metrics Collection

System SHALL asynchronously collect per-bucket metrics: pass rate, average latency, rule hit distribution, and write to ES for analysis.

#### Scenario: Per-bucket metric aggregation

WHEN requests are processed through an AB_SPLIT experiment
THEN the system SHALL asynchronously collect per-bucket metrics including pass rate, average latency, and rule hit distribution
AND SHALL write the metrics to Elasticsearch for analysis.

#### Scenario: Asynchronous collection without blocking

WHEN metrics are being collected for an experiment
THEN the collection SHALL be asynchronous and SHALL NOT add latency to the decision flow execution path.

---

### Requirement: Statistical Significance

System SHALL compute statistical significance of metric differences between buckets. System SHALL auto-alert when experiment bucket underperforms with p<0.05 significance.

#### Scenario: Significance calculation

WHEN sufficient sample size has been collected for experiment buckets
THEN the system SHALL compute statistical significance (p-value) of metric differences between experiment and control buckets.

#### Scenario: Underperformance alert

WHEN an experiment bucket's key metrics (e.g., pass rate, default rate) underperform compared to the control bucket with statistical significance of p<0.05
THEN the system SHALL generate an automatic alert notifying the relevant stakeholders.

---

### Requirement: Auto Rollback

System SHALL support automatic rollback when experiment bucket metrics degrade beyond configurable threshold. Rollback SHALL redirect 100% traffic to champion bucket.

#### Scenario: Automatic rollback on metric degradation

WHEN experiment bucket metrics degrade beyond the configured threshold (e.g., default rate increase exceeds 5%)
THEN the system SHALL automatically trigger rollback
AND SHALL redirect 100% of traffic to the champion bucket without manual intervention.

#### Scenario: Rollback notification

WHEN an automatic rollback is triggered
THEN the system SHALL log the rollback event and notify stakeholders with the reason and metrics that triggered the rollback.
