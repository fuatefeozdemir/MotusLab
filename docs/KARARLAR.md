# MotusLab — Proje ve Mimari Kararlar Dokümanı

Bu doküman, **MotusLab – Deney ve Simülasyon Verisi Yönetim Sistemi** projesinin ilk sürümü (Veritabanı Yönetim Sistemleri ders projesi) için kesinleştirilen kapsam, teknoloji yığını, yetkilendirme kuralları, veritabanı tasarımı, simülasyon sınırları ve katmanlı mimari kararlarını içerir.

---

## 1. Proje Kapsamı ve Aşamalandırma

* **Aşama 1 (Aktif Kapsam — Ders Projesi):** Yerel bilgisayarda çalışan, PostgreSQL veritabanına doğrudan bağlanan, dersin tüm veritabanı ve uygulama gereksinimlerini eksiksiz karşılayan masaüstü uygulaması.
* **Aşama 2 (Gelecek Kapsam — Şimdilik Dışarıda):** Bulut veritabanı (ör. Supabase), ayrı API sunucusu, uzaktan çoklu kullanıcı eşzamanlı çalışması ve ileri seviye simülasyon motorları.
* **Geçiş Uyumluluğu Kuralı:** Aşama 2'ye geçişi kolaylaştırmak için veritabanı erişimi yalnızca `dao` katmanında, fiziksel hesaplamalar ise yalnızca `simulation` katmanında izole edilir. Arayüz (`controller`) sınıfları doğrudan SQL sorgusu veya fizik formülü barındırmaz.

### İlk Sürümde Kapsam Dışı Tutulanlar
İlk sürümde gereksiz karmaşıklık yaratmamak için aşağıdaki yapılar eklenmeyecektir:
* HikariCP ve bağlantı havuzu
* Spring Boot ve REST API
* Repository katmanı, Dependency Injection framework veya ek tasarım kalıbı altyapıları (Factory/Strategy vb.)
* Parametre preset (şablon) sistemi
* Dinamik eklenti tabanlı simülasyon yükleyici
* Karmaşık loglama/izleme altyapısı ve bulut dağıtımı

---

## 2. Teknoloji Yığını, Güvenlik ve Bağımlılıklar

| Alan | Seçilen Teknoloji | Tercih Nedeni |
| :--- | :--- | :--- |
| **Programlama Dili** | Java | Ders zorunluluğu, güçlü tip güvenliği ve çapraz platform desteği. |
| **Masaüstü Arayüzü** | JavaFX + FXML (Scene Builder) | Modern masaüstü arayüzleri ve görsel tasarım kolaylığı. |
| **Arayüz Tasarımı** | AtlantaFX + Ikonli | Ekstra karmaşık CSS yazmadan modern tema ve vektör ikon desteği. |
| **Veritabanı** | PostgreSQL (Yerel) | Ders zorunluluğu, hafif kurulum, güçlü kısıt/procedure/trigger desteği. |
| **Veritabanı Erişimi** | Saf JDBC | SQL, Stored Procedure ve Trigger çağrılarında tam kontrol (ORM yok). |
| **Parola Güvenliği** | jBCrypt (BCrypt) | Otomatik tuzlama (salt) ile güvenli parola saklama. |
| **Bağımlılık Yönetimi** | Maven (`pom.xml`) | Kütüphane ve derleme süreçlerinin standart yönetimi. |

### 2.1. Maven (`pom.xml`) Bağımlılıkları
* **JavaFX Controls:** Arayüz bileşenleri ve grafikler (`JavaFX Charts` bu modüle dahildir).
* **JavaFX FXML:** FXML ekran yükleme ve bağlama.
* **PostgreSQL JDBC Driver:** Veritabanı bağlantısı.
* **jBCrypt:** Parola hash üretimi ve doğrulama.
* **AtlantaFX:** Uygulama teması.
* **Ikonli:** Vektör ikon setleri.

### 2.2. Veritabanı Bağlantısı ve Yapılandırma Güvenliği
* `DatabaseConnection` her ihtiyaç duyulduğunda yeni bir `Connection` üretir; tek bir statik bağlantı uygulama boyunca paylaşılmaz.
* Tüm DAO metotları bağlantıyı `try-with-resources` bloğunda açıp kapatır ve SQL enjeksiyonuna karşı `PreparedStatement` kullanır.
* Bağlantı ve sorgu hataları uygun şekilde servis katmanına iletilir.
* Veritabanı adresi, kullanıcı adı ve parola kaynak kodda tutulmaz; proje kökündeki `config/database.properties` dosyasından okunur. Bu dosya `.gitignore` içine eklenir; depoda yalnızca parolasız örnek yapılandırma dosyası paylaşılır.

### 2.3. Parola Güvenliği (`PasswordUtil`)
* Parolalar hiçbir zaman açık metin olarak saklanmaz.
* Kullanıcı oluşturulurken BCrypt hash üretilir; giriş sırasında girilen parola bu hash ile doğrulanır.
* Pasif (`is_active = false`) kullanıcılar doğru parola girse bile sisteme giriş yapamaz.
* Parola verileri kullanıcı listelerinde veya arayüzde gösterilmez.

---

## 3. Roller, Yetkilendirme ve CRUD Politikası

### 3.1. Rol ve Yetki Matrisi
Yönetici (`ADMIN`) temel olarak kullanıcı ve sistem yönetiminden sorumludur; araştırmacıların deney oluşturma, düzenleme veya çalıştırma akışlarına doğrudan müdahale etmez.

| İşlem | Admin | Deney Sahibi | `EDIT` Üyesi | `RUN` Üyesi | `VIEW` Üyesi |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Tüm deneyleri görüntüleme** | Evet | Yalnızca kendi | Paylaşılan | Paylaşılan | Paylaşılan |
| **Deney oluşturma** | Hayır | Evet | Hayır | Hayır | Hayır |
| **Deney bilgilerini düzenleme** | Hayır | Evet | Evet | Hayır | Hayır |
| **Simülasyon çalıştırma** | Hayır | Evet | Evet | Evet | Hayır |
| **Deney arşivleme** | Evet | Evet | Hayır | Hayır | Hayır |
| **Paylaşım yönetimi** | Hayır | Evet | Hayır | Hayır | Hayır |
| **Kullanıcı yönetimi** | Evet | Hayır | Hayır | Hayır | Hayır |

### 3.2. Deney Paylaşım Kuralları (`ExperimentMember`)
* Deney sahipliği `experiments.owner_id` üzerinden temsil edilir; deney sahibi `experiment_members` tablosunda normal üye olarak tutulmaz ve yetkileri bu tablodan bağımsızdır.
* Aynı kullanıcı aynı deneye iki kez eklenemez.
* Deney sahibi kendisini üye olarak ekleyemez.
* Yetkiler hiyerarşiktir: `EDIT` yetkisi `RUN` ve `VIEW` yetkilerini; `RUN` yetkisi `VIEW` yetkisini kapsar.

### 3.3. CRUD Varlıkları ve Silme / Arşivleme Politikası
Dersin "en az 3 ilişkili varlıkta tam CRUD" gereksinimi şu üç varlık üzerinden karşılanır:

| Varlık | Ekleme / Listeleme / Güncelleme | Silme / Pasifleştirme Politikası |
| :--- | :--- | :--- |
| **Kullanıcılar (`users`)** | Yönetici panelinden tam yönetim | Varsayılan olarak pasifleştirme (`is_active = false`); bağlı kaydı bulunmayan kullanıcı fiziksel olarak silinebilir. |
| **Deneyler (`experiments`)** | Araştırmacı / Deney sahibi tarafından tam yönetim | Fiziksel silme yoktur; **arşivleme** (`is_archived = true`) uygulanır. Geçmiş çalıştırmalar korunur; arşivli deneyde yeni çalıştırma başlatılamaz. |
| **Deney Üyeleri (`experiment_members`)** | Deney sahibi tarafından paylaşım yönetimi | Doğrudan **fiziksel `DELETE`** (dersin silme kriterini doğrudan karşılar). |

---

## 4. Veritabanı Tasarım Kararları

* **Normalizasyon:** Tüm şema UNF $\rightarrow$ 1NF $\rightarrow$ 2NF $\rightarrow$ 3NF adımları belgelenerek 3NF seviyesinde tasarlanır.
* **Deney Türü Değişmezliği:** Bir deneyin simülasyon türü oluşturulurken seçilir ve sonradan değiştirilemez.

### 4.1. Veritabanı Tablolarının Tam Listesi

| Tablo Adı | Görevi | İlgili Java Modeli |
| :--- | :--- | :--- |
| `roles` | Sistem rollerini (`ADMIN`, `RESEARCHER`) tutar | — (Enum / alan) |
| `users` | Kullanıcı hesaplarını ve BCrypt parola hash'lerini tutar | `User` |
| `simulation_types` | Sabit simülasyon türlerini tanımlar | `SimulationType` |
| `parameter_definitions` | Her türün parametre adını, birimini, varsayılanını ve sınırlarını tanımlar | `ParameterDefinition` |
| `experiments` | Deney temel bilgilerini, sahibini ve arşiv durumunu tutar | `Experiment` |
| `experiment_members` | Paylaşılan deneylerin üyelerini ve yetki seviyelerini tutar | `ExperimentMember` |
| `experiment_parameters` | Deneyin güncel (düzenlenebilir) parametre değerlerini tutar | — (`ExperimentParameterDAO`) |
| `simulation_runs` | Simülasyon çalıştırma kayıtlarını ve durumunu tutar | `SimulationRun` |
| `run_parameter_snapshots` | Çalıştırma anındaki parametrelerin değişmez kopyasını tutar | `RunParameters` |
| `run_samples` | Her çalıştırmanın zaman serisi örneklerini tutar | `RunSample` |
| `run_metrics` | Her çalıştırmanın özet metriklerini tutar | `RunMetric` |
| `run_status_history` | Çalıştırma durum değişikliklerinin denetim geçmişini tutar | — (Trigger yönetir) |

> **Kavram Eşleştirmesi:** SQL tarafındaki `run_parameter_snapshots` tablosu ile Java tarafındaki `RunParameters` modeli aynı kavramı temsil eder.

### 4.2. Deney Parametreleri ve Çalıştırma Parametreleri Ayrımı
* `experiment_parameters`: Deneyin güncel parametreleridir; kullanıcı tarafından düzenlenebilir.
* `run_parameter_snapshots` (`RunParameters`): Çalıştırma başladığı anda alınan değişmez kopyadır.

**Örnek Akış:**
1. Kullanıcı $20\text{ m/s}$ ilk hız ile deney oluşturur ve ilk çalıştırmayı yapar; `run_parameter_snapshots` içine $20\text{ m/s}$ kaydedilir.
2. Kullanıcı deney parametresini $30\text{ m/s}$ olarak günceller ve ikinci çalıştırmayı yapar; yeni snapshot içine $30\text{ m/s}$ kaydedilir.
3. İlk çalıştırmanın parametreleri ve sonuçları değişmeden korunur.

### 4.3. `SimulationRun` Durum Makinesi
Tek standart durum isimlendirmesi kullanılır: `CREATED`, `RUNNING`, `COMPLETED`, `FAILED`.
* **İzin verilen geçişler:** `CREATED` $\rightarrow$ `RUNNING` $\rightarrow$ `COMPLETED` veya `FAILED`.
* `COMPLETED` veya `FAILED` durumuna ulaşmış bir kayıt yeniden `RUNNING` durumuna geçirilemez; tekrar çalıştırma her zaman yeni bir kayıt oluşturur.

### 4.4. Stored Procedure ve Trigger Davranışları
* **Stored Procedure (`sp_create_run_record`):** Tek bir transaction içerisinde; deneyin varlığını, arşivli olmadığını ve kullanıcının çalıştırma yetkisini doğrular; yeni `simulation_runs` kaydını (`CREATED`) oluşturur; `experiment_parameters` değerlerini `run_parameter_snapshots` tablosuna kopyalar ve oluşturulan `run_id` değerini döndürür.
* **Trigger (Çalıştırma Durum Takibi):** `simulation_runs` üzerindeki durum güncellemelerini denetler; geçersiz durum geçişlerini reddeder; geçerli geçişlerde eski durumu, yeni durumu ve değişiklik zamanını otomatik olarak `run_status_history` tablosuna kaydeder.

### 4.5. Veri Bütünlüğü Kısıtları
Veritabanı seviyesinde (`UNIQUE`, `CHECK`, `FOREIGN KEY`) şu kurallar güvence altına alınır:
* Kullanıcı adı (`username`) benzersizdir; deney adı boş olamaz; deney sahibi geçerli bir kullanıcıdır.
* Aynı deneyde aynı kullanıcı birden fazla üyeliğe sahip olamaz (`UNIQUE(experiment_id, user_id)`).
* Parametre değerleri tanımlı sayısal sınırları aşamaz; zaman adımı pozitiftir ($\Delta t > 0$).
* Zaman serisi örneklerinde zaman negatif olamaz ($t \ge 0$) ve örnek indeksleri aynı çalıştırma içinde benzersizdir (`UNIQUE(run_id, sample_index)`).
* Aynı çalıştırmada aynı metrik kodu birden fazla kez bulunamaz (`UNIQUE(run_id, metric_code)`).
* Deneyin `simulation_type_id` değeri ile parametre tanımının simülasyon türü uyumlu olmalıdır (uygun noktalarda bileşik foreign key ile desteklenir).

---

## 5. Simülasyon Modelleri, Sınırlar ve Metrikler

### 5.1. Örnek Sayısı Sınırı ve Genel Kısıtlar
Tüm modeller sabit zaman adımı ($\Delta t$) ile hesaplanır:
* $\Delta t \ge 0.01\text{ s}$
* $T > 0$
* Yerçekimi ivmesi: $g > 0$ (varsayılan $9.81\text{ m/s}^2$)
* Atış açısı: $0^\circ \le \theta \le 90^\circ$

Toplam örnek sayısı `SimulationService` içerisinde şu formülle doğrulanır:
$$N = \left\lceil \frac{T}{\Delta t} \right\rceil + 1 \le 10\,000$$

Son örnek tam olarak bitiş zamanına denk gelmiyorsa, bitiş anı ($T$ veya yere çarpma anı) ek bir son örnek olarak üretilir.

### 5.2. Hareket Modelleri ve Bitiş Koşulları
1. **Düzgün Doğrusal Hareket (`UniformMotionModel`):** Kullanıcının belirlediği toplam süre ($T$) sonunda biter.
2. **Sabit İvmeli Doğrusal Hareket (`ConstantAccelerationModel`):** Kullanıcının belirlediği toplam süre ($T$) sonunda biter.
3. **2B Atış Hareketi (`ProjectileMotionModel`):** Hava direnci yoktur. Cisim başlangıç yüksekliğinden ($y_0 \ge 0$) hareket ettikten sonra yere ulaştığında ($y = 0$) biter. Kullanıcının belirlediği maksimum süre üst sınır olarak uygulanır. Başlangıç yüksekliği $y_0 > 0$ olduğunda uçuş süresi hesabında ikinci dereceden denklemin pozitif kökü kullanılır.

### 5.3. Zaman Serisi (`RunSample`) ve Standart Metrik Kodları (`RunMetric`)
`RunSample` alanları: `sampleIndex`, `timeSeconds`, `positionX`, `positionY` (1B modellerde `null`), `velocityX`, `velocityY` (1B modellerde `null`).

Karşılaştırma ekranında tek tip sorgu kullanılabilmesi için `RunMetric` kodları standartlaştırılmıştır:

| Model | Standart Metrik Kodları |
| :--- | :--- |
| **Düzgün Doğrusal Hareket** | `TOTAL_TIME`, `INITIAL_POSITION`, `FINAL_POSITION`, `DISPLACEMENT`, `VELOCITY` |
| **Sabit İvmeli Hareket** | `TOTAL_TIME`, `INITIAL_VELOCITY`, `FINAL_VELOCITY`, `FINAL_POSITION`, `DISPLACEMENT` |
| **2B Atış Hareketi** | `FLIGHT_TIME`, `MAX_HEIGHT`, `HORIZONTAL_RANGE`, `IMPACT_SPEED` |

### 5.4. Karşılaştırma Kuralları
* Yalnızca **aynı deneye** ait ve **`COMPLETED`** durumundaki çalıştırmalar karşılaştırılabilir.
* Aynı anda **en fazla 3 çalıştırma** seçilebilir.
* Farklı parametrelerle yapılmış çalıştırmaların karşılaştırılması serbesttir; kullanılan parametreler ekranda gösterilir.
* Zaman serileri aynı grafikte üst üste çizilir, özet metrikler yan yana tabloda listelenir.

---

## 6. Arayüz Ekranları, Senaryolar ve Asenkron Yönetim

### 6.1. Temel Ekranlar
| Ekran (FXML) | İçerik |
| :--- | :--- |
| `Login.fxml` | Kullanıcı girişi ve kimlik doğrulama |
| `Dashboard.fxml` | Rol bazlı özet, son deneyler, son çalıştırmalar ve navigasyon |
| `ExperimentList.fxml` | Deney listesi, arama, filtreleme (arşivlenmiş deneyler için ayrı filtre dahil), oluşturma |
| `ExperimentDetail.fxml` | Deney bilgileri, parametre düzenleme ve simülasyon başlatma |
| `Results.fxml` | Grafikler, özet metrikler, çalıştırma geçmişi ve karşılaştırma |
| `Sharing.fxml` | Deney üyeleri ve erişim izinleri yönetimi |
| `UserManagement.fxml` | Yönetici kullanıcı ve rol yönetimi |

### 6.2. Temel Kullanıcı Senaryoları
1. Kullanıcı giriş yapar ve rolüne göre dashboard ekranını görür.
2. Araştırmacı yeni deney oluşturur ve deney parametrelerini günceller.
3. Araştırmacı simülasyonu çalıştırır, sonuç grafiklerini ve metriklerini inceler.
4. Araştırmacı geçmiş çalıştırmaları açar ve en fazla 3 çalıştırmayı karşılaştırır.
5. Deney sahibi başka bir kullanıcıya `VIEW`, `RUN` veya `EDIT` erişimi verir / kaldırır.
6. Yönetici kullanıcı hesabı oluşturur, günceller veya pasifleştirir.
7. Kullanıcı arşivlenmiş deneyleri filtreleyerek geçmiş çalıştırma sonuçlarını görüntüler.

### 6.3. Asenkron İşlem ve Hata Yönetimi (`JavaFX Task`)
* Veritabanı ve simülasyon işlemleri `javafx.concurrent.Task` ile arka plan iş parçacıklarında yürütülür.
* İşlem sürerken yükleniyor göstergesi gösterilir ve aynı işlemin tekrar başlatılması engellenir.
* İşlem başarıyla tamamlandığında kullanıcıya bildirim (`AlertHelper`) verilir.
* Veritabanı bağlantı hatasında anlaşılır hata mesajı gösterilir; simülasyon hesaplaması başarısız olursa ilgili kayıt `FAILED` durumuna çekilir.

---

## 7. Güncel Proje Dosya Ağacı

```text
MotusLab/
├── docs/
│   └── KARARLAR.md
├── config/
│   └── database.properties
├── src/
│   ├── main/
│   │   ├── java/com/motuslab/
│   │   │   ├── app/
│   │   │   │   └── MotusLabApplication.java
│   │   │   ├── model/
│   │   │   │   ├── User.java
│   │   │   │   ├── Experiment.java
│   │   │   │   ├── ExperimentMember.java
│   │   │   │   ├── SimulationType.java
│   │   │   │   ├── ParameterDefinition.java
│   │   │   │   ├── SimulationRun.java
│   │   │   │   ├── RunParameters.java
│   │   │   │   ├── RunSample.java
│   │   │   │   └── RunMetric.java
│   │   │   ├── dao/
│   │   │   │   ├── UserDAO.java
│   │   │   │   ├── ExperimentDAO.java
│   │   │   │   ├── ExperimentMemberDAO.java
│   │   │   │   ├── ExperimentParameterDAO.java
│   │   │   │   ├── SimulationRunDAO.java
│   │   │   │   └── RunSampleDAO.java
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── UserService.java
│   │   │   │   ├── ExperimentService.java
│   │   │   │   ├── SharingService.java
│   │   │   │   └── SimulationService.java
│   │   │   ├── simulation/
│   │   │   │   ├── SimulationModel.java
│   │   │   │   ├── UniformMotionModel.java
│   │   │   │   ├── ConstantAccelerationModel.java
│   │   │   │   ├── ProjectileMotionModel.java
│   │   │   │   ├── SimulationParameters.java
│   │   │   │   └── SimulationResult.java
│   │   │   ├── controller/
│   │   │   │   ├── LoginController.java
│   │   │   │   ├── DashboardController.java
│   │   │   │   ├── ExperimentListController.java
│   │   │   │   ├── ExperimentDetailController.java
│   │   │   │   ├── ResultsController.java
│   │   │   │   ├── SharingController.java
│   │   │   │   └── UserManagementController.java
│   │   │   └── util/
│   │   │       ├── DatabaseConnection.java
│   │   │       ├── PasswordUtil.java
│   │   │       ├── SessionManager.java
│   │   │       ├── AlertHelper.java
│   │   │       └── Constants.java
│   │   └── resources/
│   │       ├── fxml/
│   │       │   ├── Login.fxml
│   │       │   ├── Dashboard.fxml
│   │       │   ├── ExperimentList.fxml
│   │       │   ├── ExperimentDetail.fxml
│   │       │   ├── Results.fxml
│   │       │   ├── Sharing.fxml
│   │       │   └── UserManagement.fxml
│   │       ├── css/
│   │       │   └── style.css
│   │       └── db/
│   │           └── motuslab_schema.sql
│   └── test/java/com/motuslab/
│       ├── simulation/
│       └── service/
├── .gitignore
├── README.md
└── pom.xml
```

> **Not:** `RunMetric` ve `RunParameters` (`run_parameter_snapshots`) işlemleri ayrı DAO sınıflarına bölünmeden `SimulationRunDAO` içinde yönetilir. `README.md` dosyası projenin amacını, kurulum adımlarını, PostgreSQL kurulumunu ve varsayılan yönetici hesabının oluşturulmasını içerir.