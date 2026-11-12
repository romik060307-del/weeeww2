# OHLI Social — Android Client v3.7.0 (rilis source terbaru)

Source ini sekarang menyiapkan **APK klien OHLI Social**, bukan APK host server. Aplikasi mengarah ke `https://ohlisocial.netlify.app/`, membawa salinan HTML/CSS/JavaScript untuk shell offline, menyimpan sesi/cache lokal, dan mengantrekan pesan teks biasa untuk dikirim saat online kembali. APK memakai WebView native Android, izin kamera/mikrofon/lokasi/file, kanal notifikasi khusus, dan aksi balas pesan dari notifikasi.

**Batas penting:** ZIP ini belum membawa Netlify Functions atau aturan proxy API. Netlify harus sudah meneruskan `/api/*` ke backend online yang aktif. Media offline, push ketika proses aplikasi dihentikan, serta uji fisik kamera/VC/LIVE belum selesai. Build APK juga belum dijalankan di lingkungan ini.

Build: ekstrak isi ZIP ke root repository GitHub, buka **Actions → Build OHLI Social Client APK → Run workflow**, lalu unduh artifact `OHLI-Social-Client-APK-v3.7.0` setelah workflow sukses. Rincian ada di `docs/ANDROID-CLIENT-V3.7.0.md`.

---

## Arsip riwayat source Android Server (versi sebelumnya)

Bagian di bawah adalah catatan historis dari rilis server lama. Kode server lama dipertahankan di source tree, tetapi tidak lagi dijalankan oleh manifest APK klien v3.7.0.

# OHLI Social v3.6.27 — reference UI and operational monitoring

## v3.6.27 — Reference UI and operational watchdog

- Refreshed OHLI Social and legacy chat with warm cream surfaces, charcoal actions, yellow accents, rounded cards, and matching dark mode.
- Updated the native Android server dashboard to the same reference palette and rounded control surfaces.
- Added a 12-second operational monitor for actual Wi-Fi loss, local listener failure, recovery notifications, and controlled server restart attempts.
- Avoids a false Wi-Fi-loss alert on first launch when Wi-Fi was already disabled.
- Health endpoint and both asset cache-busters report v3.6.27; Android versionCode is 56.
- Cloudflare Quick Tunnel remains user-started from the Tunnel panel and does not publish a URL until its public `/health` check succeeds.
- The optimizer is an offline adaptive performance controller, not a general-purpose GPT/Gemini-class generative model. Android can still force-stop the app or restrict background execution; no APK can guarantee an always-on server under every device/power policy.
- Static verification and compilation checks do not replace real tests on the target Android phone and on two external devices.

Build using `OHLI-Social-Source-v3.6.27.zip`; the GitHub Actions artifact is `OHLI-Social-Server-APK-v3.6.27`.


## Perubahan source v3.6.27

- Polling signaling WebRTC dipisahkan dari polling data sosial supaya undangan dan ICE tidak harus menunggu refresh feed.
- Video lawan menjadi stage penuh; preview kamera sendiri menjadi tile portrait membulat. Ukuran panggilan mengikuti visual viewport perangkat.
- Quick Tunnel baru menampilkan link publik setelah `/health` berhasil dibuka melalui HTTPS dari jalur publik.
- HTML, CSS, dan JavaScript dikirim dengan gzip bila browser mendukungnya; hasil kompresi di-cache di RAM server.
- Workflow Gradle mengulang unduhan, mencoba mirror distribusi, dan memverifikasi SHA-256 sebelum build.
- **OHLI AI Performance Engine** tertanam offline di server APK: model logistic online mempelajari antrean, worker aktif, tekanan heap, latensi, dan rasio error 500; model menyesuaikan worker secara terbatas mengikuti memori/CPU perangkat.
- Pencarian akun dan sesi memakai indeks hash memori yang dibangun ulang dari file JSON saat startup/perubahan data.
- Penulisan JSON mempertahankan `.bak`/`.tmp` untuk pemulihan, tetapi menggunakan rename satu direktori agar tidak menyalin seluruh file lama pada tiap perubahan.
- Respons API JSON besar dapat dikompresi GZIP berdasarkan dukungan browser dan kondisi heap; data pribadi tetap `no-store`, tidak dimasukkan cache umum.
- Diagnostik performa dipindah ke endpoint `/api/performance` yang wajib login; endpoint publik `/health` hanya memberi status layanan dan versi. Model v2 tetap dapat memigrasikan bobot model v1.
- Android `versionName` `3.6.27`, `versionCode` `55`.

## Batas kemampuan yang perlu dipahami

OHLI tetap menggunakan backend file JSON dan satu lock global untuk menjaga kompatibilitas data lama. Karena itu, source ini tidak boleh diklaim bebas lag pada jumlah pengguna tanpa batas. Untuk produksi multi-pengguna dibutuhkan migrasi teruji ke database relasional, object storage, monitoring, dan infrastruktur TURN/SFU untuk panggilan/LIVE. Quick Tunnel gratis membuat URL sementara dan bergantung pada HP host serta internet yang aktif.

Engine ini adalah model kecil online untuk keputusan performa yang bekerja lokal tanpa API eksternal; ia bukan chatbot dan bukan model generatif setara GPT/Gemini. Engine belajar dari metrik runtime (termasuk rasio exception), menyimpan bobot numerik secara privat, dan menyediakan diagnostik hanya lewat endpoint terautentikasi `/api/performance`. Endpoint publik `/health` tidak membuka statistik perangkat atau jumlah akun.

## Build Android

Unggah `OHLI-Social-Source-v3.6.27.zip` ke root repository GitHub, lalu jalankan workflow `Build OHLI Social Server APK`. Artifact yang diharapkan: `OHLI-Social-Server-APK-v3.6.27`. Build tetap harus diuji di HP dan dua perangkat sebelum digunakan publik.

---


## Riwayat rilis sebelumnya — v3.6.23

- LIVE satu peserta memakai stage penuh layar; pratinjau kamera sendiri tidak dipaksa menjadi lingkaran di tengah.
- VC mempertahankan video teman sebagai tampilan utama dan pratinjau lokal berbentuk jendela portrait; ketuk untuk menukar layout.
- Pergantian kamera VC/LIVE mengambil stream baru lebih dulu, mengganti track WebRTC, dan mempertahankan stream lama bila proses gagal. LIVE menggulung balik sender yang sudah diganti jika peer berikutnya gagal.
- Cloudflare panel kini memanggil foreground service dari APK. Engine `cloudflared` harus dikompilasi untuk Android oleh workflow dari source resmi Cloudflare, lalu ditanam ke `jniLibs`; APK tidak lagi mengunduh binary Linux saat runtime.
- Workflow mengambil repository `cloudflare/cloudflared` pada tag tetap `2026.10.7`, membaca versi Go dari `go.mod`, dan membangun engine Android untuk ABI arm64-v8a, armeabi-v7a, serta x86_64 sebelum membangun APK.
- Service tunnel mengarah ke `http://127.0.0.1:<PORT OHLI>`, membaca URL Quick Tunnel dari output proses, menyediakan Start/Stop/Copy, dan mencoba menyambung ulang selama koneksi pulih.
- Receiver boot mencoba menyalakan tunnel lagi jika sebelumnya diaktifkan pengguna. URL publik Quick Tunnel dapat berubah saat tunnel dibuat ulang.
- Duplikasi deklarasi service tunnel pada manifest dihapus. Library native APK diekstrak agar dapat diakses oleh service.
- Health endpoint, cache-busting, Gradle, dan workflow menggunakan versionName `3.6.23`, versionCode `52`.

## Batas pengujian yang belum boleh diklaim selesai

- Engine Android `cloudflared` dibangun oleh workflow GitHub Actions. Build itu belum dijalankan dari lingkungan ini, sehingga belum ada bukti APK final berisi binary yang berhasil dijalankan di HP.
- Proses binary, DNS Android, izin eksekusi dari `nativeLibraryDir`, URL Quick Tunnel, dan reconnect setelah internet putus harus diuji pada perangkat fisik. Source mengatur `GODEBUG=netdns=cgo`; keberhasilan runtime tetap harus dikonfirmasi.
- Pergantian kamera harus diuji pada dua HP sebagai host/peserta dan peserta LIVE yang berganti kamera. Pemeriksaan statis tidak membuktikan kamera fisik, WebRTC renegotiation, atau jaringan.
- Tes server 24 jam, battery optimization, auto-start OEM, waktu startup, RAM, CPU, dan performa lokal/publik belum dilakukan.
- Ketika semua koneksi internet mati, server lokal masih dapat berjalan di perangkat/LAN yang tersedia, tetapi link publik Cloudflare tidak dapat diakses sampai internet kembali.
- Quick Tunnel adalah alamat sementara. Untuk alamat publik tetap, gunakan Named Tunnel dan token Cloudflare sendiri yang disimpan sebagai secret, bukan ditulis di source.
- Backend OHLI tetap memakai berkas JSON dan lock global; perubahan ini bukan migrasi SQLite dan bukan bukti server bebas lag.

## Build

GitHub Actions workflow `.github/workflows/build-apk.yml` mengompilasi engine tunnel Android dari source Cloudflare lalu membangun `:app:assembleDebug`. Catatan ini adalah panduan historis untuk v3.6.23. Untuk source terbaru, ikuti bagian Build Android pada ringkasan v3.6.27 di awal dokumen.

---

# OHLI Social v3.6.19 — Live Photo / audio editor, Story history, FYP

- Android `versionName`: `3.6.19`; `versionCode`: `48`.
- Upload foto + musik menghasilkan **file video nyata** melalui Canvas capture + MediaRecorder, dengan pratinjau sebelum posting.
- Editor menyediakan volume musik, volume audio asli untuk video sumber, dan durasi video foto.
- Riwayat Story menampilkan media asli (foto/video), tanggal, filter kalender, daftar penonton, dan jumlah replay.
- Sorotan dapat diedit: nama, privasi, sampul dipilih dari Story dalam koleksi, bentuk thumbnail, serta fit/crop.
- Login menyediakan Lupa Kata Sandi dengan verifikasi OTP admin yang sudah ada.
- FYP memakai sinyal aktivitas OHLI: pencarian, tontonan, video selesai/dilewati, suka, simpan, mengikuti, dan “Tidak tertarik”. Tidak membaca data TikTok atau aplikasi lain.
- Perubahan difokuskan pada alur Create, History Story, Sorotan, auth reset, dan pemeringkatan FYP; fitur Chat/LIVE/feed lain dipertahankan.

## Catatan Live Photo iPhone

Safari iOS tidak selalu menyerahkan pasangan HEIC + MOV Live Photo sebagai satu file yang dapat diproses oleh halaman web. HEIC/JPG saja adalah foto diam. Untuk mempertahankan gerakan asli, ekspor Live Photo menjadi video dari aplikasi Photos, lalu unggah MOV/MP4 sebagai video sumber dan tambahkan musik di editor OHLI. Foto diam + musik akan dirender menjadi video dengan frame foto statis selama durasi yang dipilih; itu bukan gerakan Live Photo asli. Format ekspor bergantung pada dukungan `MediaRecorder` browser (MP4 atau WebM).

## Build APK versi sebelumnya (v3.6.19)

Untuk membangun rilis sebelumnya, gunakan arsip source v3.6.19 dan workflow yang sesuai pada saat rilis tersebut. Rilis terbaru v3.6.20 menggunakan nama arsip dan artifact yang tercantum di `.github/workflows/build-apk.yml`.

## Status validasi

- `node --check` untuk JavaScript utama, smoke-test, dan pemeriksa source.
- `node tools/source-verify.mjs`: 192 pemeriksaan statis source.
- ZIP diuji dengan `unzip -t` dan memuat `settings.gradle`, `build.gradle`, `gradle.properties`, serta `app/build.gradle`.
- Build Gradle penuh dan tes perangkat iOS/Android belum dilakukan di lingkungan ini.

## Batasan yang belum selesai

- Deteksi pasangan Live Photo Apple berdasarkan Content Identifier, pemrosesan native HEIC+MOV, dan mode Loop/Bounce/Smooth Loop belum tersedia. Gunakan video MOV/MP4 hasil ekspor Photos untuk mempertahankan gerakan.
- History Story milik sendiri menyimpan media lengkap. Riwayat Story teman yang tersedia adalah konten teman yang dipin/diarsipkan dan memang dibagikan sesuai privasi; OHLI tidak mengarsipkan otomatis semua Story teman yang sudah kedaluwarsa.
- Rekomendasi FYP menggunakan aktivitas OHLI sendiri dan merupakan ranking heuristik, bukan model TikTok atau akses data eksternal.
- Backend masih memakai berkas JSON dan lock global pada perangkat Android host. Pembaruan ini tidak menyelesaikan seluruh bottleneck server atau menambahkan database SQL/SFU.
- Pemilihan sampul Sorotan saat ini memilih salah satu Story di dalam koleksi, bukan mengunggah gambar sampul bebas dari galeri.

---

# OHLI Social — Android-hosted source project

Proyek ini mengubah antarmuka web RKM Chat menjadi OHLI Social dengan layout responsif baru, sambil mempertahankan server Java Android dan endpoint chat yang sudah ada. Versi sumber terbaru adalah **v3.6.17**. Sumber utama berada di `app/src/main/java/com/rk/server/ServerService.java`; halaman lama tetap di `app/src/main/assets/index.html`, sedangkan UI sosial baru dipisah ke `ohli-social.css` dan `ohli-social.js`.

> **Status penting:** ini adalah fondasi aplikasi yang sudah diimplementasikan pada proyek sumber, bukan klaim bahwa seluruh daftar kebutuhan sosial setara platform besar selesai. Backend saat ini tetap menyimpan data sebagai berkas JSON pada perangkat Android yang menjadi host. Ini belum merupakan arsitektur produksi multi-server dengan database relasional, object storage, push-notification service, atau SFU Live.

## OHLI Social v3.6.5 — perbaikan chat, media, LIVE multi-peserta, dan performa

- Menambahkan relasi teman terpisah dari fitur Ikuti dan kontak Chat: permintaan teman, daftar masuk/terkirim, terima, tolak, batalkan, dan hapus teman.
- Relasi disimpan di `ohli-friends.json` pada server Android host. Respons Social hanya memakai ID publik `oh_…`; nomor akun tidak ditampilkan pada daftar teman.
- Halaman **Teman** menampilkan status permintaan dan status online yang sudah difilter oleh privasi. Profil orang lain menyediakan tombol tambah teman, terima/tolak, batalkan, atau hapus.
- Permintaan ganda dicegah, permintaan dibatasi 30 per akun dalam 24 jam, dan akun yang saling memblokir tidak dapat berinteraksi melalui fitur ini.
- Relasi historis yang sebelumnya dianggap teman oleh aturan mutual-follow/kontak tetap terbaca. Penghapusan relasi historis menulis penanda penghapusan agar relasi lama tidak muncul lagi.
- Menu aksi pesan sekarang menyediakan unduh foto/voice note/video/file, hapus dari chat akun sendiri, dan hapus untuk semua oleh pengirim. Media sekali lihat tetap dikecualikan dari unduhan.
- Setelah pesan baru dikirim, tampilan percakapan mengikuti pesan terbaru; polling chat aktif dibatasi ke percakapan yang sedang dibuka dan polling yang bertumpuk dicegah; sinkronisasi penuh dilakukan berkala (30 detik saat chat aktif, 15 detik saat latar).
- LIVE memakai alur undangan teman OHLI, persetujuan undangan, permintaan penonton untuk tampil, persetujuan Host, kontrol kamera/mikrofon peserta, keluar panggung, dan grid maksimal empat peserta termasuk Host. Kamera/mikrofon peserta baru diminta setelah persetujuan dan tindakan eksplisit pengguna.
- LIVE menggunakan koneksi WebRTC P2P mesh untuk peserta panggung. Kapasitas awal dibatasi empat orang di panggung dan enam penonton; ini bukan SFU dan koneksi lintas jaringan tetap bergantung pada dukungan NAT/STUN. Indikator kamera dan mikrofon ditampilkan di tile.
- Server menggunakan worker pool 4–8 thread; polling latar dikurangi. Namun data masih berupa JSON file dengan satu lock global, sehingga ini optimasi awal dan bukan jaminan server tanpa batas atau bukti peningkatan kecepatan tanpa pengukuran.
- VersionCode dinaikkan menjadi 34 / versionName 3.6.5; health endpoint melaporkan 3.6.5 dan workflow GitHub Actions memprioritaskan ZIP v3.6.5.

### Batas implementasi v3.6.5

OHLI Live multi-peserta sudah dihubungkan di source pada alur WebRTC P2P, tetapi belum diverifikasi melalui uji runtime multi-perangkat. Browser/jaringan tertentu membutuhkan server TURN; untuk penonton skala besar dan koneksi yang lebih andal diperlukan SFU/TURN. **OHLI Random Live Match belum diimplementasikan.** Server Android masih menyimpan akun, pesan, status, dan data sosial di JSON file dengan lock global; belum ada database relasional/cache/object storage. Tidak ada angka benchmark latensi atau kecepatan yang diklaim. Build APK dan tes runtime dua akun/perangkat belum dijalankan pada lingkungan ini.

## Perubahan OHLI Social

- Rombak UI menjadi shell responsif: sidebar desktop, navigasi bawah HP, menu tambahan untuk semua halaman, feed/story/video, profil, pencarian, notifikasi, grup/channel dasar, arsip/tersimpan dan pengaturan.
- Mempertahankan layar chat lama, autentikasi, kontak, pesan, lampiran, profil lama, panggilan WebRTC dan signaling Live.
- Endpoint sosial server: posting teks/media, audiens public/followers/private, feed berpaginasi, follow/unfollow, like, komentar, edit/hapus/arsip/pulihkan posting, bookmark, repost, pencarian, notifikasi, dan profil. v2.4.0 menambahkan edit/hapus komentar, moderasi komentar kreator, katalog/favorit audio dan notifikasi suka Story.
- v2.1.1 menambahkan akun privat dan permintaan follow, kontrol privasi daftar followers/following/likes/saved, daftar pengikut yang dapat dibuka, tayangan profil, tab Disukai/Posting ulang, Sorotan, reaksi dan statistik Story, QR kontak, ganti kata sandi, chat grup teks dan posting Channel teks.
- Validasi tanda tangan format media dasar untuk unggahan foto/video sosial. Media sosial mendukung hingga 1 GB per file dengan batas folder media server 16 GB; beberapa unggahan foto profil/Story lama tetap mempunyai batas lebih kecil. Endpoint media mendukung HTTP byte range.
- Server Android memakai foreground service `START_STICKY`, alarm restart ketika task di-swipe, dan receiver boot yang hanya memulai ulang jika server sebelumnya diaktifkan oleh pengguna.

Lihat [`docs/FEATURE-MATRIX.md`](docs/FEATURE-MATRIX.md), [`docs/API.md`](docs/API.md), [`docs/TWO-ACCOUNT-VALIDATION.md`](docs/TWO-ACCOUNT-VALIDATION.md), [`docs/PRODUCTION-DEPLOYMENT.md`](docs/PRODUCTION-DEPLOYMENT.md), [`docs/DATA-STORAGE.md`](docs/DATA-STORAGE.md), dan [`docs/V3.6.5-TEST-PLAN.md`](docs/V3.6.5-TEST-PLAN.md). Skema PostgreSQL tujuan berada di `database/migrations/001_initial_schema.sql` dan `002_ohli_comments_music_and_story_likes.sql`; keduanya hanya referensi migrasi dan belum dipakai APK.

# OHLI Social v3.6.17

## OHLI Social v3.6.17 — hapus Story aktif milik sendiri

- Menambahkan tombol **Hapus Story** pada pemutar Story aktif milik akun sendiri.
- Tombol hanya muncul untuk Story yang masih aktif dan dimiliki akun yang sedang login; Story teman tidak menyediakan aksi hapus.
- Aksi memanggil endpoint terautentikasi `/api/status/delete`; server tetap memeriksa pemilik Story.
- Setelah penghapusan, daftar Story aktif dimuat ulang. Salinan di Riwayat Story dan Sorotan tidak ikut dihapus, sehingga dapat dikelola secara terpisah.
- Android `versionCode` 45 dan `versionName` 3.6.17; health endpoint serta cache-busting aset mengikuti versi.

## OHLI Social v3.6.15 — perbaikan struktur build Gradle dan GitHub Actions

- Menambahkan `settings.gradle`, `build.gradle` root, dan `gradle.properties` yang sebelumnya tidak ikut dalam ZIP; kini folder source benar-benar merupakan root Gradle multi-project yang memuat modul `:app`.
- Root build menggunakan Android Gradle Plugin 8.7.3, cocok dengan Gradle 8.9 pada workflow.
- Workflow secara eksplisit menemukan folder Android, memeriksa file konfigurasi, berpindah direktori (`cd`) ke root proyek, lalu menjalankan `gradle :app:assembleDebug --stacktrace`.
- Workflow mendukung ZIP v3.6.15 di root repository, source yang sudah diekstrak di root, atau source di subfolder.
- Android `versionCode` 44, `versionName` 3.6.15.

## OHLI Social v3.6.14 — perbaikan kompilasi Sorotan

- Mendeklarasikan `groupIndex` sebagai `Map<String, JSONObject>` di dalam `getHighlightArray()` sebelum digunakan untuk mengelompokkan beberapa Story ke Sorotan yang sama.
- Mempertahankan perilaku pengelompokan koleksi dan pembacaan data Sorotan lama.
- Android `versionCode` 44, `versionName` 3.6.15; endpoint `/health` dan cache-busting aset mengikuti versi ini.
- Pemeriksa source menambahkan pemeriksaan bahwa `groupIndex` dideklarasikan sebelum dipakai.

## Build APK melalui GitHub Actions

1. Unggah `OHLI-Social-Source-v3.6.15.zip` ke root repository GitHub. Alternatifnya, ekstrak seluruh isi ZIP ke root repository.
2. Pastikan workflow berada di `.github/workflows/build-apk.yml` (gunakan workflow v3.6.15 di dalam ZIP). Jangan hanya menjalankan Gradle dari root repository jika proyek berada di subfolder.
3. Workflow mencari folder yang memiliki `settings.gradle` dan `app/build.gradle`, memvalidasi `build.gradle` root, lalu menjalankan `cd "$SOURCE_DIR"` sebelum `gradle :app:assembleDebug --stacktrace`.
4. Build menggunakan Java 17, Android SDK 35, Android Gradle Plugin 8.7.3, dan Gradle 8.9.
5. Buka **Actions → Build OHLI Social Server APK → Run workflow**. Unduh artifact `OHLI-Social-Server-APK-v3.6.15` berisi `app-debug.apk`.
6. Instal APK ke perangkat Android host. Buka OHLI Social Server dan tekan **MULAI SERVER**. Gunakan **BUKA HALAMAN CHAT** atau akses `http://IP-PERANGKAT:8080/` dari perangkat pada jaringan yang sama.


### Pemeriksaan source sebelum build

Jalankan `node tools/source-verify.mjs` dari folder proyek untuk memeriksa sintaks JavaScript, skrip inline HTML, struktur blok CSS, kontrak endpoint pertemanan, aksi media Chat, alur LIVE multi-peserta v3.6.5 dan konfigurasi workflow. Skrip ini tidak menggantikan kompilasi Android atau pengujian API runtime dengan dua akun. `node tools/smoke-test.mjs` menguji privasi direktori Chat/Social, profil pribadi Chat, riwayat lintas sesi, koleksi Story, klaim View Once, siklus pertemanan (request/accept/reject/cancel/remove), pengelolaan anggota dan polling grup, Channel, reaksi, koleksi stiker serta Audio Saya, dan izin pemilik menggunakan dua akun disposable. Skrip smoke test ini belum menjalankan kamera LIVE, alur undangan/panggung LIVE, atau pengujian aksi hapus/unduh pesan; ikuti tes manual pada `docs/V3.6.5-TEST-PLAN.md`. Baca `docs/TWO-ACCOUNT-VALIDATION.md` dan `docs/V3.6.5-TEST-PLAN.md` sebelum menjalankannya; skrip mengubah sementara pengaturan DM akun A, membuat notifikasi pertemanan uji, dan meninggalkan satu riwayat pencarian bertanda unik. Gunakan hanya akun uji yang boleh dimodifikasi.

Environment variable tidak digunakan oleh backend Android saat ini. Jangan menaruh password atau token akun di repository, screenshot, atau laporan bug.

## OHLI Social v3.5.1 — perbaikan profil Chat, koleksi media, LIVE dan navigasi

- **Chat dari profil:** resolver memakai runtime bridge terautentikasi sehingga token sesi ikut pada permintaan; pemeriksaan autentikasi server tetap wajib.
- **Stiker Saya:** impor gambar menampilkan pratinjau dan baru mengunggah setelah pengguna menekan Simpan. Daftar Stiker Saya/Favorit dimuat dari metadata server; media dapat dibaca pemilik dan penghapusan ditolak jika stiker sudah dipakai komentar.
- **Audio Saya:** audio impor disimpan sebagai file dan metadata server, ditampilkan di tab Audio Saya, memuat durasi jika browser dapat membacanya, dapat diputar/dipakai ulang, serta bisa dihapus bila belum terpakai pada postingan. Batas upload 30 MB dan validasi signature MIME tetap aktif.
- **LIVE:** modal memakai header kreator, badge LIVE, jumlah penonton, video vertikal, komentar overlay, reaksi/ikuti/bagikan, dan input di bawah. Transport tetap WebRTC P2P.
- **Reels:** tombol unduh ditempatkan di atas deretan aksi. Preferensi latar navigasi Hitam/Transparan disimpan di browser dan ikon tetap terlihat.
- **Tema Orange:** tersedia sebagai aksen tambahan yang tersimpan, tanpa mengganti Mode Terang/Malam.

### Batas pengujian v3.5.1

Pemeriksaan source statis tidak membuktikan kamera, mikrofon, codec MediaRecorder, audio codec, atau perilaku keyboard pada perangkat fisik. LIVE tetap P2P (bukan SFU), sehingga kapasitas bergantung pada tiap koneksi penonton. Unduhan ber-watermark mengikuti dukungan Canvas/MediaRecorder browser. Stiker/audio disimpan di server OHLI pada perangkat host, bukan cloud terdistribusi. Pengujian HP dan runtime dua akun tetap wajib.

## Pembaruan v3.4.0 — media, komentar, LIVE, dan tema

Pembaruan ini menjaga struktur website, login, API, serta alur upload lama. Yang berubah hanya komponen yang diminta:

- **Unduh video ber-watermark:** tombol unduh tersedia untuk video yang diizinkan pemilik. Server memeriksa `allowDownload`; browser membuat salinan MP4/WebM sesuai codec `MediaRecorder` yang tersedia, menambahkan watermark OHLI dan username pada salinan tersebut, lalu mengunduhnya. Feed tetap memutar media asli; file media server tidak dimodifikasi.
- **Tata video profil:** tab Media memiliki pilihan Transparan dan Terbatas. Pilihan disimpan lokal pada browser/perangkat.
- **Komentar:** balasan bertingkat, balasan melalui tekan-tahan komentar, like/unlike dengan hitungan server, stiker bawaan, impor gambar, pengiriman stiker, serta koleksi favorit. Panel komentar menyesuaikan tinggi viewport saat keyboard mobile tampil.
- **LIVE:** Studio menyediakan judul/deskripsi, izin kamera/mikrofon, pratinjau, ganti kamera, kualitas target, audiens dan pengaturan komentar. Penonton mendapat tampilan vertikal, komentar, like, ikuti host, bagikan dan keluar. Transport tetap WebRTC peer-to-peer yang sudah ada; bukan SFU/relay.
- **Chat:** ketika percakapan dibuka, daftar pesan menuju pesan terbaru. Pesan baru tidak memaksa scroll turun bila pengguna sedang membaca riwayat lama.
- **Video/Story:** menu Copy bawaan browser dibatasi pada area video Social; Story pause ketika ditekan dan dilanjutkan pada posisi yang sama ketika dilepas.
- **Mode malam:** pilihan Terang/Malam disimpan pada local storage perangkat dan memengaruhi Chat serta Social. Mode Terang mempertahankan tampilan terang sebelumnya.

### Batas dan verifikasi v3.4.0

- Watermark dibuat melalui Canvas + `MediaRecorder` di browser. Format hasil mengikuti dukungan perangkat (MP4 atau WebM); browser yang tidak mendukung encoder/capture stream akan menampilkan pesan kegagalan, bukan mengunduh video tanpa watermark. Ekspor video panjang dapat menggunakan memori dan baterai cukup besar.
- LIVE adalah P2P signaling, bukan streaming terpusat. Source membatasi hingga enam penonton langsung; koneksi publik yang stabil memerlukan TURN/SFU, HTTPS, serta kapasitas server yang sesuai. Komentar diperbarui melalui polling berkala.
- `node tools/source-verify.mjs` lulus 77 pemeriksaan statis. Tes server dua akun belum dijalankan, dan APK belum dibangun karena lingkungan kerja ini tidak memiliki Android SDK/Gradle. Verifikasi pada HP/iPhone dan browser desktop masih diperlukan.

## Perubahan v3.2.0 — penyelesaian fitur tertunda

- **Riwayat lintas perangkat:** pencarian, profil, posting, foto, video pendek, dan Story dikirim ke server per akun. Antrean lokal menyimpan aktivitas ketika jaringan putus, mengirim ulang saat online, dan memakai `clientId` idempoten agar retry tidak menggandakan event. Perangkat lain pada host dan akun yang sama membaca riwayat server yang sama.
- **Pin/arsip Story teman:** koleksi tersimpan di server, bisa dipin/diarsipkan, dilepas pin, dikeluarkan dari arsip, dan dibaca dari sesi login kedua.
- **View Once:** teks disembunyikan dari daftar chat dan dibuka melalui klaim atomik pertama; klaim kedua, termasuk token sesi/perangkat lain, ditolak HTTP 410. Foto/suara membatasi GET pertama. Video/dokumen menggunakan kunci sesi acak untuk melanjutkan HTTP Range sampai 10 menit.
- **Privasi nomor:** Social menggunakan `/api/social/users` dengan ID `oh_…`; `/api/users` merupakan direktori Chat yang hanya mengembalikan nomor ketika kebijakan DM penerima mengizinkan dan tidak ada blokir. Pencarian nomor, resolusi Chat, pengiriman pesan, dan panggilan juga memeriksa izin.
- **Grup dan Channel:** smoke test dua akun memeriksa join grup, pesan, polling satu suara aktif, follow Channel, siaran hanya oleh pemilik, reaksi emoji, serta pembatasan penghapusan hanya untuk pemilik. Penghapusan grup/Channel juga membersihkan pesan/siaran terkait.
- Ditambahkan endpoint penghapusan Story milik sendiri untuk pengelolaan Story dan pembersihan data uji.
- VersionCode `23` / versionName `3.2.0`; `/health` melaporkan `3.2.0`; workflow saat itu memprioritaskan ZIP `OHLI-Social-Source-v3.2.0.zip`.

**Batas penting:** View Once bukan DRM dan tidak mencegah screenshot, screen recording, kamera eksternal, atau salinan saat media sedang tampil. Video/dokumen mengizinkan Range hanya pada kunci sesi pertama dalam jendela 10 menit; sesi baru ditolak. Sinkronisasi lintas perangkat memerlukan host Android yang sama. JSON pada satu perangkat bukan database multi-host. APK masih harus dibangun melalui GitHub Actions dan diuji pada perangkat Android nyata.

## Perubahan v3.0.0

- Riwayat aktivitas Sosial (pencarian, profil, postingan, foto, video pendek dan Story) dicatat oleh server per akun, dapat dibuka dari perangkat lain pada server OHLI yang sama, serta dapat dihapus dari akun.
- Koleksi Story teman kini mempunyai aksi pin, arsip, lepas pin, keluarkan dari arsip, dan hapus. Data koleksi disimpan server dan media yang dikoleksi tetap dapat diakses pemilik koleksi setelah Story sumber kedaluwarsa, selama berkas media masih ada.
- Pesan sekali lihat 1× kini memakai status pembukaan pada pesan di server. Media foto/suara/video/dokumen hanya dapat diklaim sekali oleh penerima; untuk video, klien mengambil satu salinan sementara agar pemutaran tidak terputus oleh beberapa HTTP Range. Dokumen memakai unduhan streaming browser agar tidak dimuat seluruhnya ke memori. Pesan teks baru dianggap terbuka setelah gelembung pesan terlihat pada layar selama jeda singkat.
- API Social menggunakan ID sosial acak stabil (`oh_…`) pada profil, pencarian, daftar pengikut, daftar penonton, komentar, like, repost, notifikasi, Story, grup, Channel dan Live—termasuk respons yang dikirim kembali kepada pemilik konten. Nomor telepon tetap menjadi kunci internal server dan hanya diberikan lewat alur Chat/QR/kontak yang memang membutuhkannya. Endpoint validasi nomor dipertahankan untuk fungsi tambah kontak Chat.
- Pengaturan aktivitas dan pesan langsung menerima tiga pilihan: semua orang, teman, atau tidak seorang pun. Pemeriksaan izin dijalankan di server, bukan hanya menyembunyikan tombol UI.
- VersionCode dinaikkan menjadi 21 / versionName 3.0.0; `/health` melaporkan versi 3.0.0; workflow GitHub Actions memprioritaskan arsip v3.0.0.
- Batas keamanan 1×: ini mencegah pembukaan ulang melalui API OHLI setelah klaim pertama, tetapi tidak dapat mencegah screenshot, screen recording, kamera eksternal, atau salinan yang dibuat saat media sedang tampil. Jangan gunakan 1× untuk materi yang harus dijaga kerahasiaannya secara absolut.
- Batas infrastruktur: JSON tetap disimpan pada satu perangkat Android host. Riwayat/Story collection dapat tersinkron lintas perangkat hanya jika semua perangkat mengakses host dan akun yang sama. Kehilangan perangkat host tanpa backup dapat menghilangkan data. Build APK dan uji runtime dua akun/perangkat wajib dilakukan melalui GitHub Actions dan perangkat nyata sebelum rilis.

## Perubahan v2.6.0

- Video Pendek di HP sekarang memakai area layar penuh: header aplikasi disembunyikan saat Reels aktif, tab Mengikuti/Untuk Kamu menjadi overlay, sisi aksi tetap berada di atas video, dan navigasi bawah tetap satu baris lima ikon (Beranda, Jelajahi, Buat, Video, Profil).
- Navigasi bawah memakai kolom grid yang eksplisit agar urutannya konsisten pada browser Android; label ikon dipadatkan dan tombol Buat berada di tengah.
- Aksi postingan HP tetap satu baris lima kolom dengan ikon dan label ringkas: Suka, Komentar, Simpan, Posting ulang, Bagikan.
- Story mempertahankan viewer fullscreen yang sudah ada. Progress di atas sekarang menampilkan satu segmen untuk setiap klip dari akun yang sedang dilihat; pindah ke akun berikutnya dilakukan setelah klip akun tersebut selesai.
- Autoplay video di Beranda, Reels, dan Story mencoba memutar audio asli saat browser mengizinkan. Jika autoplay bersuara ditolak browser, video tetap berjalan mute; interaksi pertama pengguna membuka izin audio untuk sesi tersebut, dan tombol speaker tetap tersedia. Kebijakan browser/OS tidak dapat dipaksa oleh kode situs.
- Bar Audio asli di kartu Beranda dan Reels kini dapat dibuka. Detail audio menawarkan pratinjau, Simpan suara, dan Gunakan audio ini; suara asli diekstrak dari video sumber lalu dimasukkan ke katalog OHLI agar dapat dipakai ulang.
- Sorotan profil dipajang sebagai lingkaran di bawah bio, dan tema opsional Liquid Glass ditambahkan tanpa menghapus tema terang/gelap yang sudah ada.
- VersionCode dinaikkan ke 17 dan versionName ke 2.6.0. Health endpoint juga melaporkan versi 2.6.0. Workflow GitHub Actions memprioritaskan ZIP v2.6.0 dan tetap menerima arsip lama.
- Pengecekan statis: `node --check` seluruh file JS, dua blok JS inline HTML, dan keseimbangan kurung CSS lulus. APK belum dibangun dan belum diuji pada perangkat fisik.

## Perubahan v2.5.0

- Tata letak Video Pendek di HP dibenahi: video memenuhi area layar, navigasi bawah diposisikan mengambang di atas video, dan mode Tema Transparan benar-benar menghilangkan latar bar navigasi (ikon tetap putih). Layout desktop/laptop tidak diubah.
- Autoplay Beranda diaktifkan untuk video yang cukup terlihat di viewport; hanya satu video OHLI yang diputar pada satu waktu. Browser dapat memulai video dalam keadaan mute karena kebijakan autoplay perangkat.
- Story diubah menjadi viewer fullscreen: media menutup layar, kontrol video native disembunyikan, dan area separuh kiri/kanan dipakai untuk Story sebelumnya/berikutnya tanpa tombol panah.
- Aksi Suka, Komentar, Simpan, Posting ulang, dan Bagikan pada kartu feed HP dipaksa satu baris horizontal agar konsisten dengan tampilan laptop.
- Pemilih suara editor video disusun menjadi empat sumber: Musik tren (diurutkan berdasarkan jumlah penggunaan pada posting OHLI), Disimpan, Ambil dari video perangkat, dan File audio perangkat. Audio katalog dapat dipakai ulang melalui tombol Gunakan Audio Ini.
- Versi Android dinaikkan ke versionCode 16 / versionName 2.5.0. Workflow GitHub Actions memprioritaskan ZIP v2.5.0 serta tetap menerima ZIP versi sebelumnya.
- Pemeriksaan sintaks JavaScript dan tes statis UI dijalankan. APK belum dibangun dan perilaku layar HP belum dapat diverifikasi pada perangkat fisik dari lingkungan ini.

## Perubahan v2.4.0

- Komentar memiliki endpoint edit (hanya penulis komentar), hapus (penulis komentar atau pemilik postingan), dan kontrol komentar aktif/nonaktif yang hanya dapat diubah kreator. Server memeriksa token, kepemilikan dan izin membaca sebelum mengubah data; perubahan tersimpan pada JSON postingan.
- Katalog audio menyimpan judul, artis, pemilik, MIME type, file media, jumlah penggunaan dan waktu unggah. Pengguna dapat menjelajah katalog, membuka detail lagu beserta daftar postingan yang memakai audio, menyimpan/menghapus favorit, dan memilih audio di editor video. Endpoint media memastikan akses mengikuti privasi pemilik.
- Editor video dapat mengambil audio dari video lokal melalui MediaRecorder/Web Audio, mengunggahnya ke katalog, serta mencampurkan audio pilihan dengan video saat publikasi. Hasil ekspor memakai format yang didukung MediaRecorder browser (umumnya video WebM/Opus; tidak melakukan transcoding paksa ke MP3/AAC). Browser harus mendukung MediaRecorder, Web Audio dan captureStream; bila tidak, UI menampilkan pesan kompatibilitas.
- Suka Story baru (bukan aksi batal suka) membuat notifikasi `story_like` di inbox server; halaman Notifikasi menampilkan nama akun, waktu, status belum dibaca dan membuka Story terkait bila masih aktif.
- Backend Android tetap memakai penyimpanan JSON privat: `ohli-music.json` dan `ohli-music-favorites.json` baru ditambahkan tanpa menghapus file data yang lama. Migrasi PostgreSQL referensi 002 menambahkan tabel musik/favorit/suka Story serta kolom moderasi komentar dan relasi musik posting.
- Versi Android dinaikkan ke versionCode 15 / versionName 2.4.0. Workflow GitHub Actions memprioritaskan ZIP v2.4.0, kemudian menerima versi sumber sebelumnya.
- Pemeriksaan statis JavaScript lulus. Build APK dan uji perangkat/browser nyata belum dijalankan di lingkungan ini karena Android SDK/Gradle tidak tersedia.

## Perubahan v2.2.0

- Perbaikan mobile: navigasi bawah memuat enam pintasan penting, halaman sosial tidak melebar ke samping, tombol aksi posting/video dirapikan, dan tombol menu chat tetap dapat dijangkau di layar kecil. Viewport tetap mendukung zoom perangkat.
- Rekomendasi akun di Beranda diacak ulang dan foto/nama akun menjadi tombol yang membuka profil.
- Story: cincin akun lain membedakan Story baru dan sudah dilihat berdasarkan catatan server; viewer otomatis berganti setelah durasi foto/teks atau saat video selesai, dapat digeser/ditap sisi kiri-kanan, dan pemilik profil dapat dibuka dari header Story.
- Story/Postingan: editor mendukung pintasan kamera, teks/stiker, crop, pemotongan rentang video, dan mute. Draf teks/caption disimpan lokal dan dapat dilanjutkan dari tab Draf profil; file media harus dipilih lagi.
- Pencarian dari Video Pendek memiliki tombol kembali yang membawa pengguna ke menu Video Pendek, bukan keluar dari alur OHLI.
- Kustomisasi tema lokal: warna aksen, aksen kedua, teks, latar, kartu, ukuran teks, dan kerapatan. Tema terang/gelap bawaan tetap tersedia.
- Chat di HP: lampiran dapat memilih beberapa foto/video/dokumen sekaligus (diunggah satu per satu agar lebih stabil), dan pesan suara mempunyai pilihan kecepatan 1×, 1,5×, dan 2×. Arsip chat mendapat pintasan di header HP; chat yang diarsipkan bisa dibuka atau dipulihkan.
- Endpoint status menambahkan `viewedByMe`/`seenAt`, dan metadata editor Story disimpan pada media status tanpa mengganti format data lama.
- Versi Android dinaikkan ke versionCode 14 / versionName 2.2.0. Workflow GitHub Actions kini mengenali ZIP v2.2.0 dan masih kompatibel dengan ZIP lama.

## Perubahan v2.1.1

- Tata letak profil dan halaman sosial di HP dibatasi ke lebar viewport, tab profil dapat digeser horizontal tanpa membuat seluruh halaman bergeser, dan rail tombol Video Pendek dirapatkan.
- Navigasi Profil selalu mengarah ke akun sendiri; profil teman tetap dibuka hanya lewat aksi profil teman. Tombol kembali baru mengubah halaman dalam OHLI, tidak menutup situs.
- Story diringkas menjadi satu lingkaran per akun. Lingkaran membuka Story terbaru; Story yang lebih lama milik akun yang sama dapat dibuka dengan geser atau tombol kiri/kanan. Nama/foto pembuat dapat ditekan untuk membuka profil.
- Foto profil di halaman profil dapat diketuk untuk melihat ukuran besar. Status tombol mengikuti pada daftar saran ikut menampilkan Ikuti/Mengikuti/Diminta.
- Pesan menampilkan centang 1 saat belum diambil perangkat penerima, centang 2 abu-abu setelah penerima mengambil pesan dari server, dan centang 2 biru setelah percakapan dibuka. Edit/hapus tetap tersedia lewat tekan lama, tidak lagi sebagai tombol permanen di setiap balon.
- Ketuk ganda pada foto/video memicu satu aksi suka; listener ganda dihapus agar sebuah ketuk ganda tidak mengirim dua toggle.
- Jelajah Topik membaca tagar dari postingan 14 hari terakhir dan memberi peringkat berdasarkan pemakaian serta engagement.
- Data JSON mempunyai backup dan file sementara yang dapat dipulihkan jika file utama rusak saat perangkat kehilangan daya. Data berada di penyimpanan internal aplikasi Android (`getFilesDir()`), sehingga restart server atau perubahan IP tidak menghapus database. Hapus data aplikasi atau uninstall APK tetap dapat menghapus penyimpanan internal; buat cadangan off-device secara berkala.

## Pengujian yang tersedia

- `node --check app/src/main/assets/ohli-social.js` dan syntax-check JavaScript inline dapat dijalankan lokal.
- `tools/smoke-test.mjs` melakukan tes API dua akun pada server yang sudah aktif. Gunakan akun khusus uji dan ikuti `docs/TWO-ACCOUNT-VALIDATION.md`; jangan gunakan akun utama.
- Build APK dan pengujian camera/mic, perilaku background service, serta dua perangkat sungguhan tetap harus dilakukan di GitHub Actions dan perangkat Android.

## Batasan operasional

- Android dapat menghentikan service jika pengguna melakukan **Force stop**, perangkat menerapkan penghematan baterai agresif, memori kritis, atau kebijakan OEM. Tidak ada aplikasi Android biasa yang dapat menjamin service hidup mutlak selamanya. Restart otomatis membantu untuk kondisi tertentu; tombol **MATIKAN SERVER** tetap mematikan service dengan sengaja.
- Server HTTP langsung di port 8080 belum layak diekspos tanpa proteksi ke internet publik. Gunakan HTTPS reverse proxy/tunnel terpercaya dan audit keamanan sebelum menyimpan data sensitif. Token login masih mengikuti rancangan lama dan beberapa request GET membawanya sebagai query parameter.
- Live menggunakan WebRTC peer-to-peer dan signaling yang disimpan pada perangkat host; bukan SFU, tidak direkam, dan dibatasi untuk skenario kecil. Koneksi tertentu memerlukan STUN/TURN. Tutup tab host atau putuskan jaringan dapat menghentikan media.
- Story sudah memiliki daftar penonton, jumlah replay per akun, daftar penyuka, serta koleksi Story teman untuk pin/arsip. Yang masih belum tersedia adalah Close Friends, daftar pengecualian Story, balasan Story lengkap, dan kontrol audiens per Story yang lebih terperinci.
- Grup saat ini mendukung pesan teks dan polling; Channel mendukung pengumuman teks satu arah dan reaksi emoji. Yang masih belum tersedia adalah lampiran grup/Channel, reply pesan, peran admin granular, undangan privat yang dikelola pemilik, panggilan grup, serta edit/hapus pesan grup.
- Fitur profil privat dan kontrol visibilitas berjalan melalui API, tetapi source ini tetap perlu diuji dengan dua akun dan dua perangkat nyata sebelum dipakai publik.


## OHLI Social v3.4.0
- Tampilan terpadu mode terang untuk semua halaman Social dan Chat: putih, abu-abu lembut, teks gelap, serta satu aksen biru seperlunya. Menggunakan font sistem iOS pada iPhone dan fallback font sistem pada platform lain; font Apple tidak disertakan sebagai file proyek.
- Profil diri Chat kini dapat dibuka dari kartu akun di header dan menu Fitur Chat. Halaman ini menampilkan nomor akun sendiri, nama, username Social, dan info pribadi melalui endpoint `/api/me` yang memerlukan sesi login.
- Nama dan username Social dapat diedit dari formulir profil Chat. Nomor tetap menjadi data akun Chat dan bukan username publik.
- Pemilik grup dapat memilih teman dari Kontak Chat maupun koneksi Social (mengikuti atau pengikut) ketika membuat grup, menambahkan teman setelah grup dibuat, melihat anggota, serta mengeluarkan anggota. Server memeriksa kepemilikan grup, akun terdaftar, relasi/kontak pemilik, dan status blokir.
- Verifikasi source ditingkatkan menjadi 64 pemeriksaan statis. Tes dua akun ditambah untuk memeriksa undangan, penambahan ulang, pencabutan akses anggota, dan privasi daftar anggota.
- Status pengujian: pemeriksaan sintaks JS dan kontrak source lulus. Tes dua akun memerlukan server aktif dan dua akun disposable; build APK masih harus dijalankan lewat GitHub Actions dan diuji pada HP.
- Batas fitur Grup tetap ada: reply pesan, lampiran grup, peran admin granular, panggilan grup, dan edit/hapus pesan grup belum tersedia.


## OHLI Social v3.5.1 — perbaikan geser untuk membalas chat

- Mempertahankan gesture geser dari kiri ke kanan pada gelembung pesan.
- Memperbaiki pengiriman agar target balasan yang tampil pada bar kutipan ikut dikirim bersama pesan.
- Aksi menu tekan lama “Balas” menggunakan mekanisme target balasan yang sama.
- Memperbaiki pembacaan pesan target dan membersihkan gesture saat sentuhan dibatalkan.
- VersionCode 27 / versionName 3.5.1.
- Pemeriksaan source tidak menggantikan uji sentuh nyata di HP.


## OHLI Social v3.6.1 — full-bleed feed dan LIVE fullscreen

- **Feed video pendek iPhone:** shell video dipasang fixed ke viewport, memakai `100dvh` dengan fallback `100vh`, latar hitam, video `object-fit: cover`, dan menu bawah ikon saja sebagai overlay transparan dengan safe-area inset. Metadata theme-color disetel hitam.
- **LIVE:** modal penyiar dan penonton memakai video fullscreen, header ringkas, rail kontrol kamera/mikrofon atau interaksi penonton, komentar overlay dengan fade, dan input bawah yang mengikuti `visualViewport`. Persiapan siaran memakai sheet pengaturan; setelah siaran dimulai sheet tertutup dan dapat dibuka dari tombol tiga titik. X dan tombol akhir siaran menampilkan dialog konfirmasi. Overlay coba kamera hanya muncul saat izin/kamera gagal.
- **Kamera depan:** pratinjau penyiar dimirror; kamera belakang kembali normal saat dibalik. Jumlah penonton awal dimulai dari 0 karena server hanya menghitung akun penonton, tidak memasukkan host.
- **Batas:** signaling tetap WebRTC P2P yang sudah ada; bukan SFU. UI baru tidak menambah server relay atau mengubah autentikasi, feed, unggah, maupun API streaming. Pengujian fisik iOS Safari/Android Chrome tetap diperlukan.


## v3.6.6 regression fixes
- Host local camera tile now hides the initials avatar only when the local camera is active; camera-off restores the avatar.
- Chat opens at the newest message and returns to the newest message after sending, including when a prior refresh was still running.
- See `OHLI-Social-v3.6.6-PERUBAHAN.txt` and `docs/V3.6.6-TEST-PLAN.md`.

## OHLI Social v3.6.7
- Bottom navigation mobile disederhanakan dengan menghilangkan pintasan Story/Status agar Chat lebih mudah disentuh.
- Polling latar dijarangkan dan hanya data relevan halaman yang diperbarui.
- Lihat `OHLI-Social-v3.6.7-PERUBAHAN.txt` dan `docs/V3.6.7-TEST-PLAN.md`.


## OHLI Social v3.6.9 — verifikasi nomor manual melalui WhatsApp

- Pendaftaran dan login baru wajib meminta OTP 6 digit yang dibuat server. APK server menerima notifikasi Android privat berisi nomor tujuan, jenis permintaan, dan kode; admin meneruskan kode secara manual melalui WhatsApp. Tidak ada koneksi ke akun WhatsApp atau API WhatsApp.
- OTP terikat pada nomor, challenge, dan jenis aksi; kedaluwarsa 5 menit, maksimal 5 percobaan, sekali pakai, dan hanya hash HMAC yang disimpan pada file privat aplikasi. Kunci HMAC dibuat acak dan disimpan terpisah di `ohli-auth-secret.bin`.
- Pembatasan: jeda 60 detik per nomor, maksimal 5 permintaan per nomor per 24 jam, dan 20 permintaan per IP per 10 menit (batas IP hanya berlaku selama proses server hidup). Jika tunnel Cloudflare berjalan pada perangkat yang sama, server membaca `CF-Connecting-IP` hanya dari koneksi loopback lokal; header dari koneksi langsung tidak dipercaya. Peta limiter di RAM juga dibatasi ukurannya. Permintaan yang terlalu sering mendapat HTTP 429.
- Endpoint lama `/api/register` dan `/api/login` ditolak agar klien lama tidak melewati OTP. Sesi yang sudah aktif sebelum peningkatan tetap bisa melanjutkan sesi; login baru memakai OTP. Logout atau sesi yang hilang akan meminta OTP lagi.
- Token API untuk fetch normal dipindahkan dari query URL ke header `X-RKM-Token`; token pada URL media lama tetap dipertahankan untuk kompatibilitas. Respons menambahkan `nosniff`, `DENY`, dan `no-referrer`. CSS/JS dicache 24 jam dengan query versi.
- Penting: notifikasi menampilkan kode di perangkat admin yang sudah terbuka, tetapi menyembunyikan konten pada lock screen. Kunci layar APK server dan matikan preview sensitif bila perangkat dipakai bersama. WhatsApp manual bukan verifikasi otomatis WhatsApp; admin harus memastikan nomor tujuan benar sebelum meneruskan kode.
- Backend masih JSON file dengan satu lock global. Cache aset mengurangi pemuatan berulang, namun tidak menggantikan benchmark perangkat atau migrasi SQLite/PostgreSQL untuk konkurensi tinggi. Jangan klaim server bebas bottleneck sebelum diuji.
- Pengujian: syntax-check JS, source-verifier dan build APK perlu dijalankan; uji OTP end-to-end memerlukan notifikasi Android nyata dan dua perangkat/nomor.


## OHLI Social v3.6.11 — perbaikan kompilasi Android

- Memperbaiki string petunjuk pada `MainActivity.java` yang sebelumnya terputus oleh baris baru mentah dan menyebabkan `unclosed string literal`.
- Memecah teks petunjuk menjadi rangkaian string Java dengan `\n` eksplisit.
- Menaikkan Android `versionCode` menjadi 39 dan `versionName` menjadi `3.6.11`.
- Memperbarui versi pada endpoint health, cache-busting aset web, serta prioritas arsip di workflow GitHub Actions.
- Tidak mengubah data pengguna, database, atau alur OTP pada perbaikan kompilasi ini.


## OHLI Social v3.6.12 — Sorotan sebagai koleksi Story

- Satu Sorotan dapat menyimpan beberapa Story; saat menambah Story, pengguna memilih koleksi yang sudah ada atau membuat baru.
- Mengetuk Sorotan membuka pemutar Story layar penuh dengan progress, autoplay video, dan navigasi antar-Story.
- Data Sorotan lama dibaca dengan kompatibilitas; endpoint tetap mengelompokkan record lama secara otomatis.
- Permintaan profil tambahan dijalankan paralel dan cover Sorotan memakai lazy loading.
- Android `versionCode` 41, `versionName` 3.6.12.


## OHLI Social v3.6.15 — Riwayat Story, Sorotan, dan Inbox Server

- Riwayat Story disimpan terpisah dari Story aktif 24 jam; Story lama tetap bisa dibuka, dibagikan ulang, ditambahkan ke Sorotan, dilihat statistiknya, atau dihapus dari Riwayat.
- Sorotan dapat diganti nama/privasi, dihapus seluruhnya, atau menghapus satu item Story tanpa menghapus Story asli di Riwayat.
- Replay Story yang sudah kedaluwarsa dihitung ketika ditonton melalui Sorotan yang boleh diakses.
- APK server memiliki tombol Pusat Notifikasi untuk membaca ulang permintaan OTP, login/daftar berhasil, dan peristiwa server. OTP kedaluwarsa dihapus dari inbox ketika inbox dibaca atau notifikasi baru disimpan.
- Verifikasi statis tidak menggantikan build Android, uji dua akun, dan pengujian perangkat nyata.


## OHLI Social v3.6.21 — update

- Direct video-call and LIVE camera switching keeps the current camera track until the replacement is acquired and applied; failed switch attempts retain the old camera.
- Call duration starts after WebRTC reaches the connected/completed state. Call history is stored per account on the Android server and cached locally in the browser.
- Social viewing history now displays square (1:1) media tiles and can reopen Story media that the account was allowed to view when the history item was recorded, provided the media file remains stored.
- Android service now refreshes its Wi-Fi lock when the Wi-Fi state changes; admin presence is refreshed in memory every 15 seconds and persisted at most once per minute; manager status polling is reduced to once every 3 seconds.
- Local user moderation, backup, and tunnel configuration panels are available in the server manager. Tunnel configuration stores/copies a public URL and provides setup guidance; it does not itself run cloudflared.

**Validation boundary:** source checks and JavaScript parsing can be run in this workspace, but an Android APK build, physical camera tests, Android sleep/Doze tests, Cloudflare tunnel connectivity tests, and multi-device WebRTC tests require a real Android build environment and device/network access. The JSON storage backend is retained for backward compatibility; SQLite migration and full performance benchmarking are not included in this version.
