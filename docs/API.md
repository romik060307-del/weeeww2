> Catatan v2.6.0: perubahan berfokus pada penyempurnaan UI dan pemutaran media mobile. API musik, Story, dan feed yang tersedia tetap digunakan; tidak ada perubahan kontrak API pada versi ini.

# API OHLI Social / RKM Server

Base URL lokal biasanya `http://IP-PERANGKAT:8080`. Endpoint ini diwarisi atau ditambahkan ke server Java Android di `ServerService.java`. Hampir semua endpoint API memerlukan token. Pada API JSON POST, token dikirim sebagai field `token`; pada GET, klien lama mengirimkannya melalui query parameter `token`. Media upload biner memakai header `X-RKM-Token`.

## Endpoint utama yang diwarisi

| Metode | Endpoint | Tujuan |
|---|---|---|
| GET | `/health` | Health check sederhana |
| POST | `/api/register` | Membuat akun nomor + password |
| POST | `/api/login` | Login |
| GET | `/api/me` | Profil akun Chat yang sedang login (nomor akun sendiri) |
| GET | `/api/users` | Direktori nomor khusus Chat; hanya akun yang mengizinkan DM dan tidak diblokir |
| GET | `/api/social/users` | Direktori Social dengan ID publik `oh_…`, bukan nomor telepon |
| GET | `/api/user-exists?phone=...` | Pemeriksaan nomor untuk alur Chat; nama/eksistensi hanya jika kebijakan DM mengizinkan |
| GET | `/api/chat/resolve?target=...` | Resolusi Social ID/username/nomor ke peer Chat setelah otorisasi DM |
| GET/POST | `/api/contacts` | Daftar atau pengelolaan kontak |
| GET/POST | `/api/messages` | Ambil/kirim pesan privat; teks 1× tidak dikirim pada daftar pesan penerima sebelum dibuka |
| POST | `/api/messages/edit` | Edit pesan sesuai aturan backend lama |
| POST | `/api/messages/delete` | Hapus pesan sesuai aturan backend lama |
| POST | `/api/messages/once/open` | Klaim pembukaan teks 1× secara atomik; isi teks hanya dikembalikan pada klaim pertama yang berhasil |
| GET/POST | `/api/activity/history` | Baca/rekam riwayat Social privat per akun pada server |
| POST | `/api/activity/history/clear` | Hapus riwayat aktivitas akun yang sedang login |
| GET/POST | `/api/status/collections` | Baca/koleksi Story teman: `pin`, `unpin`, `archive`, `unarchive`, `remove` |
| POST | `/api/status/delete` | Pemilik menghapus Story sendiri dan catatan view terkait |
| GET | `/api/groups/members?groupId=...` | Daftar anggota grup untuk anggota yang sudah bergabung; respons hanya memakai ID sosial dan username |
| POST | `/api/groups/members` | `groupId`, `action=add`, `phones` atau `action=remove`, `target` ID sosial | Pemilik mengundang kontak tersimpan/koneksi Social atau mengeluarkan anggota |
| POST | `/api/groups/delete` | Pemilik menghapus grup serta pesan grupnya |
| POST | `/api/channels/delete` | Pemilik menghapus Channel serta posting Channel-nya |
| GET | `/api/image?token=...&id=...` | Foto chat; foto 1× hanya dapat diklaim penerima sekali |
| GET | `/api/voice?token=...&id=...` | Pesan suara; suara 1× hanya dapat diklaim penerima sekali |
| GET/POST | `/api/status` | Ambil/buat Story/status lama |
| POST | `/api/media/upload?purpose=message&recipient=...` | Upload lampiran chat biner |
| POST | `/api/media/upload?purpose=status&text=...` | Upload media Story |
| GET | `/api/media?token=...&id=...` | Akses media berizin, dukungan Range; 1× penerima hanya dapat diklaim sekali dan memakai no-store |
| GET/POST | `/api/live`, `/api/live/*` | Sesi/signaling/komentar Live P2P |
| POST | `/api/calls` | Signaling panggilan lama |

## Endpoint sosial yang ditambahkan

### Pertemanan v3.6.4

Satu pasangan akun hanya memiliki satu relasi aktif. Permintaan tersimpan di `ohli-friends.json`; permintaan berulang tidak membuat salinan. Batas pengiriman adalah 30 permintaan per 24 jam per akun (termasuk yang sudah ditolak atau dibatalkan). Relasi mutual-follow/kontak lama tetap dikenali untuk kompatibilitas, tetapi relasi baru memakai status `pending`, `accepted`, `rejected`, `cancelled`, atau `removed`.

| Metode | Endpoint | Body/parameter utama | Hasil |
|---|---|---|---|
| GET | `/api/friends` | — | Daftar teman akun aktif (ID publik, status online sesuai privasi) |
| GET | `/api/friends/requests` | — | Permintaan pertemanan `incoming` dan `sent` |
| POST | `/api/friends` | `target` ID sosial/username/nomor terotorisasi; `action=request\|accept\|reject\|cancel\|remove` | Mutasi pertemanan yang divalidasi server |
| GET | `/api/feed?mode=public\|following\|videos&before=...&limit=20` | Cursor waktu, batas 1–50 | Feed berdasarkan izin, cursor dan `hasMore` |
| POST | `/api/posts` | `text`, `audience` (`public`, `followers`, `private`) | Membuat posting teks |
| POST | `/api/media/upload?purpose=post&text=...&audience=...` | File mentah, `X-RKM-Token` | Membuat posting media tunggal |
| POST | `/api/media/upload?purpose=post&text=...&audience=...&audioId=...` | File video mentah, token header, optional katalog `audioId` | Membuat posting video yang terhubung ke audio katalog |
| POST | `/api/media/upload?purpose=music&title=...&artist=...` | File audio mentah, token header | Validasi MIME/tanda tangan file dan simpan track katalog |
| GET | `/api/music` | Optional `q` | Katalog musik yang dapat diakses akun |
| GET | `/api/music/favorites` | — | Koleksi musik favorit akun sendiri |
| GET | `/api/music/detail?id=...` | ID track | Detail track dan postingan yang memakai track tersebut |
| POST | `/api/music/favorites` | `musicId`, `action=toggle\|save\|remove` | Simpan/hapus favorit musik |
| POST | `/api/music/delete` | `musicId` | Pemilik menghapus track yang belum dipakai postingan |
| POST | `/api/posts/like` | `postId` | Toggle like/unlike |
| POST | `/api/posts/comment` | `postId`, `text`, optional `parentId` | Tambah komentar; batas 500 karakter |
| POST | `/api/posts/comments/edit` | `postId`, `commentId`, `text` | Edit komentar sendiri; penulis komentar saja, 1–500 karakter |
| POST | `/api/posts/comments/delete` | `postId`, `commentId` | Hapus komentar sendiri atau komentar pada postingan milik kreator |
| POST | `/api/posts/comments/settings` | `postId`, `disabled` | Kreator mengaktifkan/nonaktifkan komentar pada postingan sendiri |
| POST | `/api/posts/edit` | `postId`, `text`, `audience` | Edit caption/teks dan audiens milik sendiri |
| POST | `/api/posts/delete` | `postId` | Hapus postingan milik sendiri dan bersihkan media jika tidak direferensikan |
| POST | `/api/posts/archive` | `postId`, `action=archive\|restore` | Arsip/pulihkan postingan sendiri |
| GET | `/api/posts/archive` | — | Daftar arsip privat pemilik |
| GET | `/api/posts/saved` | — | Bookmark privat pemilik |
| POST | `/api/posts/bookmark` | `postId` | Toggle simpan/unsimpan |
| POST | `/api/follow` | `target`, `action=follow\|unfollow` | Follow/unfollow |
| GET | `/api/profile?phone=...` | Nomor profil | Profil/counts/post publik yang boleh dilihat |
| GET | `/api/search?q=...` | Query pencarian | Pengguna dan postingan yang diizinkan; maksimum hasil saat ini 30 per kategori |
| GET | `/api/notifications` | — | Notifikasi akun |
| POST | `/api/notifications/read` | optional `id` | Tandai satu/semua notifikasi dibaca |
| GET/POST | `/api/groups` | GET daftar; POST `name`, `description`, `privacy`, optional `memberPhones` | Grup/komunitas; anggota awal dapat dipilih dari Kontak Chat atau koneksi Social (mengikuti/pengikut) pemilik |
| POST | `/api/groups/join` | `id`, `action=join\|leave` | Keanggotaan dasar |
| GET/POST | `/api/channels` | GET daftar; POST `name`, `description`, `privacy` | Channel dasar |
| POST | `/api/channels/follow` | `id`, `action=follow\|unfollow` | Keanggotaan/follow Channel dasar |
| POST | `/api/status/view` | `statusId` | Catat view Story bila penonton berhak melihatnya |
| GET | `/api/status/views?statusId=...` | ID Story sendiri | Viewer unik dan total replay; ditolak untuk akun selain pemilik |
| POST | `/api/status/like` | `statusId` | Toggle suka Story; saat like baru, server membuat notifikasi `story_like` untuk pemilik Story |
| GET | `/api/followers?phone=...&type=followers\|following` | Profil target | Daftar pengikut/mengikuti sesuai privasi pemilik |
| GET | `/api/follow/requests` | — | Permintaan follow akun sendiri yang masih menunggu |
| POST | `/api/follow/requests` | `user`, `action=approve\|reject` | Terima/tolak permintaan follow privat |
| POST | `/api/settings/privacy` | `account`, `followers`, `following`, `likes`, `saved`, `directMessages`, `activity` | Simpan pengaturan privasi akun sendiri; DM dan status aktif menerima `everyone`, `friends`, atau `private` |
| GET | `/api/profile/views` | — | Tayangan profil akun sendiri, satu catatan per akun per hari |
| GET | `/api/posts/liked?phone=...` | Profil target opsional | Postingan yang disukai target, mengikuti kontrol privasi |
| GET | `/api/posts/reposted?phone=...` | Profil target opsional | Postingan yang di-repost target; akun privat dibatasi untuk pengikut |
| GET | `/api/highlights?phone=...` | Profil target opsional | Daftar Sorotan yang dapat dilihat |
| POST | `/api/highlights` | `statusId`, `name` | Simpan Story aktif milik sendiri sebagai Sorotan |
| POST | `/api/highlights/delete` | `id` | Hapus Sorotan milik sendiri |
| GET/POST | `/api/groups/messages?groupId=...` | GET pesan; POST `groupId`, `text`, atau `kind=poll`, `question`, `options` | Chat grup teks dan polling untuk anggota |
| POST | `/api/groups/vote` | `groupId`, `messageId`, `optionId` | Satu pilihan aktif per anggota; mengganti suara menghapus pilihan lama secara atomik |
| GET/POST | `/api/channels/posts?channelId=...` | GET pengumuman; POST `channelId`, `text` | Pengumuman satu arah; hanya pemilik dapat menerbitkan |
| POST | `/api/channels/react` | `channelId`, `postId`, `emoji` | Reaksi 👍 ❤️ 🔥 👏; akses Channel privat diperiksa server |
| GET | `/api/contact-qr` | Token sesi | PNG QR kontak akun yang sedang login |
| POST | `/api/password` | `currentPassword`, `newPassword` | Ganti kata sandi; keluarkan sesi pada perangkat lain |

## Media, komentar, dan LIVE v3.4.0

Pembaruan v3.4.0 menggunakan endpoint lama berikut; tidak ada migrasi database wajib atau penggantian API dasar:

| Metode | Endpoint | Fungsi |
|---|---|---|
| POST | `/api/media/upload?purpose=post&allowDownload=true atau false` | Unggah postingan video dan simpan izin unduhan pemilik. Foto tidak menampilkan kontrol izin video. |
| GET | `/api/media?id=…&download=1` | Mengambil video untuk proses ekspor unduhan. Server memvalidasi hak baca dan menolak HTTP 403 jika pemilik mematikan download. Klien membuat salinan ber-watermark; server tetap menyimpan file asli. |
| POST | `/api/posts/download-settings` | Pemilik postingan mengganti izin download. |
| POST | `/api/posts/comment` | Membuat komentar atau balasan dengan `postId`, `text`/`mediaId`, dan opsional `parentId`. |
| POST | `/api/posts/comments/like` | Toggle like komentar oleh akun terautentikasi; hitungan dikembalikan dari server. |
| POST | `/api/media/upload?purpose=comment-sticker` | Unggah stiker gambar, maksimal 2 MB; media hanya dapat digunakan dari komentar atau koleksi yang tervalidasi. |
| GET/POST | `/api/stickers/favorites` | Daftar, simpan, atau hapus stiker dari koleksi pribadi setelah server memeriksa akses ke sumber. |
| POST/GET | `/api/live/start`, `/api/live/join`, `/api/live/signal`, `/api/live/comment`, `/api/live/comments`, `/api/live/heartbeat`, `/api/live/like`, `/api/live/stop`, `/api/live/leave` | Studio dan penonton Live melalui signaling WebRTC P2P, komentar polling, jumlah viewer, like dan follow. |

**Catatan:** video hasil download dirender di browser memakai Canvas + MediaRecorder dan tidak dikirim kembali ke server. Ketersediaan MP4/WebM, capture audio, dan izin penyimpanan/unduhan ditentukan browser. LIVE tidak menggunakan SFU; server saat ini hanya mendukung skenario kecil dengan batas enam penonton langsung. HTTPS dan izin kamera/mikrofon diperlukan.

## Tes integrasi dua akun

Buat dua akun uji terlebih dahulu di UI. Jalankan dari Node.js 18+:

```bash
OHLI_BASE_URL=http://127.0.0.1:8080 \
OHLI_PHONE_A=6281234567890 OHLI_PASSWORD_A='password-uji-A' \
OHLI_PHONE_B=6281234567891 OHLI_PASSWORD_B='password-uji-B' \
node tools/smoke-test.mjs
```

Gunakan akun uji, jangan kredensial utama. Skrip login ke dua akun tanpa mencetak token, menguji post publik, privasi post privat, like/comment/bookmark toggle, follow/unfollow, izin edit/hapus, lalu menghapus postingan uji. Komentar dan notifikasi pada postingan uji ikut hilang ketika postingan dihapus; notifikasi follow yang sudah dibuat tidak selalu dibersihkan karena merupakan log aktivitas.

Catatan v3.2.0: API sosial memakai ID publik `oh_…`; nomor pada endpoint Chat privat hanya dikembalikan setelah izin Direct Message lolos. Riwayat aktivitas memakai antrean retry lokal dan idempotensi `clientId` pada server; koleksi Story tersimpan pada JSON server. Pesan 1× menyembunyikan isi dari daftar chat dan mengunci klaim pertama lintas sesi di server. Tes API dua akun kini mencakup privasi direktori Chat/Social, riwayat, Story collections, View Once teks lintas sesi, polling grup, Channel dan reaksi.

Catatan v2.4.0: komentar dapat diedit penulisnya, dihapus penulis atau kreator postingan, dan dinonaktifkan kreator. Katalog audio mendukung upload audio, katalog/favorit/detail, akses media berizin, dan asosiasi audio saat video dipublikasikan. Penggabungan media di browser memerlukan MediaRecorder, Web Audio dan captureStream; codec ekspor mengikuti kemampuan browser dan umumnya WebM/Opus, bukan transcode wajib MP3/AAC. Notifikasi `story_like` dibuat hanya pada aksi suka baru, bukan saat batal suka.

Catatan endpoint v2.2.0: chat grup dan Channel saat ini hanya menerima teks 1–2000 karakter. Scanner QR di antarmuka memakai `BarcodeDetector` bawaan browser dan izin kamera (umumnya perlu HTTPS); jika API/izin kamera tidak tersedia, formulir input nomor manual akan muncul. QR hanya diterbitkan untuk akun sendiri. Story like berupa toggle dan jumlah suka; suka baru masuk ke inbox notifikasi pemilik Story. Endpoint `GET /api/status` mengembalikan `viewedByMe` dan `seenAt` untuk akun yang meminta data; endpoint `POST /api/status/view` mencatat tayangan. Upload `purpose=status` menerima parameter `meta` untuk metadata editor Story (batas 1500 karakter).

## Perubahan privasi v3.2.0

- Respons Social publik memakai `socialId` stabil berawalan `oh_…` sebagai `phone` kompatibilitas, serta menghapus `phoneNumber`, `mobile`, dan `number`. Ini berlaku untuk daftar pengguna, profil, pencarian, followers/following, viewers Story, notifikasi, grup, Channel, Live, postingan, dan katalog musik.
- Nomor masih menjadi kunci internal JSON. Endpoint Chat privat `/api/chat/resolve` hanya mengembalikan nomor setelah sesi valid, penerima mengizinkan Direct Message, dan tidak ada blokir. Endpoint pemeriksaan nomor/tambah kontak dan QR kontak juga merupakan jalur Chat privat.
- `/api/messages` menyembunyikan isi pesan teks 1× pada respons penerima sampai endpoint `/api/messages/once/open` berhasil diklaim. Klaim kedua mendapatkan HTTP 410, termasuk dari token login/perangkat kedua. Untuk video/dokumen, Range dilanjutkan dengan `onceKey` sesi pertama sampai 10 menit; permintaan sesi baru ditolak. Pengirim tetap dapat membaca pesan yang ia kirim.
- Riwayat aktivitas dibatasi per akun dan tidak menampilkan kunci internal owner. Story pin/arsip disimpan di `ohli-story-collections.json` dan hanya dapat diakses pemilik koleksi.

## Catatan keamanan

- ID publik pengguna sudah bukan nomor telepon pada endpoint Social yang telah diserialisasi. Namun server tetap memakai nomor secara internal dan mengembalikan nomor pada endpoint Chat privat, QR/tambah kontak, dan data milik akun sendiri. Audit seluruh API dan penggunaan HTTPS tetap wajib sebelum membuka server ke internet publik.
- Token pada beberapa GET berada di URL; URL dapat masuk ke history/log/referer. Jangan menganggap ini setara cookie `HttpOnly` atau desain token produksi.
- CORS server lama terbuka untuk `*`. Gunakan HTTPS reverse proxy, pembatasan asal, rate limiting dan audit otorisasi sebelum produksi.
- Pencarian belum memiliki indeks SQL. Pesan privat tidak termasuk pencarian sosial publik; endpoint pencarian chat lama tetap milik pemilik sesi/percakapan sesuai API lama.


## v2.1 notes
- `GET /api/trending` returns hashtag topics extracted from readable posts from the last 14 days.
- `GET /api/messages` marks messages delivered when the receiving account retrieves them; `POST /api/messages/read` marks messages read when that conversation is opened.
- `GET /api/blocks` and `POST /api/blocks` list/block/unblock accounts. Block rules apply to profiles, search, feeds, follows, and direct messages.


## Uji integrasi dua akun v3.2.0

Jalankan `node tools/smoke-test.mjs` setelah server aktif dengan dua akun khusus uji. Environment yang diperlukan: `OHLI_BASE_URL`, `OHLI_PHONE_A`, `OHLI_PASSWORD_A`, `OHLI_PHONE_B`, `OHLI_PASSWORD_B`. Skrip membuat konten uji dengan nama unik lalu menghapusnya; satu item riwayat pencarian uji mungkin tetap tercatat pada akun A karena endpoint riwayat hanya menyediakan hapus seluruh riwayat, bukan hapus per peristiwa. Skrip sementara mengubah kebijakan DM akun A ke privat dan mengembalikannya pada akhir tes. Jangan pakai akun utama. Tes lintas sesi memakai dua token login terpisah untuk akun yang sama; pengujian di perangkat Android nyata masih disarankan.

Video/dokumen 1× memakai `onceKey` acak 128-bit untuk mempertahankan satu sesi HTTP Range sampai 10 menit. Kunci tersebut tidak dikirim pada respons daftar pesan. Ini mencegah pembukaan ulang lewat sesi baru, tetapi tidak dapat membatalkan salinan yang telah diterima oleh klien.


## Perubahan UI dan Grup v3.4.0

- Frontend Chat memakai endpoint terautentikasi `GET /api/me` untuk profil diri sendiri. Jangan menggunakan respons ini untuk profil publik atau daftar Social karena responsnya memang berisi nomor akun pemilik sesi.
- `/api/groups` menerima `memberPhones` untuk memilih teman dari kontak tersimpan pemilik saat pembuatan grup. `GET /api/groups/members` hanya untuk anggota, mengembalikan Social ID dan username, sementara `POST /api/groups/members` memerlukan pemilik grup untuk menambah/mengeluarkan anggota.
- Endpoint anggota tidak mengembalikan nomor telepon di daftar anggota. Pada operasi `add`, nomor hanya diterima sebagai data input privat dari pemilik dan server memvalidasi bahwa tiap target merupakan kontak tersimpan atau memiliki hubungan mengikuti/pengikut dengan pemilik.


## Koleksi stiker dan audio pribadi v3.5.0

- `GET /api/stickers` memerlukan token dan mengembalikan `mine` serta `favorites` untuk akun aktif.
- Unggah stiker melalui `POST /api/media/upload?purpose=comment-sticker` dengan header `X-RKM-Token`. Gambar dibatasi 2 MB, MIME dan signature diperiksa, lalu file serta metadata pemilik disimpan di server.
- `POST /api/stickers/delete` hanya dapat menghapus stiker milik sesi aktif. Penghapusan ditolak bila media sudah dipakai komentar agar komentar lama tidak rusak.
- `GET /api/music/mine` mengembalikan audio impor milik akun aktif. `POST /api/media/upload?purpose=music` menerima audio maksimal 30 MB, memeriksa signature file, dan menyimpan judul, artis, serta durasi.
- Audio yang belum dipakai postingan dapat dihapus melalui `POST /api/music/delete`; penghapusan ditolak bila audio telah direferensikan postingan.
- `GET /api/media?id=...&token=...` mengizinkan pemilik membaca stiker/audio sendiri; media komentar tetap tunduk pada hak akses postingan.
- Resolver Chat dari profil memakai runtime bridge agar token sesi terlampir pada `GET /api/chat/resolve?target=...`; autentikasi server tetap wajib.
