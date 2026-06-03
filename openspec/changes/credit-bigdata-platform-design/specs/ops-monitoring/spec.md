## ADDED Requirements

### Requirement: Three-Level Monitoring

System SHALL provide 3-level monitoring: (a) Infrastructure level - monitoring Hadoop, Flink, Redis, MySQL, and Elasticsearch cluster health including CPU, memory, disk, and network metrics; (b) Task level - monitoring ETL job success rate and latency, Flink job backpressure, and SLA breach detection; (c) Business level - monitoring decision pass rate, model KS value, and system availability.

#### Scenario: Infrastructure-level cluster health monitoring

WHEN the monitoring system collects metrics from all infrastructure components (Hadoop, Flink, Redis, MySQL, Elasticsearch)
THEN the system SHALL display cluster health status with CPU usage, memory usage, disk usage, and network throughput for each component, flagging any component exceeding resource thresholds.

#### Scenario: Task-level ETL job monitoring

WHEN an ETL job completes or fails
THEN the system SHALL record the job outcome and latency, update the success rate metric, and detect if the job breached its SLA time window.

#### Scenario: Task-level Flink backpressure detection

WHEN a Flink streaming job experiences backpressure
THEN the system SHALL detect and report the backpressure level, identifying which operators are causing the bottleneck.

#### Scenario: Business-level decision pass rate monitoring

WHEN the system aggregates business metrics for the current period
THEN the system SHALL display the decision pass rate, current model KS value, and overall system availability percentage on the business monitoring dashboard.

### Requirement: Alert System

System SHALL support multi-channel alerts via WeCom, email, and phone. Alert rules SHALL be configurable per metric with custom thresholds and escalation policies. The system SHALL support configurable severity levels (info, warning, critical) with different notification channels per severity.

#### Scenario: Critical alert triggers multi-channel notification

WHEN a critical-level alert fires (e.g., system availability drops below 99.5%)
THEN the system SHALL send notifications via WeCom, email, and phone call to the on-call team simultaneously.

#### Scenario: User configures alert rule for a metric

WHEN an admin creates an alert rule for "decision P99 latency" with threshold > 500ms and severity "warning"
THEN the system SHALL save the alert rule and begin monitoring the metric, triggering a warning alert via WeCom and email when the threshold is exceeded.

#### Scenario: Alert escalation policy

WHEN a warning-level alert is not acknowledged within 30 minutes
THEN the system SHALL escalate the alert to critical severity and trigger phone notifications to the escalation contact.

### Requirement: Task Scheduling

System SHALL integrate DolphinScheduler for DAG-based ETL task scheduling with dependency management supporting task dependencies (upstream task completion), data dependencies (data availability), and time dependencies (scheduled triggers). System SHALL support manual trigger, scheduled trigger (cron-based), and event-driven trigger (e.g., data arrival notification).

#### Scenario: Schedule ETL DAG with task dependencies

WHEN a user defines an ETL DAG with tasks A, B, C where B depends on A and C depends on B
THEN DolphinScheduler SHALL execute the tasks in order: A first, then B after A completes, then C after B completes.

#### Scenario: Event-driven task trigger

WHEN an upstream data source notifies the system that new data has arrived
THEN the system SHALL trigger the associated ETL DAG automatically without waiting for a scheduled time.

#### Scenario: Scheduled cron-based trigger

WHEN a user configures a cron schedule of "0 2 * * *" (daily at 2:00 AM) for an ETL DAG
THEN the system SHALL trigger the DAG execution at 2:00 AM every day automatically.

#### Scenario: Manual task trigger

WHEN a user clicks the manual trigger button on an ETL DAG
THEN the system SHALL immediately start the DAG execution regardless of the scheduled time.

### Requirement: Performance Dashboard

System SHALL display real-time metrics: decision QPS (queries per second), P50/P90/P99 latency percentiles, error rate (percentage of failed requests), active experiment count, and rule cache hit rate. All metrics SHALL update in real-time with sub-second refresh.

#### Scenario: Real-time decision QPS display

WHEN the performance dashboard is open
THEN the system SHALL display the current decision QPS with real-time updates, showing requests per second processed by the decision engine.

#### Scenario: Latency percentile monitoring

WHEN the performance dashboard is viewed
THEN the system SHALL display P50, P90, and P99 latency values for decision requests, updated in real-time.

#### Scenario: Error rate spike detection

WHEN the decision error rate exceeds 1% for a sustained period of 5 minutes
THEN the system SHALL visually highlight the error rate metric on the dashboard and trigger an alert if configured.

#### Scenario: Rule cache hit rate monitoring

WHEN the performance dashboard is viewed
THEN the system SHALL display the rule cache hit rate percentage, indicating how often rule evaluations are served from cache versus recomputed.
