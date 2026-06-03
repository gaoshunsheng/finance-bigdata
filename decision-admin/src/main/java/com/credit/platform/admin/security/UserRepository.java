package com.credit.platform.admin.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

/**
 * 用户仓库 — 内存实现，后续替换为 MyBatis-Plus + MySQL。
 */
@Repository
public class UserRepository {

    private final ConcurrentHashMap<String, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0);

    /**
     * 保存用户。
     */
    public User save(User user) {
        Objects.requireNonNull(user);
        if (user.getId() == null) {
            user.setId(idSequence.incrementAndGet());
        }
        user.setUpdatedAt(java.time.LocalDateTime.now());
        store.put(user.getUsername(), user);
        return user;
    }

    /**
     * 按用户名查找。
     */
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(store.get(username));
    }

    /**
     * 按 ID 查找。
     */
    public Optional<User> findById(Long id) {
        return store.values().stream()
            .filter(u -> u.getId().equals(id))
            .findFirst();
    }

    /**
     * 列出所有用户。
     */
    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * 按角色过滤。
     */
    public List<User> findByRole(Role role) {
        return store.values().stream()
            .filter(u -> u.getRole() == role)
            .toList();
    }

    /**
     * 删除用户。
     */
    public boolean deleteByUsername(String username) {
        return store.remove(username) != null;
    }

    /**
     * 用户是否存在。
     */
    public boolean existsByUsername(String username) {
        return store.containsKey(username);
    }

    /**
     * 总数。
     */
    public long count() {
        return store.size();
    }
}
