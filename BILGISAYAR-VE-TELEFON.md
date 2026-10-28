# TKD PRO'yu bilgisayarda ve telefonda birlikte kullanma

TKD PRO artık iki parçadan oluşuyor ve ikisi de **aynı verileri** gösteriyor:

| Cihaz | Ne kullanılır | Ne yapılır |
|---|---|---|
| **Bilgisayar (Windows)** | `TKD-PRO.exe` | Sunucuyu çalıştırır ve uygulamayı kendi penceresinde açar. Veriler bu bilgisayarda saklanır. |
| **Telefon (Android)** | TKD PRO uygulaması (APK) | GPS koşu, video analizi, reaksiyon testi, her şey çevrimdışı da çalışır. Bilgisayarla otomatik eşitlenir. |
| **Başka bir cihaz** (tablet, iPhone, ikinci bilgisayar) | Tarayıcı | Aynı Wi-Fi'da bilgisayarın adresini açın, 6 haneli kodla giriş yapın; "Uygulama olarak yükle" ile ana ekrana ekleyin. |

Bir cihazda yaptığınız değişiklik (antrenman, toparlanma girişi, hedef, maç, AI sohbeti…) diğer cihazda görünür.
Telefonda uygulama her açıldığında, arka planda da yaklaşık 30 dakikada bir eşitleme yapılır. Bilgisayardaki
uygulama ise değişiklikleri birkaç saniye içinde alır.

## 0. Dosyalar

- `desktop\dist\TKD-PRO.exe`: bilgisayar uygulaması.
- `desktop\dist\TKD-PRO-telefon.apk`: telefon uygulaması. Telefona kopyalayıp açın; Android "bilinmeyen kaynaklardan
  yükleme" izni isteyebilir. (Eski sürüm yüklüyse üzerine kurulur, veriler korunur.)

Proje OneDrive içinde olduğu için Android derleme çıktısı `%LOCALAPPDATA%\TKD-PRO-build` klasörüne yazılır
(OneDrive dosyaları kilitlediği için).

## 1. Bilgisayarda başlatma

1. `desktop\dist\TKD-PRO.exe` dosyasına çift tıklayın (isterseniz masaüstüne kısayol oluşturun).
2. İlk açılışta Windows Güvenlik Duvarı izin isteyebilir: **Özel ağlar**'a izin verin. Telefonun bağlanabilmesi için bu gerekli.
3. TKD PRO kendi penceresinde açılır ve otomatik olarak giriş yapar. Siyah konsol penceresi sunucudur:
   uygulamayı kullanırken açık kalsın, kapatınca TKD PRO durur.

Veriler `%LOCALAPPDATA%\TKD-PRO` klasöründe durur (OneDrive dışında):

- `tkdpro.db`: tüm kayıtlarınız (yedeklemek için bu dosyayı kopyalayın)
- `server.env`: ayarlar (OpenAI anahtarı, port)
- `owner-token.txt`: acil durumda giriş için sahip anahtarı (gizli tutun)

## 2. Telefonu eşleştirme (bir kez)

1. Bilgisayardaki TKD PRO'da **Ayarlar → Cihaz bağla → Eşleştirme kodunu göster**'e basın.
   Ekranda 6 haneli bir kod ve `http://192.168.x.x:8000` gibi bir adres görünür.
2. Telefonda **Profil → Ayarlar → Bilgisayar ve telefon** bölümüne bu adresi ve kodu girip **Eşleştir**'e basın.
3. Telefondaki tüm mevcut kayıtlar bilgisayara yüklenir; bundan sonra eşitleme iki yönlü ve otomatiktir.

Kod 5 dakika geçerlidir ve yalnızca bir kez kullanılabilir. Bağlı cihazları **Ayarlar → Giriş yapmış cihazlar**
bölümünde görebilir ve oradan çıkış yaptırabilirsiniz.

## 3. AI Koç (isteğe bağlı)

`%LOCALAPPDATA%\TKD-PRO\server.env` dosyasında `OPENAI_API_KEY=` satırına anahtarınızı yazın ve TKD PRO'yu
yeniden başlatın. Sonra bilgisayarda **Ayarlar → AI veri izinleri**'nden hangi verilerin kullanılabileceğini
seçin. Telefonda da izinler ayrı olarak telefonun kendi ayarlarından verilir. Ad, kulüp, antrenör, fotoğraf,
video ve GPS rotaları AI'a hiçbir zaman gönderilmez.

## 4. Neyin nerede yapıldığı

- **Yalnızca telefonda:** GPS koşu kaydı, reaksiyon testleri (dokunma zamanlaması), cihaz üzerinde video analizi,
  uygulama kilidi. Sonuçları bilgisayarda görünür (koşu haritası dahil).
- **İki tarafta da:** antrenman ve teknik kayıtları, teknikler, kombinasyonlar, maçlar ve raund gözlemleri,
  müsabakalar, toparlanma girişleri, performans testi sonuçları, hedefler, kuşak geçmişi, profil, AI sohbeti ve haftalık rapor.
- **Videolar** telefonda kalır; bilgisayara yalnızca analiz sonuçları gelir.

## 5. Sorun giderme

| Sorun | Çözüm |
|---|---|
| Telefon "Eşleştirme başarısız: Network error" diyor | İki cihaz aynı Wi-Fi'da mı? Güvenlik Duvarı'nda TKD PRO'ya özel ağ izni verildi mi? Adresi tam olarak (`http://` ve `:8000` ile) yazın. |
| Telefon bağlanıyor ama sonra eşitlemiyor | Bilgisayarda TKD-PRO.exe açık olmalı. Telefonda **Şimdi eşitle**'ye basıp "Son hata" satırına bakın. |
| Evden uzaktayken eşitleme | Her iki cihaza [Tailscale](https://tailscale.com) kurun ve telefonda bilgisayarın `100.x.x.x` adresini kullanın. |
| Bilgisayardaki adres değişti | Telefonda yeni adresle tekrar eşleştirin; veriler kaybolmaz. |
| Şifre/anahtar sorulan bir tarayıcı | `owner-token.txt` içindeki anahtarla "Bunun yerine sahip anahtarını kullan" seçeneğinden giriş yapın. |

Bilgisayar kapalıyken telefon normal çalışmaya devam eder; değişiklikler bekler ve bilgisayar açılınca gönderilir.
