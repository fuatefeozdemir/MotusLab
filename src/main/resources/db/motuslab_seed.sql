-- =========================================
-- MotusLab Başlangıç Verileri (Seed Data)
-- Tekrar çalıştırılabilir (Idempotent)
-- =========================================

-- 1. ROLLER
INSERT INTO roles (name, description)
VALUES
    ('ADMIN', 'Sistem yöneticisi; kullanıcı ve sistem ayarlarını yönetir.'),
    ('RESEARCHER', 'Araştırmacı; deney oluşturur, çalıştırır ve paylaşır.')
    ON CONFLICT (name) DO UPDATE
                              SET description = EXCLUDED.description;

-- 2. SİMÜLASYON TÜRLERİ
INSERT INTO simulation_types (code, name, description)
VALUES
    ('UNIFORM_MOTION', 'Düzgün Doğrusal Hareket', 'Sabit hızla tek boyutta (1B) doğrusal hareket modeli.'),
    ('CONSTANT_ACCEL', 'Sabit İvmeli Hareket', 'Sabit ivme altında tek boyutta (1B) doğrusal hareket modeli.'),
    ('PROJECTILE_2D', '2B Eğik Atış', 'Yerçekimi altında iki boyutta (2B) sürtünmesiz eğik atış modeli.')
    ON CONFLICT (code) DO UPDATE
                              SET name        = EXCLUDED.name,
                              description = EXCLUDED.description;

-- 3. PARAMETRE TANIMLARI

-- 3.1. Düzgün Doğrusal Hareket (UNIFORM_MOTION)
INSERT INTO parameter_definitions (simulation_type_id, code, name, unit, default_value, min_value, max_value, display_order)
SELECT st.id, p.code, p.name, p.unit, p.default_value, p.min_value, p.max_value, p.display_order
FROM simulation_types st
         CROSS JOIN (VALUES
                         ('initial_position', 'Başlangıç Konumu (x0)', 'm',   0.0,  -1000.0, 1000.0, 1),
                         ('velocity',         'Sabit Hız (v)',         'm/s', 10.0,  -500.0,  500.0, 2),
                         ('duration',         'Süre (t)',              's',   10.0,     0.1, 3600.0, 3),
                         ('time_step',        'Zaman Adımı (dt)',      's',    0.1,   0.001,   10.0, 4)
) AS p(code, name, unit, default_value, min_value, max_value, display_order)
WHERE st.code = 'UNIFORM_MOTION'
    ON CONFLICT (simulation_type_id, code) DO UPDATE
                                                  SET name          = EXCLUDED.name,
                                                  unit          = EXCLUDED.unit,
                                                  default_value = EXCLUDED.default_value,
                                                  min_value     = EXCLUDED.min_value,
                                                  max_value     = EXCLUDED.max_value,
                                                  display_order = EXCLUDED.display_order;

-- 3.2. Sabit İvmeli Hareket (CONSTANT_ACCEL)
INSERT INTO parameter_definitions (simulation_type_id, code, name, unit, default_value, min_value, max_value, display_order)
SELECT st.id, p.code, p.name, p.unit, p.default_value, p.min_value, p.max_value, p.display_order
FROM simulation_types st
         CROSS JOIN (VALUES
                         ('initial_position', 'Başlangıç Konumu (x0)', 'm',     0.0, -1000.0, 1000.0, 1),
                         ('initial_velocity', 'İlk Hız (v0)',          'm/s',   0.0,  -500.0,  500.0, 2),
                         ('acceleration',     'İvme (a)',              'm/s^2', 2.0,  -100.0,  100.0, 3),
                         ('duration',         'Süre (t)',              's',    10.0,     0.1, 3600.0, 4),
                         ('time_step',        'Zaman Adımı (dt)',      's',     0.1,   0.001,   10.0, 5)
) AS p(code, name, unit, default_value, min_value, max_value, display_order)
WHERE st.code = 'CONSTANT_ACCEL'
    ON CONFLICT (simulation_type_id, code) DO UPDATE
                                                  SET name          = EXCLUDED.name,
                                                  unit          = EXCLUDED.unit,
                                                  default_value = EXCLUDED.default_value,
                                                  min_value     = EXCLUDED.min_value,
                                                  max_value     = EXCLUDED.max_value,
                                                  display_order = EXCLUDED.display_order;

-- 3.3. 2B Eğik Atış (PROJECTILE_2D)
INSERT INTO parameter_definitions (simulation_type_id, code, name, unit, default_value, min_value, max_value, display_order)
SELECT st.id, p.code, p.name, p.unit, p.default_value, p.min_value, p.max_value, p.display_order
FROM simulation_types st
         CROSS JOIN (VALUES
                         ('initial_height', 'Başlangıç Yüksekliği (y0)', 'm',     0.0,   0.0, 1000.0, 1),
                         ('initial_speed',  'Fırlatma Hızı (v0)',        'm/s',  25.0,   0.1,  500.0, 2),
                         ('launch_angle',   'Atış Açısı',                'deg',  45.0,   0.0,   90.0, 3),
                         ('gravity',        'Yerçekimi İvmesi (g)',      'm/s^2', 9.81,  0.1,   50.0, 4),
                         ('time_step',      'Zaman Adımı (dt)',          's',     0.05, 0.001,   1.0, 5)
) AS p(code, name, unit, default_value, min_value, max_value, display_order)
WHERE st.code = 'PROJECTILE_2D'
    ON CONFLICT (simulation_type_id, code) DO UPDATE
                                                  SET name          = EXCLUDED.name,
                                                  unit          = EXCLUDED.unit,
                                                  default_value = EXCLUDED.default_value,
                                                  min_value     = EXCLUDED.min_value,
                                                  max_value     = EXCLUDED.max_value,
                                                  display_order = EXCLUDED.display_order;