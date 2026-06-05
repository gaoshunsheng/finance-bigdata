package com.credit.platform.data.service.controller;

import com.credit.platform.data.service.model.ApiResponse;
import com.credit.platform.data.service.service.EnterpriseProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 企业画像查询 API — 聚合多源数据生成统一企业画像。
 *
 * <p>性能目标: P99 < 500ms
 */
@RestController
@RequestMapping("/api/v1/profile")
public class EnterpriseProfileController {

    private final EnterpriseProfileService profileService;

    public EnterpriseProfileController(EnterpriseProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * 查询企业画像。
     *
     * @param enterpriseId 企业 ID
     * @return 企业画像数据
     */
    @GetMapping("/{enterpriseId}")
    public ApiResponse queryProfile(@PathVariable String enterpriseId) {
        Map<String, Object> profile = profileService.queryProfile(enterpriseId);
        return ApiResponse.ok(profile, "企业画像查询成功");
    }
}
