#!/usr/bin/env node
/** Static verification for the OHLI Social Android source. No server/account required. */
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
let passed = 0;
function check(condition, message) {
  if (!condition) throw new Error(`FAIL: ${message}`);
  passed += 1;
  console.log(`PASS ${passed}: ${message}`);
}
function checkCssBraces(css) {
  css = css.replace(/\/\*[\s\S]*?\*\//g, '');
  const stack = [];
  let quote = null, escaped = false;
  for (let i = 0; i < css.length; i += 1) {
    const ch = css[i];
    if (quote) {
      if (escaped) escaped = false;
      else if (ch === '\\') escaped = true;
      else if (ch === quote) quote = null;
      continue;
    }
    if (ch === '"' || ch === "'") quote = ch;
    else if (ch === '{') stack.push(i);
    else if (ch === '}') {
      if (!stack.length) return false;
      stack.pop();
    }
  }
  return stack.length === 0 && quote === null;
}

const js = read('app/src/main/assets/ohli-social.js');
new vm.Script(js, {filename: 'ohli-social.js'});
check(true, 'JavaScript OHLI Social dapat diparse');

const html = read('app/src/main/assets/index.html');
const inlineStyles = [...html.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style\s*>/gi)].map((m) => m[1]);
check(inlineStyles.length > 0 && inlineStyles.every(checkCssBraces), `${inlineStyles.length} blok CSS inline HTML seimbang`);
const inlineScripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\s*>/gi)].map((m) => m[1]).filter((s) => s.trim());
for (let i = 0; i < inlineScripts.length; i += 1) new vm.Script(inlineScripts[i], {filename: `index-inline-${i + 1}.js`});
check(inlineScripts.length > 0, `${inlineScripts.length} skrip inline HTML dapat diparse`);
check(checkCssBraces(read('app/src/main/assets/ohli-social.css')), 'Kurung blok CSS Social seimbang');

const java = read('app/src/main/java/com/rk/server/ServerService.java');
for (const [needle, message] of [
  ['/api/activity/history', 'endpoint riwayat server tersedia'],
  ['/api/social/users', 'endpoint penemuan akun Social terpisah dari Chat tersedia'],
  ['private void getChatUsers', 'endpoint daftar Chat memakai serializer nomor privat'],
  ['private void getSocialUsers', 'endpoint daftar Social memakai ID publik'],
  ['boolean allowed=u!=null', 'pencarian nomor menggunakan hasil izin akses'],
  ['&&canDirectMessage(me.optString("phone"),phone)', 'pencarian nomor tidak mengungkap akun yang melarang DM'],
  ['ohli-activity-history.json', 'riwayat memiliki penyimpanan server'],
  ['/api/status/collections', 'endpoint koleksi Story tersedia'],
  ['/api/status/delete', 'penghapusan Story milik sendiri tersedia'],
  ['/api/groups/delete', 'penghapusan grup oleh pemilik tersedia'],
  ['/api/channels/delete', 'penghapusan Channel oleh pemilik tersedia'],
  ['ohli-story-collections.json', 'pin/arsip Story memiliki penyimpanan server'],
  ['/api/messages/once/open', 'endpoint klaim teks sekali lihat tersedia'],
  ['viewOnceSessionKey', 'media 1× memiliki kunci sesi server'],
  ['if(!onceSessionKey.matches("[a-fA-F0-9]{32}"))', 'media 1× menolak permintaan tanpa kunci sesi'],
  ['safe.put("text","")', 'isi teks 1× disamarkan dari daftar chat penerima'],
  ['r.put("phone",id)', 'API Social mengganti nomor dengan ID publik stabil'],
  ['/api/groups/vote', 'endpoint pemungutan suara grup tersedia'],
  ['/api/channels/react', 'endpoint reaksi Channel tersedia'],
  ['canDirectMessage(sender,recipient)', 'aturan DM diperiksa di server saat pengiriman'],
]) {
  if (!message) continue;
  check(java.includes(needle), message);
}
const postSerializer = java.slice(java.indexOf('private JSONObject publicPost'), java.indexOf('private double socialPostRank'));
check(postSerializer.includes('p.put("author",authorUser==null?"":socialId(authorUser))'), 'nomor penulis postingan tidak bocor bahkan kepada pemiliknya');
check(postSerializer.includes('sc.put("phone",cu==null?"":socialId(cu))') && postSerializer.includes('sl.put("phone",lu==null?"":socialId(lu))') && postSerializer.includes('sr.put("phone",ru==null?"":socialId(ru))'), 'nomor pemberi komentar, like, dan repost disamarkan');
check(java.includes('row.put("repostedBy",repostUser==null?"":socialId(repostUser))'), 'penanda repost tidak mengembalikan nomor akun sendiri');
check(java.includes('safeComment.put("phone",socialId(me))') && java.includes('p.put("authorName",socialDisplayName(authorUser))') && java.includes('socialDisplayName(JSONObject u)'), 'respons komentar dan nama penulis Social disanitasi, termasuk akun lama yang nama tampilannya berupa nomor');
check(java.includes('publicStatus.put("phone",socialId(me))') && java.includes('group.put("owner",publicOwner)'), 'respons publik pembuatan Story dan Sorotan tidak membocorkan nomor');
const channelSerializer = java.slice(java.indexOf('private JSONObject publicChannelPost'), java.indexOf('private void getChannelPosts'));
check(channelSerializer.includes('p.put("author",author==null?"":socialId(author))') && channelSerializer.includes('sr.put("phone",ru==null?"":socialId(ru))'), 'nomor penulis dan pemberi reaksi Channel selalu disamarkan');
check(java.includes('safe.put("sender",senderUser==null?"":socialId(senderUser))') && java.includes('safe.put("name",socialDisplayName(senderUser))'), 'respons Grup menampilkan ID sosial dan nama aman');
const statusSerializer = java.slice(java.indexOf('private void getStatuses'), java.indexOf('private void getImage'));
check(statusSerializer.includes('safe.put("phone",statusOwner==null?"":socialId(statusOwner))') && statusSerializer.includes('sl.put("phone",lu==null?"":socialId(lu))'), 'nomor pemilik dan pemberi like Story Social selalu disamarkan');
const socialUi = read('app/src/main/assets/ohli-social.js');
const css = read('app/src/main/assets/ohli-social.css');
check(socialUi.includes("rt.request('/api/social/users')") && !socialUi.includes("rt.request('/api/users')"), 'UI Social tidak lagi meminta daftar nomor telepon Chat');
check(socialUi.includes('(s.isMine||s.phone===me.phone)') && socialUi.includes("var freshStories=await rt.request('/api/status?surface=social')"), 'UI Story mengenali Story sendiri tanpa mengandalkan nomor telepon');
check(socialUi.includes("data-action=\"delete-own-story\"") && socialUi.includes("async function deleteOwnStory(id)") && socialUi.includes("rt.request('/api/status/delete',{method:'POST',body:{statusId:id}})"), 'pemilik dapat menghapus Story aktif dari pemutar Story tanpa menghapus salinan Riwayat dan Sorotan');
check(socialUi.includes('isMine:items.some(function(s){return s.isMine===true;})') && socialUi.includes('isMine=!!p.isMine') && socialUi.includes('canManage=!!c.isMine||!!p.isMine'), 'UI feed, Story, dan komentar memakai penanda isMine saat nomor disamarkan');
const onceOpen = java.slice(java.indexOf('private void openViewOnceMessage'), java.indexOf('private void markMessagesRead'));
check(onceOpen.includes('viewOnceOpenedAt') && onceOpen.includes('410'), 'klaim kedua teks 1× ditolak server');
const media = java.slice(java.indexOf('private void getMedia'), java.indexOf('private void getImage'));
check(media.includes('viewOnceSessionKey') && media.includes('10L*60L*1000L') && media.includes('Cache-Control: no-store'), 'media 1× mendukung Range untuk sesi yang sama dan no-store');
const poll = java.slice(java.indexOf('private void voteGroupPoll'), java.indexOf('private JSONObject publicChannelPost'));
check(poll.indexOf('if(!validOption)') < poll.indexOf('option.put("votes",next)'), 'poll memvalidasi opsi sebelum mengubah suara');
const channelReact = java.slice(java.indexOf('private void toggleChannelReaction'), java.indexOf('private void createChannelPost'));
check(channelReact.includes('!\"public\".equals(channel.optString(\"privacy\"') && channelReact.includes('followers'), 'reaksi Channel memeriksa akses Channel privat');

const finalChatCss = html.slice(html.indexOf('id=\"ohli-v330-unified-instagram-ui\"'));
check(finalChatCss.includes('SF Pro Text') && finalChatCss.includes('#fafafa') && finalChatCss.includes('#0095f6'), 'tema terang minimalis dan font sistem Apple berlaku sebagai override paling akhir');
check(finalChatCss.includes('body .auth-screen{background:#fafafa!important}') && finalChatCss.includes('body .call-overlay{background:#fafafa!important'), 'halaman login dan panggilan mengikuti tema netral yang sama');
check(finalChatCss.includes('#app .my-profile{display:flex!important') && finalChatCss.includes('#ohliSocialShell .ohli-sidebar{background:#fff!important'), 'profil diri Chat dan navigasi Social tidak disembunyikan tema lama');
check(java.includes('r.put("name",socialDisplayName(u))') && socialUi.includes("topName=String(me.name||'').trim()"), 'nama akun lama yang sama dengan nomor tidak tampil sebagai identitas Social');
check(socialUi.includes('async function loadGroupInviteOptions') && socialUi.includes("rt.request('/api/contacts')") && socialUi.includes("rt.request('/api/social/users')"), 'pemilih grup menggabungkan kontak Chat dan koneksi Social');
check(socialUi.includes("digits===String(c.phone||'').replace(/\\D/g,'')") && socialUi.includes("name=c.username?'@'+c.username:'Pengguna OHLI'"), 'pemilih teman Social tidak menampilkan nomor ketika nama akun lama sama dengan nomor');
check(java.includes('row.put(\"followsYou\",isFollowing(target,viewer))') && java.includes('canInviteToGroup(owner,target)'), 'backend mengizinkan undangan kontak atau koneksi Social yang berelasi');
check(socialUi.includes('async function showCommunityForm') && socialUi.includes('name=\"memberPhones\"'), 'formulir buat grup mengirim teman pilihan sebagai ID sosial');
check(socialUi.includes('async function showGroupMemberPicker') && socialUi.includes('loadGroupInviteOptions()') && socialUi.includes("'/api/groups/members'"), 'pemilik grup dapat menambah kontak atau koneksi Social setelah grup dibuat');
check(socialUi.includes('data-action=\"group-members\"') && socialUi.includes('data-action=\"group-add-members\"'), 'ruang grup menampilkan aksi anggota dan tambah teman');
check(html.includes('id=\"myProfileButton\"') && html.includes('async function openMyChatProfile') && html.includes('/api/me'), 'profil diri Chat memuat nomor pribadi dari endpoint akun terautentikasi');
check(html.includes('id=\"editUsername\"') && html.includes('username:username'), 'username Social bisa diedit dari profil akun Chat');
check(!socialUi.includes('toggle-liquid-glass": [') && !socialUi.includes('Warna aksen utama<input name=\"accent\" type=\"color\"'), 'kontrol warna yang tidak selaras dihapus dari pengaturan Social');
const gradle = read('app/build.gradle');
check(gradle.includes("versionCode 56") && gradle.includes('versionName "3.6.27"'), 'identitas build Android sesuai v3.6.27');
check(html.includes('.nav-tab[data-section=\"status\"]{display:none!important}') && html.includes('grid-template-columns:repeat(4,minmax(0,1fr))'), 'navigasi mobile menyederhanakan tab dengan menghilangkan pintasan Story/Status dan memberi ruang sentuh lebih lega');
check(html.includes('lastBackgroundMessagePollAt>(visible?18000:35000)') && html.includes('document.hidden?12000') && html.includes('jobs.push(refreshMessages(false))'), 'polling Chat dan fitur latar dijarangkan untuk mengurangi beban request');
const smoke = read('tools/smoke-test.mjs');
for (const [needle, message] of [
  ["'/api/social/users'", 'tes integrasi memeriksa direktori Social tanpa nomor'],
  ["'/api/activity/history'", 'tes integrasi memeriksa riwayat lintas sesi'],
  ["'/api/status/collections'", 'tes integrasi memeriksa pin dan arsip Story'],
  ["'/api/messages/once/open'", 'tes integrasi memeriksa klaim View Once dari sesi kedua'],
  ["'/api/groups/members'", 'tes integrasi memeriksa tambah dan keluarkan anggota grup'],
  ["'/api/me'", 'tes integrasi memeriksa data profil pribadi Chat'],
  ["'/api/groups/vote'", 'tes integrasi memeriksa polling grup'],
  ["'/api/channels/react'", 'tes integrasi memeriksa reaksi Channel'],
  ["'/api/groups/delete'", 'tes integrasi membersihkan grup uji'],
  ["'/api/channels/delete'", 'tes integrasi membersihkan Channel uji'],
]) check(smoke.includes(needle), message);

const tunnelJava = read('app/src/main/java/com/rk/server/CloudflareTunnelService.java');
const mainActivity = read('app/src/main/java/com/rk/server/MainActivity.java');
const manifest = read('app/src/main/AndroidManifest.xml');
check(tunnelJava.includes('nativeLibraryDir') && tunnelJava.includes('libcloudflared.so') && tunnelJava.includes('binary.canExecute()'), 'engine tunnel diambil dari binary Android yang tertanam di APK, bukan executable Linux yang diunduh saat runtime');
check(tunnelJava.includes('ACTION_START_QUICK') && tunnelJava.includes('127.0.0.1:" + ServerService.PORT') && tunnelJava.includes('trycloudflare\\\\.com'), 'Quick Tunnel diarahkan ke server lokal OHLI dan URL publik dibaca otomatis');
check(tunnelJava.includes('ACTION_STOP') && tunnelJava.includes('ProcessBuilder') && tunnelJava.includes('START_STICKY'), 'tunnel memiliki proses Start/Stop dan foreground service yang dapat dipulihkan');
check((manifest.match(/android:name="\.CloudflareTunnelService"/g)||[]).length===1 && manifest.includes('android:extractNativeLibs="true"'), 'service tunnel hanya terdaftar sekali dan library native diekstrak agar dapat dijalankan');
check(mainActivity.includes('☁ MULAI QUICK TUNNEL') && mainActivity.includes('SALIN LINK PUBLIK') && mainActivity.includes('ACTION_START_QUICK') && mainActivity.includes('engine cloudflared Android yang tertanam'), 'panel Android memulai engine tertanam dan menyediakan tombol salin URL publik');
check(html.includes('async function acquireAlternateCamera') && html.includes('deviceId:{exact:cameras[i].deviceId}'), 'pergantian kamera memilih device kamera lain, tidak hanya mengubah constraint ideal');
check(html.includes('var rkmCameraLayoutV3622=document.createElement') && html.includes('aspect-ratio:3/4!important') && html.includes('border-radius:16px!important'), 'preview panggilan berbentuk jendela portrait membulat, bukan lingkaran');
check(html.includes('.rkm-live-participant-grid[data-count="1"] .rkm-live-tile{position:absolute!important;inset:0!important') && html.includes('.rkm-live-self-tile .rkm-live-tile-video{position:absolute!important;inset:0!important'), 'LIVE satu peserta memenuhi stage dan preview lokal tidak lagi dipaksa lingkaran');
check(html.includes('var previous=ctx.stream,fresh=null') && html.includes('if(previous){ctx.stream=previous;video.srcObject=previous'), 'startCamera mempertahankan kamera lama saat kamera baru gagal dibuka');

const workflow = read('.github/workflows/build-apk.yml');
check(workflow.includes('OHLI-Social-Source-v3.6.27.zip') && workflow.includes('cd \"$SOURCE_DIR\"') && workflow.includes('gradle :app:assembleDebug --stacktrace'), 'workflow memilih source v3.6.27 dan menjalankan Gradle dari root Android yang terdeteksi');
check(workflow.includes('repository: cloudflare/cloudflared') && workflow.includes('ref: 2026.10.7') && workflow.includes('go-version-file: .cloudflared-src/go.mod'), 'workflow mengambil source cloudflared resmi pada tag tetap dan Go sesuai go.mod');
check(workflow.includes('GOOS=android') && workflow.includes('CGO_ENABLED=1') && workflow.includes('aarch64-linux-android21-clang') && workflow.includes('armv7a-linux-androideabi19-clang'), 'workflow membangun engine cloudflared native untuk ABI Android 64-bit dan ARMv7');
check(workflow.includes('jniLibs/arm64-v8a/libcloudflared.so') && workflow.includes('Verify APK contains Android tunnel engines') && workflow.includes('CLOUDFLARED-LICENSE.txt') && workflow.includes('llvm-readelf'), 'workflow memeriksa ABI ELF dan menyertakan lisensi upstream di APK');
check(tunnelJava.includes('GODEBUG') && tunnelJava.includes('netdns=cgo') && tunnelJava.includes('while (!stopRequested)') && tunnelJava.includes('Math.min(60000L'), 'tunnel memakai DNS resolver Android dan retry berkala tanpa batas 12 percobaan');
check(read('app/src/main/java/com/rk/server/BootReceiver.java').includes('PREF_ENABLED') && read('app/src/main/java/com/rk/server/BootReceiver.java').includes('ACTION_START_QUICK'), 'BootReceiver memulihkan Quick Tunnel jika sebelumnya diaktifkan pengguna');
check(!tunnelJava.includes('cloudflared-linux-arm64') && !tunnelJava.includes('releases/download/'), 'APK tidak lagi mencoba mengunduh executable Linux cloudflared saat runtime');
check(read('settings.gradle').includes("include ':app'") && read('settings.gradle').includes('google()') && read('build.gradle').includes("com.android.application"), 'settings.gradle dan build.gradle root melengkapi proyek Android multi-project');
check(read('gradle.properties').includes('android.useAndroidX=true') && read('app/build.gradle').includes("versionCode 56"), 'properti Gradle dan versionCode 56 tersedia');
check(html.includes("window.ohliRuntime.request('/api/chat/resolve?target='"), 'resolver Chat dari profil menyertakan token melalui runtime bridge autentikasi');
check(socialUi.includes('data-social-id=') && socialUi.includes('var socialId=button.dataset.socialId||button.dataset.phone'), 'tombol Chat menggunakan Social ID dari profil yang sedang aktif');
check(socialUi.includes('chatOpenRequest') && socialUi.includes('requestId!==chatOpenRequest'), 'request resolve lama tidak dapat menimpa target Chat yang lebih baru');
check(html.includes('openChatWith:async function(phone)') && html.includes('await refreshMessages(false)'), 'Chat memvalidasi target, membuka peer spesifik, lalu menyegarkan riwayat server');
check(socialUi.includes('function readCustomThemeColors()') && socialUi.includes('ohli:social-custom-colors'), 'preferensi warna ikon dan tulisan disimpan lokal');
check(socialUi.includes('data-action=\"theme-custom-save\"') && socialUi.includes('data-action=\"theme-custom-reset\"'), 'UI simpan dan reset warna tema tersedia');
check(html.includes('ohli-v363-focused-fixes') && html.includes('.ohli-nav-link[data-page=\"create\"] svg') && html.includes('.ohli-reel-notifications svg'), 'ikon plus dan lonceng video pendek diatur putih');
check(html.includes('.rkm-live-modal.is-host:not(.is-live) .rkm-live-menu>summary') && html.includes('.rkm-live-menu[open]>.rkm-live-sheet-panel'), 'menu tiga titik LIVE tersedia sejak tahap persiapan dan sheet dikendalikan buka/tutup');
check(html.includes('id=\"rkmLiveTitle\"') && html.includes('id=\"rkmLiveStartBtn\"') && html.includes('id=\"rkmLiveEnd\"'), 'pengaturan judul, Mulai LIVE, dan Akhiri LIVE tetap tersedia');

check(socialUi.includes('var pendingStickerImport = null') && socialUi.includes('async function previewImportedSticker') && socialUi.includes('async function saveImportedSticker') && socialUi.includes('rt.uploadCommentSticker(pending.file)'), 'impor stiker memiliki pratinjau dan upload server setelah konfirmasi');
check(socialUi.includes("rt.request('/api/stickers')") && socialUi.includes("rt.request('/api/stickers/delete'"), 'koleksi Stiker Saya dibaca dari server dan penghapusan memakai pemeriksaan pemilik');
check(java.includes('private void getStickerLibrary') && java.includes('private void deleteOwnSticker') && java.includes('GET".equals(method) && "/api/stickers".equals(path)'), 'backend menyediakan daftar dan hapus stiker milik akun');
check(java.includes('private void getMyMusic') && java.includes('durationSeconds') && java.includes('Audio maksimal 30 MB per file'), 'Audio Saya tersimpan server dengan durasi dan batas ukuran upload');
check(java.includes('r.put("version", "3.6.27")'), 'endpoint health menampilkan versi source v3.6.27');
check(smoke.includes('uploadBinary') && smoke.includes('/api/stickers/delete') && smoke.includes('/api/music/delete') && smoke.includes('makeSilentWav'), 'smoke test dua akun memiliki skenario upload/persistensi/hapus stiker dan audio biner');
check(socialUi.includes("rt.request('/api/music/mine')") && socialUi.includes('Audio Saya') && socialUi.includes('readAudioDuration(musicFile)'), 'Audio Saya dapat dimuat ulang dan menampilkan durasi file impor');
check(socialUi.includes("shell.classList.toggle('ohli-reels-nav-transparent'") && socialUi.includes("shell.classList.toggle('ohli-reels-nav-black'") && socialUi.includes("localStorage.setItem('ohli:social-reel-nav-bg'"), 'latar navigasi video Hitam/Transparan tersimpan tanpa menyembunyikan ikon');
check(socialUi.includes("'orange'") && socialUi.includes('data-theme="orange"') && html.includes("document.body.classList.toggle('v5-orange',orange)"), 'Tema Orange menjadi opsi tersimpan di Chat dan Social');
check(css.includes('.ohli-sticker-preview') && css.includes('.ohli-reels-nav-transparent') && css.includes('.ohli-reels-nav-black') && css.includes('.ohli-orange'), 'CSS tambahan hanya menargetkan pratinjau stiker, latar nav video, dan aksen Orange');
check(socialUi.includes('async function downloadVideoWithWatermark') && socialUi.includes("'OHLI'") && socialUi.includes('allowDownload===false'), 'unduhan membuat salinan video ber-watermark OHLI dan memeriksa izin pemilik');
check(java.includes('"1".equals(queryParam(query,"download")) && !post.optBoolean("allowDownload",true)'), 'server tetap menolak unduhan bila pemilik menonaktifkannya');
check(socialUi.includes('profileVideoLayout') && socialUi.includes('data-action="profile-media-mode"') && socialUi.includes('is-transparent') && socialUi.includes('is-limited'), 'profil menyediakan mode video Transparan dan Terbatas');
check(socialUi.includes('data-action="reply-comment"') && socialUi.includes('data-action="like-comment"') && socialUi.includes("'/api/posts/comments/like'"), 'komentar mendukung balasan bertingkat dan like server-side');
check(socialUi.includes("'/api/stickers/favorites'") && socialUi.includes('data-comment-sticker-file') && java.includes('private void toggleStickerFavorite'), 'stiker bawaan, impor, dan koleksi favorit terhubung ke server');
check(html.includes('function liveModalHtml') && html.includes('function openRkmLivePreview') && html.includes('rkmLiveQuality') && html.includes('rkmLiveFlip') && html.includes('/api/live/comment'), 'studio LIVE mengatur kamera, mikrofon, kualitas, dan komentar realtime');
check(html.includes('new RTCPeerConnection') && html.includes('/api/live/signal') && html.includes('maksimal 4 orang di panggung') && java.includes('batas uji 6 penonton langsung'), 'LIVE memakai signaling WebRTC P2P dan memberi batas kapasitas yang jujur');
check(html.includes('shouldFollow=arr.length&&(wasAtBottom||peerChanged||(latest&&same(latest.sender,me.phone)))') && html.includes('mainBody.scrollTop=mainBody.scrollHeight'), 'Chat otomatis mengikuti pesan terbaru yang dikirim tanpa melompat saat membaca pesan lama');
check(socialUi.includes('function onCommentHoldStart') && socialUi.includes('function replyToComment') && socialUi.includes('setTimeout(function(){commentHoldConsumed=true'), 'menahan komentar membuka alur Balas');
check(socialUi.includes('function onStoryHoldStart') && socialUi.includes('function pauseStoryPlayback') && socialUi.includes('function resumeStoryPlayback'), 'Story dijeda saat ditahan dan dilanjutkan dari posisi saat dilepas');
check(html.includes('function mediaTarget') && html.includes("e.target.closest('#ohliSocialShell .ohli-comment')") && html.includes('selectstart'), 'menu Copy bawaan diblokir hanya pada video dan teks komentar Social');
check(html.includes('body.v5-dark') && html.includes('#ohliSocialShell.ohli-dark') && html.includes('setTheme:function(next)'), 'Mode Malam tersimpan dan mencakup Chat serta Social');
check(html.includes('ohli-comment-actions') && html.includes('visualViewport') && socialUi.includes('data-comment-id="\'+esc(c.id)'), 'panel komentar punya aksi berjarak, balasan tahan, dan penyesuaian keyboard HP');

check(html.includes('var once=sendOnceMode,reply=window.__reply') && html.includes('if(window.clearReply)window.clearReply()'), 'pesan yang digeser benar-benar mengirim kutipan balasan dan membersihkan bar setelah berhasil');
check(html.includes('function messageForRow(row)') && html.includes("body.addEventListener('touchcancel',finishGesture"), 'gesture swipe memetakan bubble yang benar dan membersihkan sentuhan yang dibatalkan');


check(socialUi.includes('ohli-reel-download') === false && socialUi.includes('data-action=\"share-download\"') && socialUi.includes("else if (kind === 'download')") && socialUi.includes("else if (action === 'share-download')"), 'tombol download video pendek dipindahkan ke dalam menu Bagikan dengan pemeriksaan izin');
check(socialUi.includes('ohli-ed-tools-scroll') && socialUi.includes('ohli-ed-tools'), 'toolbar editor unggahan memiliki wadah geser horizontal tanpa mengubah aksi alat');
check(css.includes('.ohli-ed-tools-scroll') && css.includes('touch-action:pan-x') && css.includes('overscroll-behavior-x:contain'), 'toolbar editor mendukung swipe HP dan tidak mengambil alih scroll vertikal');
check(css.includes('#crGo') && css.includes('grid-template-columns:minmax(0,1fr) minmax(0,1fr)'), 'baris pengaturan upload responsif dan tombol Bagikan tetap terlihat di layar HP');
check(html.includes('rkm-live-modal .rkm-live-comment') && html.includes('background:#171a20!important') && html.includes('backdrop-filter:none!important'), 'panel LIVE menggunakan latar solid tanpa efek transparan');
check(html.includes('.ohli-full-reels[data-page=\"videos\"] .ohli-sidebar{background:transparent!important') && html.includes('text-shadow:0 1px 5px #000b'), 'navigasi bawah video pendek hanya menampilkan ikon tanpa panel latar');
check(read('app/build.gradle').includes('versionName "3.6.27"') && read('app/build.gradle').includes('versionCode 56'), 'versi Android dinaikkan ke v3.6.27');
check(java.includes('collectionId') && java.includes('group.put("items",items)') && java.includes('requestedCollection.length()>0'), 'backend Sorotan mendukung beberapa Story dalam satu koleksi dan tetap membaca data Sorotan lama');
check(java.includes('Map<String,JSONObject> groupIndex=new HashMap<>()') && java.indexOf('Map<String,JSONObject> groupIndex=new HashMap<>()') < java.indexOf('JSONObject group=groupIndex.get(collectionId)'), 'indeks koleksi Sorotan dideklarasikan sebelum dipakai untuk mengelompokkan Story');
check(socialUi.includes('highlightId=') || socialUi.includes('payload.highlightId=') && socialUi.includes('function openHighlight(h)') && socialUi.includes('activeHighlight=h'), 'UI dapat memilih Sorotan lama atau membuat baru dan memutar isinya dengan Story Viewer layar penuh');
check(socialUi.includes('profileAux=await Promise.all') && css.includes('.ohli-story-screen'), 'permintaan pendukung profil berjalan paralel dan Sorotan memakai pemutar Story responsif');


check(html.includes('content="#000000"') && html.includes('id="ohli-v36-fullbleed-feed-live"') && html.includes('height:100dvh') && html.includes('grid-template-areas:"stage"'), 'feed video memakai viewport penuh, tema browser hitam, dan fallback viewport dinamis');
check(html.includes('id="rkmLiveMenu"') && html.includes('id="rkmLiveCameraError"') && html.includes('id="rkmLiveConfirmEnd"') && html.includes('id="rkmLiveConfirmCancel"'), 'LIVE memiliki menu pengaturan, overlay error kamera, dan dialog akhiri siaran');
check(html.includes('visualViewport') && html.includes('rkm-live-sheet-panel') && html.includes('mask-image:linear-gradient(to bottom'), 'LIVE menyesuaikan keyboard iOS, bottom sheet, dan fade komentar');


check(socialUi.includes('var socialId=button.dataset.socialId||button.dataset.phone') && socialUi.includes('profileData.user.socialId'), 'Chat dari profil memakai ID pada tombol profil yang benar, bukan target profil sebelumnya');
check(html.includes('html.ohli-custom-icons #app svg') && html.includes('html.ohli-custom-text #app') && socialUi.includes("root.classList.toggle('ohli-custom-icons'") && socialUi.includes("root.classList.toggle('ohli-custom-text'"), 'warna ikon dan tulisan dapat diterapkan ke Social serta Chat');
check(html.includes('rkmLiveMenu') && html.includes('rkmLiveStudioSettings') && html.includes('rkmLiveStartBtn') && html.includes('rkmLiveQuality'), 'tombol tiga titik LIVE membuka pengaturan lengkap termasuk mulai siaran');


check(java.includes('private void getSocialFriends') && java.includes('private void getSocialFriendRequests') && java.includes('private void updateSocialFriendship'), 'backend menyediakan daftar teman dan siklus permintaan pertemanan');
check(java.includes('socialFriends=loadArray(socialFriendsFile,20000)') && java.includes('ohli-friends.json'), 'relasi pertemanan disimpan terpisah di server Android');
check(java.includes('friend_request') && java.includes('friend_accepted') && java.includes('Too Many Requests'), 'permintaan teman memicu notifikasi dan pembatasan spam server-side');
check(java.includes('friendRequestSent') && java.includes('friendRequestReceived') && socialUi.includes("rt.request('/api/friends')") && socialUi.includes("rt.request('/api/friends/requests')"), 'profil dan halaman Teman terhubung ke API permintaan pertemanan');
check(socialUi.includes('data-action="friend-request"') && socialUi.includes('data-action="friend-accept"') && socialUi.includes('data-action="friend-reject"') && socialUi.includes('data-action="friend-cancel"') && socialUi.includes('data-action="friend-remove"'), 'UI menyediakan tambah, terima, tolak, batalkan, dan hapus teman');
check(css.includes('.ohli-friends-toolbar') && css.includes('.ohli-friend-actions'), 'halaman Teman memiliki layout responsif tanpa mengganti tema lama');
check(java.includes('r.put("version", "3.6.27")'), 'health endpoint melaporkan v3.6.27');
check(smoke.includes("/api/friends/requests") && smoke.includes("friendRequest.requested") && smoke.includes("action: 'reject'") && smoke.includes("action: 'cancel'"), 'smoke test dua akun mencakup siklus permintaan teman');
check(smoke.includes('/api/highlights') && smoke.includes('groupedHighlight.items?.length === 2') && smoke.includes('menghapus seluruh koleksi'), 'smoke test dua akun memverifikasi penggabungan Story dan penghapusan seluruh Sorotan');

check(html.includes('async function downloadChatMedia') && html.includes('id="rkmDownload"') && html.includes('id="rkmHide"') && html.includes('id="rkmDelete"'), 'aksi pesan mendukung unduh media, hapus dari chat sendiri, dan hapus untuk semua');
check(html.includes('function pollRkmLiveRoom') && !html.includes('pollLiveRoom(ctx)') && html.includes('activateLiveParticipant') && html.includes('leaveLiveStage') && html.includes('rkm-live-local-participant'), 'alur penonton, aktivasi peserta, tile kamera sendiri, dan keluar panggung terhubung');
check(html.includes("apiPath('/api/friends',{token:token})") && html.includes("'/api/live/invite'") && html.includes("'/api/live/join-request'") && html.includes("'/api/live/join-request/respond'"), 'undangan memakai daftar teman OHLI dan permintaan tampil memakai endpoint server');
check(!java.includes('isBlockedEither(phone,target)||!canReadLive(target,live)') && java.includes('hostPart.put("lastSeen",now)') && java.includes('r.put("viewerCount",liveViewerCount'), 'undangan privat diizinkan dan heartbeat menjaga host tetap menjadi peserta LIVE');
check(html.includes('lastBackgroundMessagePollAt>(visible?18000:35000)') && html.includes('document.hidden?12000') && java.includes('adaptiveEngine.createExecutor(memoryClass)'), 'polling latar dijarangkan dan worker server tetap mendukung request bersamaan');
check(html.includes('rkm-live-tile-mic') && html.includes('mic.hidden=person.micOn!==false') && java.includes('Tunggu \"+Math.max(1,(remaining+999)/1000)+\" detik sebelum meminta bergabung lagi'), 'indikator mikrofon tiap tile LIVE dan jeda permintaan ulang tersedia');

check(html.includes('hostAvatar.hidden=ctx.cameraOn!==false') && html.includes('ownAvatar.hidden=ctx.cameraOn'), 'tile Host menyembunyikan avatar saat kamera aktif dan menampilkannya saat kamera dimatikan');
check(html.includes('function scrollChatToLatest(peer)') && html.includes('scrollChatToLatest(phone)') && html.includes('scrollChatToLatest(targetPeer)'), 'Chat memaksa posisi pesan terbaru saat membuka percakapan dan sesudah pesan berhasil dikirim');

// v3.6.9 regression checks
check(html.includes('async function promoteLiveParticipantConnections(ctx)') && html.includes("tr.direction='sendrecv'") && html.includes('await promoteLiveParticipantConnections(ctx)'), 'peserta yang disetujui menambahkan track kamera/mikrofon dan renegosiasi dua arah ke Host');
check(html.includes('ctx.needsBidirectional=needsRetry') && html.includes('needsBidirectional'), 'renegosiasi WebRTC dicoba ulang bila SDP awal belum stabil');
check(html.includes('ctx.remoteStreams[phone]') && html.includes('ctx.signalPollBusy') && html.includes('ctx.commentsPollBusy'), 'track remote streamless digabung dan polling LIVE tidak saling menumpuk');
check(css.includes('#app .nav-tabs .nav-tab[data-section="status"]{display:flex!important}') && css.includes('#ohliSocialShell .ohli-sidebar .ohli-nav-link[data-page="story"]{display:none!important}'), 'Story/Pembaruan Chat dikembalikan sementara pintasan Story OHLI Social disembunyikan dengan selector terpisah');
check(read('app/build.gradle').includes('versionName "3.6.27"') && read('app/build.gradle').includes('versionCode 56') && java.includes('r.put("version", "3.6.27")'), 'versi Android dan health endpoint diperbarui ke v3.6.27');


// v3.6.9 authentication and transport hardening
check(java.includes('private void requestAuthCode') && java.includes('private void verifyAuthCode') && java.includes('/api/auth/request') && java.includes('/api/auth/verify'), 'pendaftaran dan login baru melewati alur permintaan dan verifikasi OTP server-side');
check(java.includes('HmacSHA256') && java.includes('ohli-auth-secret.bin') && java.includes('otpHash') && java.includes('challenge.remove("otpHash")'), 'OTP disimpan sebagai HMAC dengan kunci privat dan hash dihapus saat kode dipakai');
check(java.includes('AUTH_OTP_TTL_MS = 5L * 60L * 1000L') && java.includes('attempts >= 5') && java.includes('AUTH_REQUEST_COOLDOWN_MS'), 'OTP kedaluwarsa 5 menit, dibatasi 5 percobaan, dan memiliki cooldown permintaan');
check(java.includes('times.size() >= 20') && java.includes('phoneCount >= 5') && java.includes('notifyAuthOtp(phone, purpose, code'), 'permintaan OTP diberi rate-limit IP/nomor dan diteruskan sebagai notifikasi Android admin');
check(java.includes('setVisibility(Notification.VISIBILITY_PRIVATE)') && java.includes('OTP: " + code') && java.includes('setLockscreenVisibility(Notification.VISIBILITY_PRIVATE)'), 'notifikasi OTP menyembunyikan isi di lock screen dan menampilkan kode pada perangkat admin yang terbuka');
check(java.includes('410, "Gone"') && java.includes('"state", "consumed"') && java.includes('"state", "locked"'), 'kode OTP kedaluwarsa, sekali pakai, dan terkunci setelah percobaan berlebih');
check(java.includes('"/api/register".equals(path) || "/api/login".equals(path)') && java.includes('410, "Gone"'), 'endpoint pendaftaran/login lama ditolak agar tidak melewati OTP');
check(html.includes('id="authOtpField"') && html.includes("api('/api/auth/request'") && html.includes("api('/api/auth/verify'"), 'layar login dan daftar meminta serta memverifikasi OTP enam digit');
check(html.includes('function stripTokenQuery') && html.includes("config.headers['X-RKM-Token']=token") && java.includes('headerToken.length() > 0 && queryParam(query, "token").length() == 0'), 'token request API dikirim melalui header dan tidak lagi ditempel ke query fetch normal');
check(java.includes('X-Content-Type-Options: nosniff') && java.includes('X-Frame-Options: DENY') && java.includes('Referrer-Policy: no-referrer'), 'respons server menambahkan header keamanan browser');
check(java.includes('sendAssetResponse(output') && java.includes('readAssetGzipCached') && html.includes('ohli-social.js?v=3.6.27') && html.includes('ohli-social.css?v=3.6.27'), 'CSS dan JavaScript dicache dengan cache-busting versi agar pemuatan ulang lebih ringan');
check(java.includes('\"cf-connecting-ip\".equals(headerName)') && java.includes('client.getInetAddress().isLoopbackAddress()'), 'IP asli Cloudflare hanya dipercaya dari reverse proxy lokal yang tepercaya');
check(java.includes('authIpRequestTimes.size() > 2048') && java.includes('authPhoneLastRequest.size() > 5000'), 'tabel pembatas permintaan OTP dibatasi agar tidak tumbuh tanpa batas di RAM');

check(html.includes('ohli-social-v3611-mobile-reels-fix') && html.includes('.ohli-nav-link[data-page="story"]{display:none!important}') && html.includes('.ohli-nav-link[data-page="chat"]{display:flex!important;grid-column:6!important}'), 'navigasi mobile menghilangkan ikon Story saja dan mengembalikan Chat sebagai item keenam');
check(html.includes('scroll-snap-type:y mandatory') && html.includes('.ohli-reel-info') && html.includes('.ohli-reel-side'), 'Shorts mobile memakai snap vertikal dengan panel info kiri dan aksi vertikal kanan');
check(socialUi.includes("function wantMuted(v) { return !soundOn || !audioUnlocked || v.dataset.mute === '1'; }"), 'video pendek menunggu interaksi pengguna sebelum autoplay bersuara sesuai kebijakan browser');
check(read('app/src/main/assets/ohli-social.css').includes('.ohli-reel-shade{display:none!important}') && read('app/src/main/assets/ohli-social.css').includes('object-position:center center!important'), 'overlay gradasi reel yang berpotensi menjadi bidang gelap dihapus dan posisi video mobile distabilkan');
check(socialUi.includes('data-video-src=') && socialUi.includes('preload=\"none\"') && socialUi.includes('if (!v.getAttribute(\'src\') && v.dataset.videoSrc)'), 'sumber video pendek dimuat on-demand hanya saat video mulai aktif');
check(socialUi.includes('&limit=8') && socialUi.includes('loadMoreReels') && socialUi.includes('reelsEl.addEventListener(\'scroll\''), 'feed video pendek dimuat bertahap delapan item dan mengambil batch berikutnya saat scroll');
check(read('app/src/main/assets/ohli-social.css').includes('position:absolute!important;inset:0!important') && read('app/src/main/assets/ohli-social.css').includes('overscroll-behavior-y:contain!important'), 'video reel mobile mengisi stage penuh dan scroll-snap tetap berada di dalam feed');

check(java.includes("/api/status/history") && java.includes("/api/status/history/delete") && java.includes("/api/status/history/repost"), 'Story History memiliki endpoint daftar, hapus, dan bagikan ulang');
check(java.includes("ohli-story-history.json") && java.includes("historyAt") && java.includes("socialStoryHistory=loadArray"), 'riwayat Story disimpan terpisah dan dimigrasikan dari Story lama tanpa menghapus data aktif');
check(java.includes("/api/highlights/edit") && java.includes("/api/highlights/item/delete") && java.includes("visibility"), 'Sorotan mendukung edit nama, privasi, dan hapus satu Story dari koleksi');
check(java.includes('Satu Story boleh disimpan di beberapa koleksi Sorotan') && java.includes('requestedCollection.equals(oldCollection)'), 'Story dapat ditambahkan ke Sorotan berbeda tanpa duplikat dalam koleksi yang sama');
check(java.includes("Story lama hanya dapat dihitung saat dilihat melalui Sorotan") && java.includes("statusViewsFile"), 'tayangan ulang Story kedaluwarsa dicatat hanya melalui Sorotan yang boleh dilihat');
check(socialUi.includes("async function openStoryHistory") && socialUi.includes("data-action=\"view-history-story\"") && socialUi.includes("formatStoryHistoryDate"), 'UI Riwayat Story membuka media dan menampilkan tanggal lengkap');
check(socialUi.includes("data-action=\"history-story-highlight\"") && socialUi.includes("/api/status/history/repost") && socialUi.includes("data-action=\"delete-history-story\""), 'History dapat ditambahkan ke Sorotan, dibagikan ulang, dan dihapus');
check(socialUi.includes("data-action=\"edit-highlight\"") && socialUi.includes("data-action=\"delete-highlight\"") && socialUi.includes("data-action=\"delete-highlight-item\""), 'profil menyediakan edit/hapus koleksi dan hapus Story satu per satu');
check(java.includes("/api/status/history") && java.includes("phone.equals(saved.optString(\"phone\"))") && socialUi.includes("if(!status.isMine&&status.phone!==me.phone)"), 'media History hanya dapat dibuka pemilik dan replay Story Sorotan dicatat');
const main = read('app/src/main/java/com/rk/server/MainActivity.java');
check(main.includes("PUSAT NOTIFIKASI") && main.includes("showServerInbox()") && main.includes("refreshInboxBadge()"), 'APK server menyediakan pusat notifikasi dengan badge belum dibaca');
check(java.includes("readServerInbox(Context context)") && java.includes("markServerInboxRead(Context context)") && java.includes("ohli-server-inbox.json"), 'notifikasi admin disimpan di file privat perangkat dan bisa dibaca ulang');
check(main.includes("Kode OTP: ") && main.includes("SALIN OTP") && main.includes("OTP sudah kedaluwarsa"), 'pusat notifikasi dapat menyalin OTP aktif dan menyembunyikan OTP kedaluwarsa');
check(java.includes("server_started") && java.includes("server_stopped") && java.includes("server_error"), 'pusat notifikasi mencatat server mulai, berhenti, dan error');



// v3.6.21 — Live Photo/photo-to-video, Story media history, password recovery, and personalized feed
check(socialUi.includes('function renderPhotoWithMusic(imageFile, track, settings)') && socialUi.includes('canvas.captureStream(24)') && socialUi.includes('new MediaRecorder(stream,opts)'), 'foto + musik dirender menjadi file video nyata melalui Canvas capture dan MediaRecorder');
check(socialUi.includes('function showPhotoVideoPreview(file,button)') && socialUi.includes('cr.renderedFile=file') && socialUi.includes("button.textContent=cr.target==='story'?'Bagikan Story video':'Posting video'"), 'video hasil gabungan dipratinjau sebelum diunggah sebagai media video');
check(socialUi.includes('crOriginalVolume') && socialUi.includes('crMusicVolume') && socialUi.includes('crPhotoDuration') && socialUi.includes('originalVolume:0.22, musicVolume:0.85'), 'editor menyediakan volume suara asli, volume musik, dan durasi foto-video terpisah');
check(socialUi.includes("startCreateWithFile(selectedFile,'post')") && socialUi.includes("startCreateWithFile(selectedStatusFile,'story')") && socialUi.includes("startCreateWithFile(camFile,camTarget)"), 'jalur upload postingan, Story, dan kamera diarahkan ke editor foto/video yang sama');
check(socialUi.includes('durationSeconds||cr.audioTrack.duration') && socialUi.includes('musicVolume:cr.musicVolume') && html.includes('uploadStatusMedia:function'), 'durasi katalog audio dan pengaturan volume diteruskan ke proses render');
check(socialUi.includes('Untuk gerakan Live Photo asli') && socialUi.includes('MOV hasil ekspor Live Photo') && socialUi.includes('HEIC/JPG saja tidak membawa rekaman geraknya'), 'UI menjelaskan batas sumber Live Photo dan tidak mengklaim foto diam memiliki gerakan asli');
check(socialUi.includes('async function openStoryHistory') && socialUi.includes('input type="date" id="ohliStoryHistoryDate"') && socialUi.includes('renderStoryHistoryRows'), 'Riwayat Story memiliki filter kalender tanggal dan pengelompokan harian');
check(socialUi.includes('media= s.mediaId') === false && socialUi.includes('Buka Story penuh') && socialUi.includes('preload="metadata"'), 'Riwayat Story merender media foto/video asli dan kontrol untuk membuka Story penuh');
check(socialUi.includes('name="coverStatusId"') && socialUi.includes('Sampul Sorotan') && java.includes('coverStatusId') && java.includes('Sampul harus dipilih dari Story di Sorotan ini'), 'pemilik dapat memilih ulang sampul Sorotan dari item Story yang ada dan server memvalidasi kepemilikan koleksi');
check(socialUi.includes('highlightThumbShape') && socialUi.includes('highlightThumbFit') && css.includes('data-highlight-shape="circle"') && css.includes('data-highlight-fit="contain"'), 'bentuk thumbnail Sorotan dan cara crop/fit dapat diatur');
check(html.includes("setAuthMode('reset')") && html.includes("purpose:authMode") && html.includes("mode==='reset'?'Atur ulang kata sandi'"), 'halaman login menyediakan alur Lupa kata sandi memakai mode reset OTP');
check(java.includes('"reset".equals(purpose)') && java.includes('resetSalt') && java.includes('passwordHash'), 'backend reset kata sandi mewajibkan OTP yang sesuai sebelum mengganti hash sandi');
check(java.includes('buildSocialInterests') && java.includes('video_complete') && java.includes('video_skip') && java.includes('not_interested') && java.includes('rankScores'), 'FYP menyusun skor personal dari sinyal OHLI: pencarian, tonton selesai, lewati, dan tidak tertarik');
check(java.includes('likedIds') && java.includes('savedIds') && java.includes('isFollowing(viewer,author)') && socialUi.includes("recordSocialActivity('video_complete'") && socialUi.includes("recordSocialActivity('video_skip'"), 'FYP juga mempertimbangkan like, simpan, mengikuti, dan durasi tontonan dari interaksi OHLI');
check(socialUi.includes("v.addEventListener('ended',markFypComplete)") && socialUi.includes('v.currentTime/v.duration>=0.95'), 'sinyal video selesai dicatat pada event ended atau saat mendekati akhir video');
check(html.includes('ohli-social.js?v=3.6.27') && html.includes('ohli-social.css?v=3.6.27') && read('app/build.gradle').includes('versionName "3.6.27"') && read('app/build.gradle').includes('versionCode 56') && java.includes('r.put("version", "3.6.27")'), 'cache-busting aset, Gradle, dan health endpoint sama-sama menggunakan v3.6.27');
check(workflow.includes('OHLI-Social-Source-v3.6.27.zip') && workflow.includes('OHLI-Social-Server-APK-v3.6.27'), 'workflow Android mengemas source dan artifact APK dengan versi v3.6.27');

check(java.includes('import java.util.Set;') && java.includes('import java.util.HashSet;'), 'ServerService mengimpor Set dan HashSet yang digunakan algoritma FYP');

const adaptiveEngine = read('app/src/main/java/com/rk/server/OhliAdaptiveEngine.java');
check(java.includes('adaptiveEngine.createExecutor(memoryClass)') && adaptiveEngine.includes('safeThreadCap()') && adaptiveEngine.includes('heapPressureEwma'), 'engine adaptif memilih kapasitas worker dari kelas memori dan tekanan heap aktual');
check(java.includes('adaptiveEngine.recordRequest(metricRoute') && java.includes('adaptiveEngine.recordRejected(workers)') && adaptiveEngine.includes('latencyEwmaMs') && adaptiveEngine.includes('learnOverloadRisk') && adaptiveEngine.includes('modelWeights') && adaptiveEngine.includes('importModelState') && adaptiveEngine.includes('exportModelState') && java.includes('persistAdaptiveModelIfDue(false)') && java.includes('ohli_ai_performance') && java.includes('persistAdaptiveModelIfDue(true)'), 'engine mencatat latensi, error, antrean penuh, dan baseline latensi yang dipelajari saat runtime');
check(java.includes('rebuildUserIndex()') && java.includes('rebuildSessionIndex()') && java.includes('userIndex.get(phone)') && java.includes('sessionIndex.get(token)'), 'pencarian pengguna dan sesi memakai indeks memori yang dibangun ulang dari data server');
check(java.includes('copyFileForBackup(f,backup)') && java.includes('previousMoved=f.renameTo(backup)'), 'penyimpanan JSON memakai rename satu direktori untuk mengurangi salinan file penuh dan tetap menyimpan recovery backup');
check(java.includes('gzipPreference.set(Boolean.valueOf(gzipAllowed))') && java.includes('adaptiveEngine.shouldCompressJson(body.length)') && java.includes('Content-Encoding: gzip'), 'respons JSON besar ikut dikompresi secara adaptif tanpa mengompresi payload kecil atau saat heap tertekan');
check(java.includes('adaptiveEngine.snapshot(workers)') && socialUi.includes("rt.request('/api/performance')") && main.includes('ServerService.adaptiveStatus()'), 'status engine AI ditampilkan melalui diagnostik web terautentikasi dan panel APK');
check(java.includes('"/api/performance".equals(path)') && java.includes('private void getPerformance') && java.includes('userForToken(queryParam(query, "token"))') && java.includes('result.put("performance", adaptiveEngine.snapshot(workers))') && java.includes('Public health is deliberately minimal') && !java.includes('r.put("performance", adaptiveEngine.snapshot(workers))'), 'metrik internal AI hanya tersedia setelah autentikasi dan tidak bocor dari endpoint publik /health');
check(adaptiveEngine.includes('double[] candidateWeights') && adaptiveEngine.includes('System.arraycopy(candidateWeights, 0, modelWeights, 0, modelWeights.length)'), 'pemulihan model AI memvalidasi seluruh state sebelum mengganti bobot aktif');
check(adaptiveEngine.includes('failureRateEwma') && adaptiveEngine.includes('failureRatePercent') && adaptiveEngine.includes('failureRateEwma >= 0.18d'), 'AI memasukkan rasio exception server ke pembelajaran overload dan diagnostik runtime');
check(adaptiveEngine.includes('ohli-ai-v2') && adaptiveEngine.includes('ohli-ai-v1') && adaptiveEngine.includes('boolean legacyV1'), 'format model AI v2 mempertahankan migrasi aman dari bobot model v1');

check(html.includes('class="rkm-call-topbar"') && html.includes('class="rkm-call-bottom"') && html.includes('id="flipCamera"'), 'panggilan video memiliki topbar, kontrol layar penuh, dan tombol ganti kamera');
check(html.includes('function toggleCallVideoLayout()') && html.includes("$('videoStage').addEventListener('click'") && html.includes('rkm-call-self-main'), 'ketukan pada jendela video menukar video utama dan pratinjau diri');
check(html.includes('rkm-call-stage .video-remote') && html.includes('object-fit:cover!important') && html.includes('rkm-call-stage .video-local'), 'video teman memenuhi layar dan video sendiri menjadi jendela mengambang');
check(css.includes('flex:0 0 66px!important') && css.includes('aspect-ratio:1/1!important') && css.includes('border-radius:50%!important'), 'thumbnail Sorotan dipaksa menjadi lingkaran dengan ukuran tetap');
check(html.includes('setTimeout(cycle,document.hidden?12000:(activeChat?4200:8500))') && html.includes('Promise.allSettled(jobs)'), 'polling adaptif mengurangi request ketika tab tidak aktif dan mencegah request bertumpuk');
check(socialUi.includes('var sideTasks=[') && socialUi.includes("rt.request('/api/trending')") && socialUi.includes('await Promise.allSettled(sideTasks)'), 'Story, saran pengguna, dan trending dimuat paralel dengan feed');
check(java.includes('RKServer-DataLoad') && java.includes('dataReady'), 'pemuatan awal data dipindahkan dari thread utama Android');
check(java.includes('adaptiveEngine.createExecutor(memoryClass)') && read('app/src/main/java/com/rk/server/OhliAdaptiveEngine.java').includes('new ThreadPoolExecutor.AbortPolicy()') && java.includes('503, "Service Unavailable"'), 'antrean server dibatasi tanpa membuat accept thread menjalankan request berat');
check(java.includes('\"index.html\", 0, gzipAllowed') && java.includes('\"ohli-social.css\", 86400, gzipAllowed') && java.includes('\"ohli-social.js\", 86400, gzipAllowed') && java.includes('readAssetGzipCached'), 'HTML, CSS, dan JavaScript disajikan melalui cache memori dan cache gzip');

// v3.6.21 regression checks: camera switching, visual history, 24h service, moderation and call history.
check(html.includes('async function flipCallCamera()') && html.includes('await sender.replaceTrack(newTrack)') && html.includes('Kamera sebelumnya tetap aktif'), 'pergantian kamera VC mengganti track WebRTC dan mempertahankan kamera lama ketika gagal');
check(html.includes('async function switchLiveCamera(') && html.includes('changed.push(sender)') && html.includes('await changed[j].replaceTrack(oldTrack)'), 'pergantian kamera LIVE memakai rollback track jika penggantian di salah satu peer gagal');
check(css.includes('.ohli-history-square') && css.includes('aspect-ratio:1/1') && socialUi.includes('data-action="history-open"'), 'riwayat tontonan memakai thumbnail 1:1 dan aksi buka ulang konten');
check(java.includes('private JSONArray socialCallHistory') && java.includes('getCallHistory(OutputStream') && java.includes('recordCallHistory(OutputStream') && java.includes('clearCallHistory(OutputStream') && java.includes('ohli-call-history.json'), 'riwayat panggilan disimpan di server per akun dan dapat disinkronkan lintas perangkat');
check(html.includes("api('/api/calls/history',{method:'POST',body:rec})") && html.includes("api('/api/calls/history').then(function(data)") && html.includes("api('/api/calls/history/clear'"), 'riwayat panggilan menyinkronkan tanggal, durasi, jenis, dan hapus ke server');
check(java.includes('WIFI_STATE_CHANGED_ACTION') && java.includes('refreshWifiLock()') && java.includes('registerReceiver(wifiStateReceiver') && java.includes('unregisterReceiver(wifiStateReceiver'), 'server memperbarui Wi-Fi lock saat status Wi-Fi berubah dan melepas receiver saat berhenti');
check(java.includes('PowerManager.PARTIAL_WAKE_LOCK') && java.includes('START_STICKY') && read('app/src/main/java/com/rk/server/BootReceiver.java').includes('ACTION_START'), 'service server memakai foreground restart dan CPU wake lock untuk dukungan operasi layar mati');
check(read('app/src/main/java/com/rk/server/MainActivity.java').includes('handler.postDelayed(this, 3000L)'), 'refresh panel server dikurangi menjadi setiap tiga detik');
check(java.includes('now-last>=15000L') && java.includes('lastSessionPresenceSaveAt>=60000L') && java.includes('session.put("lastActive",now)'), 'heartbeat memperbarui status online berkala tanpa menulis sessions JSON pada setiap request');
check(gradle.includes('versionCode 56') && gradle.includes('versionName "3.6.27"') && java.includes('r.put("version", "3.6.27")'), 'versi Gradle dan health endpoint selaras pada v3.6.27');

check(java.includes("accept-encoding") && java.includes("Content-Encoding: gzip") && java.includes("readAssetGzipCached") && java.includes("Vary: Accept-Encoding"), 'HTML, CSS, dan JavaScript mendukung gzip dengan cache hasil kompresi untuk mengurangi transfer data');
check(java.includes('Pattern.compile("(?i)q\\\\s*=\\\\s*([0-9.]+)")'), 'parser gzip menggunakan escape regex yang kompatibel dengan sourceCompatibility Java 8');
check(read('app/src/main/java/com/rk/server/MainActivity.java').includes('|| \"verifying\".equals(status)'), 'kontrol Stop tetap tersedia saat URL publik sedang diverifikasi');
check(html.includes('function startCallSignalPolling()') && html.includes('currentCall?900:1800') && !html.includes('if(visible||currentCall)jobs.push(refreshCalls(false))'), 'polling signaling panggilan dipisahkan dari polling feed agar offer dan ICE tidak tertahan 4–8 detik');
check(html.includes('--rkm-viewport-height,100vh') && html.includes('rounded portrait tile') && html.includes('.rkm-call-stage .video-remote{position:absolute!important;inset:0!important;width:100%!important;height:100%!important'), 'panggilan memakai tinggi visual viewport dan mempertahankan video lawan layar penuh dengan preview diri portrait');
check(tunnelJava.includes('verifyPublicHealth(publicUrl)') && tunnelJava.includes('PREF_STATE, "verifying"') && tunnelJava.includes('endpoint publik /health'), 'Quick Tunnel baru berstatus ONLINE dan membagikan URL setelah health-check HTTPS publik berhasil');
check(workflow.includes('retry and mirror fallback') && workflow.includes('downloads.gradle.org') && workflow.includes('sha256sum --check'), 'workflow Gradle mengulang unduhan, memakai endpoint cadangan, dan memverifikasi checksum resmi');


check(read('app/src/main/assets/ohli-social.css').includes('v3.6.27 reference refresh') && read('app/src/main/java/com/rk/server/MainActivity.java').includes('roundRect(WHITE'), 'tema referensi diterapkan pada shell web/chat dan dashboard native Android');
check(java.includes('NOTIFICATION_WIFI_ALERT') && java.includes('NOTIFICATION_SERVER_ALERT') && java.includes('operationalHandler.postDelayed(this, 12000L)'), 'pemantau operasional dan kanal notifikasi Wi-Fi/server tertanam pada service');
check(java.includes('previousWifi.booleanValue()') && java.includes('Koneksi Wi-Fi OHLI terputus'), 'peringatan Wi-Fi hanya dipicu oleh transisi koneksi yang terpantau');
check(read('app/src/main/AndroidManifest.xml').includes('android.permission.POST_NOTIFICATIONS'), 'manifest mendeklarasikan izin notifikasi');


check(java.includes('lastWifiConnected = Boolean.valueOf(isWifiConnected())') && java.includes('Capture the initial network state'), 'pemantau menyimpan keadaan Wi-Fi awal agar putus koneksi setelah startup tidak terlewat');
check(read('app/src/main/java/com/rk/server/CloudflareTunnelService.java').includes('workflow v3.6.27'), 'pesan error ABI Cloudflare mengarahkan ke workflow source yang benar');
check(read('app/src/main/assets/ohli-social.css').includes('body:not(.v5-light) #app .main-head') && read('app/src/main/assets/ohli-social.css').includes('body.v5-light .auth-card'), 'tema legacy chat, autentikasi, dan mode malam menggunakan palet referensi');

console.log(`\nLulus ${passed} pemeriksaan statis. Ini bukan pengganti build APK atau tes runtime pada dua perangkat.`);
