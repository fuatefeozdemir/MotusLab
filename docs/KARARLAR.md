# MotusLab — Proje ve Mimari Kararlar Dokümanı

Bu doküman, **MotusLab – Deney ve Simülasyon Verisi Yönetim Sistemi** projesinin ilk sürümü (Veritabanı Yönetim Sistemleri ders projesi) için uygulanan kapsam, teknoloji yığını, yetkilendirme kuralları, veritabanı tasarımı, simülasyon sınırları ve katmanlı mimari kararlarını belgeler.

---

## 1. Proje Kapsamı ve Aşamalandırma

* **Aşama 1 (Aktif Kapsam — Ders Projesi):** Yerel bilgisayarda çalışan, PostgreSQL veritabanına doğrudan bağlanan, dersin tüm veritabanı ve uygulama gereksinimlerini eksiksiz karşılayan masaüstü uygulaması.
* **Aşama 2 (Gelecek Kapsam — Şimdilik Dışarıda):** Bulut veritabanı (ör. Supabase), ayrı API sunucusu, uzaktan çoklu kullanıcı eşzamanlı çalışması ve ileri seviye simülasyon motorları.
* **Katman İzolasyonu Kuralı:** Veritabanı erişimi yalnızca `dao` katmanında, iş kuralları ve yetki kontrolleri `service` katmanında, fiziksel hesaplamalar ise yalnızca `simulation` katmanında izole edilir. Arayüz (`ui`) sınıfları doğrudan SQL sorgusu veya fizik formülü barındırmaz.

### İlk Sürümde Kapsam Dışı Tutulanlar
İlk sürümde gereksiz karmaşıklık yaratmamak için aşağıdaki yapılar eklenmemiştir:
* HikariCP ve bağlantı havuzu
* Spring Boot ve REST API
* ORM araçları (Hibernate / JPA) ve Dependency Injection framework'leri
* Parametre preset (şablon) sistemi ve dinamik eklenti yükleyici
* Karmaşık loglama/izleme altyapısı ve bulut dağıtımı

---

## 2. Teknoloji Yığını, Güvenlik ve Bağımlılıklar

| Alan | Seçilen Teknoloji | Tercih Nedeni |
| :--- | :--- | :--- |
| **Programlama Dili** | Java 25 | Ders zorunluluğu, güçlü tip güvenliği, `record` desteği ve çapraz platform çalışma. |
| **Masaüstü Arayüzü** | JavaFX 25 (Programatik UI) | FXML/Scene Builder karmaşıklığı olmadan bileşen ve diyalogların doğrudan tip güvenli Java koduyla modüler yönetimi. |
| **Arayüz Tasarımı** | AtlantaFX + Ikonli | Ekstra karmaşık CSS yazmadan modern tema ve vektör ikon desteği. |
| **Veritabanı** | PostgreSQL (Yerel) | Ders zorunluluğu, güçlü kısıt, stored function ve trigger desteği. |
| **Veritabanı Erişimi** | Saf JDBC | SQL, Stored Function, Trigger ve transaction yönetiminde tam kontrol. |
| **Parola Güvenliği** | jBCrypt (BCrypt) | Otomatik tuzlama (salt) ile güvenli parola saklama. |
| **Test Altyapısı** | JUnit 5 (`junit-jupiter`) | Fizik motoru hesaplamalarının ve validasyon kurallarının otomatik birim testleri. |
| **Bağımlılık Yönetimi** | Maven (`pom.xml`) | Kütüphane, derleme ve test süreçlerinin standart yönetimi. |

### 2.1. Maven (`pom.xml`) Bağımlılıkları
* **JavaFX Controls & FXML:** Arayüz bileşenleri ve `LineChart` grafikleri.
* **PostgreSQL JDBC Driver:** Veritabanı bağlantısı.
* **jBCrypt:** Parola hash üretimi ve doğrulama.
* **AtlantaFX:** Uygulama teması.
* **Ikonli (`ikonli-javafx`, `ikonli-feather-pack`):** Vektör ikon setleri.
* **JUnit Jupiter (`scope: test`):** Birim testleri ve `maven-surefire-plugin`.

### 2.2. Veritabanı Bağlantısı ve Yapılandırma Güvenliği
* `config.DatabaseConnection` her ihtiyaç duyulduğunda yeni bir `Connection` üretir; tek bir statik bağlantı uygulama boyunca paylaşılmaz.
* Tüm DAO metotları bağlantıyı `try-with-resources` bloğunda açıp kapatır ve SQL enjeksiyonuna karşı `PreparedStatement` kullanır.
* Çok adımlı yazma/silme işlemleri (deney oluşturma, parametre güncelleme, deney silme) `conn.setAutoCommit(false)` ile transaction içinde yürütülür; hata durumunda `rollback` yapılır.
* Veritabanı bağlantı bilgileri kaynak kodda tutulmaz; `src/main/resources/config.properties` dosyasından okunur. Bu dosya `.gitignore` içinde yer alır; repoda şifresiz `src/main/resources/config.properties.example` şablonu paylaşılır.

### 2.3. Parola, Oturum ve Hata Yönetimi
* **Parola Güvenliği:** Parolalar açık metin olarak saklanmaz; `AuthService` içinde BCrypt ile hash'lenir ve doğrulanır. Pasif (`is_active = false`) kullanıcılar doğru parola girse bile giriş yapamaz.
* **Oturum Yönetimi:** Aktif kullanıcı bilgisi `service.SessionManager` üzerinden tutulur; yetki gerektiren tüm servis metotları oturum kontrolü yapar.
* **Özel İstisna Hiyerarşisi (`exception/`):** Tüm uygulama hataları `MotusLabException` temel sınıfından türer:
  * `DataAccessException` (JDBC/SQL hataları)
  * `ValidationException` (geçersiz girdi veya iş kuralı ihlali)
  * `AuthorizationException` (yetkisiz işlem)
  * `NotFoundException` (kayıt bulunamadı)
  * `ExportException` (CSV dışa aktarma hataları)

---

## 3. Roller, Yetkilendirme ve CRUD Politikası

### 3.1. Rol ve Yetki Matrisi
Sistemde iki temel rol bulunur: `ADMIN` ve `RESEARCHER`. Sisteme kayıt olan ilk kullanıcı otomatik olarak `ADMIN`, sonraki kullanıcılar `RESEARCHER` rolüyle oluşturulur.

| İşlem | Admin | Deney Sahibi | `EDIT` Üyesi | `RUN` Üyesi | `VIEW` Üyesi |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Erişilebilir deneyleri görüntüleme** | Kendi + Paylaşılan | Kendi + Paylaşılan | Paylaşılan | Paylaşılan | Paylaşılan |
| **Deney oluşturma** | Evet | Evet | — | — | — |
| **Deney başlık/açıklama/parametre düzenleme** | Evet (arşivli değilse) | Evet (arşivli değilse) | Evet (arşivli değilse) | Hayır | Hayır |
| **Simülasyon çalıştırma** | Sahip/Üye ise | Evet (arşivli değilse) | Evet (arşivli değilse) | Evet (arşivli değilse) | Hayır |
| **Deney arşivleme / arşivden çıkarma** | Evet | Evet | Hayır | Hayır | Hayır |
| **Deney silme** | Evet | Evet | Hayır | Hayır | Hayır |
| **Paylaşım yönetimi** | Evet | Evet | Hayır | Hayır | Hayır |
| **Kullanıcı yönetimi (rol & aktif/pasif)** | Evet | Hayır | Hayır | Hayır | Hayır |

* **Admin Paneli Güvenlik Kuralları (`AdminService`):** Yönetici kendi hesabını pasife alamaz ve kendi `ADMIN` rolünü düşüremez. Bu kural hem arayüzde (`AdminPanelDialog`) hem de servis katmanında zorunlu tutulur.

### 3.2. Deney Paylaşım Kuralları (`ExperimentMember`)
* Deney sahipliği `experiments.owner_id` üzerinden temsil edilir; deney sahibi `experiment_members` tablosunda üye olarak tutulmaz.
* Aynı kullanıcı aynı deneye iki kez eklenemez; deney sahibi kendisini üye olarak ekleyemez.
* Yetkiler hiyerarşiktir: `EDIT` yetkisi `RUN` ve `VIEW` yetkilerini; `RUN` yetkisi `VIEW` yetkisini kapsar.

### 3.3. CRUD Varlıkları ve Silme / Arşivleme Politikası
Dersin "en az 3 ilişkili varlıkta tam CRUD" gereksinimi aşağıdaki varlıklar üzerinden karşılanır:

| Varlık | Ekleme / Listeleme / Güncelleme | Silme / Pasifleştirme Politikası |
| :--- | :--- | :--- |
| **Kullanıcılar (`users`)** | Kayıt ekranı (`AuthView`) ve Admin Paneli (`AdminPanelDialog`) üzerinden rol ve durum yönetimi | Hesap pasifleştirme / aktifleştirme (`is_active`). |
| **Deneyler (`experiments`)** | Oluşturma, arama/filtreleme, başlık/açıklama ve parametre düzenleme | Hem **arşivleme / arşivden çıkarma** (`is_archived`) hem de deney sahibi/admin tarafından transaction içinde **fiziksel silme** desteklenir. Arşivli deneyler salt okunurdur ve yeni çalıştırma başlatılamaz. |
| **Deney Üyeleri (`experiment_members`)** | Paylaşım penceresinden üye ekleme ve yetki güncelleme | Doğrudan **fiziksel `DELETE`**. |
| **Çalıştırmalar (`simulation_runs`)** | Simülasyon çalıştırma, deney bazlı sıralı `run_number`, ad (`name`) ve not (`notes`) düzenleme | Tekil çalıştırma **fiziksel `DELETE`** (bağlı snapshot, örnek, metrik ve durum geçmişi `ON DELETE CASCADE` ile temizlenir). |

---

## 4. Veritabanı Tasarım Kararları

* **Normalizasyon:** Tüm şema UNF $\rightarrow$ 1NF $\rightarrow$ 2NF $\rightarrow$ 3NF adımlarıyla 3NF seviyesinde tasarlanmıştır (`docs/VERİTABANI.md`).
* **Deney Türü Değişmezliği:** Bir deneyin simülasyon türü oluşturulurken seçilir ve sonradan değiştirilemez.
* **Şema ve Başlangıç Verileri:** `src/main/resources/db/motuslab_schema.sql` ve tekrar çalıştırılabilir (idempotent) `src/main/resources/db/motuslab_seed.sql` dosyalarıyla yönetilir.

### 4.1. Veritabanı Tabloları ve Java Eşleşmesi

| Tablo Adı | Görevi | Java Modeli | Sorumlu DAO |
| :--- | :--- | :--- | :--- |
| `roles` | Sistem rollerini (`ADMIN`, `RESEARCHER`) tutar | `Role` | `UserDao` |
| `users` | Kullanıcı hesapları ve BCrypt hash'leri | `User` | `UserDao` |
| `simulation_types` | Sabit simülasyon türleri | `SimulationType` | `SimulationTypeDao` |
| `parameter_definitions` | Parametre adı, birimi, varsayılanı ve alt/üst sınırları | `ParameterDefinition` | `SimulationTypeDao` |
| `experiments` | Deney temel bilgileri, sahibi ve `is_archived` durumu | `Experiment` | `ExperimentDao` |
| `experiment_members` | Paylaşılan deney üyeleri ve yetkileri | `ExperimentMember` | `ExperimentMemberDao` |
| `experiment_parameters` | Deneyin güncel (düzenlenebilir) parametre değerleri | `ExperimentParameter` | `ExperimentDao` |
| `simulation_runs` | Çalıştırma kayıtları (`run_number`, `name`, `notes`, `status`) | `SimulationRun` | `SimulationRunDao` |
| `run_parameter_snapshots` | Çalıştırma anındaki parametrelerin değişmez kopyası | `RunParameterSnapshot` | `SimulationRunDao` |
| `run_samples` | Çalıştırmanın zaman serisi örnekleri | `RunSample` | `SimulationRunDao` |
| `run_metrics` | Çalıştırmanın özet metrikleri | `RunMetric` | `SimulationRunDao` |
| `run_status_history` | Çalıştırma durum geçişlerinin denetim geçmişi | `RunStatusHistory` | `SimulationRunDao` (Trigger yazar) |

### 4.2. Deney Parametreleri ve Çalıştırma Parametreleri Ayrımı
* `experiment_parameters`: Deneyin güncel parametreleridir; kullanıcı tarafından düzenlenebilir.
* `run_parameter_snapshots`: Çalıştırma başladığı anda `sp_create_run_record` tarafından alınan değişmez kopyadır. Deney parametreleri sonradan değiştirilse bile geçmiş çalıştırmaların parametreleri korunur.

### 4.3. `SimulationRun` Durum Makinesi
Standart durumlar: `CREATED`, `RUNNING`, `COMPLETED`, `FAILED`.
* **İzin verilen geçişler:** `CREATED` $\rightarrow$ `RUNNING` $\rightarrow$ `COMPLETED` veya `FAILED` (veya başlatma hatasında `CREATED` $\rightarrow$ `FAILED`).
* `COMPLETED` veya `FAILED` durumundaki kayıtların durumu değiştirilemez; yeniden çalıştırma her zaman yeni bir kayıt ve yeni bir `run_number` üretir.

### 4.4. Stored Function ve Trigger Davranışları
* **Stored Function (`sp_create_run_record`):** Tek bir veritabanı çağrısında; kullanıcının aktif olduğunu, deneyin varlığını (`FOR UPDATE` satır kilidiyle), arşivli olmadığını ve çalıştırma yetkisini doğrular; deney bazında sıralı `run_number` ($COALESCE(\max(\text{run\_number}), 0) + 1$) hesaplayarak `CREATED` durumunda yeni `simulation_runs` kaydı oluşturur; `experiment_parameters` değerlerini `run_parameter_snapshots` tablosuna kopyalar ve `run_id` döndürür.
* **Trigger (`trg_simulation_run_status` / `fn_track_simulation_run_status`):** `simulation_runs` üzerindeki `INSERT` ve `UPDATE OF status` işlemlerini denetler; geçersiz durum geçişlerini reddeder ve geçerli geçişleri `run_status_history` tablosuna kaydeder.

### 4.5. Veri Bütünlüğü Kısıtları
Veritabanı seviyesinde (`UNIQUE`, `CHECK`, `FOREIGN KEY`) şu kurallar güvence altına alınır:
* `username` ve `email` benzersizdir; deney başlığı boş olamaz.
* Aynı deneyde aynı kullanıcı birden fazla üyeliğe sahip olamaz (`UNIQUE(experiment_id, user_id)`).
* Aynı deneyde çalıştırma numaraları benzersiz ve pozitiftir (`UNIQUE(experiment_id, run_number)`, `run_number > 0`).
* Parametre tanımlarında $\text{min\_value} \le \text{default\_value} \le \text{max\_value}$ koşulu zorunludur.
* Zaman serisi örneklerinde $t \ge 0$, `sample_index >= 0` ve `UNIQUE(run_id, sample_index)` kısıtları vardır.
* Aynı çalıştırmada aynı metrik kodu tekrarlanamaz (`UNIQUE(run_id, metric_code)`).
* Deneyin `simulation_type_id` değeri ile parametre tanımının simülasyon türü bileşik foreign key (`(experiment_id, simulation_type_id)` ve `(parameter_definition_id, simulation_type_id)`) ile eşleştirilir.

---

## 5. Simülasyon Modelleri, Sınırlar ve Metrikler

### 5.1. Örnek Sayısı Sınırı ve Genel Kısıtlar
Tüm modeller `PhysicsModel` arayüzünü uygular ve `SimulationEngine` üzerinden simülasyon türü koduna göre çalıştırılır:
* Zaman adımı: $\Delta t > 0$ (parametre tanımlarında alt sınır $0.001\text{ s}$)
* Maksimum adım sayısı (`PhysicsModel.MAX_STEPS`):
$$\left\lceil \frac{T}{\Delta t} \right\rceil \le 50\,000$$
* Yerçekimi ivmesi: $g > 0$ (varsayılan $9.81\text{ m/s}^2$)
* Atış açısı: $0^\circ \le \theta \le 90^\circ$
* Yüksek örnek sayısına sahip çalıştırmalarda JavaFX `LineChart` performansını korumak için çizim öncesinde örnekleme azaltma (downsampling) uygulanır; veritabanında ve CSV dışa aktarımında tüm örnekler korunur.

### 5.2. Hareket Modelleri ve Standart Metrik Kodları
1. **Düzgün Doğrusal Hareket (`UNIFORM_MOTION` — `UniformMotionModel`):**
   * Parametreler: `initial_position`, `velocity`, `duration`, `time_step`
   * Metrikler: `total_time`, `final_position`, `displacement`, `max_speed`
2. **Sabit İvmeli Hareket (`CONSTANT_ACCEL` — `ConstantAccelerationModel`):**
   * Parametreler: `initial_position`, `initial_velocity`, `acceleration`, `duration`, `time_step`
   * Metrikler: `total_time`, `final_position`, `displacement`, `final_velocity`, `max_speed`
3. **2B Eğik Atış (`PROJECTILE_2D` — `Projectile2DModel`):**
   * Hava direnci yoktur; başlangıç yüksekliği $y_0 \ge 0$ değerinden cisim yere ulaşana kadar ($y = 0$) sürer. Uçuş süresi $y_0 + v_{y0} t - \frac{1}{2} g t^2 = 0$ denkleminin pozitif köküyle hesaplanır.
   * Parametreler: `initial_height`, `initial_speed`, `launch_angle`, `gravity`, `time_step`
   * Metrikler: `flight_time`, `range`, `max_height`, `impact_speed`, `max_speed`

### 5.3. Karşılaştırma ve Dışa Aktarma Kuralları
* **Karşılaştırma (`RunComparisonDialog`):** Aynı deneye ait `COMPLETED` durumundaki çalıştırmaların zaman serileri aynı grafikte üst üste çizilir, özet metrikleri yan yana karşılaştırılır.
* **CSV Dışa Aktarma (`CsvExportService`):** Seçili çalıştırmanın zaman serisi örnekleri UTF-8 CSV dosyası olarak dışa aktarılabilir.

---

## 6. Arayüz Bileşenleri ve Asenkron Yönetim

### 6.1. Programatik JavaFX Ekranları ve Diyaloglar (`ui/`)
| Sınıf | Sorumluluk |
| :--- | :--- |
| `MainApp` | Uygulama giriş noktası, AtlantaFX tema kurulumu, `AuthView` $\leftrightarrow$ `DashboardView` geçişleri |
| `AuthView` | Kullanıcı girişi ve yeni hesap kaydı |
| `DashboardView` | Ana çalışma alanı: canlı arama, sahiplik/tür/arşiv filtreleme, deney detayı, parametre düzenleme, asenkron çalıştırma ve çalıştırma geçmişi |
| `RunResultPane` | Seçili çalıştırmanın özet metrikleri, `LineChart` grafikleri, ad/not düzenleme, silme ve CSV dışa aktarma |
| `NewExperimentDialog` | Yeni deney oluşturma ve başlangıç parametrelerini girme |
| `EditExperimentDialog` | Deney başlığı ve açıklamasını güncelleme |
| `ShareExperimentDialog` | Deney üyelerini listeleme, ekleme, yetki değiştirme ve kaldırma |
| `EditRunDialog` | Çalıştırma adı (`name`) ve notlarını (`notes`) düzenleme |
| `RunComparisonDialog` | Tamamlanmış çalıştırmaların grafik ve metrik karşılaştırması |
| `AdminPanelDialog` | `ADMIN` kullanıcıları için kullanıcı listesi, rol değiştirme ve aktif/pasif yönetimi |

### 6.2. Asenkron İşlem Yönetimi (`JavaFX Task`)
* Simülasyon çalıştırma işlemi `DashboardView` içinde `javafx.concurrent.Task` ve bir `daemon` arka plan iş parçacığı (`motuslab-sim-runner`) üzerinde yürütülür; böylece uzun süren simülasyonlarda arayüz donmaz.
* İşlem sırasında çalıştırma butonu pasifleşir ve `ProgressIndicator` gösterilir.
* Kullanıcı simülasyon sürerken başka bir deneye geçerse, görev tamamlandığında yanlış deneyin listesinin güncellenmemesi için hedef `experimentId` kontrolü yapılır.
* Hata durumunda ilgili kayıt `FAILED` durumuna geçirilir ve hata mesajı kullanıcıya gösterilir.

---

## 7. Güncel Proje Dosya Ağacı

```text
MotusLab/
├── docs/
│   ├── KARARLAR.md
│   └── VERİTABANI.md
├── src/
│   ├── main/
│   │   ├── java/com/motuslab/
│   │   │   ├── MainApp.java
│   │   │   ├── config/
│   │   │   │   └── DatabaseConnection.java
│   │   │   ├── dao/
│   │   │   │   ├── ExperimentDao.java
│   │   │   │   ├── ExperimentMemberDao.java
│   │   │   │   ├── SimulationRunDao.java
│   │   │   │   ├── SimulationTypeDao.java
│   │   │   │   └── UserDao.java
│   │   │   ├── exception/
│   │   │   │   ├── AuthorizationException.java
│   │   │   │   ├── DataAccessException.java
│   │   │   │   ├── ExportException.java
│   │   │   │   ├── MotusLabException.java
│   │   │   │   ├── NotFoundException.java
│   │   │   │   └── ValidationException.java
│   │   │   ├── model/
│   │   │   │   ├── Experiment.java
│   │   │   │   ├── ExperimentMember.java
│   │   │   │   ├── ExperimentParameter.java
│   │   │   │   ├── ParameterDefinition.java
│   │   │   │   ├── Role.java
│   │   │   │   ├── RunMetric.java
│   │   │   │   ├── RunParameterSnapshot.java
│   │   │   │   ├── RunSample.java
│   │   │   │   ├── RunStatusHistory.java
│   │   │   │   ├── SimulationRun.java
│   │   │   │   ├── SimulationType.java
│   │   │   │   └── User.java
│   │   │   ├── service/
│   │   │   │   ├── AdminService.java
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── CsvExportService.java
│   │   │   │   ├── ExperimentMemberService.java
│   │   │   │   ├── ExperimentService.java
│   │   │   │   ├── SessionManager.java
│   │   │   │   └── SimulationRunService.java
│   │   │   ├── simulation/
│   │   │   │   ├── ConstantAccelerationModel.java
│   │   │   │   ├── PhysicsModel.java
│   │   │   │   ├── Projectile2DModel.java
│   │   │   │   ├── SimulationEngine.java
│   │   │   │   ├── SimulationResult.java
│   │   │   │   ├── SimulationSample.java
│   │   │   │   └── UniformMotionModel.java
│   │   │   └── ui/
│   │   │       ├── AdminPanelDialog.java
│   │   │       ├── AuthView.java
│   │   │       ├── DashboardView.java
│   │   │       ├── EditExperimentDialog.java
│   │   │       ├── EditRunDialog.java
│   │   │       ├── NewExperimentDialog.java
│   │   │       ├── RunComparisonDialog.java
│   │   │       ├── RunResultPane.java
│   │   │       └── ShareExperimentDialog.java
│   │   └── resources/
│   │       ├── config.properties
│   │       ├── config.properties.example
│   │       └── db/
│   │           ├── motuslab_schema.sql
│   │           └── motuslab_seed.sql
│   └── test/
│       └── java/com/motuslab/simulation/
│           └── SimulationEngineTest.java
├── .gitignore
└── pom.xml
```