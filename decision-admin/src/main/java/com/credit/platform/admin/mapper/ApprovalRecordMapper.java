package com.credit.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.platform.admin.model.ApprovalRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {

    @Select("SELECT * FROM approval_record WHERE target_type = #{type} AND target_id = #{id} ORDER BY operated_at DESC")
    List<ApprovalRecord> findByTarget(@Param("type") String type, @Param("id") String id);

    @Select("SELECT * FROM approval_record WHERE target_type = #{type} AND target_id = #{id} AND target_version = #{version} ORDER BY operated_at DESC")
    List<ApprovalRecord> findByTargetAndVersion(@Param("type") String type, @Param("id") String id, @Param("version") int version);
}
