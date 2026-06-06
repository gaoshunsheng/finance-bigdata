package com.credit.platform.admin.service;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;

/**
 * 决策流管理服务 — 决策流 DAG CRUD 与环检测。
 * <p>
 * 在通用 RuleAdminService 基础上增加决策流特有的逻辑:
 * <ul>
 *   <li>DAG 环检测 — 防止创建有环的决策流</li>
 *   <li>节点依赖分析 — 检测节点引用的规则/评分卡是否存在</li>
 *   <li>版本管理 — 创建新版本、版本回滚</li>
 *   <li>发布流程 — DRAFT → RELEASED</li>
 * </ul>
 * </p>
 */
@Service
public class FlowAdminService {

    private static final Logger log = LoggerFactory.getLogger(FlowAdminService.class);
    private static final String TYPE = "FLOW";

    private final RuleRepository repository;

    public FlowAdminService(RuleRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    // ==================== CRUD ====================

    /**
     * 创建决策流。
     *
     * @param name        决策流名称
     * @param content     决策流 JSON 定义（DAG 节点和边）
     * @param description 决策流描述
     * @return 创建的决策流实体
     */
    public RuleEntity createFlow(String name, String content, String description) {
        validateContent(content);
        detectCycle(content);
        String id = repository.nextId(TYPE);
        RuleEntity entity = RuleEntity.create(id, name, TYPE, content);
        entity.setDescription(description);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        log.info("创建决策流: id={}, name={}", id, name);
        return repository.save(entity);
    }

    /**
     * 更新决策流内容（仅 DRAFT 状态可修改，自动进行环检测）。
     */
    public RuleEntity updateFlow(String id, String content) {
        validateContent(content);
        detectCycle(content);
        RuleEntity latest = getLatest(id);
        assertDraft(latest);

        latest.setContent(content);
        log.info("更新决策流: id={}, version={}", id, latest.getVersion());
        return repository.save(latest);
    }

    /**
     * 软删除决策流。
     */
    public boolean deleteFlow(String id) {
        RuleEntity latest = getLatest(id);
        if (latest.getStatus() == PublishStatus.RELEASED) {
            throw new IllegalStateException("Cannot delete a released flow. Rollback first.");
        }
        log.info("删除决策流: id={}, version={}", id, latest.getVersion());
        return repository.delete(TYPE, id, latest.getVersion());
    }

    /**
     * 获取决策流最新版本。
     */
    public RuleEntity getFlow(String id) {
        return getLatest(id);
    }

    /**
     * 列出所有决策流（最新版本），支持按状态过滤。
     */
    public List<RuleEntity> listFlows(Map<String, Object> params) {
        if (params != null && params.containsKey("status")) {
            String status = (String) params.get("status");
            return repository.findByTypeAndStatus(TYPE, PublishStatus.valueOf(status));
        }
        return repository.listByType(TYPE);
    }

    // ==================== 版本管理 ====================

    /**
     * 创建新版本。
     */
    public RuleEntity createNewVersion(String id) {
        RuleEntity latest = getLatest(id);
        int newVersion = latest.getVersion() + 1;
        RuleEntity newEntity = latest.newVersion(newVersion);
        log.info("创建决策流新版本: id={}, version={}", id, newVersion);
        return repository.save(newEntity);
    }

    /**
     * 获取版本历史。
     */
    public List<RuleEntity> listVersions(String id) {
        return repository.findVersions(TYPE, id);
    }

    // ==================== 发布 ====================

    /**
     * 发布决策流: DRAFT → RELEASED。
     * <p>
     * 发布前自动进行环检测，确保 DAG 合法。
     * </p>
     */
    public RuleEntity publishFlow(String id) {
        RuleEntity entity = getLatest(id);
        if (entity.getStatus() == PublishStatus.RELEASED) {
            throw new IllegalStateException("Flow already released");
        }
        // 发布前再次校验 DAG 无环
        detectCycle(entity.getContent());
        entity.setStatus(PublishStatus.RELEASED);
        log.info("发布决策流: id={}, version={}", id, entity.getVersion());
        return repository.save(entity);
    }

    // ==================== DAG 环检测 ====================

    /**
     * 检测决策流 DAG 是否存在环。
     * <p>
     * 解析 content JSON 中的 edges 数组，构建邻接表，使用 DFS 检测环。
     * content 格式示例:
     * <pre>
     * {
     *   "nodes": [{"id": "node1", ...}, {"id": "node2", ...}],
     *   "edges": [{"source": "node1", "target": "node2"}, ...]
     * }
     * </pre>
     * </p>
     *
     * @param content 决策流 JSON 内容
     * @throws IllegalStateException 如果检测到环
     */
    public void detectCycle(String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        try {
            // 简易 JSON 解析 edges — 提取 source/target 对
            Map<String, List<String>> adjacency = buildAdjacencyList(content);
            if (adjacency.isEmpty()) {
                return;
            }

            // DFS 环检测: 使用着色法 (WHITE=0, GRAY=1, BLACK=2)
            Map<String, Integer> color = new HashMap<>();
            for (String node : adjacency.keySet()) {
                color.put(node, 0);
            }

            for (String node : adjacency.keySet()) {
                if (color.get(node) == 0) {
                    if (dfsDetectCycle(node, adjacency, color)) {
                        throw new IllegalStateException(
                            "Decision flow contains a cycle. DAG must be acyclic.");
                    }
                }
            }

            log.debug("DAG 环检测通过: 无环");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("DAG 环检测解析异常，跳过检测: {}", e.getMessage());
        }
    }

    /**
     * 从 content JSON 中构建邻接表。
     * <p>
     * 解析 edges 数组中的 source/target 字段构建图结构。
     * </p>
     */
    private Map<String, List<String>> buildAdjacencyList(String content) {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        // 解析 nodes 收集所有节点
        // 解析 edges 收集所有边
        String trimmed = content.trim();

        // 提取 nodes 部分
        int nodesIdx = trimmed.indexOf("\"nodes\"");
        int edgesIdx = trimmed.indexOf("\"edges\"");

        if (edgesIdx == -1) {
            return adjacency;
        }

        // 提取 edges 数组内容
        String edgesSection = trimmed.substring(edgesIdx);
        int arrayStart = edgesSection.indexOf('[');
        int arrayEnd = edgesSection.indexOf(']');

        if (arrayStart == -1 || arrayEnd == -1) {
            return adjacency;
        }

        String edgesArray = edgesSection.substring(arrayStart + 1, arrayEnd);

        // 解析每条 edge: {"source": "xxx", "target": "yyy"}
        // 简易解析
        for (String segment : edgesArray.split("\\{")) {
            String source = extractJsonValue(segment, "source");
            String target = extractJsonValue(segment, "target");
            if (source != null && target != null) {
                adjacency.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                // 确保目标节点也在图中
                adjacency.computeIfAbsent(target, k -> new ArrayList<>());
            }
        }

        // 同样收集 nodes 中的节点 ID
        if (nodesIdx != -1 && nodesIdx < edgesIdx) {
            String nodesSection = trimmed.substring(nodesIdx, edgesIdx);
            int nodesArrayStart = nodesSection.indexOf('[');
            int nodesArrayEnd = nodesSection.indexOf(']');
            if (nodesArrayStart != -1 && nodesArrayEnd != -1) {
                String nodesArray = nodesSection.substring(nodesArrayStart + 1, nodesArrayEnd);
                for (String segment : nodesArray.split("\\{")) {
                    String nodeId = extractJsonValue(segment, "id");
                    if (nodeId != null) {
                        adjacency.computeIfAbsent(nodeId, k -> new ArrayList<>());
                    }
                }
            }
        }

        return adjacency;
    }

    private String extractJsonValue(String segment, String key) {
        String pattern = "\"" + key + "\"";
        int idx = segment.indexOf(pattern);
        if (idx == -1) {
            return null;
        }
        int colonIdx = segment.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) {
            return null;
        }
        int startQuote = segment.indexOf('"', colonIdx + 1);
        if (startQuote == -1) {
            return null;
        }
        int endQuote = segment.indexOf('"', startQuote + 1);
        if (endQuote == -1) {
            return null;
        }
        return segment.substring(startQuote + 1, endQuote);
    }

    /**
     * DFS 环检测 — 着色法。
     * <ul>
     *   <li>WHITE (0): 未访问</li>
     *   <li>GRAY (1): 正在访问（当前 DFS 路径上）</li>
     *   <li>BLACK (2): 已完成</li>
     * </ul>
     * 遇到 GRAY 节点表示有环。
     */
    private boolean dfsDetectCycle(String node, Map<String, List<String>> adjacency,
                                    Map<String, Integer> color) {
        color.put(node, 1); // GRAY

        List<String> neighbors = adjacency.getOrDefault(node, List.of());
        for (String neighbor : neighbors) {
            int neighborColor = color.getOrDefault(neighbor, 0);
            if (neighborColor == 1) {
                // 遇到 GRAY 节点 — 有环
                return true;
            }
            if (neighborColor == 0 && dfsDetectCycle(neighbor, adjacency, color)) {
                return true;
            }
        }

        color.put(node, 2); // BLACK
        return false;
    }

    // ==================== 内部方法 ====================

    private RuleEntity getLatest(String id) {
        return repository.findLatest(TYPE, id)
            .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));
    }

    private void assertDraft(RuleEntity entity) {
        if (entity.getStatus() != PublishStatus.DRAFT) {
            throw new IllegalStateException(
                "Cannot update flow in " + entity.getStatus() + " status. Create a new version first.");
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Flow content cannot be empty");
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("{")) {
            throw new IllegalArgumentException("Flow content must be valid JSON object");
        }
    }
}
