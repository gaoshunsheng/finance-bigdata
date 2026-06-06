package com.credit.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.platform.admin.model.RuleEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RuleMapper extends BaseMapper<RuleEntity> {

    /**
     * Find specific version of a rule entity.
     * Note: Uses mybatis-plus result map for JSON type handler support.
     */
    @Select("SELECT * FROM rule_entity WHERE type = #{type} AND id = #{id} AND version = #{version}")
    @ResultMap("mybatis-plus_RuleEntity")
    Optional<RuleEntity> findByTypeAndIdAndVersion(@Param("type") String type,
                                                    @Param("id") String id,
                                                    @Param("version") int version);

    /**
     * Find the latest version of a rule by type and id.
     */
    @Select("SELECT * FROM rule_entity WHERE type = #{type} AND id = #{id} ORDER BY version DESC LIMIT 1")
    @ResultMap("mybatis-plus_RuleEntity")
    Optional<RuleEntity> findLatest(@Param("type") String type, @Param("id") String id);

    /**
     * Find all versions of a rule by type and id, ordered by version desc.
     */
    @Select("SELECT * FROM rule_entity WHERE type = #{type} AND id = #{id} ORDER BY version DESC")
    @ResultMap("mybatis-plus_RuleEntity")
    List<RuleEntity> findVersions(@Param("type") String type, @Param("id") String id);

    /**
     * List latest version of each rule by type.
     * Uses a subquery to get max version per id.
     */
    @Select("SELECT re.* FROM rule_entity re " +
            "INNER JOIN (SELECT id, MAX(version) as max_ver FROM rule_entity WHERE type = #{type} GROUP BY id) sub " +
            "ON re.id = sub.id AND re.version = sub.max_ver AND re.type = #{type}")
    @ResultMap("mybatis-plus_RuleEntity")
    List<RuleEntity> listLatestByType(@Param("type") String type);

    /**
     * Find by type and status (latest versions only).
     */
    @Select("SELECT re.* FROM rule_entity re " +
            "INNER JOIN (SELECT id, MAX(version) as max_ver FROM rule_entity WHERE type = #{type} GROUP BY id) sub " +
            "ON re.id = sub.id AND re.version = sub.max_ver AND re.type = #{type} " +
            "WHERE re.status = #{status}")
    @ResultMap("mybatis-plus_RuleEntity")
    List<RuleEntity> findByTypeAndStatus(@Param("type") String type, @Param("status") String status);

    /**
     * Count all records.
     */
    @Select("SELECT COUNT(*) FROM rule_entity")
    long countAll();

    /**
     * Delete specific version.
     */
    @Delete("DELETE FROM rule_entity WHERE type = #{type} AND id = #{id} AND version = #{version}")
    int deleteByTypeAndIdAndVersion(@Param("type") String type, @Param("id") String id, @Param("version") int version);
}
