package com.motuslab.service;

import com.motuslab.dao.UserDao;
import com.motuslab.exception.AuthorizationException;
import com.motuslab.exception.NotFoundException;
import com.motuslab.exception.ValidationException;
import com.motuslab.model.User;
import org.mindrot.jbcrypt.BCrypt;

public class AuthService {

    private static final int BCRYPT_ROUNDS = 12;
    private final UserDao userDao;

    public AuthService() {
        this(new UserDao());
    }

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    public User login(String identifier, String rawPassword) {
        if (identifier == null || identifier.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new ValidationException("Kullanıcı adı/e-posta ve şifre boş bırakılamaz.");
        }

        User user = userDao.findByUsernameOrEmail(identifier.trim())
                .orElseThrow(() -> new AuthorizationException("Kullanıcı adı/e-posta veya şifre hatalı."));

        if (!BCrypt.checkpw(rawPassword, user.getPasswordHash())) {
            throw new AuthorizationException("Kullanıcı adı/e-posta veya şifre hatalı.");
        }

        if (!user.isActive()) {
            throw new AuthorizationException("Hesabınız pasif durumda. Sistem yöneticisiyle iletişime geçin.");
        }

        userDao.updateLastLogin(user.getId());
        SessionManager.setCurrentUser(user);
        return user;
    }

    public User register(String username, String email, String rawPassword, String fullName) {
        String cleanUsername = validateUsername(username);
        String cleanEmail = validateEmail(email);
        validatePassword(rawPassword);

        if (userDao.existsByUsername(cleanUsername)) {
            throw new ValidationException("Bu kullanıcı adı zaten kullanılıyor.");
        }
        if (userDao.existsByEmail(cleanEmail)) {
            throw new ValidationException("Bu e-posta adresi zaten kayıtlı.");
        }

        int researcherRoleId = userDao.findRoleIdByName("RESEARCHER")
                .orElseThrow(() -> new NotFoundException("RESEARCHER rolü bulunamadı. Seed verilerini kontrol edin."));

        User user = new User();
        user.setRoleId(researcherRoleId);
        user.setRoleName("RESEARCHER");
        user.setUsername(cleanUsername);
        user.setEmail(cleanEmail);
        user.setPasswordHash(BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_ROUNDS)));
        user.setFullName(fullName == null || fullName.isBlank() ? null : fullName.trim());
        user.setActive(true);

        return userDao.create(user);
    }

    public void logout() {
        SessionManager.clear();
    }

    public void ensureDefaultAdminExists(String username, String email, String rawPassword) {
        if (userDao.existsAnyAdmin()) {
            return;
        }

        int adminRoleId = userDao.findRoleIdByName("ADMIN")
                .orElseThrow(() -> new NotFoundException("ADMIN rolü bulunamadı. Seed verilerini kontrol edin."));

        User admin = new User();
        admin.setRoleId(adminRoleId);
        admin.setRoleName("ADMIN");
        admin.setUsername(validateUsername(username));
        admin.setEmail(validateEmail(email));
        admin.setPasswordHash(BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_ROUNDS)));
        admin.setFullName("System Administrator");
        admin.setActive(true);

        userDao.create(admin);
    }

    private String validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Kullanıcı adı boş bırakılamaz.");
        }
        String trimmed = username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 50) {
            throw new ValidationException("Kullanıcı adı 3 ile 50 karakter arasında olmalıdır.");
        }
        if (!trimmed.matches("^[a-zA-Z0-9._-]+$")) {
            throw new ValidationException("Kullanıcı adı yalnızca harf, rakam ve . _ - karakterlerini içerebilir.");
        }
        return trimmed;
    }

    private String validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("E-posta adresi boş bırakılamaz.");
        }
        String trimmed = email.trim();
        if (trimmed.length() > 120 || !trimmed.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("Geçerli bir e-posta adresi girin.");
        }
        return trimmed;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new ValidationException("Şifre en az 6 karakter olmalıdır.");
        }
    }
}