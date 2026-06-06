package com.credit.platform.server.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DecisionController 集成测试。
 */
@SpringBootTest(
    classes = com.credit.platform.server.DecisionServerApplication.class,
    properties = {
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration"
    }
)
@AutoConfigureMockMvc
@DisplayName("DecisionController REST API")
class DecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("1. 健康检查 — 返回 UP 状态")
    void health_returnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/decision/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("decision-server"));
    }

    @Test
    @DisplayName("2. 执行决策 — 无策略返回 MANUAL")
    void execute_noStrategy_returnsManual() throws Exception {
        Map<String, Object> request = Map.of(
            "strategyId", "non-existent-strategy",
            "channel", "APP",
            "applicant", Map.of("name", "张三", "age", 25)
        );

        mockMvc.perform(post("/api/v1/decision/execute")
                .header("Authorization", "Bearer test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("MANUAL"))
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.durationMs").isNumber());
    }

    @Test
    @DisplayName("3. 执行决策 — 无 Token 返回 401")
    void execute_noToken_returns401() throws Exception {
        Map<String, Object> request = Map.of(
            "strategyId", "test-strategy",
            "channel", "APP"
        );

        mockMvc.perform(post("/api/v1/decision/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. 查询报告 — 不存在的 ID 返回 404")
    void getReport_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/decision/report/non-existent")
                .header("Authorization", "Bearer test-secret"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("5. 健康检查 — 不需要鉴权")
    void health_noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/v1/decision/health"))
            .andExpect(status().isOk());
    }
}
