
---

# MotusLab — Veritabanı Mimarisi ve Veri Sözlüğü

**Veritabanı:** PostgreSQL (`motuslab`)

**Kapsam:** 12 tablo, veri bütünlüğü kısıtları, 4 özel indeks, 1 Trigger ve 1 Stored Function.

---

## 1. Tablolar ve İlişkiler

### Grup 1: Kimlik ve Yetkilendirme

| Tablo | Görevi | Temel Sütunlar ve Kısıtlar |
| --- | --- | --- |
| **`roles`** | Sistem rollerini (`ADMIN`, `RESEARCHER`) tutar. | `id` (PK), `name` (UNIQUE, boş olamaz), `description` (opsiyonel) |
| **`users`** | Kullanıcı hesaplarını tutar. | `id` (PK), `role_id` (FK → `roles`, `RESTRICT`), `username` (UNIQUE), `email` (UNIQUE), `password_hash`, `full_name` (opsiyonel), `is_active` (varsayılan `TRUE`), `last_login_at`, `created_at`, `updated_at` |

### Grup 2: Simülasyon Tanımları ve Deney Yönetimi

| Tablo | Görevi | Temel Sütunlar ve Kısıtlar |
| --- | --- | --- |
| **`simulation_types`** | Desteklenen fizik modellerinin kataloğudur (`UNIFORM_MOTION`, `CONSTANT_ACCEL`, `PROJECTILE_2D`). | `id` (PK), `code` (UNIQUE), `name`, `description` |
| **`parameter_definitions`** | Her simülasyon türünün beklediği parametreleri ve sınırlarını tanımlar. | `id` (PK), `simulation_type_id` (FK → `simulation_types`, `RESTRICT`), `code`, `name`, `unit`, `default_value`, `min_value`, `max_value`, `display_order` <br>

<br>• `UNIQUE(simulation_type_id, code)` <br>

<br>• `CHECK(min_value <= default_value <= max_value)` |
| **`experiments`** | Kullanıcıların oluşturduğu deney ana kayıtlarıdır. Fiziksel silinmez, arşivlenir. | `id` (PK), `owner_id` (FK → `users`, `RESTRICT`), `simulation_type_id` (FK → `simulation_types`, `RESTRICT`), `title` (boş olamaz), `description`, `is_archived` (varsayılan `FALSE`), `created_at`, `updated_at` |
| **`experiment_members`** | Deney sahibinin dışındaki kullanıcıların paylaşım yetkilerini tutar. Fiziksel silinebilir. | `id` (PK), `experiment_id` (FK → `experiments`, `RESTRICT`), `user_id` (FK → `users`, `RESTRICT`), `permission` (`VIEW`, `RUN`, `EDIT`), `added_at` <br>

<br>• `UNIQUE(experiment_id, user_id)` |
| **`experiment_parameters`** | Bir deneyin o anki düzenlenebilir parametre değerleridir. | `id` (PK), `experiment_id`, `simulation_type_id`, `parameter_definition_id`, `value` <br>

<br>• **Bileşik FK:** `(experiment_id, simulation_type_id)` → `experiments` ve `(parameter_definition_id, simulation_type_id)` → `parameter_definitions` <br>

<br>• `UNIQUE(experiment_id, parameter_definition_id)` |

### Grup 3: Çalıştırma, Sonuç ve Denetim

| Tablo | Görevi | Temel Sütunlar ve Kısıtlar |
| --- | --- | --- |
| **`simulation_runs`** | Bir deneyin her bir çalıştırmasının (**Run**) ana kaydıdır. Fiziksel silinebilir. | `id` (PK), `experiment_id` (FK → `experiments`, `RESTRICT`), `triggered_by` (FK → `users`, `RESTRICT`), `status` (`CREATED`, `RUNNING`, `COMPLETED`, `FAILED`), `created_at`, `started_at`, `finished_at`, `error_message` <br>

<br>• `CHECK(finished_at >= started_at)` |
| **`run_parameter_snapshots`** | Çalıştırma anındaki parametrelerin değişmez kopyasıdır. | `id` (PK), `run_id` (FK → `simulation_runs`, `CASCADE`), `parameter_definition_id` (FK → `parameter_definitions`, `RESTRICT`), `value` <br>

<br>• `UNIQUE(run_id, parameter_definition_id)` |
| **`run_samples`** | Grafik çizimi için zaman serisi noktalarını tutar. | `id` (PK), `run_id` (FK → `simulation_runs`, `CASCADE`), `sample_index` (`>= 0`), `time_seconds` (`>= 0`), `position_x`, `position_y` (1B modellerde NULL), `velocity_x`, `velocity_y` (1B modellerde NULL) <br>

<br>• `UNIQUE(run_id, sample_index)` |
| **`run_metrics`** | Çalıştırma sonunda hesaplanan özet metrikleri standart kodlarla tutar. | `id` (PK), `run_id` (FK → `simulation_runs`, `CASCADE`), `metric_code`, `metric_value`, `unit` <br>

<br>• `UNIQUE(run_id, metric_code)` |
| **`run_status_history`** | Çalıştırma durum geçişlerinin denetim (log) tablosudur. Yalnızca trigger tarafından doldurulur. | `id` (PK), `run_id` (FK → `simulation_runs`, `CASCADE`), `old_status` (ilk kayıtta NULL), `new_status`, `changed_at` |

---

## 2. Silme ve Bütünlük Politikaları

* **`ON DELETE RESTRICT`:** `roles`, `users`, `simulation_types`, `parameter_definitions` ve `experiments` tabloları bağlı kayıtları varken silinemez. Deneyler silinmek yerine `is_archived = TRUE` ile arşivlenir.
* **`ON DELETE CASCADE`:** `simulation_runs` tablosundan bir çalıştırma silindiğinde ona bağlı `run_parameter_snapshots`, `run_samples`, `run_metrics` ve `run_status_history` kayıtları otomatik silinir.
* **Parametre Türü Koruması:** `experiment_parameters` tablosundaki bileşik yabancı anahtarlar sayesinde bir deneye yalnızca kendi simülasyon türüne ait parametreler eklenebilir.

---

## 3. Veritabanı Programlama Nesneleri

### Trigger: `trg_simulation_run_status` (`fn_track_simulation_run_status`)

* **Çalışma Zamanı:** `simulation_runs` tablosunda `AFTER INSERT OR UPDATE OF status`.
* **İşlevi:**
1. Yeni çalıştırma eklendiğinde (`INSERT`) `run_status_history` tablosuna `NULL -> CREATED` kaydı atar.
2. Geçersiz durum geçişlerini engeller ve işlemi geri alır (rollback):
* `COMPLETED` veya `FAILED` durumundaki kayıtlar değiştirilemez.
* `CREATED` yalnızca `RUNNING` veya `FAILED` olabilir.
* `RUNNING` yalnızca `COMPLETED` veya `FAILED` olabilir.


3. Geçerli her geçişi tarih/saat bilgisiyle `run_status_history` tablosuna yazar.



### Stored Function: `sp_create_run_record(p_experiment_id, p_user_id)`

* **Dönüş Tipi:** `INTEGER` (oluşturulan `run_id`).
* **Java Çağrımı:** `SELECT sp_create_run_record(?, ?)`
* **İşlevi (tek transaction içinde):**
1. Kullanıcının var olduğunu ve `is_active = TRUE` olduğunu doğrular.
2. Deneyin var olduğunu ve `is_archived = FALSE` olduğunu doğrular.
3. Kullanıcının yetkisini kontrol eder (deney sahibi veya `experiment_members` tablosunda `RUN`/`EDIT` yetkisi).
4. `simulation_runs` tablosuna `CREATED` durumunda yeni kayıt ekler.
5. `experiment_parameters` tablosundaki güncel değerleri `run_parameter_snapshots` tablosuna kopyalar.
6. Oluşan `run_id` değerini döndürür.



---

## 4. Performans İndeksleri

| İndeks Adı | Tablo ve Sütunlar | Amaç |
| --- | --- | --- |
| `idx_experiments_owner_archived` | `experiments(owner_id, is_archived)` | Kullanıcının aktif/arşivli deneylerini hızlı listeleme |
| `idx_experiment_members_user` | `experiment_members(user_id)` | Kullanıcıyla paylaşılan deneyleri hızlı getirme |
| `idx_simulation_runs_exp_status` | `simulation_runs(experiment_id, status)` | Deney geçmişi ve `COMPLETED` karşılaştırma sorguları |
| `idx_run_status_history_run_time` | `run_status_history(run_id, changed_at)` | Durum geçmişini kronolojik çekme |
| `uq_run_samples_run_index` *(otomatik)* | `run_samples(run_id, sample_index)` | Grafik zaman serilerini sıralı ve hızlı okuma |