package com.credit.platform.data.service.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 统一 API 响应模型
 */
public class ApiResponse {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private boolean success;
    private Object data;
    private String message;
    private String timestamp;

    public ApiResponse(boolean success, Object data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.timestamp = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(FMT);
    }

    public static ApiResponse ok(Object data, String message) {
        return new ApiResponse(true, data, message);
    }

    public static ApiResponse ok(Object data) {
        return new ApiResponse(true, data, "操作成功");
    }

    public static ApiResponse error(String message) {
        return new ApiResponse(false, null, message);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public Object getData() { return data; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
}
