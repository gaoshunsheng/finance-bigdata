## ADDED Requirements

### Requirement: Scorecard Definition

System SHALL support scorecard definition in JSON with initialScore, characteristics (each with field, bins with range+score+reason), and cutoff thresholds (reject/review/pass).

#### Scenario: Valid scorecard definition parsing

WHEN a JSON scorecard definition is provided with initialScore, characteristics array, and cutoff thresholds
THEN the system SHALL parse and validate all fields including field names, bin ranges, scores, reason codes, and threshold values.

#### Scenario: Scorecard definition with multiple characteristics

WHEN a scorecard is defined with multiple characteristics each containing multiple bins
THEN the system SHALL accept the definition and make it available for compilation and execution.

---

### Requirement: Scorecard Compilation

System SHALL compile scorecards into pre-sorted bin arrays supporting binary search. Initial score + accumulated bin scores determine total score.

#### Scenario: Bin array compilation with binary search support

WHEN a scorecard is compiled
THEN each characteristic's bins SHALL be sorted by range boundaries
AND the compiled representation SHALL support O(log n) binary search lookup for score determination.

---

### Requirement: Scorecard Execution

System SHALL execute scorecards by: iterating characteristics, resolving field variable value, binary-searching matching bin, accumulating score. Total score SHALL be compared against cutoff thresholds to determine reject/review/pass.

#### Scenario: Score accumulation and threshold comparison

WHEN a compiled scorecard is executed against input data
THEN the engine SHALL iterate each characteristic, resolve its field variable, binary-search for the matching bin, and accumulate the bin score
AND the total score (initialScore + accumulated bin scores) SHALL be compared against cutoff thresholds to produce a reject, review, or pass decision.

#### Scenario: Characteristic with no matching bin

WHEN a characteristic's input value does not match any defined bin range
THEN the system SHALL assign a score of zero for that characteristic and SHALL continue processing remaining characteristics.

---

### Requirement: Score Detail Tracking

System SHALL record per-characteristic score breakdown (characteristic name, input value, matched bin, score contribution) for explainability.

#### Scenario: Score breakdown generation

WHEN a scorecard execution completes
THEN the system SHALL produce a detailed score breakdown listing each characteristic's name, input value, matched bin, and score contribution
AND the breakdown SHALL be available for audit and explainability purposes.

---

### Requirement: Cutoff Thresholds

System SHALL support configurable cutoff thresholds for reject, review, and pass decisions. Multiple scorecards MAY coexist in the same decision flow.

#### Scenario: Threshold-based decision output

WHEN the total score is below the reject threshold
THEN the decision SHALL be reject
WHEN the total score is between reject and review thresholds
THEN the decision SHALL be review
WHEN the total score meets or exceeds the pass threshold
THEN the decision SHALL be pass.

#### Scenario: Multiple scorecards in a single decision flow

WHEN a decision flow contains multiple scorecard nodes
THEN each scorecard SHALL execute independently with its own characteristics, bins, and cutoff thresholds
AND each scorecard SHALL produce its own decision and score breakdown.
