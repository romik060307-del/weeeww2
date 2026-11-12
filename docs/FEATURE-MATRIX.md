# Matriks Fitur OHLI Social — v3.6.5

## v3.6.4 — Pertemanan

- **Tersedia di source:** endpoint daftar teman, permintaan masuk/terkirim, request, accept, reject, cancel, dan remove. Status disimpan pada `ohli-friends.json` dan ditampilkan melalui halaman Teman serta tombol profil.
- **Privasi:** daftar memakai ID publik Social; pengguna yang memblokir/saling diblokir tidak tampil dan tidak dapat mengirim permintaan. Status Online tetap melewati filter privasi dan heartbeat lama.
- **Kompatibilitas:** mutual-follow dan kontak yang sebelumnya dihitung sebagai teman oleh aturan izin DM tetap dikenali sebagai relasi lama.
- **Sudah terhubung pada source v3.6.5:** panggung LIVE WebRTC P2P hingga empat peserta termasuk Host, undangan teman, permintaan tampil dari penonton, persetujuan Host, kontrol kamera/mikrofon, dan keluar panggung. Belum diuji runtime lintas perangkat. **Belum tersedia:** Random LIVE Match, SFU/TURN produksi, push notification native, dan server multi-node.

## Pembaruan v3.6.5 — aksi media Chat, scroll pesan, LIVE multi-peserta, dan respons server

- **Chat:** unduh lampiran foto, voice note, video, dan file; hapus dari tampilan akun sendiri; pengirim dapat menghapus pesan untuk semua. Media sekali lihat tidak disediakan untuk diunduh.
- **Chat terbaru:** pengiriman pesan membawa percakapan ke pesan paling baru. Pembacaan riwayat lama tidak memaksa scroll turun.
- **LIVE:** Host mengundang teman OHLI; penerima menyetujui/menolak undangan; penonton meminta tampil; Host menerima/menolak; peserta secara terpisah mengaktifkan kamera/mikrofon; peserta dapat keluar panggung tanpa mengakhiri siaran. Grid menampung maksimal empat orang termasuk Host.
- **Performa awal:** pesan Chat saat percakapan aktif diminta berdasarkan peer, refresh bertumpuk dicegah, polling latar diperlambat, dan server memakai worker pool 4–8 thread.
- **Batas:** P2P mesh bukan SFU, maksimum awal enam penonton, tidak ada TURN produksi atau Random LIVE Match. JSON file + lock global masih membatasi skala; tidak ada benchmark runtime dalam paket ini.

## Pembaruan v3.5.0 — profil Chat, koleksi stiker/audio, LIVE, dan navigasi

- **Chat dari profil:** UI memanggil runtime bridge yang menyertakan token sesi; endpoint resolver tetap memvalidasi autentikasi dan aturan DM.
- **Stiker Saya:** gambar diimpor melalui preview lalu disimpan dengan upload binary ke server. Metadata pemilik masuk ke `ohli-comment-stickers.json`; daftar koleksi tersedia melalui `GET /api/stickers`. Pemilik dapat menghapus media yang belum dipakai komentar; media yang direferensikan komentar dilindungi dari penghapusan.
- **Audio Saya:** file audio dan metadata title/artist/duration disimpan di server melalui upload stream. `GET /api/music/mine` mengembalikan koleksi milik akun, media bisa dipreview dan dipakai kembali, sedangkan penghapusan hanya diizinkan bila belum direferensikan postingan. Audio dibatasi 30 MB dan signature format diperiksa.
- **LIVE:** UI fullscreen vertikal memakai signaling WebRTC P2P dan komentar realtime yang sudah ada. Bukan SFU; server saat ini membatasi enam penonton per siaran.
- **Reels:** tombol Download dipindahkan ke area tindakan bagian atas. Pilihan latar navigasi hitam/transparan tersimpan lokal dan tidak menghilangkan ikon.
- **Tema Orange:** pilihan aksen tambahan disimpan bersama preferensi tema Terang/Malam.
- **Chat mobile:** posisi pesan terbaru dipulihkan setelah layout media selesai; jika pengguna membaca riwayat lama, penanda Pesan terbaru tidak memaksa scroll.

### Batas v3.5.0

- Pemeriksaan otomatis statis dan smoke test yang disediakan berbeda dari pengujian perangkat. Kamera/mikrofon, encoder browser, hasil watermark, dan layout keyboard wajib diuji di HP target.
- File impor disimpan di server Android host dengan JSON metadata. Pastikan storage host cukup, server aktif, dan backup dilakukan.
- LIVE P2P cocok untuk tes kecil; penggunaan publik skala besar memerlukan TURN/SFU.

## Pembaruan v3.4.0 — video, komentar, LIVE, dan tema

- **Download video ber-watermark:** tombol Download tampil untuk video yang diizinkan pemilik. Server memvalidasi izin melalui `allowDownload`; browser membuat salinan file unduhan dengan watermark OHLI + username. Feed dan file media asli tetap tanpa watermark dan tidak ditulis ulang.
- **Profil video:** pilihan Transparan (grid menyatu dengan tepi halaman) dan Terbatas (grid terpusat dengan jarak) tersedia pada tab Media profil; preferensi disimpan lokal per browser/perangkat.
- **Komentar:** balasan bertingkat memakai `parentId`, balas melalui tombol atau tekan-tahan komentar, like/unlike serta hitungan server-side, stiker emoji berbentuk gambar, impor gambar, kirim stiker, simpan stiker ke favorit, dan gunakan kembali koleksi.
- **LIVE Studio:** pengaturan judul, deskripsi, audiens, komentar, kualitas target, pratinjau kamera, pergantian kamera, status mikrofon, izin kamera/mikrofon dan aksi mulai/akhiri. Viewer fullscreen vertikal mendukung komentar berkala, like, follow host, share dan keluar.
- **Chat:** membuka peer langsung ke pesan terbaru. Pesan yang masuk mengikuti posisi terbawah hanya ketika pengguna sebelumnya sedang di bawah; membaca pesan lama tidak memicu lompatan.
- **Video/Story:** menu konteks browser dan pemilihan teks diblokir terbatas pada elemen video Social; Story pause saat ditekan dan resume dari `currentTime`/waktu progres saat dilepas.
- **Tema:** pilihan mode terang/malam disimpan di local storage perangkat dan diterapkan pada Chat dan Social. Tema terang tetap menjadi default dan mempertahankan tampilan lama.
- `node tools/source-verify.mjs` lulus 77 pemeriksaan statis. Build APK, tes dua akun aktif, dan pengujian kamera/MediaRecorder pada perangkat nyata belum dijalankan.

### Batasan v3.4.0 yang perlu diketahui

- Proses watermark menggunakan Canvas dan MediaRecorder di perangkat pengguna. Ekstensi MP4/WebM ditentukan oleh codec yang didukung browser. Jika browser tidak mendukung encoder/capture stream, proses unduh gagal dengan pemberitahuan; kode tidak mengunduh file asli sebagai jalan pintas.
- Audio disalin dari capture video jika browser menyediakan `captureStream` atau Web Audio. Dukungan pemeliharaan audio dan codec berbeda antarbrowser, terutama Safari/iOS; wajib diuji pada perangkat target.
- LIVE tetap WebRTC P2P dengan signaling dan polling komentar. Batas server saat ini enam penonton langsung; layanan TURN/SFU/relay dibutuhkan untuk siaran publik dengan banyak penonton dan koneksi lebih andal. Tidak ada hadiah, moderator atau pembayaran palsu yang ditambahkan.
- Mode profil dan tema disimpan di perangkat, tidak disinkronkan antarperangkat. Riwayat komentar, like, dan stiker tersimpan pada backend JSON yang sudah ada.

## UI mobile v2.6.0
- Reels HP full-bleed dengan tab overlay, bar navigasi bawah transparan opsional, dan lima ikon pada satu baris.
- Feed HP menampilkan lima aksi dalam satu baris dengan label ringkas.
- Story tetap fullscreen dan menampilkan satu segmen progres per klip Story dari akun aktif.
- Audio video di feed, Reels, dan Story mencoba autoplay bersuara; browser dapat menolak autoplay bersuara hingga interaksi pengguna.
- Audio asli pada video dapat dibuka ke detail, disimpan ke katalog, lalu digunakan ulang.
- Sorotan profil tampil melingkar di bawah bio; tema Liquid Glass menjadi opsi tampilan tambahan.

## UI mobile v2.5.0
- Video Pendek mobile: stage fullscreen dan navbar overlay; mode transparan menghapus bidang latar navigasi.
- Feed mobile: video autoplay saat cukup terlihat (mute awal mengikuti kebijakan browser), dengan satu video aktif.
- Story fullscreen: tanpa kontrol native/tombol panah; tap separuh kiri/kanan berpindah cerita.
- Aksi feed mobile disusun satu baris: suka, komentar, simpan, posting ulang, bagikan.
- Editor suara: Musik tren, Disimpan, Dari video perangkat, Dari file perangkat. Tren diurutkan menurut jumlah pemakaian audio OHLI.

 Matriks fitur OHLI Social v2.4.0

Status di bawah menjelaskan apa yang disediakan oleh source code. Fitur tetap perlu diuji di dua akun/perangkat nyata sebelum digunakan publik.

## Fitur tambahan v2.4.0 yang telah dihubungkan ke server

- Komentar sendiri dapat diedit dan dihapus. Pemilik postingan dapat menghapus komentar siapa pun pada postingannya. Kreator dapat mengunci/membuka komentar per postingan; API server tetap memvalidasi hak akses walaupun klien dimodifikasi.
- Katalog musik dengan upload dan validasi tanda tangan dasar audio MP3, AAC/ADTS, M4A/MP4, OGG, WAV dan WebM audio; halaman detail memuat daftar postingan yang mengaitkan `audioId`; pengguna dapat menyimpan favorit dan memilih audio untuk video.
- Audio terunggah disimpan pada penyimpanan media server dan metadata berada pada `ohli-music.json`; favorit berada pada `ohli-music-favorites.json`. Akses stream musik mengikuti privasi akun pemilik dan aturan blokir.
- Editor video menyediakan ekstraksi trek lokal melalui MediaRecorder/Web Audio, serta pencampuran audio video dan musik terpilih sebelum upload bila browser mendukung captureStream + MediaRecorder. Track `audioId` ikut disimpan ke postingan, sehingga tautan judul audio dan halaman detail dapat dibuka.
- Suka Story baru membuat notifikasi `story_like` pada file notifikasi server. Halaman Notifikasi menampilkan aktivitas dan dapat menandai satu/semua notifikasi telah dibaca; membuka notifikasi mencoba membuka Story jika Story belum kedaluwarsa.

## Sudah memiliki alur UI dan endpoint server

- Registrasi/login, sesi token, penyimpanan akun pada server Android, dan hashing kata sandi PBKDF2 untuk akun baru/perubahan kata sandi.
- Chat privat dan fitur dasar lama: teks, lampiran, kontak, pengeditan/penghapusan sesuai implementasi yang tersedia, status pesan dan polling. v2.2 mendukung pemilihan batch beberapa foto/video/dokumen yang diunggah berurutan, serta tombol kecepatan pesan suara 1×/1,5×/2×.
- Feed sosial, posting teks/foto/video, audiens publik/pengikut/hanya saya, like, komentar, simpan, repost, bagikan, edit/hapus, arsip dan pemulihan.
- Profil, edit nama/bio/foto/cover, jumlah postingan, daftar pengikut/mengikuti yang dapat dibuka, dan penghitungan tayangan profil (maksimal satu catatan per akun per hari).
- Akun privat dengan permintaan mengikuti, persetujuan/penolakan permintaan, dan kontrol visibilitas daftar pengikut, daftar mengikuti, postingan disukai, serta koleksi tersimpan. Pemeriksaan akses diterapkan juga pada endpoint server.
- Tab profil Disukai, Posting ulang, Disimpan, Media, Sorotan, Draf (teks/caption lokal), dan Tentang. Akun yang direkomendasikan dapat diacak dan dibuka melalui foto/nama profil.
- Story teks/foto/video, viewer unik dan jumlah pemutaran ulang, status `viewedByMe`, lingkaran sudah/belum dilihat, viewer otomatis untuk foto/teks dan video, navigasi tap/swipe, statistik penonton untuk pemilik, reaksi suka, dan penyimpanan Story ke Sorotan. Sorotan bertahan setelah Story 24 jam berakhir.
- QR kontak akun sendiri, pemindai QR jika browser mendukung `BarcodeDetector`, dan jalur input nomor manual sebagai fallback.
- Penggantian kata sandi dengan verifikasi kata sandi lama; sesi perangkat lain dikeluarkan setelah kata sandi berubah.
- Pembuatan grup/Channel, alur gabung/keluar atau ikuti/berhenti mengikuti, chat grup teks + polling, serta pengumuman satu arah berupa teks dari pemilik Channel dengan reaksi emoji pengikut.
- LIVE WebRTC peer-to-peer dengan signaling, komentar dan daftar siaran; batas kapasitas nyata tergantung koneksi dan browser.
- UI responsif untuk webview/browser, feed, video pendek, chat, pengaturan, notifikasi, dan navigasi OHLI. Pada v2.2, navigasi HP diprioritaskan untuk Beranda, Video, Buat, Story, Chat, dan Profil; menu tambahan tetap tersedia melalui menu atas.

## Ada, tetapi masih terbatas

- Privasi akun menyaring data dan konten melalui API. Jumlah pengikut/mengikuti tetap dapat tampak sebagai angka meski daftar namanya dibatasi.
- Tayangan profil dihitung per akun per hari, bukan setiap refresh. Ini bukan analytics waktu nyata atau daftar kunjungan historis tanpa batas.
- QR scanner menggunakan `BarcodeDetector` atau ZXing browser dan meminta kamera belakang. Kamera browser memerlukan secure context: HTTPS atau localhost; `http://IP-lokal` biasa akan ditolak browser. Jika izin/API tidak tersedia atau library tidak bisa dimuat, input nomor manual menjadi fallback.
- Grup mendukung pesan teks dan polling; Channel mendukung pengumuman teks satu arah dan reaksi emoji. Lampiran grup/channel, balasan pesan, admin granular, undangan privat yang dikelola pemilik, panggilan grup, serta edit/hapus pesan grup belum tersedia.
- Sorotan dibuat dari Story aktif milik sendiri. Belum ada editor urutan, sampul, kategori, atau pengelolaan highlight lanjutan.
- Suka Story menggunakan toggle server, notifikasi masuk ke inbox, dan endpoint daftar Story pemilik menampilkan siapa yang menyukai Story.
- Perbaikan CSS v2.2 membatasi overflow horizontal, merapikan tombol mobile, menampilkan navigasi enam pintasan, dan memastikan menu tambahan chat dan pintasan arsip di header dapat dijangkau. Zoom tetap diizinkan oleh viewport. Kondisi tiap ukuran layar/iPhone tetap perlu diuji pada perangkat asli.
- LIVE hanya signaling P2P. Untuk banyak penonton diperlukan TURN/SFU; host harus tetap terhubung dan memberi izin kamera/mikrofon.
- Draf konten dan tema kustom hanya disimpan lokal di browser/perangkat pengguna (tidak tersinkron). Media draf harus dipilih ulang ketika melanjutkan. Arsip chat tetap menggunakan preferensi lokal browser. Seluruh JSON akun, pesan, status, follow, dan blokir berada di `getFilesDir()` pada perangkat host, dilengkapi file sementara dan cadangan `.bak` untuk pemulihan saat proses mati/listrik terputus. Data bertahan saat reboot, server mati, Wi-Fi terputus, dan alamat IP berubah; setelah IP berubah, buka alamat server baru. Menghapus data aplikasi atau uninstall tetap menghapus data lokal.

## Belum disediakan / perlu infrastruktur atau pengembangan lanjutan

- Database relasional produksi, transaksi multi-proses, indeks pencarian, object storage eksternal, backup off-device, monitoring, WAF/rate limit, audit keamanan, dan pemulihan bencana.
- OTP/SMS/e-mail, reset kata sandi terverifikasi, push notification ketika aplikasi tertutup, dan enkripsi end-to-end dengan protokol yang diaudit.
- Konversi/transcoding hasil campuran audio ke MP3/AAC secara paksa belum disediakan; hasil ekspor mengikuti codec MediaRecorder browser (umumnya WebM/Opus). Browser tanpa MediaRecorder/Web Audio/captureStream tidak dapat menggunakan alur ekstraksi/pencampuran ini. Fitur daftar penyuka Story, algoritma FYP personal lanjutan, draf server-side, dukungan multi-foto satu posting, chat grup dengan media, indeks pencarian, moderasi/laporan, role admin granular, serta analytics kreator lanjutan masih memerlukan pengembangan tambahan.
- Pengujian dua perangkat fisik (terutama iPhone) dan build APK final. Build lokal pada sesi pembuatan source tidak tersedia karena Android SDK dan Gradle tidak terpasang; gunakan GitHub Actions pada repository.

Jangan menganggap keterbatasan di atas sebagai fitur produksi yang telah selesai.


## Perbaikan v2.1
- Responsif HP: overflow horizontal profil ditahan di viewport dan rail Video Pendek dirapatkan.
- Story satu bar per akun; cerita terbaru terbuka pertama dan cerita sebelumnya dapat dinavigasi.
- Foto profil dapat dilihat ukuran besar, follow state ditampilkan dengan benar, dan double-tap like memakai satu request.
- Centang pesan mengikuti status dikirim/diterima/dibaca; edit/hapus tersedia melalui tekan lama.
- FYP menggunakan skor engagement dan peluruhan waktu; Jelajah Topik menampilkan tagar aktif dari postingan dua minggu terakhir.
- Blokir disimpan di server dan operasi blokir bersifat idempoten.
- JSON menyimpan cadangan atomik di storage internal aplikasi untuk pemulihan setelah restart atau kehilangan daya.


## Perbaikan v2.2.0 dan batasnya
- Story menggunakan endpoint `/api/status/view` yang sudah ada untuk mencatat tontonan. Status baca hanya diperbarui saat request view berhasil; pada koneksi terputus, viewer tetap bisa berjalan tetapi indikator mungkin belum tersinkron sampai data dimuat ulang.
- Editor video menyimpan metadata crop/mute/rentang putar; server menyimpan metadata dan file asli. Ini bukan proses render/transcoding permanen seperti editor video penuh.
- Pencarian dari Reels kembali ke Reels melalui tombol kembali internal OHLI.
- Kustomisasi tema, draf Story/Post, arsip chat, dan beberapa preferensi adalah lokal pada browser/perangkat, bukan sinkron lintas perangkat.
- Kirim batch chat mengirim setiap file sebagai pesan terpisah dan satu per satu. Jika terjadi kegagalan di tengah, file sebelumnya tetap terkirim dan UI menampilkan jumlah yang berhasil.
- Kamera/pemindai QR memerlukan secure context (HTTPS atau localhost) serta izin kamera; fallback nomor manual tetap dipertahankan.
- Musik dari video lain, pemilihan audio dari galeri untuk Reels, halaman audio, dan favorit suara belum dibuat karena API server belum memiliki katalog/stream audio, atribusi hak pakai, atau pipeline mixing/transcoding. Tidak ada tombol palsu yang mengklaim fitur ini aktif.
- Algoritma `Untuk Kamu` dan Jelajah Topik masih mengandalkan skor engagement/umur konten serta tagar pada server saat ini; belum merupakan sistem rekomendasi personal dengan riwayat tontonan dan pembelajaran perilaku tingkat lanjut.
- Belum dilakukan build APK atau pengujian kamera/gesture lintas perangkat dalam sesi ini. Jalankan workflow GitHub Actions, lalu uji pada Android dan iPhone/browser nyata.


## Pembaruan frontend v2.3.0

- Reels menggunakan IntersectionObserver dengan ambang 80%, pause eksklusif ant video, gesture tahan sekitar 420 ms untuk 2x, pengaturan autoplay/suara, label audio dari metadata jika ada, dan susunan tombol horizontal untuk desktop lebar. Autoplay bersuara tetap bergantung pada izin gesture browser.
- Story menyediakan balasan melalui chat privat yang memakai endpoint pesan lama; daftar penonton diurutkan menurun menurut jumlah replay yang telah dicatat server.
- Tema transparan nav Reels dan Anti-zoom tersedia sebagai preferensi lokal browser.
- Tidak ada endpoint baru untuk edit/hapus komentar atau disable comments. Belum ada katalog/pencampuran musik atau ekstraksi audio video lokal. Suka Story belum mengirim notifikasi khusus dari server.


## Catatan pembaruan v2.4.0
- Endpoint komentar baru: `/api/posts/comments/edit`, `/api/posts/comments/delete`, dan `/api/posts/comments/settings`.
- Endpoint musik baru: upload `purpose=music`, `GET /api/music`, `GET /api/music/favorites`, `GET /api/music/detail`, `POST /api/music/favorites`, dan `POST /api/music/delete`.
- `POST /api/status/like` membuat notifikasi `story_like` bagi pemilik Story ketika pengguna lain menyukai Story tersebut.
- Data musik tersimpan sebagai JSON privat dan file media; migrasi SQL referensi tersedia pada `database/migrations/002_ohli_comments_music_and_story_likes.sql`. Migrasi SQL tidak dijalankan oleh aplikasi Android.


## Penyelesaian v3.0.0

- Riwayat Social disimpan oleh server per akun (`ohli-activity-history.json`), termasuk pencarian, profil, posting, foto, video pendek, dan Story. Riwayat dapat dibuka dari browser lain yang masuk ke host/akun sama, serta dapat dihapus.
- Pin dan arsip Story teman menggunakan `/api/status/collections` dan `ohli-story-collections.json`; aksi lepas pin, keluarkan dari arsip, dan hapus tersedia. Koleksi harus dibuat saat Story masih dapat dilihat.
- Pesan 1×: server menyembunyikan isi teks dalam GET daftar chat penerima sampai klaim pembukaan eksplisit pertama pada `/api/messages/once/open`. Foto dan suara diklaim sekali pada endpoint media; video/dokumen memakai kunci sesi untuk range/unduhan selama 10 menit. Pengeditan pesan 1× ditolak. Batasan screenshot/rekam layar tetap berlaku.
- API Social menggunakan Social ID acak stabil (`oh_…`), bukan nomor telepon. Nomor hanya tersedia pada jalur Chat privat/QR/tambah kontak dan data milik akun sendiri.
- Grup memiliki polling server-side dengan satu suara aktif per anggota; Channel memiliki reaksi emoji server-side. Serialisasi pesan grup, hasil voting, dan respons pembuatan Channel menyamarkan nomor internal.
- Izin Direct Message dan status aktif diperiksa di server dengan pilihan Semua orang/Teman/Tidak seorang pun.
- Uji sintaks dan pemeriksaan struktur source tidak menggantikan uji runtime dua perangkat. Build APK serta uji Android/iOS nyata tetap harus dilakukan di GitHub Actions/perangkat.


## Penyelesaian v3.2.0

- Riwayat aktivitas Social tersimpan server-side per akun; antrean retry lokal menangani offline dan `clientId` mencegah duplikasi saat retry. Peristiwa yang direkam: pencarian, profil, posting, foto, video pendek, dan Story. Login akun yang sama dari perangkat lain membaca history server yang sama.
- Pin dan arsip Story teman bertahan pada storage server. Aksi unpin dan unarchive membersihkan item koleksi jika kedua penanda sudah tidak aktif. Story collection API mengembalikan Social ID pemilik Story, bukan nomor.
- View Once teks menggunakan klaim atomik; isi tersembunyi pada GET daftar pesan dan klaim kedua (termasuk token login kedua) menerima HTTP 410. Foto/suara mengunci akses pertama; video/dokumen mengunci pembukaan pertama dan mengizinkan Range memakai kunci sesi yang sama selama 10 menit. Screenshot/rekaman layar tidak dapat dicegah.
- Pemisahan API direktori diperketat: `/api/social/users` memberikan ID `oh_…`; `/api/users` adalah direktori nomor Chat yang hanya mengembalikan penerima yang kebijakan DM-nya mengizinkan. `/api/user-exists`, `/api/chat/resolve`, pesan dan signaling panggilan memeriksa kebijakan DM/blokir.
- Grup dan Channel memiliki endpoint penghapusan owner-only untuk membersihkan semua pesan grup atau posting Channel terkait. Ini juga menjadi jalur cleanup saat smoke test.
- `tools/smoke-test.mjs` kini menguji dua akun dan sesi login kedua untuk direktori privasi, history, Story collections, View Once, Group/poll, Channel/reaction, serta posting Social. Jalankan pada server uji dengan dua akun disposable.
