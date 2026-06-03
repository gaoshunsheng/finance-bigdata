## ADDED Requirements

### Requirement: Batch Data Collection via DataX

The system SHALL support T+1 daily incremental batch data collection from MySQL and Oracle source databases into the HDFS ODS layer using DataX.

Each per-table batch job MUST handle a maximum of 500 million rows per execution cycle.

Every batch job SHALL implement checkpoint-based resume capability so that interrupted jobs can restart from the last successful checkpoint without data loss or duplication.

Upon completion of each batch load, the system SHALL perform row-count validation comparing source count against target count, and SHALL raise an alert if counts mismatch.

#### Scenario: Successful daily incremental batch load

WHEN the daily scheduled batch job triggers at the configured T+1 time
THEN the system SHALL connect to the configured MySQL/Oracle source, extract incremental records based on the last checkpoint, write them to the HDFS ODS layer in the correct partition, record a new checkpoint, and produce a row-count validation report.

#### Scenario: Batch job interrupted and resumed

WHEN a batch job is interrupted mid-transfer after processing 200M of 500M rows
THEN the system SHALL resume from the last recorded checkpoint on the next run, skip already-transferred rows, and complete the remaining records without duplication.

#### Scenario: Row-count validation failure

WHEN a batch load completes but the source row count does not match the target row count
THEN the system SHALL mark the job as failed, retain the previous checkpoint, and send an alert to the operations team via WeCom and email.

---

### Requirement: Real-Time CDC via Canal/Debezium to Kafka

The system SHALL capture real-time change data from MySQL and Oracle databases using Canal or Debezium and publish change events to Kafka in JSON format.

Each CDC event MUST include the fields: `op_type` (INSERT/UPDATE/DELETE), `before` (previous row state), `after` (new row state), and `ts` (event timestamp).

End-to-end CDC latency from source commit to Kafka availability SHALL be less than 5 seconds under normal load.

#### Scenario: Real-time INSERT event captured

WHEN a new row is inserted into a monitored source table
THEN the system SHALL publish a JSON event to the configured Kafka topic within 5 seconds, with `op_type=INSERT`, `before=null`, `after` containing the new row data, and `ts` set to the source commit timestamp.

#### Scenario: Real-time UPDATE event captured

WHEN an existing row is updated in a monitored source table
THEN the system SHALL publish a JSON event with `op_type=UPDATE`, `before` containing the prior row state, and `after` containing the updated row state.

#### Scenario: CDC latency exceeds threshold

WHEN end-to-end CDC latency exceeds 5 seconds
THEN the system SHALL emit a monitoring alert and log the latency spike for operations review.

---

### Requirement: External API Data Collection

The system SHALL support data collection from 10 external API sources: credit bureau (征信), business registration (工商), judicial records (司法), telecom carrier (运营商), tax (税务), social security (社保), e-commerce (电商), blacklist (黑名单), public opinion (舆情), and an additional external data source.

External API data SHALL be fetched via HTTP, cleaned and transformed, then written to both HDFS and HBase for downstream consumption.

The system SHALL maintain a Redis cache for external API responses with configurable TTL per API source.

Each external API integration MUST implement rate limiting to respect the API provider's quotas.

Each external API integration SHALL implement a degradation fallback strategy so that when an API is unavailable, the system returns cached data or a graceful error rather than failing the entire request.

#### Scenario: Successful external API data fetch

WHEN the system invokes the credit bureau API with a valid query
THEN the system SHALL return the response data, cache it in Redis with the configured TTL, write the cleaned result to HDFS and HBase, and record the API call in the audit log.

#### Scenario: External API rate limit reached

WHEN the number of requests to an external API exceeds the configured rate limit within the time window
THEN the system SHALL queue subsequent requests and retry after the rate limit window resets, without dropping any requests.

#### Scenario: External API unavailable with fallback

WHEN an external API endpoint returns a connection error or 5xx status
THEN the system SHALL return cached data from Redis if available and not expired, log the degradation event, and alert operations if the API remains unavailable beyond the configured retry threshold.

---

### Requirement: Log Collection via Fluentd/Filebeat to Kafka

The system SHALL collect application and system logs using Fluentd or Filebeat and publish them as structured JSON messages to Kafka.

Log events SHALL be routed from Kafka to both HDFS for long-term storage and Elasticsearch for real-time search and analysis.

#### Scenario: Structured log event ingested

WHEN an application emits a log event in the configured format
THEN Fluentd or Filebeat SHALL parse the event into structured JSON, publish it to the designated Kafka topic, and the event SHALL be available in both HDFS (within the next batch cycle) and Elasticsearch (within 10 seconds).

#### Scenario: Log pipeline backpressure

WHEN Kafka topic lag for log topics exceeds the configured threshold
THEN the system SHALL alert the operations team and enable backpressure on the log collection agents to prevent data loss.
