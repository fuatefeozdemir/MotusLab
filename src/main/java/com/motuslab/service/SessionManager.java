package com.motuslab.service;

import com.motuslab.exception.AuthorizationException;
import com.motuslab.model.User;

public final class SessionManager {

    private static User currentUser;

    private SessionManager() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static User requireCurrentUser() {
        if (currentUser == null) {
            throw new AuthorizationException("Bu işlem için giriş yapmanız gerekiyor.");
        }
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static void clear() {
        currentUser = null;
    }
}