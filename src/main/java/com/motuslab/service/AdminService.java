package com.motuslab.service;

import com.motuslab.dao.UserDao;
import com.motuslab.exception.AuthorizationException;
import com.motuslab.exception.NotFoundException;
import com.motuslab.exception.ValidationException;
import com.motuslab.model.User;

import java.util.List;

public class AdminService {

    private final UserDao userDao;

    public AdminService() {
        this(new UserDao());
    }

    public AdminService(UserDao userDao) {
        this.userDao = userDao;
    }

    public List<User> getAllUsers() {
        requireAdmin();
        return userDao.findAll();
    }

    public List<String> getAllRoleNames() {
        requireAdmin();
        return userDao.findAllRoleNames();
    }

    public void setUserActive(int targetUserId, boolean active) {
        User currentAdmin = requireAdmin();

        if (currentAdmin.getId() == targetUserId && !active) {
            throw new ValidationException("Kendi hesabınızı pasif duruma alamazsınız.");
        }

        userDao.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı."));

        userDao.updateActiveStatus(targetUserId, active);
    }

    public void changeUserRole(int targetUserId, String newRoleName) {
        User currentAdmin = requireAdmin();

        if (newRoleName == null || newRoleName.isBlank()) {
            throw new ValidationException("Geçerli bir rol seçin.");
        }

        String cleanRole = newRoleName.trim();
        if (currentAdmin.getId() == targetUserId && !"ADMIN".equalsIgnoreCase(cleanRole)) {
            throw new ValidationException("Kendi ADMIN rolünüzü düşüremezsiniz.");
        }

        userDao.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı."));

        List<String> validRoles = userDao.findAllRoleNames();
        if (!validRoles.contains(cleanRole)) {
            throw new ValidationException("Geçersiz rol: " + cleanRole);
        }

        userDao.updateRoleByName(targetUserId, cleanRole);
    }

    private User requireAdmin() {
        User currentUser = SessionManager.requireCurrentUser();
        if (!currentUser.isAdmin()) {
            throw new AuthorizationException("Bu işlem için ADMIN yetkisi gereklidir.");
        }
        return currentUser;
    }
}