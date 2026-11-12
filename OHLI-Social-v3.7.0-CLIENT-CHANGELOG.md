# OHLI Social Android Client v3.7.0

## Perubahan utama

- Mengubah MainActivity dari dashboard pengelola server menjadi klien WebView OHLI Social.
- Target online: `https://ohlisocial.netlify.app/`.
- Menanam salinan HTML, CSS, dan JavaScript OHLI Social sebagai aset APK untuk shell offline.
- Menggunakan origin HTTPS Netlify yang sama saat menyajikan aset lokal agar sesi/storage WebView tidak berpindah origin.
- Menambahkan pemulihan sesi dari cache lokal setelah sebelumnya berhasil login online.
- Menambahkan outbox lokal untuk pesan teks biasa dan percobaan kirim ulang saat jaringan pulih.
- Menambahkan `clientId` pada pengiriman teks dan pemeriksaan idempotensi pada implementasi ServerService yang ada di source.
- Menambahkan WebView bridge untuk izin kamera/mikrofon/lokasi, penyimpanan token sesi, notifikasi native, dan pembukaan setelan notifikasi Android.
- Menambahkan tindakan balas langsung pada notifikasi pesan melalui Android RemoteInput.
- Membuat tiga kanal notifikasi dan tiga nada khusus: pesan, panggilan, aktivitas.
- Menambahkan ikon OHLI Social dan splash screen.
- Mengubah perilaku tombol Back agar mengutamakan navigasi/halaman dalam aplikasi.
- Menghapus deklarasi ServerService dan Cloudflare tunnel dari manifest klien.
- Menyiapkan workflow GitHub Actions untuk menghasilkan APK debug.

## Batasan rilis ini

- Outbox offline hanya mengantrekan teks biasa. Media, pesan sekali lihat, LIVE, dan panggilan memerlukan internet.
- Notifikasi memerlukan proses WebView yang masih hidup di latar belakang; push saat proses dihentikan belum diintegrasikan.
- API online Netlify harus sudah meneruskan `/api/*` ke backend aktif. Source ZIP tidak membawa Netlify Functions atau aturan proxy.
- APK belum dibangun melalui Gradle dan belum diuji di HP pada lingkungan ini.
