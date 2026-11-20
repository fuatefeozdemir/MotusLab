-- =========================================
-- MotusLab Veritabanı Şeması (PostgreSQL)
-- =========================================

-- =========================================
-- GRUP 1: KİMLİK VE YETKİLENDİRME
-- =========================================

CREATE TABLE roles (
                       id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       name        VARCHAR(30) NOT NULL UNIQUE,
                       description TEXT,
                       CONSTRAINT chk_roles_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE TABLE users (
                       id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       role_id       INTEGER NOT NULL,
                       username      VARCHAR(50) NOT NULL UNIQUE,
                       email         VARCHAR(120) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       full_name     VARCHAR(100),
                       is_active     BOOLEAN NOT NULL DEFAULT TRUE,
                       last_login_at TIMESTAMPTZ,
                       created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT fk_users_role
                           FOREIGN KEY (role_id) REFERENCES roles(id)
                               ON DELETE RESTRICT,
                       CONSTRAINT chk_users_username_not_empty CHECK (LENGTH(TRIM(username)) > 0),
                       CONSTRAINT chk_users_email_not_empty    CHECK (LENGTH(TRIM(email)) > 0),
                       CONSTRAINT chk_users_password_not_empty CHECK (LENGTH(TRIM(password_hash)) > 0)
);

-- =========================================
-- GRUP 2: SİMÜLASYON TANIMLARI VE DENEY YÖNETİMİ
-- =========================================

CREATE TABLE simulation_types (
                                  id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                  code        VARCHAR(50) NOT NULL UNIQUE,
                                  name        VARCHAR(100) NOT NULL,
                                  description TEXT,

                                  CONSTRAINT chk_sim_types_code_not_empty CHECK (LENGTH(TRIM(code)) > 0),
                                  CONSTRAINT chk_sim_types_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE TABLE parameter_definitions (
                                       id                 INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                       simulation_type_id INTEGER NOT NULL,
                                       code               VARCHAR(50) NOT NULL,
                                       name               VARCHAR(100) NOT NULL,
                                       unit               VARCHAR(20) NOT NULL,
                                       default_value      DOUBLE PRECISION NOT NULL,
                                       min_value          DOUBLE PRECISION NOT NULL,
                                       max_value          DOUBLE PRECISION NOT NULL,
                                       display_order      INTEGER NOT NULL DEFAULT 0,

                                       CONSTRAINT fk_param_defs_sim_type
                                           FOREIGN KEY (simulation_type_id) REFERENCES simulation_types(id)
                                               ON DELETE RESTRICT,
                                       CONSTRAINT uq_param_defs_type_code UNIQUE (simulation_type_id, code),
                                       CONSTRAINT uq_param_defs_id_type   UNIQUE (id, simulation_type_id),
                                       CONSTRAINT chk_param_defs_bounds   CHECK (min_value <= default_value AND default_value <= max_value),
                                       CONSTRAINT chk_param_defs_order    CHECK (display_order >= 0),
                                       CONSTRAINT chk_param_defs_code     CHECK (LENGTH(TRIM(code)) > 0),
                                       CONSTRAINT chk_param_defs_name     CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE TABLE experiments (
                             id                 INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             owner_id           INTEGER NOT NULL,
                             simulation_type_id INTEGER NOT NULL,
                             title              VARCHAR(150) NOT NULL,
                             description        TEXT,
                             is_archived        BOOLEAN NOT NULL DEFAULT FALSE,
                             created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_experiments_owner
                                 FOREIGN KEY (owner_id) REFERENCES users(id)
                                     ON DELETE RESTRICT,
                             CONSTRAINT fk_experiments_sim_type
                                 FOREIGN KEY (simulation_type_id) REFERENCES simulation_types(id)
                                     ON DELETE RESTRICT,
                             CONSTRAINT uq_experiments_id_type UNIQUE (id, simulation_type_id),
                             CONSTRAINT chk_experiments_title  CHECK (LENGTH(TRIM(title)) > 0)
);

CREATE TABLE experiment_members (
                                    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                    experiment_id INTEGER NOT NULL,
                                    user_id       INTEGER NOT NULL,
                                    permission    VARCHAR(10) NOT NULL,
                                    added_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_exp_members_experiment
                                        FOREIGN KEY (experiment_id) REFERENCES experiments(id)
                                            ON DELETE RESTRICT,
                                    CONSTRAINT fk_exp_members_user
                                        FOREIGN KEY (user_id) REFERENCES users(id)
                                            ON DELETE RESTRICT,
                                    CONSTRAINT uq_exp_members_exp_user UNIQUE (experiment_id, user_id),
                                    CONSTRAINT chk_exp_members_perm    CHECK (permission IN ('VIEW', 'RUN', 'EDIT'))
);

CREATE TABLE experiment_parameters (
                                       id                      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                       experiment_id           INTEGER NOT NULL,
                                       simulation_type_id      INTEGER NOT NULL,
                                       parameter_definition_id INTEGER NOT NULL,
                                       value                   DOUBLE PRECISION NOT NULL,

                                       CONSTRAINT fk_exp_params_experiment
                                           FOREIGN KEY (experiment_id, simulation_type_id)
                                               REFERENCES experiments(id, simulation_type_id)
                                               ON DELETE RESTRICT,
                                       CONSTRAINT fk_exp_params_param_def
                                           FOREIGN KEY (parameter_definition_id, simulation_type_id)
                                               REFERENCES parameter_definitions(id, simulation_type_id)
                                               ON DELETE RESTRICT,
                                       CONSTRAINT uq_exp_params_exp_def UNIQUE (experiment_id, parameter_definition_id)
);

-- =========================================
-- GRUP 3: ÇALIŞTIRMA, SONUÇ VE DENETİM
-- =========================================

CREATE TABLE simulation_runs (
                                 id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 experiment_id INTEGER NOT NULL,
                                 triggered_by  INTEGER NOT NULL,
                                 status        VARCHAR(20) NOT NULL DEFAULT 'CREATED',
                                 created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 started_at    TIMESTAMPTZ,
                                 finished_at   TIMESTAMPTZ,
                                 error_message TEXT,

                                 CONSTRAINT fk_sim_runs_experiment
                                     FOREIGN KEY (experiment_id) REFERENCES experiments(id)
                                         ON DELETE RESTRICT,
                                 CONSTRAINT fk_sim_runs_triggered_by
                                     FOREIGN KEY (triggered_by) REFERENCES users(id)
                                         ON DELETE RESTRICT,
                                 CONSTRAINT chk_sim_runs_status
                                     CHECK (status IN ('CREATED', 'RUNNING', 'COMPLETED', 'FAILED')),
                                 CONSTRAINT chk_sim_runs_time_order
                                     CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);

CREATE TABLE run_parameter_snapshots (
                                         id                      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                         run_id                  INTEGER NOT NULL,
                                         parameter_definition_id INTEGER NOT NULL,
                                         value                   DOUBLE PRECISION NOT NULL,

                                         CONSTRAINT fk_run_snapshots_run
                                             FOREIGN KEY (run_id) REFERENCES simulation_runs(id)
                                                 ON DELETE CASCADE,
                                         CONSTRAINT fk_run_snapshots_param_def
                                             FOREIGN KEY (parameter_definition_id) REFERENCES parameter_definitions(id)
                                                 ON DELETE RESTRICT,
                                         CONSTRAINT uq_run_snapshots_run_def UNIQUE (run_id, parameter_definition_id)
);

CREATE TABLE run_samples (
                             id           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             run_id       INTEGER NOT NULL,
                             sample_index INTEGER NOT NULL,
                             time_seconds DOUBLE PRECISION NOT NULL,
                             position_x   DOUBLE PRECISION NOT NULL,
                             position_y   DOUBLE PRECISION,
                             velocity_x   DOUBLE PRECISION NOT NULL,
                             velocity_y   DOUBLE PRECISION,

                             CONSTRAINT fk_run_samples_run
                                 FOREIGN KEY (run_id) REFERENCES simulation_runs(id)
                                     ON DELETE CASCADE,
                             CONSTRAINT uq_run_samples_run_index UNIQUE (run_id, sample_index),
                             CONSTRAINT chk_run_samples_index    CHECK (sample_index >= 0),
                             CONSTRAINT chk_run_samples_time     CHECK (time_seconds >= 0)
);

CREATE TABLE run_metrics (
                             id           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             run_id       INTEGER NOT NULL,
                             metric_code  VARCHAR(50) NOT NULL,
                             metric_value DOUBLE PRECISION NOT NULL,
                             unit         VARCHAR(20) NOT NULL,

                             CONSTRAINT fk_run_metrics_run
                                 FOREIGN KEY (run_id) REFERENCES simulation_runs(id)
                                     ON DELETE CASCADE,
                             CONSTRAINT uq_run_metrics_run_code UNIQUE (run_id, metric_code),
                             CONSTRAINT chk_run_metrics_code    CHECK (LENGTH(TRIM(metric_code)) > 0)
);

CREATE TABLE run_status_history (
                                    id         INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                    run_id     INTEGER NOT NULL,
                                    old_status VARCHAR(20),
                                    new_status VARCHAR(20) NOT NULL,
                                    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_run_status_history_run
                                        FOREIGN KEY (run_id) REFERENCES simulation_runs(id)
                                            ON DELETE CASCADE,
                                    CONSTRAINT chk_run_history_old_status
                                        CHECK (old_status IS NULL OR old_status IN ('CREATED', 'RUNNING', 'COMPLETED', 'FAILED')),
                                    CONSTRAINT chk_run_history_new_status
                                        CHECK (new_status IN ('CREATED', 'RUNNING', 'COMPLETED', 'FAILED'))
);

-- =========================================
-- PERFORMANS İNDEKSLERİ
-- =========================================

CREATE INDEX idx_experiments_owner_archived
    ON experiments (owner_id, is_archived);

CREATE INDEX idx_experiment_members_user
    ON experiment_members (user_id);

CREATE INDEX idx_simulation_runs_exp_status
    ON simulation_runs (experiment_id, status);

CREATE INDEX idx_run_status_history_run_time
    ON run_status_history (run_id, changed_at);

-- =========================================
-- TRIGGER: ÇALIŞTIRMA DURUM TAKİBİ VE KONTROLÜ
-- =========================================

CREATE OR REPLACE FUNCTION fn_track_simulation_run_status()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO run_status_history (run_id, old_status, new_status)
        VALUES (NEW.id, NULL, NEW.status);
RETURN NEW;
END IF;

    IF OLD.status = NEW.status THEN
        RETURN NEW;
END IF;

    IF OLD.status IN ('COMPLETED', 'FAILED') THEN
        RAISE EXCEPTION 'Tamamlanmış veya başarısız olmuş bir çalıştırmanın durumu değiştirilemez (% -> %).',
            OLD.status, NEW.status;
END IF;

    IF OLD.status = 'CREATED' AND NEW.status NOT IN ('RUNNING', 'FAILED') THEN
        RAISE EXCEPTION 'Geçersiz durum geçişi: % -> %', OLD.status, NEW.status;
END IF;

    IF OLD.status = 'RUNNING' AND NEW.status NOT IN ('COMPLETED', 'FAILED') THEN
        RAISE EXCEPTION 'Geçersiz durum geçişi: % -> %', OLD.status, NEW.status;
END IF;

INSERT INTO run_status_history (run_id, old_status, new_status)
VALUES (NEW.id, OLD.status, NEW.status);

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_simulation_run_status
    AFTER INSERT OR UPDATE OF status ON simulation_runs
    FOR EACH ROW
    EXECUTE FUNCTION fn_track_simulation_run_status();

-- =========================================
-- STORED FUNCTION: ÇALIŞTIRMA BAŞLATMA
-- =========================================

CREATE OR REPLACE FUNCTION sp_create_run_record(
    p_experiment_id INTEGER,
    p_user_id       INTEGER
)
RETURNS INTEGER AS $$
DECLARE
v_owner_id      INTEGER;
    v_is_archived   BOOLEAN;
    v_user_active   BOOLEAN;
    v_has_perm      BOOLEAN;
    v_run_id        INTEGER;
BEGIN
SELECT is_active INTO v_user_active
FROM users
WHERE id = p_user_id;

IF NOT FOUND OR v_user_active = FALSE THEN
        RAISE EXCEPTION 'Kullanıcı bulunamadı veya hesap pasif durumda (user_id: %).', p_user_id;
END IF;

SELECT owner_id, is_archived INTO v_owner_id, v_is_archived
FROM experiments
WHERE id = p_experiment_id;

IF NOT FOUND THEN
        RAISE EXCEPTION 'Deney bulunamadı (experiment_id: %).', p_experiment_id;
END IF;

    IF v_is_archived = TRUE THEN
        RAISE EXCEPTION 'Arşivlenmiş bir deney için yeni çalıştırma başlatılamaz.';
END IF;

    IF p_user_id = v_owner_id THEN
        v_has_perm := TRUE;
ELSE
SELECT EXISTS (
    SELECT 1
    FROM experiment_members
    WHERE experiment_id = p_experiment_id
      AND user_id = p_user_id
      AND permission IN ('RUN', 'EDIT')
) INTO v_has_perm;
END IF;

    IF NOT v_has_perm THEN
        RAISE EXCEPTION 'Bu deneyi çalıştırma yetkiniz yok.';
END IF;

INSERT INTO simulation_runs (experiment_id, triggered_by, status)
VALUES (p_experiment_id, p_user_id, 'CREATED')
    RETURNING id INTO v_run_id;

INSERT INTO run_parameter_snapshots (run_id, parameter_definition_id, value)
SELECT v_run_id, parameter_definition_id, value
FROM experiment_parameters
WHERE experiment_id = p_experiment_id;

RETURN v_run_id;
END;
$$ LANGUAGE plpgsql;