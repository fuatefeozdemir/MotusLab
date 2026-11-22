<p align="center">
  <img src="src/main/resources/images/logo-full.png" alt="MotusLab Logo" width="240" />
</p>

# MotusLab — Deney ve Simülasyon Verisi Yönetim Sistemi

![Status](https://img.shields.io/badge/Status-In%20Progress-yellow.svg) ![Java](https://img.shields.io/badge/Java-25-blue.svg) ![JavaFX](https://img.shields.io/badge/GUI-JavaFX%2025-orange.svg) ![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL-336791.svg) ![Maven](https://img.shields.io/badge/Build-Maven-C71A36.svg)

**MotusLab**, fizik tabanlı hareket simülasyonlarının tanımlanmasını, parametrelerinin yönetilmesini, asenkron olarak çalıştırılmasını ve üretilen zaman serisi verilerinin ilişkisel veritabanında saklanıp analiz edilmesini sağlayan masaüstü bir yazılım çözümüdür. Katmanlı mimari, saf JDBC erişimi ve 3NF veritabanı tasarımı merkeze alınarak geliştirilmiştir.

---

## Temel Özellikler

- **Kullanıcı, Rol ve Paylaşım Yönetimi:** Sistemde `ADMIN` ve `RESEARCHER` rolleri bulunur; ilk kayıt olan kullanıcı otomatik olarak `ADMIN` atanır. Deneyler diğer kullanıcılarla hiyerarşik yetkilerle (`EDIT`, `RUN`, `VIEW`) paylaşılabilir. Parolalar BCrypt ile tuzlanarak saklanır.
- **Deney ve Çalıştırma Yaşam Döngüsü:** Her deney için parametreler düzenlenebilir, deneyler arşivlenebilir veya kalıcı olarak silinebilir. Çalıştırma başlatıldığında parametrelerin değişmez kopyası (snapshot) alınır ve simülasyon arayüzü dondurmadan arka planda (`JavaFX Task`) yürütülür.
- **Etkileşimli Grafikler ve Karşılaştırma:** Zaman serisi sonuçları yakınlaştırma, sıfırlama ve canlı koordinat okuması destekli grafiklerle sunulur. Aynı deneye ait iki tamamlanmış çalıştırma üst üste çizilerek metrik farkları (`B - A`) yan yana incelenebilir.
- **CSV Dışa Aktarma:** Tamamlanmış çalıştırmaların özet metrikleri ve tüm zaman serisi örnekleri UTF-8 CSV formatında dışa aktarılabilir.
- **Özel Masaüstü Arayüzü:** 5 sekmeli tek pencereli çalışma alanı (`Genel Bakış`, `Deneyler`, `Karşılaştırma`, `Arşiv`, `Profil/Yönetim`), görev çubuğunu koruyan özel pencere başlık çubuğu (`UNDECORATED`) ve `Inter` / `JetBrains Mono` tipografisi ile tasarlanmıştır.

---

## Desteklenen Simülasyon Modelleri

Tüm modeller `PhysicsModel` arayüzünü uygular. Yüksek örnek sayısında grafik performansı için çizim öncesi downsampling (maks. 500 nokta) uygulanırken veritabanında ve CSV çıktısında tüm örnekler korunur.

| Model | Kod | Temel Parametreler | Üretilen Metrikler |
| :--- | :--- | :--- | :--- |
| **Düzgün Doğrusal Hareket** | `UNIFORM_MOTION` | `initial_position`, `velocity`, `duration`, `time_step` | `total_time`, `final_position`, `displacement`, `max_speed` |
| **Sabit İvmeli Hareket** | `CONSTANT_ACCEL` | `initial_position`, `initial_velocity`, `acceleration`, `duration`, `time_step` | `total_time`, `final_position`, `displacement`, `final_velocity`, `max_speed` |
| **2B Eğik Atış** | `PROJECTILE_2D` | `initial_height`, `initial_speed`, `launch_angle`, `gravity`, `time_step` | `flight_time`, `range`, `max_height`, `impact_speed`, `max_speed` |

---

## Veritabanı Mimarisi

MotusLab veritabanı (`motuslab`), 3NF normalizasyon kurallarına uygun olarak **12 tablo**, **4 özel performans indeksi**, **1 Stored Function** ve **1 Trigger** ile PostgreSQL üzerinde tasarlanmıştır.

### Tablo Grupları

- **Kimlik ve Yetkilendirme:**
  - `roles`: Sistem rollerini (`ADMIN`, `RESEARCHER`) tutar.
  - `users`: Kullanıcı hesapları, BCrypt parola hash'leri ve aktiflik (`is_active`) bilgisini tutar.
- **Simülasyon Tanımları ve Deney Yönetimi:**
  - `simulation_types`: Sabit fizik modellerinin kataloğudur.
  - `parameter_definitions`: Her modele ait parametrelerin birimini, varsayılanını ve alt/üst sınırlarını (`CHECK(min_value <= default_value <= max_value)`) tanımlar.
  - `experiments`: Deney ana kayıtlarını, sahipliği ve arşiv (`is_archived`) durumunu tutar.
  - `experiment_members`: Paylaşılan deney üyelerini ve yetkilerini (`VIEW`, `RUN`, `EDIT`) saklar.
  - `experiment_parameters`: Deneyin güncel parametre değerlerini tutar; bileşik yabancı anahtarlar (`(experiment_id, simulation_type_id)` ve `(parameter_definition_id, simulation_type_id)`) ile bir deneye yalnızca kendi simülasyon türüne ait parametrelerin eklenmesini garanti eder.
- **Çalıştırma, Sonuç ve Denetim:**
  - `simulation_runs`: Deney bazlı sıralı `run_number`, durum (`CREATED`, `RUNNING`, `COMPLETED`, `FAILED`), ad ve not bilgilerini tutar.
  - `run_parameter_snapshots`: Çalıştırma anındaki parametrelerin değişmez kopyasıdır.
  - `run_samples`: Grafik ve CSV için zaman serisi verilerini (`time_seconds`, `position_x/y`, `velocity_x/y`) saklar.
  - `run_metrics`: Çalıştırma sonunda hesaplanan özet metrikleri tutar.
  - `run_status_history`: Çalıştırma durum geçişlerinin denetim (audit) kaydıdır; yalnızca trigger tarafından yazılır.

### Veritabanı Programlama Nesneleri

- **Stored Function — `sp_create_run_record(p_experiment_id, p_user_id)`:**
  Tek bir atomik çağrıda kullanıcının aktif olduğunu, deneyin arşivli olmadığını (`FOR UPDATE` satır kilidiyle) ve çalıştırma yetkisini doğrular; deney içi sıradaki `run_number` değerini hesaplayarak `CREATED` durumunda yeni çalıştırma kaydı açar ve güncel parametreleri `run_parameter_snapshots` tablosuna kopyalayarak `run_id` döndürür.
- **Trigger — `trg_simulation_run_status` (`fn_track_simulation_run_status`):**
  `simulation_runs` üzerindeki `INSERT` ve `UPDATE OF status` işlemlerini denetler. Geçersiz durum geçişlerini reddeder (`COMPLETED`/`FAILED` kayıtlar değiştirilemez; `CREATED` → `RUNNING`/`FAILED`; `RUNNING` → `COMPLETED`/`FAILED`) ve geçerli her geçişi `run_status_history` tablosuna kaydeder.

### Veri Bütünlüğü, Silme Politikası ve İndeksler

- **`ON DELETE RESTRICT`:** `roles`, `users`, `simulation_types`, `parameter_definitions` ve `experiments` kayıtları bağlı verileri varken doğrudan silinemez. Deney silme işlemi `ExperimentDao.delete()` içinde tek bir JDBC transaction ile kontrollü olarak yürütülür.
- **`ON DELETE CASCADE`:** Bir çalıştırma (`simulation_runs`) silindiğinde bağlı `run_parameter_snapshots`, `run_samples`, `run_metrics` ve `run_status_history` kayıtları otomatik temizlenir.
- **Performans İndeksleri:** `idx_experiments_owner_archived`, `idx_experiment_members_user`, `idx_simulation_runs_exp_status` ve `idx_run_status_history_run_time` indeksleri ile listeleme, paylaşım, karşılaştırma ve durum geçmişi sorguları hızlandırılmıştır.

---

## Rol ve Yetki Matrisi

| İşlem | Admin | Deney Sahibi | `EDIT` Üyesi | `RUN` Üyesi | `VIEW` Üyesi |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Deney oluşturma** | Evet | Evet | — | — | — |
| **Deney bilgisi ve parametre düzenleme** | Evet *(arşivli değilse)* | Evet *(arşivli değilse)* | Evet *(arşivli değilse)* | Hayır | Hayır |
| **Simülasyon çalıştırma** | Sahip/Üye ise | Evet *(arşivli değilse)* | Evet *(arşivli değilse)* | Evet *(arşivli değilse)* | Hayır |
| **Arşivleme / Paylaşım / Kalıcı silme** | Evet | Evet | Hayır | Hayır | Hayır |
| **Kullanıcı yönetimi (rol & aktif/pasif)** | Evet | Hayır | Hayır | Hayır | Hayır |

---

## Kullanılan Teknolojiler

- **Programlama Dili:** Java 25
- **Kullanıcı Arayüzü:** JavaFX 25 (Programatik UI), AtlantaFX, Ikonli (Feather), Özel CSS
- **Veritabanı:** PostgreSQL & Saf JDBC
- **Güvenlik:** jBCrypt (BCrypt parola hash'leme), `PreparedStatement`
- **Test & Derleme:** JUnit 5 (`junit-jupiter`), Maven

---

## Proje Yapısı

```text
MotusLab/
├── src/
│   ├── main/
│   │   ├── java/com/motuslab/
│   │   │   ├── config/      # PostgreSQL bağlantı yönetimi
│   │   │   ├── dao/         # Saf JDBC CRUD, transaction ve stored function çağrıları
│   │   │   ├── exception/   # Özel uygulama istisna hiyerarşisi
│   │   │   ├── model/       # Veritabanı varlık modelleri
│   │   │   ├── service/     # İş kuralları, oturum, yetkilendirme ve CSV dışa aktarma
│   │   │   ├── simulation/  # Fizik modelleri ve simülasyon motoru
│   │   │   └── ui/          # JavaFX ekranları, workspace bileşenleri ve diyaloglar
│   │   └── resources/
│   │       ├── css/         # motuslab.css tasarım sistemi
│   │       ├── db/          # motuslab_schema.sql ve motuslab_seed.sql
│   │       ├── fonts/       # Inter ve JetBrains Mono font dosyaları
│   │       └── images/      # Uygulama logoları
│   └── test/                # JUnit 5 fizik motoru birim testleri
└── pom.xml
```

---

## Kurulum ve Çalıştırma

### Gereksinimler
- Java 25
- Maven 3.9+
- PostgreSQL 15+

### 1. Veritabanını Hazırlama
PostgreSQL üzerinde `motuslab` veritabanını oluşturduktan sonra şema ve başlangıç verilerini sırasıyla çalıştırın:

```bash
psql -U postgres -d motuslab -f src/main/resources/db/motuslab_schema.sql
psql -U postgres -d motuslab -f src/main/resources/db/motuslab_seed.sql
```

### 2. Bağlantı Yapılandırması
`src/main/resources/config.properties.example` dosyasını `src/main/resources/config.properties` olarak kopyalayıp kendi PostgreSQL bilgilerinizi girin:

```properties
db.url=jdbc:postgresql://localhost:5432/motuslab
db.user=postgres
db.password=sifreniz
```

### 3. Test ve Çalıştırma

```bash
# Birim testlerini çalıştır
mvn test

# Uygulamayı başlat
mvn javafx:run
```
