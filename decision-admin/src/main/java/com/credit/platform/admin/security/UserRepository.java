package com.credit.platform.admin.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.credit.platform.admin.mapper.UserMapper;
import org.springframework.stereotype.Repository;

/**
 * 用户仓库 — MyBatis-Plus + MySQL 持久化实现。
 */
@Repository
public class UserRepository {

    private final UserMapper userMapper;

    public UserRepository(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 保存用户 — INSERT or UPDATE。
     */
    public User save(User user) {
        Objects.requireNonNull(user);
        user.setUpdatedAt(java.time.LocalDateTime.now());
        if (user.getId() == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        return user;
    }

    /**
     * 按用户名查找。
     */
    public Optional<User> findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    /**
     * 按 ID 查找。
     */
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    /**
     * 列出所有用户。
     */
    public List<User> findAll() {
        return userMapper.selectList(null);
    }

    /**
     * 按角色过滤。
     */
    public List<User> findByRole(Role role) {
        return userMapper.findByRole(role.name());
    }

    /**
     * 删除用户。
     */
    public boolean deleteByUsername(String username) {
        Optional<User> user = findByUsername(username);
        if (user.isEmpty()) return false;
        return userMapper.deleteById(user.get().getId()) > 0;
    }

    /**
     * 用户是否存在。
     */
    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }

    /**
     * 总数。
     */
    public long count() {
        return userMapper.selectCount(null);
    }
}
