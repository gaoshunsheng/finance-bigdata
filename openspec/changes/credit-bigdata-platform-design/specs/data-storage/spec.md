## ADDED Requirements

### Requirement: HDFS Data Lake Storage

The system SHALL provide an HDFS-based data lake storing files in Parquet or ORC format.

The ODS (Operational Data Store) layer SHALL retain data for a minimum of 2 years.

The DWD (Data Warehouse Detail) layer and all downstream layers SHALL retain data for a minimum of 5 years.

#### Scenario: ODS data written in Parquet format

WHEN a batch ingestion job writes data to the ODS layer
THEN the system SHALL store the data in Parquet format partitioned by date, and the data SHALL remain accessible for at least 2 years from the date of write.

#### Scenario: DWD layer data retention enforced

WHEN data in the DWD or downstream layers reaches 5 years of age
THEN the system SHALL archive or purge the data according to the configured lifecycle policy, and SHALL NOT delete data younger than 5 years.

---

### Requirement: Hive Four-Layer Data Warehouse

The system SHALL implement a four-layer Hive data warehouse: ODS (raw data), DWD (cleaned and unified data), DWS (aggregated data), and ADS (application data).

All tables SHALL be daily-partitioned by business date.

#### Scenario: Data flows through all four layers

WHEN a daily ETL job executes
THEN raw data SHALL be loaded into ODS, cleaned and standardized into DWD, aggregated into DWS, and prepared for application use in ADS, with each layer daily-partitioned and queryable.

#### Scenario: Daily partition created and queryable

WHEN a new business date arrives
THEN the system SHALL create a new partition for that date in every table across all four layers and make it available for querying within the scheduled ETL window.

---

### Requirement: Kafka Real-Time Stream Storage

The system SHALL use Kafka as the real-time stream storage layer with messages in JSON format.

Kafka topics SHALL be configured with a 7-day retention period for real-time processing windows.

#### Scenario: Real-time event stored in Kafka

WHEN a CDC event or log event is published to Kafka
THEN the event SHALL be stored in JSON format and retained for 7 days, after which it SHALL be eligible for deletion.

#### Scenario: Kafka retention policy enforced

WHEN a message in a Kafka topic exceeds 7 days of age
THEN Kafka SHALL automatically remove the message segment, and consumers SHALL have processed it within the retention window.

---

### Requirement: HBase Key-Value Store for Feature Queries

The system SHALL use HBase as a key-value store optimized for high-concurrency random read and write operations, primarily serving feature queries for the credit decision engine.

HBase tables SHALL use a RowKey design of `customerID + timestamp` to ensure even distribution and efficient point lookups.

HBase data SHALL be retained for a minimum of 3 years.

#### Scenario: Feature query with point lookup

WHEN the decision engine queries a customer's real-time features from HBase using customerID and timestamp
THEN the system SHALL return the feature values within the SLA threshold under high concurrency.

#### Scenario: HBase data retention enforced

WHEN HBase records reach 3 years of age
THEN the system SHALL archive or expire them according to the configured lifecycle policy, and SHALL NOT remove data younger than 3 years.

---

### Requirement: Elasticsearch Search Store with ILM

The system SHALL store decision logs and audit records in Elasticsearch as JSON documents.

Elasticsearch SHALL implement Index Lifecycle Management (ILM) with hot-cold tiering to manage storage costs.

Decision logs and audit data SHALL be retained for a minimum of 5 years.

#### Scenario: Decision log indexed and searchable

WHEN a credit decision is made and logged
THEN the system SHALL index the decision log as a JSON document in Elasticsearch and make it searchable within 5 seconds.

#### Scenario: ILM migration from hot to cold tier

WHEN an Elasticsearch index reaches the configured age threshold in the hot tier
THEN ILM SHALL automatically migrate the index to the cold tier, reducing storage costs while maintaining queryability for the full 5-year retention period.

---

### Requirement: Redis Cluster Cache for Real-Time Features

The system SHALL use a Redis Cluster deployment as a cache layer for real-time feature data.

Each cached feature SHALL have a configurable TTL ranging from 1 hour to 24 hours, set per feature type.

#### Scenario: Feature cached with configurable TTL

WHEN a real-time feature is computed and written to Redis
THEN the system SHALL store the feature value with the configured TTL for that feature type (between 1 and 24 hours), after which the key SHALL expire automatically.

#### Scenario: Cache miss triggers feature recomputation

WHEN a feature query results in a Redis cache miss
THEN the system SHALL trigger feature recomputation from the source of truth (HBase or real-time compute), cache the result with the appropriate TTL, and return the value to the caller.
