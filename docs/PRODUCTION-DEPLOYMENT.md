# Menyalakan server, auto-restart, dan akses online

## Apa yang dijalankan oleh APK ini

APK adalah **server Android**, bukan sekadar aplikasi klien. Saat tombol **MULAI SERVER** ditekan, ia menjalankan Java HTTP service pada port 8080 dan menyajikan frontend serta API. Data akun, chat, Story, media dan fitur sosial disimpan pada penyimpanan privat aplikasi di perangkat host.

## Membuat service bertahan lebih baik

Implementasi saat ini sudah:

1. Menjalankan service sebagai foreground service dengan notifikasi persisten dan `START_STICKY`.
2. Memegang wake lock dan Wi-Fi lock ketika service hidup, dengan konsumsi baterai yang meningkat.
3. Menjadwalkan restart setelah task aplikasi di-swipe dari Recents.
4. Memulai kembali setelah broadcast boot apabila server sebelumnya diaktifkan oleh pengguna.
5. Mematikan auto-restart dan menghapus alarm ketika pengguna menekan **MATIKAN SERVER**.

Agar lebih stabil, sambungkan perangkat host ke daya, gunakan Wi-Fi stabil, buka pengaturan baterai Android dan izinkan aplikasi berjalan tanpa optimasi jika perangkat menyediakan opsi tersebut. Pada sebagian perangkat vendor ada pengaturan autostart tersendiri.

**Batasan Android:** tidak bisa dijamin hidup mutlak. **Force stop** dari App Info, penghentian oleh sistem/OEM, kehilangan daya, reboot tertentu, aplikasi yang dihapus, atau kegagalan port/jaringan tetap dapat menghentikannya. Setelah Force stop, Android biasanya tidak mengizinkan auto-start sampai pengguna membuka aplikasi lagi. `START_STICKY` dan alarm adalah best-effort, bukan uptime guarantee.

## Akses pada LAN

1. Tekan **MULAI SERVER**.
2. Dari perangkat di jaringan Wi-Fi yang sama, buka `http://IP-ANDROID:8080/`.
3. Buka `http://IP-ANDROID:8080/health` untuk health check. Alamat IP dapat berubah pada router; buat DHCP reservation jika router mendukung.
4. Jangan membuka port 8080 langsung ke internet tanpa HTTPS dan hardening. HTTP lokal tidak mengenkripsi password/token di jaringan.

## Akses internet (opsi sementara)

Untuk mengakses perangkat host dari luar LAN, perlu jalur HTTPS yang stabil seperti reverse proxy/tunnel yang benar-benar dijalankan dan di-monitor pada host lain atau perangkat host. Domain statis saja, Netlify static hosting saja, atau aplikasi klien yang tertutup tidak menjalankan backend ini. Jangan pernah memasukkan token akun pada layanan tunnel publik yang tidak dipercaya.

Karena backend Android ini tidak memakai konfigurasi environment variable, tidak ada `.env` yang dibaca oleh APK. Menambahkan file `.env` saja tidak akan mengubah port/URL/backend.

## Kebutuhan agar menjadi server produksi yang sungguh-sungguh persisten

Untuk layanan yang harus tersedia meski perangkat Android mati/tidak online, migrasikan backend ke VPS/container atau platform backend persisten. Arsitektur yang disarankan:

- API service terpisah di proses server permanen, reverse proxy HTTPS (Caddy/Nginx), dan pembatasan rate.
- PostgreSQL dengan migrasi, foreign key, indeks, transaksi, dan cadangan off-site.
- Object storage untuk media, URL berizin/signed, dan proses thumbnail/transcoding.
- STUN/TURN dan SFU (misalnya LiveKit, Janus atau mediasoup) untuk Live/panggilan skala lebih besar.
- Push provider untuk notifikasi saat klien ditutup.
- Monitoring health, log redacted, restart policy dan backup diuji.

Itu bukan konfigurasi yang sudah selesai di APK ini, dan memerlukan deployment terpisah beserta biaya VPS/database/storage/bandwidth. Jangan menunjuk domain produksi ke aplikasi ini sebelum memperbaiki CORS, token di query string, privasi nomor pengguna, rate limiting, backup, TLS dan threat model.

## Build APK melalui repository GitHub

Workflow `.github/workflows/build-apk.yml` membangun `app-debug.apk` memakai Java 17 + Android SDK + Gradle 8.9. Jika build berhasil, artifact `OHLI-Social-Server-APK` tersedia di tab Actions. Lingkungan modifikasi ini tidak memiliki Android SDK/Gradle terpasang, jadi APK final belum dibangun atau diuji di perangkat pada sesi ini.
