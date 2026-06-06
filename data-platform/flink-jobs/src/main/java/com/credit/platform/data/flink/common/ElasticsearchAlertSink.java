package com.credit.platform.data.flink.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch 告警 Sink — 将数据质量违规写入 ES 索引。
 *
 * <p>索引: {@code data-quality-alert-{yyyy.MM}}
 * <p>使用 Elasticsearch REST API (_bulk 或 _doc) 写入文档。
 * <p>文档结构: violation details + timestamp + severity
 *
 * <p>连接失败时日志告警并跳过，保证作业容错运行。
 */
public class ElasticsearchAlertSink extends RichSinkFunction<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchAlertSink.class);
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter INDEX_FMT =
            DateTimeFormatter.ofPattern("yyyy.MM").withZone(ZoneId.of("Asia/Shanghai"));

    private final String esHost;
    private final int esPort;

    private transient HttpClient httpClient;
    private transient ObjectMapper objectMapper;

    /**
     * @param esHost Elasticsearch 主机地址
     * @param esPort Elasticsearch 端口
     */
    public ElasticsearchAlertSink(String esHost, int esPort) {
        this.esHost = esHost;
        this.esPort = esPort;
    }

    @Override
    public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
        log.info("[ElasticsearchAlertSink] 初始化完成: {}:{}", esHost, esPort);
    }

    @Override
    public void invoke(Map<String, Object> value, Context context) throws Exception {
        try {
            String indexMonth = INDEX_FMT.format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")));
            String indexName = "data-quality-alert-" + indexMonth;

            Map<String, Object> doc = new HashMap<>(value);
            doc.putIfAbsent("severity", "HIGH");
            doc.putIfAbsent("timestamp", ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            String json = objectMapper.writeValueAsString(doc);
            String url = "http://" + esHost + ":" + esPort + "/" + indexName + "/_doc";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("[ElasticsearchAlertSink] ES 写入返回非成功状态: status={}, body={}",
                        response.statusCode(), response.body());
            }
        } catch (IOException e) {
            log.warn("[ElasticsearchAlertSink] ES 连接失败，跳过: {}", e.getMessage());
        }
    }
}
