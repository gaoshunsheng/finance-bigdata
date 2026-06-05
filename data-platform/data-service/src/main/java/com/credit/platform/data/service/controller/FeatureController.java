package com.credit.platform.data.service.controller;

import com.credit.platform.data.service.model.ApiResponse;
import com.credit.platform.data.service.model.CustomerFeatures;
import com.credit.platform.data.service.service.FeatureQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 特征查询 API — 供决策引擎查询客户实时特征。
 *
 * <p>性能目标: P99 < 20ms
 *
 * <p>查询策略: Redis（缓存） → HBase（持久化），二级回查
 */
@RestController
@RequestMapping("/api/v1/features")
public class FeatureController {

    private final FeatureQueryService featureQueryService;

    public FeatureController(FeatureQueryService featureQueryService) {
        this.featureQueryService = featureQueryService;
    }

    /**
     * 查询客户全部实时特征。
     *
     * @param customerId 客户 ID
     * @return 客户特征数据
     */
    @GetMapping("/{customerId}")
    public ApiResponse queryAllFeatures(@PathVariable String customerId) {
        CustomerFeatures features = featureQueryService.queryFeatures(customerId);
        return ApiResponse.ok(features, "特征查询成功");
    }

    /**
     * 查询客户指定特征。
     *
     * @param customerId  客户 ID
     * @param featureKeys 需要查询的特征 Key 列表（逗号分隔）
     * @return 客户特征数据
     */
    @GetMapping("/{customerId}/select")
    public ApiResponse querySelectedFeatures(
            @PathVariable String customerId,
            @RequestParam("keys") String featureKeys) {
        List<String> keys = List.of(featureKeys.split(","));
        CustomerFeatures features = featureQueryService.queryFeatures(customerId, keys);
        return ApiResponse.ok(features, "特征查询成功");
    }
}
