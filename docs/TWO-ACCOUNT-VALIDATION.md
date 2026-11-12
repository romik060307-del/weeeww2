# Uji dua akun OHLI Social v3.4.0

Dokumen ini memisahkan pemeriksaan otomatis dari uji tampilan manual. Jalankan hanya pada server uji dan dua akun disposable. Jangan memasukkan kredensial utama ke repository atau chat.

## Uji otomatis API

Syarat: server OHLI berjalan dan dua akun uji terdaftar. Dengan Node.js 18+ jalankan: 

```bash
OHLI_BASE_URL=http://IP-HOST:8080 \
OHLI_PHONE_A=628xxxxxxxxxx OHLI_PASSWORD_A='password-uji-A' \
OHLI_PHONE_B=628yyyyyyyyyy OHLI_PASSWORD_B='password-uji-B' \
node tools/smoke-test.mjs
```

Skrip login ke akun A dan B, lalu membuat sesi kedua untuk masing-masing akun. Pada v3.4.0 skrip juga menguji profil pribadi Chat dan undangan/penambahan ulang anggota grup. Yang dites: direktori Social hanya berisi ID `oh_…`, kebijakan DM privat menyembunyikan nomor dari direktori Chat dan menolak resolusi nomor, riwayat A dapat dibaca dari sesi A kedua tetapi tidak dari B, pin/arsip Story teman tersimpan lintas sesi, isi teks 1× disembunyikan dan klaim kedua mendapat HTTP 410, grup menerima pesan dan polling dengan satu suara aktif, Channel hanya bisa diposting pemilik dan reaksinya tersimpan, serta pemilik saja dapat menghapus grup/Channel. Skrip menghapus postingan, pesan, Story, grup dan Channel uji. Satu riwayat pencarian bertanda unik tertinggal di akun A karena API menghapus riwayat secara keseluruhan, bukan satu item. Kebijakan DM akun A dipulihkan ke nilai awal pada blok cleanup.

## Uji manual pada HP

1. Login akun A dan B pada dua ponsel/browser terpisah, keduanya mengarah ke URL host OHLI yang sama.
2. Pada A, ubah Privasi → Pesan langsung ke **Tidak seorang pun**. Dari B, pastikan A tidak muncul di hasil Chat, ikon Chat pada profil Social tidak tersedia, pencarian nomor tidak mengungkap akun, dan mengirim pesan/panggilan langsung ditolak. Kembalikan ke Semua orang atau Teman setelah tes.
3. Dari A, lakukan pencarian, buka profil, postingan, foto, video pendek, dan Story. Login akun A dari perangkat kedua dan buka Pengaturan → Riwayat aktivitas. Aktivitas harus muncul dari server. Uji dengan mematikan jaringan sementara saat merekam aktivitas, lalu online kembali dan cek bahwa antrean tersinkron tanpa duplikasi.
4. Dari A, buat Story publik. Dari B, buka Story lalu gunakan Pin dan Arsip. Tutup aplikasi, buka kembali, dan cek koleksi dari B: item harus tetap ada. Lakukan Lepas pin/Keluarkan dari arsip untuk memulihkannya.
5. Dari A, kirim teks/foto/video/dokumen dalam mode 1× kepada B. B buka sekali. Dari sesi B lain, coba buka lagi: server harus menolak klaim baru. Video/dokumen boleh melanjutkan HTTP Range hanya pada kunci sesi pembukaan pertama dalam 10 menit. Tutup sesi dan pastikan pembukaan baru ditolak.
6. Dari A, buat grup publik. B bergabung, kirim pesan, buat polling, lalu B pilih opsi lain. Pastikan hanya satu suara B yang aktif dan anggota lain melihat hasil yang sama.
7. Dari A, buat Channel publik. B mengikuti dan memberi reaksi emoji. Pastikan B bisa membaca/mereaksi, tetapi tidak bisa menerbitkan siaran; hanya pemilik yang bisa memposting.
8. Coba buka profil, Story, komentar, daftar pengikut, notifikasi, Grup dan Channel milik akun lain. Tidak boleh ada nomor telepon yang muncul di permukaan OHLI Social; nomor hanya tersedia di jalur Chat/akun sendiri yang memang diizinkan.
9. Untuk uji Grup v3.4.0, buat hubungan Social antara A dan B (ikuti akun B dari A atau sebaliknya), kemudian dari pemilih anggota grup pastikan B tampil dengan label Mengikuti/Pengikut. Pilih B dan buat grup privat. Pastikan `memberCount` menjadi 2 dan akun B dapat membuka grup tanpa menekan Bergabung. Ulangi dengan kontak Chat tersimpan untuk memastikan kedua sumber anggota berfungsi.
10. Ambil `GET /api/groups/members` dari akun A, keluarkan anggota B memakai `id` sosial yang dikembalikan, lalu pastikan akses B ke `/api/groups/messages` ditolak `403`. Tambahkan lagi dengan `POST /api/groups/members` berisi `action: "add"` dan `phones: [socialId-B]`; akses B seharusnya pulih.
11. `GET /api/me` hanya boleh dipanggil dari sesi akun sendiri; verifikasi nomor yang dikembalikan cocok dengan akun yang login. Jangan menaruh hasil `/api/me` pada profil publik Social.
12. Periksa semua halaman Social dan Chat pada layar ponsel dan desktop: teks utama gelap, permukaan putih, latar abu-abu muda, dan aksen biru. Profil diri Chat harus terlihat di header sidebar dan dapat dibuka.

## Batas keamanan

View Once membatasi klaim/pengambilan kedua melalui API OHLI; ia tidak dapat mencegah tangkapan layar, rekam layar, kamera eksternal, atau salinan yang dibuat saat media ditampilkan. Jangan gunakan untuk rahasia yang membutuhkan jaminan kerahasiaan absolut. Riwayat/Story/Grup/Channel tersimpan pada satu perangkat Android host (JSON privat), jadi sinkronisasi lintas perangkat hanya berlaku ketika semua perangkat tersambung ke host dan akun yang sama. Backup off-device masih harus dibuat terpisah.

Build APK tetap harus dijalankan lewat GitHub Actions. Smoke test membutuhkan server aktif; tes tampilan dan gesture perlu dilakukan di perangkat nyata.
