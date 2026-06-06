package com.credit.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.platform.admin.security.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    Optional<User> findByUsername(@Param("username") String username);

    @Select("SELECT * FROM sys_user WHERE role = #{role}")
    List<User> findByRole(@Param("role") String role);

    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username}")
    boolean existsByUsername(@Param("username") String username);
}
