## ADDED Requirements

### Requirement: Sample Management

System SHALL support sample selection by time window, product type, channel, and customer type. System SHALL support configurable positive/negative label definition (e.g., overdue > 90 days as positive label). System SHALL enforce time-based train/validation/test split to prevent data leakage, ensuring no future data appears in training sets.

#### Scenario: User selects samples with time-based split

WHEN a user defines a sample selection with time window 2024-01-01 to 2024-12-31, product type "credit_card", and sets train/validation/test split ratios as 70/15/15
THEN the system SHALL select matching records and split them chronologically so that all training data precedes validation data, and all validation data precedes test data, preventing data leakage.

#### Scenario: User configures custom label definition

WHEN a user configures the positive label definition as "overdue_days > 90" within a 12-month observation window
THEN the system SHALL apply this definition to label all selected samples, tagging records matching the condition as positive and the rest as negative.

### Requirement: Feature Engineering

System SHALL support feature selection (IV value filtering, correlation filtering), feature derivation (cross-combination, polynomial, time-series statistics such as mean, max, min, std over rolling windows), WOE encoding (auto-binning with equal-frequency, equal-width, and decision-tree methods plus WOE transform), and PSI stability check (alert when PSI > 0.25 indicating unstable features).

#### Scenario: User runs IV-based feature selection

WHEN a user configures feature selection with IV threshold 0.02
THEN the system SHALL compute IV values for all candidate features and filter out features with IV below 0.02, returning the remaining features ranked by IV value.

#### Scenario: User generates derived features via time-series statistics

WHEN a user selects numeric features and configures rolling window statistics (mean, max, min, std) with a 3-month window
THEN the system SHALL generate new derived features computing these statistics over the specified rolling window for each selected feature.

#### Scenario: User applies WOE encoding with auto-binning

WHEN a user selects features for WOE encoding and chooses the decision-tree binning method
THEN the system SHALL automatically determine optimal bin boundaries using decision tree splitting, compute WOE values for each bin, and transform the features to their WOE-encoded values.

#### Scenario: PSI stability check triggers alert

WHEN the system computes PSI between training and validation feature distributions and finds a feature with PSI > 0.25
THEN the system SHALL flag that feature as unstable and alert the user with the PSI value and recommendation to remove or investigate the feature.

### Requirement: Model Training

System SHALL support LR (Logistic Regression), XGBoost, and LightGBM algorithms. System SHALL provide hyperparameter search (grid search, random search, and bayesian optimization), K-fold cross-validation, time-series rolling validation, and automatic feature importance output after training.

#### Scenario: User trains an XGBoost model with bayesian hyperparameter search

WHEN a user selects XGBoost algorithm, configures hyperparameter search space (learning_rate, max_depth, n_estimators), and selects bayesian optimization with 50 iterations
THEN the system SHALL execute bayesian hyperparameter search, evaluate each configuration using K-fold cross-validation, and return the best model with its hyperparameters and cross-validation scores.

#### Scenario: User trains a model with time-series rolling validation

WHEN a user selects time-series rolling validation with 3 folds and a 6-month training window expanding by 1 month per fold
THEN the system SHALL train the model on successive time windows and validate on the subsequent period, returning performance metrics for each fold.

#### Scenario: Feature importance output after training

WHEN a model training job completes successfully
THEN the system SHALL automatically output feature importance rankings, including gain-based importance for tree models and coefficient values for LR.

### Requirement: Model Evaluation

System SHALL compute and display the following metrics: KS value (threshold > 0.30 for production approval), AUC (threshold > 0.70), PSI (< 0.10 for model stability), confusion matrix, Lift@10%, and VIF (< 5.0 for LR models). System SHALL fail the evaluation if minimum thresholds are not met, preventing promotion to production.

#### Scenario: Model passes all evaluation thresholds

WHEN a trained model produces KS=0.35, AUC=0.78, PSI=0.05, and Lift@10%=3.2
THEN the system SHALL display all metrics as passing (green) and allow the model to proceed to deployment approval.

#### Scenario: Model fails KS threshold

WHEN a trained model produces KS=0.25 which is below the 0.30 minimum threshold
THEN the system SHALL fail the evaluation, display the KS metric as failing (red), and block the model from proceeding to production deployment.

#### Scenario: LR model fails VIF check

WHEN an LR model evaluation computes VIF > 5.0 for one or more features
THEN the system SHALL flag the high-VIF features indicating multicollinearity and SHALL fail the evaluation, recommending feature removal.

### Requirement: Model Deployment

System SHALL export models to PMML format (for LR, XGBoost, LightGBM) and ONNX format (for XGBoost, LightGBM). System SHALL deploy each model as an independent inference microservice with both REST API and gRPC endpoints. System SHALL support multi-version coexistence for grayscale rollout scenarios.

#### Scenario: Deploy model as inference microservice

WHEN a user deploys an approved XGBoost model version v2
THEN the system SHALL export the model to PMML and ONNX, create an independent inference microservice with REST API and gRPC endpoints, and register it in the service registry.

#### Scenario: Multi-version coexistence for grayscale rollout

WHEN model v2 is deployed for grayscale at 20% traffic while model v1 handles 80%
THEN the system SHALL route 20% of inference requests to model v2 and 80% to model v1, both running concurrently as separate service instances.

#### Scenario: Export model to PMML

WHEN a user requests PMML export for an LR model
THEN the system SHALL generate a valid PMML file containing the model definition, coefficients, and feature mappings.

### Requirement: Model Monitoring

System SHALL monitor model stability (daily PSI, alert at > 0.10 warning level, > 0.25 critical level), feature distribution drift (weekly), prediction distribution changes (daily), and KS/AUC trend (monthly). System SHALL auto-trigger a retraining alert when KS drops more than 20% from the baseline established during model evaluation.

#### Scenario: Daily PSI exceeds warning threshold

WHEN the daily PSI computation for a production model returns 0.15
THEN the system SHALL generate a warning-level alert indicating model stability degradation and notify the model monitoring team.

#### Scenario: Daily PSI exceeds critical threshold

WHEN the daily PSI computation for a production model returns 0.30
THEN the system SHALL generate a critical-level alert and escalate via configured notification channels (WeCom, email, phone).

#### Scenario: Auto-trigger retraining alert on KS drop

WHEN the monthly KS value for a production model drops from 0.35 to 0.25 (a 28.6% drop exceeding the 20% threshold)
THEN the system SHALL auto-trigger a retraining alert and create a retraining task in the workflow queue.

#### Scenario: Weekly feature distribution drift detection

WHEN the weekly feature drift analysis detects that a feature distribution has shifted significantly (e.g., population stability index of the feature exceeds threshold)
THEN the system SHALL report the drifted features with before/after distribution summaries and recommend investigation.
