package com.motuslab.service;

import com.motuslab.dao.ExperimentDao;
import com.motuslab.dao.ExperimentMemberDao;
import com.motuslab.dao.UserDao;
import com.motuslab.exception.AuthorizationException;
import com.motuslab.exception.NotFoundException;
import com.motuslab.exception.ValidationException;
import com.motuslab.model.Experiment;
import com.motuslab.model.ExperimentMember;
import com.motuslab.model.User;

import java.util.List;
import java.util.Set;

public class ExperimentMemberService {

    private static final Set<String> VALID_PERMISSIONS = Set.of("VIEW", "RUN", "EDIT");

    private final ExperimentMemberDao memberDao;
    private final ExperimentDao experimentDao;
    private final UserDao userDao;

    public ExperimentMemberService() {
        this(new ExperimentMemberDao(), new ExperimentDao(), new UserDao());
    }

    public ExperimentMemberService(
            ExperimentMemberDao memberDao,
            ExperimentDao experimentDao,
            UserDao userDao
    ) {
        this.memberDao = memberDao;
        this.experimentDao = experimentDao;
        this.userDao = userDao;
    }

    public List<ExperimentMember> getMembers(int experimentId) {
        User currentUser = SessionManager.requireCurrentUser();
        Experiment experiment = experimentDao.findById(experimentId)
                .orElseThrow(() -> new NotFoundException("Deney bulunamadı."));

        requireOwnerOrAdmin(experiment, currentUser);
        return memberDao.findByExperimentId(experimentId);
    }

    public void addOrUpdateMember(int experimentId, String username, String permission) {
        User currentUser = SessionManager.requireCurrentUser();
        Experiment experiment = experimentDao.findById(experimentId)
                .orElseThrow(() -> new NotFoundException("Deney bulunamadı."));

        requireOwnerOrAdmin(experiment, currentUser);

        if (username == null || username.isBlank()) {
            throw new ValidationException("Kullanıcı adı boş bırakılamaz.");
        }
        if (permission == null || !VALID_PERMISSIONS.contains(permission)) {
            throw new ValidationException("Geçersiz yetki türü. (VIEW, RUN veya EDIT olmalı)");
        }

        User targetUser = userDao.findByUsername(username.trim())
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı: " + username.trim()));

        if (targetUser.getId() == experiment.getOwnerId()) {
            throw new ValidationException("Deney sahibi zaten tam yetkiye sahiptir.");
        }

        memberDao.upsertMember(experimentId, targetUser.getId(), permission);
    }

    public void removeMember(int experimentId, int targetUserId) {
        User currentUser = SessionManager.requireCurrentUser();
        Experiment experiment = experimentDao.findById(experimentId)
                .orElseThrow(() -> new NotFoundException("Deney bulunamadı."));

        requireOwnerOrAdmin(experiment, currentUser);
        memberDao.removeMember(experimentId, targetUserId);
    }

    /**
     * Mevcut kullanıcının deney üzerindeki etkin yetkisini döner:
     * OWNER, EDIT, RUN, VIEW veya NONE.
     */
    public String getEffectivePermission(Experiment experiment) {
        User currentUser = SessionManager.requireCurrentUser();
        if (experiment.getOwnerId() == currentUser.getId() || currentUser.isAdmin()) {
            return "OWNER";
        }
        return memberDao.findPermission(experiment.getId(), currentUser.getId()).orElse("NONE");
    }

    private void requireOwnerOrAdmin(Experiment experiment, User user) {
        if (experiment.getOwnerId() != user.getId() && !user.isAdmin()) {
            throw new AuthorizationException("Bu deneyin paylaşım ayarlarını yalnızca deney sahibi veya admin yönetebilir.");
        }
    }
}