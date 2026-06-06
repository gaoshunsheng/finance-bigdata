package com.credit.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.platform.admin.model.GrayscaleConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface GrayscaleConfigMapper extends BaseMapper<GrayscaleConfig> {

    @Select("SELECT * FROM grayscale_config WHERE target_type = #{type} AND target_id = #{id} LIMIT 1")
    Optional<GrayscaleConfig> findByTarget(@Param("type") String type, @Param("id") String id);

    @Select("SELECT * FROM grayscale_config WHERE grayscale_status = 'IN_PROGRESS'")
    List<GrayscaleConfig> listActiveGrayscales();
}
