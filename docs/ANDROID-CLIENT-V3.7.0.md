# OHLI Social Android Client v3.7.0

## Arsitektur

- APK adalah **aplikasi klien Android**, bukan host server.
- Layanan utama dituju pada `https://ohlisocial.netlify.app/`.
- APK memuat `index.html`, `ohli-social.css`, dan `ohli-social.js` dari `app/src/main/assets/` ketika perangkat offline. Aset lokal dipetakan ke origin HTTPS Netlify yang sama agar storage WebView aplikasi tetap satu origin saat berpindah online/offline.
- Panggilan `/api/...` tidak dipalsukan atau dijawab dari cache; request tetap menuju layanan online.
- Implementasi server lama masih berada di source tree untuk dokumentasi/kompatibilitas source, tetapi `ServerService` dan Cloudflare tunnel tidak dideklarasikan dalam manifest klien dan tidak dijalankan oleh APK ini.

## Perilaku offline

- Login pertama dan validasi akun tetap membutuhkan koneksi.
- Setelah berhasil login online, profil ringkas, kontak, daftar pengguna, pesan yang telah dimuat (maks. 500), status (maks. 300), dan token sesi WebView disimpan di penyimpanan lokal aplikasi.
- Jika aplikasi dibuka tanpa internet, sesi terakhir dicoba dipulihkan dari cache lokal.
- Pesan **teks biasa** yang ditulis saat offline disimpan di outbox lokal per akun (maks. 200 item) dan ditampilkan sebagai pending.
- Ketika koneksi kembali, outbox dicoba kirim otomatis. Setiap item membawa `clientId` untuk membantu mencegah pengiriman ganda; backend online harus mendukung idempotensi `clientId` agar jaminan ini berlaku sepenuhnya.
- Teks pesan sekali lihat, foto, video, audio, unggahan Story/post, LIVE, dan panggilan tidak diantrekan offline pada versi ini. Konten media yang sudah ada di server hanya dapat dimuat saat online.
- Data lokal tidak sama dengan cadangan akun di server. Menghapus data aplikasi atau memasang ulang APK dapat menghapus cache/outbox lokal.

## Android native

- WebView memakai JavaScript + DOM Storage dan mengizinkan media WebRTC dari origin Netlify setelah izin kamera/mikrofon Android diberikan.
- Geolocation WebView dibatasi ke origin Netlify dan baru diberi izin setelah persetujuan Android.
- Pemilih file Android digunakan untuk memilih gambar, video, audio, dan dokumen; izin media umum tidak diminta secara otomatis bila pemilih sistem sudah memberi akses URI.
- Kanal notifikasi Android dibuat untuk pesan, panggilan, dan aktivitas; masing-masing memiliki berkas nada sendiri.
- Notifikasi pesan yang diterima ketika WebView masih berjalan di latar belakang dapat memuat aksi **Balas** melalui Android RemoteInput. Balasan dikirim ke `/api/messages`; bila gagal, balasan ditaruh di antrean lokal native lalu dipindahkan ke outbox WebView ketika aplikasi aktif dan online.
- Menekan Back lebih dahulu menutup modal/halaman dalam aplikasi; dari beranda, Activity dipindahkan ke latar belakang.

## Persyaratan layanan online

Netlify static hosting saja tidak otomatis menyediakan endpoint `/api/*`. ZIP ini tidak berisi `netlify.toml`, `_redirects`, atau direktori `netlify/functions`. Domain Netlify harus sudah mempunyai salah satu dari:

1. Netlify Functions untuk semua endpoint yang diperlukan; atau
2. aturan proxy/redirect yang meneruskan `/api/*` dan `/health` ke backend OHLI Social yang aktif.

Backend yang dituju harus mempertahankan endpoint API lama, token/session, unggahan media, polling signaling WebRTC/LIVE, dan mendukung `clientId` idempotency untuk `/api/messages`. Jika URL Netlify hanya menyajikan HTML/CSS/JS tanpa API proxy, halaman dapat dibuka, tetapi login, chat, media, LIVE, dan VC tidak akan bekerja online.

## Build APK

Cara yang disiapkan adalah GitHub Actions:

1. Ekstrak isi ZIP source ini ke root repository GitHub (jangan mengunggah ZIP sebagai satu file saja).
2. Pastikan file `settings.gradle` berada di root repository dan folder `app/` berada di sampingnya.
3. Buka **Actions → Build OHLI Social Client APK → Run workflow**.
4. Setelah workflow sukses, unduh artifact `OHLI-Social-Client-APK-v3.7.0`; APK debug berada di dalam artifact sebagai `app-debug.apk`.

Build harus menggunakan JDK 17, Android SDK Platform 35, Build Tools 35.0.0, dan Gradle 8.9.

## Status validasi

- Pemeriksaan source statis dan parse JavaScript dilakukan oleh `tools/client-source-verify.mjs`.
- XML manifest/theme harus lolos parsing.
- Pemeriksaan ini **bukan** pengganti Gradle build, instalasi APK, atau uji dua HP.
- Build Gradle lokal belum tersedia pada lingkungan kerja ini (tidak ditemukan Gradle/Android SDK dan ZIP asli tidak membawa Gradle Wrapper). Karena itu APK belum dapat dinyatakan berhasil dibangun.
- Belum ada uji perangkat untuk permission camera/mic/location, Android RemoteInput, tone channel, offline restart, sinkronisasi pesan, media upload, WebRTC/VC, LIVE, atau notifikasi ketika aplikasi dihentikan.
- Notifikasi masuk saat proses aplikasi benar-benar dihentikan memerlukan push provider (mis. FCM) dan konfigurasi backend. Polling WebView tidak berjalan andal setelah Android menghentikan proses.
