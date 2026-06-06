package com.credit.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.platform.admin.security.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("SELECT * FROM audit_log WHERE operator = #{operator} ORDER BY operated_at DESC")
    List<AuditLog> findByOperator(@Param("operator") String operator);

    @Select("SELECT * FROM audit_log WHERE target_type = #{targetType} AND target_id = #{targetId} ORDER BY operated_at DESC")
    List<AuditLog> findByTarget(@Param("targetType") String targetType, @Param("targetId") String targetId);

    @Select("SELECT * FROM audit_log WHERE action = #{action} ORDER BY operated_at DESC")
    List<AuditLog> findByAction(@Param("action") String action);

    @Select("SELECT * FROM audit_log ORDER BY operated_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<AuditLog> findAllPaged(@Param("offset") int offset, @Param("limit") int limit);
}
