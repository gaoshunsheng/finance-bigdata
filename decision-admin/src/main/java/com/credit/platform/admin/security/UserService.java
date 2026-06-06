package com.credit.platform.admin.security;

import java.util.List;
import java.util.Objects;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户管理服务 — 用户 CRUD + 密码编码。
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 创建用户。
     *
     * @param username    用户名
     * @param rawPassword 明文密码
     * @param displayName 显示名
     * @param email       邮箱
     * @param role        角色
     * @return 创建的用户
     */
    public User createUser(String username, String rawPassword, String displayName,
                            String email, Role role) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        User user = User.create(username, displayName, email, role);
        user.setPassword(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    /**
     * 更新用户信息。
     */
    public User updateUser(String username, String displayName, String email, Role role) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (displayName != null) user.setDisplayName(displayName);
        if (email != null) user.setEmail(email);
        if (role != null) user.setRole(role);
        return userRepository.save(user);
    }

    /**
     * 修改密码。
     */
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 重置密码（管理员操作）。
     */
    public void resetPassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 启用/禁用用户。
     */
    public User toggleEnabled(String username, boolean enabled) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        user.setEnabled(enabled);
        return userRepository.save(user);
    }

    /**
     * 按用户名查找。
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    /**
     * 列出所有用户。
     */
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    /**
     * 列出指定角色的用户。
     */
    public List<User> listByRole(Role role) {
        return userRepository.findByRole(role);
    }

    /**
     * 删除用户。
     */
    public boolean deleteUser(String username) {
        return userRepository.deleteByUsername(username);
    }

    /**
     * 验证密码。
     */
    public boolean checkPassword(String username, String rawPassword) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return false;
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    /**
     * 获取 PasswordEncoder 实例。
     */
    public PasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    /**
     * 初始化默认管理员。
     * <p>
     * 默认密码从环境变量 ADMIN_INITIAL_PASSWORD 读取，
     * 未配置时自动生成随机密码并输出到日志（仅一次）。
     * </p>
     */
    public void initDefaultAdmin() {
        if (!userRepository.existsByUsername("admin")) {
            String initialPassword = System.getenv("ADMIN_INITIAL_PASSWORD");
            if (initialPassword == null || initialPassword.isEmpty()) {
                // 自动生成随机密码
                String generated = generateRandomPassword(16);
                System.getLogger(UserService.class.getName())
                    .log(System.Logger.Level.WARNING,
                        "未配置 ADMIN_INITIAL_PASSWORD，已为 admin 用户生成随机密码，请查阅应用日志获取");
                System.getLogger(UserService.class.getName())
                    .log(System.Logger.Level.INFO,
                        "admin 初始密码: " + generated + " (请立即修改)");
                initialPassword = generated;
            }
            createUser("admin", initialPassword, "系统管理员", "admin@credit.platform", Role.ADMIN);
        }
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
