## ADDED Requirements

### Requirement: Metadata Management with Auto-Discovery

The system SHALL automatically discover and register schemas from Hive, MySQL, and HBase data sources.

The system SHALL provide data lineage visualization showing the flow of data from source through each processing layer to consumption.

The system SHALL support change impact analysis so that when a schema or table changes, all downstream dependencies are identified and reported.

The system SHALL implement a tagging system allowing data assets to be tagged with business domain, sensitivity level, owner, and custom tags.

#### Scenario: Auto-discovery of Hive table schema

WHEN a new table is created in Hive or an existing table schema changes
THEN the metadata management system SHALL detect the change within 1 hour, register or update the schema in the metadata catalog, and notify the table owner.

#### Scenario: Data lineage visualization for a table

WHEN a user views the lineage of a specific DWS table
THEN the system SHALL display the full data flow from ODS source tables through DWD to the DWS table, and forward to any ADS tables or reports consuming it.

#### Scenario: Change impact analysis

WHEN a source table column is modified or removed
THEN the system SHALL identify all downstream tables, ETL jobs, reports, and APIs affected by the change and produce an impact report listing each dependency.

#### Scenario: Tagging a data asset

WHEN a data steward assigns tags to a table (e.g., domain=credit, sensitivity=confidential, owner=risk-team)
THEN the system SHALL persist the tags, make them searchable in the metadata catalog, and enforce any tag-driven policies (such as access control based on sensitivity).

---

### Requirement: Data Quality Monitoring with Five Dimensions

The system SHALL monitor data quality across five dimensions:

1. **Completeness** - null-rate checking to ensure required fields are populated
2. **Accuracy** - enum, range, and regex validation to ensure values conform to expected patterns
3. **Consistency** - cross-table validation to ensure referential integrity and business rule compliance
4. **Timeliness** - SLA monitoring to ensure data arrives within the expected time window
5. **Uniqueness** - primary key duplicate detection to prevent duplicate records

When any quality rule is violated, the system SHALL send alerts via WeCom and email to the configured recipients.

#### Scenario: Completeness rule detects high null rate

WHEN a data quality check finds that the null rate for a required field exceeds the configured threshold
THEN the system SHALL record the violation, send a WeCom alert and email notification to the data owner, and mark the affected dataset with a quality warning.

#### Scenario: Accuracy rule rejects out-of-range value

WHEN a field value falls outside the configured enum, range, or regex pattern
THEN the system SHALL flag the record as inaccurate, log the violation with the field name and invalid value, and alert the responsible team.

#### Scenario: Consistency rule detects cross-table mismatch

WHEN a cross-table validation finds orphan records or business rule violations between related tables
THEN the system SHALL report the inconsistent records, quantify the mismatch, and alert the data steward.

#### Scenario: Timeliness SLA breach detected

WHEN a dataset does not arrive in the target layer by the configured SLA time
THEN the system SHALL send an urgent alert via WeCom and email, including the expected arrival time, actual status, and affected downstream consumers.

#### Scenario: Uniqueness rule detects duplicate primary keys

WHEN a primary key duplicate is found within a table or partition
THEN the system SHALL flag the duplicate records, alert the data owner, and quarantine the duplicates for manual review.

---

### Requirement: Data Security with Four-Level Classification

The system SHALL classify all data assets into four sensitivity levels: public, internal, confidential, and top-secret.

The system SHALL automatically mask sensitive fields including national ID numbers, phone numbers, and bank card numbers for users without explicit unmasking permission.

Data classified as confidential or above SHALL be encrypted at rest using AES-256.

The system SHALL enforce Role-Based Access Control (RBAC) with row-level and column-level permissions.

The system SHALL maintain an audit log of all data access and modifications with a minimum 5-year retention period.

All data in transit SHALL be encrypted using TLS 1.3 or higher.

#### Scenario: Automatic masking of sensitive fields

WHEN a user without unmasking permission queries a table containing national ID, phone, or bank card fields
THEN the system SHALL return masked values (e.g., `***` or partial masking) for those fields, while returning other fields unchanged.

#### Scenario: AES-256 encryption for confidential data at rest

WHEN data classified as confidential or top-secret is written to storage
THEN the system SHALL encrypt the data using AES-256 before writing and decrypt it transparently for authorized access.

#### Scenario: RBAC with row-level and column-level permissions

WHEN a user with restricted role queries a table with row-level and column-level policies
THEN the system SHALL return only the rows matching the user's row filter and only the columns the user is authorized to see.

#### Scenario: Audit log captures data access

WHEN any user reads, writes, or modifies data in a governed table
THEN the system SHALL record the access event (user, action, table, timestamp, affected rows) in the audit log and retain it for at least 5 years.

#### Scenario: TLS 1.3 enforced for data in transit

WHEN data is transmitted between any system components (clients, services, databases)
THEN the connection SHALL use TLS 1.3 or higher, and connections attempting older protocol versions SHALL be rejected.

---

### Requirement: Lifecycle Management with Tiered Storage

The system SHALL implement tiered storage with hot, warm, cold, and archive tiers, with tier placement determined by the data layer and age.

Elasticsearch SHALL use Index Lifecycle Management (ILM) policies to automatically migrate indices through storage tiers based on age and access patterns.

Redis SHALL enforce per-feature TTL policies to expire cached data automatically.

#### Scenario: Data migrated from hot to warm tier

WHEN data in the hot tier reaches the configured age threshold for the warm tier
THEN the system SHALL automatically migrate the data to the warm storage tier, reducing storage costs while maintaining queryability.

#### Scenario: Elasticsearch ILM migrates index to cold tier

WHEN an Elasticsearch index reaches the ILM policy age threshold for the cold tier
THEN ILM SHALL move the index to cold storage nodes, and the index SHALL remain queryable but with reduced performance expectations.

#### Scenario: Redis TTL expires cached feature

WHEN a Redis key's TTL expires
THEN the key SHALL be automatically removed from Redis, and subsequent queries SHALL trigger feature recomputation from the source of truth.

#### Scenario: Archive tier for historical data

WHEN data reaches the end of its active retention period but must be retained for compliance
THEN the system SHALL move the data to the archive tier with the highest compression and lowest access cost, where it remains readable but not directly queryable through standard interfaces.
