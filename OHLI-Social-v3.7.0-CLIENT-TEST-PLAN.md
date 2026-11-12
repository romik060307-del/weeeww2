# OHLI Social Android Client v3.7.0 — Test Plan

## Static checks

- Jalankan `node tools/client-source-verify.mjs`.
- Jalankan `node --check app/src/main/assets/ohli-social.js`.
- Parse semua blok inline JS dalam `index.html`.
- Parse `AndroidManifest.xml` dan `styles.xml`.
- Pastikan file tiga WAV dan ikon ikut terkemas ke APK.

## Device tests (belum dilakukan)

1. **Install/launch:** install APK pada Android 5.0+; cek ikon, splash, ukuran layout, keyboard, dan Back pada beranda, chat, modal, Story, LIVE, dan VC.
2. **Online login:** buka URL Netlify dari APK, login/register, tutup Activity, buka ulang, pastikan sesi lokal pulih.
3. **Offline startup:** login online terlebih dahulu, matikan Wi-Fi/data, force-stop lalu buka aplikasi; halaman lokal harus tampil dengan cache terakhir.
4. **Offline text:** kirim teks ke chat saat offline; pesan harus tampak pending dan tidak mengaku terkirim.
5. **Reconnect:** aktifkan internet; pastikan outbox dikirim dan pesan tidak ganda (backend harus menghormati `clientId`).
6. **Native reply:** terima notifikasi saat Activity masih berjalan di latar belakang, balas dari notifikasi, pastikan pesan muncul pada perangkat penerima.
7. **Native channels:** cek izin notifikasi dan nada berbeda pada kanal pesan, panggilan, aktivitas di Settings Android.
8. **Media permission:** tes kamera depan/belakang, mikrofon, galeri, file audio/video, dan lokasi dengan persetujuan per fitur.
9. **Realtime:** uji panggilan suara, video call, LIVE, undangan, peserta, dan jaringan pada dua perangkat nyata.
10. **Process death:** hentikan proses aplikasi; konfirmasi push belum dianggap selesai sampai FCM/push provider terpasang dan diuji.

## Acceptance

Jangan tandai rilis sebagai siap produksi sampai seluruh tes perangkat, endpoint API online, dan pemulihan data/outbox selesai diuji.
