"""
决策引擎配置数据模型 — Pydantic BaseModel 定义。

包含:
- RuleEntity: rule_entity 表模型
- ConditionRule / RuleSet: 条件规则 & 规则集
- ScorecardConfig: 评分卡配置
- DecisionTableConfig: 决策表配置
- DecisionTreeConfig: 决策树配置
- FlowDAGConfig: 决策流 DAG 配置
- VariableDefinition: 变量定义
- ExperimentConfig: AB 实验配置
- GrayscaleRecord / ApprovalRecord / AuditLogEntry: 运维记录
"""

from typing import Optional, Any
from datetime import datetime

from pydantic import BaseModel, Field


# ---------------------------------------------------------------------------
# 1. RuleEntity — rule_entity 表模型
# ---------------------------------------------------------------------------
class RuleEntity(BaseModel):
    """规则实体 — 对应 MySQL rule_entity 表"""
    id: str                                          # 实体 ID
    name: str                                        # 实体名称
    type: str                                        # RULE / SCORECARD / DECISION_TABLE / DECISION_TREE / FLOW / VARIABLE / EXPERIMENT
    version: int = 1                                 # 版本号
    status: str = "DRAFT"                            # DRAFT / TEST / REVIEW / GRAYSCALE / RELEASED
    content: str = "{}"                              # JSON 内容
    description: Optional[str] = None                # 描述
    created_by: Optional[str] = None                 # 创建人
    updated_by: Optional[str] = None                 # 修改人
    created_at: Optional[str] = None                 # yyyy-MM-dd HH:mm:ss
    updated_at: Optional[str] = None                 # yyyy-MM-dd HH:mm:ss
    attributes: Optional[dict] = None                # 扩展属性 JSON

    def to_db_row(self) -> dict:
        """转为 DB 写入所需的 dict。"""
        import json
        return {
            "id": self.id,
            "name": self.name,
            "type": self.type,
            "version": self.version,
            "status": self.status,
            "content": self.content if isinstance(self.content, str) else json.dumps(self.content, ensure_ascii=False),
            "description": self.description or "",
            "created_by": self.created_by or "system",
            "updated_by": self.updated_by or "system",
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "attributes": json.dumps(self.attributes or {}, ensure_ascii=False),
        }


# ---------------------------------------------------------------------------
# 2. 条件规则
# ---------------------------------------------------------------------------
class ComparisonCondition(BaseModel):
    """比较条件"""
    field: str                                       # 变量名
    op: str                                          # GT / LT / GTE / LTE / EQ / NEQ / BETWEEN / IN
    value: Any                                       # 比较值 (BETWEEN 时为 [min,max], IN 时为 [...])


class LogicalCondition(BaseModel):
    """逻辑条件"""
    operator: str                                    # AND / OR / NOT
    operands: list[dict] = Field(default_factory=list)


class RuleAction(BaseModel):
    """规则动作"""
    type: str                                        # REJECT / PASS / REVIEW / MANUAL / ASSIGN / SCORE
    reason: Optional[str] = None                     # 原因描述
    code: Optional[str] = None                       # 错误码


class ConditionRule(BaseModel):
    """单条条件规则"""
    ruleId: str
    name: Optional[str] = None
    version: int = 1
    priority: int = 100
    conditions: dict                                 # ComparisonCondition 或 LogicalCondition 的 dict
    actions: list[dict] = Field(default_factory=list)


class RuleSet(BaseModel):
    """规则集"""
    ruleSetId: str
    name: Optional[str] = None
    version: int = 1
    hitPolicy: str = "FIRST_HIT"                     # FIRST_HIT / ALL / PRIORITY
    rules: list[dict] = Field(default_factory=list)  # ConditionRule dict 列表

    def to_json(self) -> str:
        """序列化为 JSON 字符串。"""
        import json
        return json.dumps(self.model_dump(exclude_none=True), ensure_ascii=False)


# ---------------------------------------------------------------------------
# 3. 评分卡
# ---------------------------------------------------------------------------
class ScoreBin(BaseModel):
    """评分分箱"""
    range: list[Optional[float]]                     # [lower, upper], null=无界
    score: int                                       # 该分箱的评分权重
    reason: Optional[str] = None
    label: Optional[str] = None


class ScoreCharacteristic(BaseModel):
    """评分特征"""
    name: str
    field: str                                       # 变量名
    bins: list[dict] = Field(default_factory=list)   # ScoreBin dict 列表


class ScorecardConfig(BaseModel):
    """评分卡配置"""
    scorecardId: str
    name: Optional[str] = None
    version: int = 1
    initialScore: int = 500
    characteristics: list[dict] = Field(default_factory=list)
    cutoff: dict = Field(default_factory=lambda: {"reject": 500, "review": 550, "pass": 550})

    def to_json(self) -> str:
        import json
        return json.dumps(self.model_dump(exclude_none=True), ensure_ascii=False)


# ---------------------------------------------------------------------------
# 4. 决策表
# ---------------------------------------------------------------------------
class DecisionTableColumn(BaseModel):
    """决策表列定义"""
    name: str
    field: str


class DecisionTableRow(BaseModel):
    """决策表行"""
    conditions: list[str]                            # 与 columns 一一对应, "*" 为通配
    result: dict                                     # 结果键值对


class DecisionTableConfig(BaseModel):
    """决策表配置"""
    tableId: str
    name: Optional[str] = None
    version: int = 1
    columns: list[dict] = Field(default_factory=list)
    rows: list[dict] = Field(default_factory=list)
    hitPolicy: str = "FIRST_MATCH"

    def to_json(self) -> str:
        import json
        return json.dumps(self.model_dump(exclude_none=True), ensure_ascii=False)


# ---------------------------------------------------------------------------
# 5. 决策树
# ---------------------------------------------------------------------------
class DecisionTreeConfig(BaseModel):
    """决策树配置 — 嵌套节点结构"""
    treeId: str
    name: Optional[str] = None
    version: int = 1
    rootNode: dict = Field(default_factory=dict)     # DecisionTreeNode dict

    def to_json(self) -> str:
        import json
        return json.dumps(self.model_dump(exclude_none=True), ensure_ascii=False)


# ---------------------------------------------------------------------------
# 6. 决策流 DAG
# ---------------------------------------------------------------------------
class FlowNode(BaseModel):
    """决策流节点"""
    id: str                                          # 节点 ID
    type: str                                        # DATA_PREP / RULE_SET / SCORECARD / MODEL / DECISION / SUB_FLOW / AB_SPLIT / ACTION / SCRIPT
    config: dict = Field(default_factory=dict)       # 节点配置


class FlowEdge(BaseModel):
    """决策流边"""
    from_field: str = Field(alias="from")             # 源节点 ID
    to: str                                          # 目标节点 ID
    condition: Optional[str] = None                  # Aviator 条件表达式 (空=无条件)

    class Config:
        populate_by_name = True


class FlowDAGConfig(BaseModel):
    """决策流 DAG 配置"""
    flowId: str
    name: Optional[str] = None
    version: int = 1
    nodes: list[dict] = Field(default_factory=list)
    edges: list[dict] = Field(default_factory=list)

    def to_json(self) -> str:
        import json
        return json.dumps(self.model_dump(exclude_none=True), ensure_ascii=False)


# ---------------------------------------------------------------------------
# 7. 变量定义
# ---------------------------------------------------------------------------
class VariableDefinition(BaseModel):
    """变量定义"""
    varId: str                                       # 变量唯一标识
    name: Optional[str] = None                       # 显示名称
    layer: str                                       # INPUT / EXTERNAL / CACHED / DERIVED
    dataType: str                                    # STRING / INTEGER / DECIMAL / DATE / BOOLEAN
    category: Optional[str] = None                   # credit / commerce / telecom / internal / derived
    expression: Optional[str] = None                 # Aviator 表达式 (仅 L3 DERIVED)
    dependencies: list[str] = Field(default_factory=list)  # 依赖变量 ID 列表
    version: int = 1
    description: Optional[str] = None

    def to_json(self) -> str:
        import json
        return json.dumps(self.model_dump(exclude_none=True), ensure_ascii=False)


# ---------------------------------------------------------------------------
# 8. AB 实验配置
# ---------------------------------------------------------------------------
class ExperimentGroup(BaseModel):
    """实验分组"""
    groupId: str
    name: Optional[str] = None
    trafficRatio: float = 0.5                       # 流量比例
    strategyId: str                                  # 绑定策略 ID


class ExperimentConfig(BaseModel):
    """AB 实验配置"""
    experimentId: str
    name: Optional[str] = None
    trafficKey: str = "customerId"                   # 分流 Key
    groups: list[dict] = Field(default_factory=list) # ExperimentGroup dict 列表
    startTimeMs: Optional[int] = None
    endTimeMs: Optional[int] = None
    enabled: bool = True
    terminationCondition: Optional[str] = None

    def to_json(self) -> str:
        import json
        return json.dumps(self.model_dump(exclude_none=True), ensure_ascii=False)


# ---------------------------------------------------------------------------
# 9. 运维记录模型
# ---------------------------------------------------------------------------
class GrayscaleRecord(BaseModel):
    """灰度发布记录 — grayscale_config 表"""
    config_id: str
    target_type: str
    target_id: str
    target_version: int = 1
    percentage: int = 0                              # 0-100
    previous_percentage: int = 0
    operator: Optional[str] = None
    started_at: Optional[str] = None                 # yyyy-MM-dd HH:mm:ss
    grayscale_status: str = "NOT_STARTED"            # NOT_STARTED / GRAYSCALE / RELEASED

    def to_db_row(self) -> dict:
        return self.model_dump()


class ApprovalRecord(BaseModel):
    """审批记录 — approval_record 表"""
    record_id: str
    target_type: str
    target_id: str
    target_version: int = 1
    action: str                                      # SUBMIT / APPROVE / REJECT / PUBLISH
    operator: str
    comment: Optional[str] = None
    operated_at: Optional[str] = None

    def to_db_row(self) -> dict:
        return self.model_dump()


class AuditLogEntry(BaseModel):
    """审计日志 — audit_log 表"""
    operator: str
    action: str                                      # CREATE / UPDATE / SUBMIT / APPROVE / REJECT / PUBLISH
    target_type: Optional[str] = None
    target_id: Optional[str] = None
    target_version: Optional[int] = None
    before_snapshot: Optional[str] = None             # JSON
    after_snapshot: Optional[str] = None              # JSON
    details: Optional[str] = None
    ip_address: str = "127.0.0.1"
    operated_at: Optional[str] = None

    def to_db_row(self) -> dict:
        return self.model_dump()
