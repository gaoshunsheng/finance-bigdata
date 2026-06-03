## ADDED Requirements

### Requirement: Spark Offline Compute for ETL Pipeline

The system SHALL execute T+1 offline ETL jobs using Spark to transform data through the ODS to DWD to DWS to ADS pipeline layers.

Spark SHALL also be used for offline computation of model features required by the credit scoring and risk models.

#### Scenario: Daily ETL pipeline execution

WHEN the daily T+1 ETL schedule triggers
THEN Spark SHALL read data from the ODS layer, apply cleaning and standardization rules to produce DWD data, compute aggregations into DWS, and prepare application-specific datasets in ADS, completing all layers within the scheduled window.

#### Scenario: Offline model feature computation

WHEN a model training or batch scoring job requests offline features
THEN Spark SHALL compute the required features from DWD and DWS data, output the results to the designated storage (HDFS or HBase), and record job metadata including row counts and execution time.

---

### Requirement: Flink Real-Time Compute Jobs

The system SHALL deploy exactly 5 key Flink real-time streaming jobs:

1. `credit_query_3m` - credit query count over the past 3 months using a sliding window
2. `overdue_6m` - overdue event aggregation over the past 6 months using a sliding window
3. `apply_freq_1m` - application frequency count over the past 1 month using a sliding window
4. `transaction_summary_1h` - transaction amount summary over the past 1 hour using a sliding window
5. `data_quality_check` - real-time data quality validation across incoming streams

Each Flink job SHALL use sliding windows appropriate to its business logic and SHALL output results to the designated storage: Redis for low-latency feature serving, HBase for persistent feature storage, or Elasticsearch for monitoring and alerting.

#### Scenario: credit_query_3m job processes events

WHEN a credit query event arrives in the Kafka source topic
THEN the `credit_query_3m` Flink job SHALL update the sliding 3-month window count for the relevant customer and write the updated count to Redis and HBase.

#### Scenario: overdue_6m job detects overdue aggregation

WHEN an overdue event is received from the CDC stream
THEN the `overdue_6m` Flink job SHALL aggregate overdue events for the customer over the 6-month sliding window and output the result to HBase for feature storage.

#### Scenario: apply_freq_1m job tracks application frequency

WHEN a loan application event is ingested
THEN the `apply_freq_1m` Flink job SHALL increment the application frequency count for the customer within the 1-month sliding window and write the result to Redis.

#### Scenario: transaction_summary_1h job computes hourly summary

WHEN transaction events flow through the Kafka stream
THEN the `transaction_summary_1h` Flink job SHALL compute the transaction amount summary (total, count, average) over the 1-hour sliding window and write the results to Redis and HBase.

#### Scenario: data_quality_check job detects anomaly

WHEN the `data_quality_check` Flink job detects a data quality violation (null field, out-of-range value, or schema mismatch)
THEN the job SHALL write the anomaly to Elasticsearch and emit an alert to the monitoring system.

---

### Requirement: Trino Ad-Hoc Federated Query

The system SHALL provide Trino as an ad-hoc federated query engine capable of querying across Hive, HBase, and MySQL data sources in a single SQL statement.

Trino SHALL enforce a maximum of 20 concurrent queries.

Each Trino query SHALL have a timeout of 30 minutes.

Trino SHALL enforce row-level security policies to restrict data access based on the querying user's identity and role.

#### Scenario: Federated query across Hive and HBase

WHEN an analyst submits a SQL query joining data from a Hive table and an HBase table
THEN Trino SHALL execute the federated query, return results within the 30-minute timeout, and enforce the analyst's row-level security permissions on both data sources.

#### Scenario: Concurrent query limit enforced

WHEN 20 queries are already running and a 21st query is submitted
THEN Trino SHALL queue the 21st query and execute it only when a running query completes, without rejecting the request.

#### Scenario: Row-level security enforced

WHEN a user with restricted permissions queries a table with row-level security policies
THEN Trino SHALL return only the rows the user is authorized to see, based on their role and identity, and SHALL log the access in the audit trail.

#### Scenario: Query timeout enforced

WHEN a Trino query execution exceeds 30 minutes
THEN the system SHALL cancel the query, release all resources, and return a timeout error to the caller.
