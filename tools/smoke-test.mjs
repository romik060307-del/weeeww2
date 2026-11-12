#!/usr/bin/env node
/**
 * Non-production end-to-end smoke test for the Android-hosted OHLI API.
 * Uses two existing disposable accounts, two sessions per account, and cleans
 * up test posts/messages/stories/groups/channels. It leaves one uniquely tagged
 * activity-history record because the API intentionally exposes account-wide
 * clear, not per-event deletion. Never run this against primary accounts.
 */
const base = (process.env.OHLI_BASE_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const phoneA = String(process.env.OHLI_PHONE_A || '').replace(/\D/g, '');
const tokenAEnv = process.env.OHLI_TOKEN_A || '';
const tokenADevice2Env = process.env.OHLI_TOKEN_A_DEVICE2 || '';
const phoneB = String(process.env.OHLI_PHONE_B || '').replace(/\D/g, '');
const tokenBEnv = process.env.OHLI_TOKEN_B || '';
const tokenBDevice2Env = process.env.OHLI_TOKEN_B_DEVICE2 || '';

if (!phoneA || !phoneB || !tokenAEnv || !tokenBEnv) {
  console.error('Isi OHLI_PHONE_A, OHLI_TOKEN_A, OHLI_PHONE_B, dan OHLI_TOKEN_B dari dua sesi uji yang sudah diverifikasi OTP. Opsional: OHLI_TOKEN_A_DEVICE2 dan OHLI_TOKEN_B_DEVICE2.');
  process.exit(2);
}
if (phoneA === phoneB) {
  console.error('Akun A dan B harus berbeda.');
  process.exit(2);
}

let passed = 0;
const createdPostsA = [];
const createdStickersA = [];
const createdMusicA = [];
const createdMessagesA = [];
const createdStatusesA = [];
const createdHighlightsA = [];
const createdCommunities = [];
let tokenA = '';
let tokenB = '';
let tokenADevice2 = '';
let tokenBDevice2 = '';
let originalDirectMessagesA = 'everyone';
let privacyAChanged = false;
let contactAtoBAdded = false;
const unique = 'OHLI_SMOKE_' + Date.now().toString(36).toUpperCase();

async function request(path, {method = 'GET', token, body, allowStatuses = []} = {}) {
  const url = new URL(path, base);
  const headers = {Accept: 'application/json'};
  if (token) headers['X-RKM-Token'] = token;
  const config = {method, headers};
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json; charset=utf-8';
    config.body = JSON.stringify({...body});
  }
  let response;
  try {
    response = await fetch(url, config);
  } catch (error) {
    throw new Error(`Tidak dapat menghubungi ${base}: ${error.message}`);
  }
  let data = {};
  try { data = await response.json(); } catch { /* non-JSON response checked below */ }
  if (!response.ok && !allowStatuses.includes(response.status)) {
    throw new Error(`${method} ${path} => HTTP ${response.status}: ${data.error || 'respons bukan JSON sukses'}`);
  }
  if (response.ok && data.ok === false && !allowStatuses.includes(response.status)) {
    throw new Error(`${method} ${path} => ${data.error || 'API mengembalikan ok:false'}`);
  }
  return {status: response.status, data};
}
function check(ok, message) {
  if (!ok) throw new Error(message);
  passed++;
  console.log(`PASS ${passed}: ${message}`);
}
async function uploadBinary(path, token, bytes, mime) {
  const response = await fetch(new URL(path, base), {
    method: 'POST',
    headers: {'Content-Type': mime, 'X-RKM-Token': token},
    body: bytes,
  });
  let data = {};
  try { data = await response.json(); } catch {}
  if (!response.ok || data.ok === false) throw new Error(`POST ${path} => HTTP ${response.status}: ${data.error || 'unggahan gagal'}`);
  return data;
}
function makeSilentWav() {
  const sampleRate = 8000, samples = 8000, bytes = Buffer.alloc(44 + samples, 128);
  bytes.write('RIFF', 0); bytes.writeUInt32LE(36 + samples, 4); bytes.write('WAVE', 8);
  bytes.write('fmt ', 12); bytes.writeUInt32LE(16, 16); bytes.writeUInt16LE(1, 20);
  bytes.writeUInt16LE(1, 22); bytes.writeUInt32LE(sampleRate, 24); bytes.writeUInt32LE(sampleRate, 28);
  bytes.writeUInt16LE(1, 32); bytes.writeUInt16LE(8, 34); bytes.write('data', 36); bytes.writeUInt32LE(samples, 40);
  return bytes;
}

async function createPost(token, text, audience = 'public') {
  const {data} = await request('/api/posts', {method: 'POST', token, body: {text, audience}});
  if (!data.post?.id) throw new Error('API tidak mengembalikan ID postingan baru.');
  if (token === tokenA) createdPostsA.push(data.post.id);
  return data.post;
}

try {
  const {data: health} = await request('/health');
  check(health.ok === true, 'health endpoint menjawab OK');
  tokenA = tokenAEnv;
  tokenB = tokenBEnv;
  tokenADevice2 = tokenADevice2Env || tokenA;
  tokenBDevice2 = tokenBDevice2Env || tokenB;
  check(Boolean(tokenA && tokenB && tokenA !== tokenB), 'dua akun uji memakai sesi yang telah melewati verifikasi OTP');
  if (tokenADevice2Env && tokenBDevice2Env) check(tokenADevice2 !== tokenA && tokenBDevice2 !== tokenB, 'token perangkat kedua terpisah untuk dua akun uji');
  const ownChatMe = (await request('/api/me', {token: tokenA})).data;
  check(ownChatMe.phone === phoneA && Boolean(ownChatMe.name), 'profil Chat akun sendiri mengembalikan nomor hanya melalui sesi terautentikasi');
  const privateMe = await request('/api/me', {allowStatuses: [401]});
  check(privateMe.status === 401, 'endpoint profil pribadi tidak dapat dibuka tanpa sesi akun');
  const initialStickerLibrary = (await request('/api/stickers', {token: tokenA})).data;
  check(Array.isArray(initialStickerLibrary.mine) && Array.isArray(initialStickerLibrary.favorites), 'API koleksi stiker mengembalikan Stiker Saya dan Favorit untuk akun terautentikasi');
  const initialMyMusic = (await request('/api/music/mine', {token: tokenA})).data;
  check(Array.isArray(initialMyMusic.music), 'API Audio Saya mengembalikan koleksi audio pemilik');

  // Social discovery must never expose phone numbers, even though Chat has a separate private directory.
  const socialUsersB = (await request('/api/social/users', {token: tokenB})).data.users || [];
  const socialA = socialUsersB.find(user => user.username && user.phone);
  check(socialUsersB.every(user => /^oh_[a-f0-9]{20}$/i.test(String(user.phone || '')) && user.phone !== phoneA && user.phone !== phoneB), 'daftar Social hanya mengembalikan ID publik oh_, bukan nomor telepon');

  // Dedicated friendship lifecycle, including persistence and public-ID addressing.
  const socialUsersA = (await request('/api/social/users', {token: tokenA})).data.users || [];
  const socialB = socialUsersA.find(user => user.username && user.phone);
  if (!socialA?.phone || !socialB?.phone) throw new Error('ID Social untuk pasangan akun uji tidak ditemukan.');
  const friendRequest = (await request('/api/friends', {method: 'POST', token: tokenB, body: {target: socialA.phone, action: 'request'}})).data;
  check(friendRequest.requested === true, 'permintaan teman dapat dikirim dengan ID publik');
  const duplicateFriendRequest = (await request('/api/friends', {method: 'POST', token: tokenB, body: {target: socialA.phone, action: 'request'}})).data;
  check(duplicateFriendRequest.requested === true && duplicateFriendRequest.changed === false, 'permintaan teman berulang tidak membuat relasi ganda');
  const incomingFriendRequests = (await request('/api/friends/requests', {token: tokenA})).data.incoming || [];
  check(incomingFriendRequests.filter(user => user.phone === socialB.phone).length === 1, 'permintaan masuk muncul sekali dan hanya memakai ID Social');
  const acceptedFriend = (await request('/api/friends', {method: 'POST', token: tokenA, body: {target: socialB.phone, action: 'accept'}})).data;
  check(acceptedFriend.isFriend === true, 'penerima dapat menerima permintaan dan membuat relasi teman');
  const friendsOnSecondSession = (await request('/api/friends', {token: tokenBDevice2})).data.friends || [];
  check(friendsOnSecondSession.some(user => user.phone === socialA.phone), tokenBDevice2Env ? 'daftar teman tersimpan di server dan terbaca dari sesi perangkat kedua' : 'daftar teman tersimpan dan terbaca pada sesi aktif (token kedua opsional)');
  const removedFriend = (await request('/api/friends', {method: 'POST', token: tokenA, body: {target: socialB.phone, action: 'remove'}})).data;
  check(removedFriend.isFriend === false && !((await request('/api/friends', {token: tokenB})).data.friends || []).some(user => user.phone === socialA.phone), 'hapus teman memutus relasi pada kedua akun');
  await request('/api/friends', {method: 'POST', token: tokenB, body: {target: socialA.phone, action: 'request'}});
  await request('/api/friends', {method: 'POST', token: tokenA, body: {target: socialB.phone, action: 'reject'}});
  const afterReject = (await request('/api/friends/requests', {token: tokenA})).data.incoming || [];
  check(!afterReject.some(user => user.phone === socialB.phone), 'tolak permintaan menghapusnya dari antrean masuk');
  await request('/api/friends', {method: 'POST', token: tokenB, body: {target: socialA.phone, action: 'request'}});
  await request('/api/friends', {method: 'POST', token: tokenB, body: {target: socialA.phone, action: 'cancel'}});
  const afterCancel = (await request('/api/friends/requests', {token: tokenB})).data.sent || [];
  check(!afterCancel.some(user => user.phone === socialA.phone), 'pengirim dapat membatalkan permintaan teman');

  const ownProfileA = (await request(`/api/profile?phone=${encodeURIComponent(phoneA)}`, {token: tokenA})).data;
  originalDirectMessagesA = ownProfileA.user?.privacy?.directMessages || 'everyone';
  await request('/api/settings/privacy', {method: 'POST', token: tokenA, body: {directMessages: 'private'}});
  privacyAChanged = true;
  const chatUsersPrivate = (await request('/api/users', {token: tokenB})).data.users || [];
  check(!chatUsersPrivate.some(user => String(user.phone) === phoneA), 'akun dengan DM privat tidak muncul pada direktori nomor Chat');
  const phoneProbe = await request(`/api/user-exists?phone=${encodeURIComponent(phoneA)}`, {token: tokenB});
  check(phoneProbe.data.exists === false && !phoneProbe.data.name, 'pemeriksaan nomor tidak membocorkan keberadaan akun yang menolak DM');
  const resolvePrivate = await request(`/api/chat/resolve?target=${encodeURIComponent(phoneA)}`, {token: tokenB, allowStatuses: [403]});
  check(resolvePrivate.status === 403, 'resolusi nomor untuk membuka Chat ditolak saat DM privat');
  await request('/api/settings/privacy', {method: 'POST', token: tokenA, body: {directMessages: 'everyone'}});
  const chatUsersAllowed = (await request('/api/users', {token: tokenB})).data.users || [];
  check(chatUsersAllowed.some(user => String(user.phone) === phoneA), 'nomor akun yang mengizinkan DM tersedia hanya pada direktori Chat');
  const profileChatResolve = await request(`/api/chat/resolve?target=${encodeURIComponent(phoneA)}`, {token: tokenB});
  check(profileChatResolve.data.phone === phoneA, 'resolver Chat mengembalikan target yang benar saat session aktif');

  // Real binary persistence check for imported gallery sticker and Audio Saya.
  const tinyPng = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9f8AAAAASUVORK5CYII=', 'base64');
  const stickerUpload = await uploadBinary('/api/media/upload?purpose=comment-sticker&filename=ohli-smoke.png', tokenA, tinyPng, 'image/png');
  const stickerMediaId = stickerUpload.sticker?.mediaId || stickerUpload.mediaId;
  if (!stickerMediaId) throw new Error('Upload stiker berhasil tetapi server tidak mengembalikan mediaId.');
  createdStickersA.push(stickerMediaId);
  const stickerLibraryAfterUpload = (await request('/api/stickers', {token: tokenA})).data;
  check((stickerLibraryAfterUpload.mine || []).some(item => item.mediaId === stickerMediaId), 'stiker galeri masuk ke Stiker Saya setelah dibaca ulang dari server');
  const stickerFetch = await fetch(new URL(`/api/media?id=${encodeURIComponent(stickerMediaId)}&token=${encodeURIComponent(tokenA)}`, base));
  check(stickerFetch.ok && (await stickerFetch.arrayBuffer()).byteLength === tinyPng.length, 'file stiker pribadi dapat dibaca kembali dari endpoint media');
  await request('/api/stickers/delete', {method: 'POST', token: tokenA, body: {mediaId: stickerMediaId}});
  createdStickersA.splice(createdStickersA.indexOf(stickerMediaId), 1);
  const stickerLibraryAfterDelete = (await request('/api/stickers', {token: tokenA})).data;
  check(!(stickerLibraryAfterDelete.mine || []).some(item => item.mediaId === stickerMediaId), 'pemilik dapat menghapus stiker galeri yang belum dipakai komentar');

  const wav = makeSilentWav();
  const musicUpload = await uploadBinary('/api/media/upload?purpose=music&filename=ohli-smoke.wav&title=Audio%20Uji&artist=OHLI%20Test&duration=1', tokenA, wav, 'audio/wav');
  if (!musicUpload.music?.id || !musicUpload.music?.mediaId) throw new Error('Upload audio berhasil tetapi metadata musik tidak lengkap.');
  createdMusicA.push(musicUpload.music.id);
  const myMusicAfterUpload = (await request('/api/music/mine', {token: tokenA})).data.music || [];
  const savedSmokeMusic = myMusicAfterUpload.find(item => item.id === musicUpload.music.id);
  check(Boolean(savedSmokeMusic) && savedSmokeMusic.durationSeconds === 1 && savedSmokeMusic.isMine === true, 'audio impor dan metadata durasi muncul di Audio Saya setelah pembacaan ulang server');
  const musicFetch = await fetch(new URL(`/api/media?id=${encodeURIComponent(musicUpload.music.mediaId)}&token=${encodeURIComponent(tokenA)}`, base));
  check(musicFetch.ok && (await musicFetch.arrayBuffer()).byteLength === wav.length, 'file audio pribadi dapat diputar melalui endpoint media setelah diunggah');
  await request('/api/music/delete', {method: 'POST', token: tokenA, body: {musicId: musicUpload.music.id}});
  createdMusicA.splice(createdMusicA.indexOf(musicUpload.music.id), 1);
  const myMusicAfterDelete = (await request('/api/music/mine', {token: tokenA})).data.music || [];
  check(!myMusicAfterDelete.some(item => item.id === musicUpload.music.id), 'pemilik dapat menghapus audio impor yang belum dipakai postingan');

  const noToken = await request('/api/feed', {allowStatuses: [401]});
  check(noToken.status === 401, 'endpoint feed menolak sesi tanpa token');

  // Server-persisted activity is visible to another login session of the same account, not another account.
  const activityClientId = `${unique}_ACTIVITY_CLIENT`;
  await request('/api/activity/history', {method: 'POST', token: tokenA, body: {type: 'search', id: unique, label: `${unique}_HISTORY`, query: unique, clientId: activityClientId}});
  await request('/api/activity/history', {method: 'POST', token: tokenA, body: {type: 'search', id: unique, label: `${unique}_HISTORY_UPDATED`, query: unique, clientId: activityClientId}});
  const historyADevice2 = (await request('/api/activity/history', {token: tokenADevice2})).data.activities || [];
  const historyMatches = historyADevice2.filter(item => item.query === unique);
  check(historyMatches.length === 1 && historyMatches[0].label === `${unique}_HISTORY_UPDATED`, 'riwayat tersimpan lintas sesi akun dan retry tidak menggandakan event');
  const historyB = (await request('/api/activity/history', {token: tokenB})).data.activities || [];
  check(!historyB.some(item => item.query === unique), 'riwayat aktivitas akun A tidak terlihat oleh akun B');

  // A friend can pin/archive an active public Story; collections remain on the server across sessions.
  const story = (await request('/api/status', {method: 'POST', token: tokenA, body: {text: `${unique}_STORY`, audience: 'public', destination: 'social'}})).data.status;
  if (!story?.id) throw new Error('Story uji tidak mengembalikan ID.');
  createdStatusesA.push(story.id);
  const visibleStoriesB = (await request('/api/status?surface=social', {token: tokenB})).data.statuses || [];
  check(visibleStoriesB.some(item => item.id === story.id), 'Story publik akun A terlihat oleh akun B');
  const highlight1 = (await request('/api/highlights', {method: 'POST', token: tokenA, body: {statusId: story.id, name: `${unique}_HIGHLIGHT`}})).data.highlight;
  if (!highlight1?.id) throw new Error('Sorotan baru tidak mengembalikan ID koleksi.');
  createdHighlightsA.push(highlight1.id);
  const story2 = (await request('/api/status', {method: 'POST', token: tokenA, body: {text: `${unique}_STORY_2`, audience: 'public', destination: 'social'}})).data.status;
  if (!story2?.id) throw new Error('Story kedua untuk uji Sorotan tidak mengembalikan ID.');
  createdStatusesA.push(story2.id);
  await request('/api/highlights', {method: 'POST', token: tokenA, body: {statusId: story2.id, highlightId: highlight1.id}});
  const ownHighlights = (await request('/api/highlights', {token: tokenA})).data.highlights || [];
  const groupedHighlight = ownHighlights.find(item => item.id === highlight1.id);
  check(groupedHighlight && groupedHighlight.items?.length === 2, 'dua Story tersimpan dalam satu Sorotan yang sama dan tetap terbaca dari API');
  await request('/api/highlights/delete', {method: 'POST', token: tokenA, body: {id: highlight1.id}});
  createdHighlightsA.splice(createdHighlightsA.indexOf(highlight1.id), 1);
  const highlightsAfterDelete = (await request('/api/highlights', {token: tokenA})).data.highlights || [];
  check(!highlightsAfterDelete.some(item => item.id === highlight1.id), 'menghapus Sorotan menghapus seluruh koleksi, bukan hanya satu Story');
  await request('/api/status/collections', {method: 'POST', token: tokenB, body: {statusId: story.id, action: 'pin'}});
  await request('/api/status/collections', {method: 'POST', token: tokenB, body: {statusId: story.id, action: 'archive'}});
  const pinnedOnDevice2 = (await request('/api/status/collections?mode=pinned', {token: tokenBDevice2})).data.items || [];
  const archivedOnDevice2 = (await request('/api/status/collections?mode=archived', {token: tokenBDevice2})).data.items || [];
  check(pinnedOnDevice2.some(item => item.statusId === story.id) && archivedOnDevice2.some(item => item.statusId === story.id), 'pin dan arsip Story tersimpan server dan terlihat dari sesi kedua');
  await request('/api/status/collections', {method: 'POST', token: tokenB, body: {statusId: story.id, action: 'unpin'}});
  await request('/api/status/collections', {method: 'POST', token: tokenB, body: {statusId: story.id, action: 'unarchive'}});
  const clearedStoryCollection = (await request('/api/status/collections', {token: tokenBDevice2})).data.items || [];
  check(!clearedStoryCollection.some(item => item.statusId === story.id), 'Story keluar dari koleksi setelah pin dan arsip dilepas');
  await request('/api/status/delete', {method: 'POST', token: tokenA, body: {statusId: story.id}});
  createdStatusesA.splice(createdStatusesA.indexOf(story.id), 1);
  const activeAfterOwnDelete = (await request('/api/status?surface=social', {token: tokenA})).data.statuses || [];
  const historyAfterOwnDelete = (await request('/api/status/history', {token: tokenA})).data.stories || [];
  check(!activeAfterOwnDelete.some(item => item.id === story.id), 'Story yang dihapus pemilik hilang dari daftar Story aktif');
  check(historyAfterOwnDelete.some(item => item.id === story.id), 'menghapus Story aktif tidak ikut menghapus salinan Riwayat');

  // A true server-claimed 1x text can be opened only once, including from a second login session.
  const onceText = `${unique}_VIEW_ONCE_TEXT`;
  const onceMessage = (await request('/api/messages', {method: 'POST', token: tokenA, body: {recipient: phoneB, text: `\u2063RKM1X\u2063${onceText}`}})).data.message;
  if (!onceMessage?.id) throw new Error('Pesan teks 1× tidak mengembalikan ID pesan.');
  createdMessagesA.push(onceMessage.id);
  const receiverMessages = (await request(`/api/messages?peer=${encodeURIComponent(phoneA)}`, {token: tokenB})).data.messages || [];
  const onceInList = receiverMessages.find(item => item.id === onceMessage.id);
  check(Boolean(onceInList?.viewOnce) && onceInList.text === '', 'isi pesan teks 1× disembunyikan sebelum penerima mengklaimnya');
  const firstOpen = (await request('/api/messages/once/open', {method: 'POST', token: tokenB, body: {id: onceMessage.id}})).data;
  check(firstOpen.opened === true && firstOpen.text === onceText, 'klaim pertama pesan teks 1× mengembalikan isi pesan');
  const secondDeviceOpen = await request('/api/messages/once/open', {method: 'POST', token: tokenBDevice2, body: {id: onceMessage.id}, allowStatuses: [410]});
  check(secondDeviceOpen.status === 410, 'klaim kedua dari sesi/perangkat login lain ditolak HTTP 410');
  const onceAfterOpen = (await request(`/api/messages?peer=${encodeURIComponent(phoneA)}`, {token: tokenBDevice2})).data.messages || [];
  const onceAfterOpenRow = onceAfterOpen.find(item => item.id === onceMessage.id);
  check(Boolean(onceAfterOpenRow?.viewOnceOpened) && onceAfterOpenRow.text === '', 'status 1× tetap terkunci pada sesi kedua setelah pembukaan pertama');

  // Group room: invite a saved Chat friend during creation, manage membership, chat and poll.
  await request('/api/contacts', {method: 'POST', token: tokenA, body: {action: 'add', phone: phoneB, alias: `${unique} FRIEND`}});
  contactAtoBAdded = true;
  const contactRows = (await request('/api/contacts', {token: tokenA})).data.contacts || [];
  const inviteSocialId = contactRows.find(item => String(item.phone) === phoneB)?.socialId;
  check(Boolean(inviteSocialId) && /^oh_[a-f0-9]{20}$/i.test(inviteSocialId), 'kontak Chat memberi kunci undangan Social yang tidak menampilkan nomor');
  const group = (await request('/api/groups', {method: 'POST', token: tokenA, body: {name: `${unique} GROUP`, description: 'Grup uji otomatis', privacy: 'private', memberPhones: [inviteSocialId]}})).data.item;
  if (!group?.id) throw new Error('Grup uji tidak mengembalikan ID.');
  createdCommunities.push({kind: 'group', id: group.id});
  check(group.memberCount === 2, 'pembuatan grup menambahkan teman pilihan dari Kontak Chat sejak awal');
  const invitedMembers = (await request(`/api/groups/members?groupId=${encodeURIComponent(group.id)}`, {token: tokenB})).data.members || [];
  check(invitedMembers.some(item => item.isMe) && invitedMembers.every(item => !Object.prototype.hasOwnProperty.call(item, 'phone')), 'anggota undangan dapat melihat daftar anggota tanpa nomor telepon publik');
  const membersForOwner = (await request(`/api/groups/members?groupId=${encodeURIComponent(group.id)}`, {token: tokenA})).data.members || [];
  const memberB = membersForOwner.find(item => !item.isOwner);
  if (!memberB?.id) throw new Error('Anggota undangan tidak ditemukan untuk tes hapus/tambah.');
  await request('/api/groups/members', {method: 'POST', token: tokenA, body: {groupId: group.id, action: 'remove', target: memberB.id}});
  const removedAccess = await request(`/api/groups/messages?groupId=${encodeURIComponent(group.id)}`, {token: tokenB, allowStatuses: [403]});
  check(removedAccess.status === 403, 'anggota yang dikeluarkan tidak lagi dapat membuka ruang grup privat');
  const readded = (await request('/api/groups/members', {method: 'POST', token: tokenA, body: {groupId: group.id, action: 'add', phones: [inviteSocialId]}})).data;
  check(readded.added === 1, 'pemilik dapat menambahkan kembali teman tersimpan ke grup');
  const joinedGroup = (await request(`/api/groups/messages?groupId=${encodeURIComponent(group.id)}`, {token: tokenB})).data.group;
  check(Boolean(joinedGroup?.joined), 'teman yang ditambahkan dapat membuka ruang grup setelah diundang');
  const groupText = (await request('/api/groups/messages', {method: 'POST', token: tokenB, body: {groupId: group.id, text: `${unique}_GROUP_MESSAGE`}})).data.message;
  check(Boolean(groupText?.id), 'pesan grup dari anggota tersimpan');
  const poll = (await request('/api/groups/messages', {method: 'POST', token: tokenA, body: {groupId: group.id, kind: 'poll', question: `${unique}_POLL`, options: ['Pilihan A', 'Pilihan B']}})).data.message;
  if (!poll?.id || !Array.isArray(poll.options) || poll.options.length !== 2) throw new Error('Polling grup tidak dibuat sesuai dua pilihan.');
  await request('/api/groups/vote', {method: 'POST', token: tokenB, body: {groupId: group.id, messageId: poll.id, optionId: poll.options[0].id}});
  await request('/api/groups/vote', {method: 'POST', token: tokenB, body: {groupId: group.id, messageId: poll.id, optionId: poll.options[1].id}});
  const groupMessagesB = (await request(`/api/groups/messages?groupId=${encodeURIComponent(group.id)}`, {token: tokenB})).data.messages || [];
  const pollAfterVote = groupMessagesB.find(message => message.id === poll.id);
  check(Boolean(pollAfterVote) && pollAfterVote.options.filter(option => option.votedByMe).length === 1 && pollAfterVote.options.find(option => option.votedByMe)?.id === poll.options[1].id, 'polling grup menyimpan satu pilihan aktif per anggota');
  const groupMessagesA = (await request(`/api/groups/messages?groupId=${encodeURIComponent(group.id)}`, {token: tokenA})).data.messages || [];
  check(groupMessagesA.some(message => message.text === `${unique}_GROUP_MESSAGE`), 'pesan anggota tampil pada ruang grup milik akun A');
  const groupDeleteAsB = await request('/api/groups/delete', {method: 'POST', token: tokenB, body: {id: group.id}, allowStatuses: [403]});
  check(groupDeleteAsB.status === 403, 'anggota biasa tidak dapat menghapus grup milik orang lain');
  await request('/api/groups/delete', {method: 'POST', token: tokenA, body: {id: group.id}});
  createdCommunities.splice(createdCommunities.findIndex(item => item.id === group.id), 1);
  check(true, 'pemilik dapat menghapus grup dan membersihkan pesan pollingnya');

  // Channel broadcast: only owner publishes; followers can read and react.
  const channel = (await request('/api/channels', {method: 'POST', token: tokenA, body: {name: `${unique} CHANNEL`, description: 'Channel uji otomatis', privacy: 'public'}})).data.item;
  if (!channel?.id) throw new Error('Channel uji tidak mengembalikan ID.');
  createdCommunities.push({kind: 'channel', id: channel.id});
  const followed = (await request('/api/channels/follow', {method: 'POST', token: tokenB, body: {id: channel.id, action: 'follow'}})).data;
  check(followed.joined === true, 'akun B dapat mengikuti Channel publik');
  const channelPost = (await request('/api/channels/posts', {method: 'POST', token: tokenA, body: {channelId: channel.id, text: `${unique}_CHANNEL_POST`}})).data.post;
  check(Boolean(channelPost?.id), 'pemilik dapat menerbitkan pengumuman Channel');
  const channelRead = (await request(`/api/channels/posts?channelId=${encodeURIComponent(channel.id)}`, {token: tokenB})).data;
  check((channelRead.posts || []).some(post => post.id === channelPost.id), 'pengikut dapat membaca siaran Channel');
  const notOwnerPost = await request('/api/channels/posts', {method: 'POST', token: tokenB, body: {channelId: channel.id, text: `${unique}_FORBIDDEN_POST`}, allowStatuses: [403]});
  check(notOwnerPost.status === 403, 'pengikut tidak dapat menerbitkan pesan ke Channel satu arah');
  const reacted = (await request('/api/channels/react', {method: 'POST', token: tokenB, body: {channelId: channel.id, postId: channelPost.id, emoji: '🔥'}})).data.post;
  const fireReaction = (reacted.reactionSummary || []).find(item => item.emoji === '🔥');
  check(Boolean(fireReaction) && fireReaction.count === 1 && fireReaction.mine === true, 'reaksi emoji Channel tersimpan dan dihitung');
  const channelDeleteAsB = await request('/api/channels/delete', {method: 'POST', token: tokenB, body: {id: channel.id}, allowStatuses: [403]});
  check(channelDeleteAsB.status === 403, 'pengikut tidak dapat menghapus Channel milik orang lain');
  await request('/api/channels/delete', {method: 'POST', token: tokenA, body: {id: channel.id}});
  createdCommunities.splice(createdCommunities.findIndex(item => item.id === channel.id), 1);
  check(true, 'pemilik dapat menghapus Channel dan membersihkan postingannya');

  const publicPost = await createPost(tokenA, `${unique}_PUBLIC`, 'public');
  const privatePost = await createPost(tokenA, `${unique}_PRIVATE`, 'private');
  const feedB = (await request('/api/feed?mode=public', {token: tokenB})).data.posts || [];
  check(feedB.some(p => p.id === publicPost.id), 'posting publik akun A terlihat oleh akun B');
  check(!feedB.some(p => p.id === privatePost.id), 'posting privat akun A tidak terlihat pada feed akun B');

  const profileB = (await request(`/api/profile?phone=${encodeURIComponent(phoneA)}`, {token: tokenB})).data;
  check((profileB.posts || []).some(p => p.id === publicPost.id), 'profil menampilkan postingan yang boleh dilihat');
  check(!(profileB.posts || []).some(p => p.id === privatePost.id), 'profil tidak membocorkan postingan privat');

  const crossEdit = await request('/api/posts/edit', {method: 'POST', token: tokenB, body: {postId: publicPost.id, text: 'UNAUTHORIZED_EDIT', audience: 'public'}, allowStatuses: [403]});
  check(crossEdit.status === 403, 'akun B tidak dapat mengedit postingan milik akun A');
  const crossArchive = await request('/api/posts/archive', {method: 'POST', token: tokenB, body: {postId: publicPost.id, action: 'archive'}, allowStatuses: [403]});
  check(crossArchive.status === 403, 'akun B tidak dapat mengarsipkan postingan milik akun A');
  const crossDelete = await request('/api/posts/delete', {method: 'POST', token: tokenB, body: {postId: publicPost.id}, allowStatuses: [403]});
  check(crossDelete.status === 403, 'akun B tidak dapat menghapus postingan milik akun A');

  const liked = (await request('/api/posts/like', {method: 'POST', token: tokenB, body: {postId: publicPost.id}})).data;
  check(liked.liked === true, 'like dapat ditambahkan oleh akun B');
  const unliked = (await request('/api/posts/like', {method: 'POST', token: tokenB, body: {postId: publicPost.id}})).data;
  check(unliked.liked === false, 'like dapat dibatalkan oleh akun B');

  const comment = (await request('/api/posts/comment', {method: 'POST', token: tokenB, body: {postId: publicPost.id, text: `${unique}_COMMENT`}})).data;
  check(Boolean(comment.comment?.id), 'komentar tersimpan pada postingan');

  const saved = (await request('/api/posts/bookmark', {method: 'POST', token: tokenB, body: {postId: publicPost.id}})).data;
  check(saved.saved === true, 'bookmark pribadi dapat dibuat');
  const savedList = (await request('/api/posts/saved', {token: tokenB})).data.posts || [];
  check(savedList.some(p => p.id === publicPost.id), 'bookmark muncul di daftar tersimpan milik akun B');
  const unsaved = (await request('/api/posts/bookmark', {method: 'POST', token: tokenB, body: {postId: publicPost.id}})).data;
  check(unsaved.saved === false, 'bookmark dapat dihapus');

  const follow = (await request('/api/follow', {method: 'POST', token: tokenB, body: {target: phoneA, action: 'follow'}})).data;
  check(follow.following === true, 'akun B dapat mengikuti akun A');
  const unfollow = (await request('/api/follow', {method: 'POST', token: tokenB, body: {target: phoneA, action: 'unfollow'}})).data;
  check(unfollow.following === false, 'akun B dapat berhenti mengikuti akun A');

  // Verify archive/restore persistence, then restore so no test post remains hidden.
  const archive = (await request('/api/posts/archive', {method: 'POST', token: tokenA, body: {postId: publicPost.id, action: 'archive'}})).data;
  check(archive.archived === true, 'pemilik dapat mengarsipkan postingan');
  const archiveList = (await request('/api/posts/archive', {token: tokenA})).data.posts || [];
  check(archiveList.some(p => p.id === publicPost.id), 'postingan ada pada arsip privat pemilik');
  const restore = (await request('/api/posts/archive', {method: 'POST', token: tokenA, body: {postId: publicPost.id, action: 'restore'}})).data;
  check(restore.archived === false, 'pemilik dapat memulihkan postingan dari arsip');

  console.log(`\nBerhasil: ${passed} pemeriksaan API.`);
} catch (error) {
  console.error(`\nGAGAL setelah ${passed} pemeriksaan: ${error.message}`);
  process.exitCode = 1;
} finally {
  if (tokenA && privacyAChanged) {
    try { await request('/api/settings/privacy', {method: 'POST', token: tokenA, body: {directMessages: originalDirectMessagesA}}); } catch {}
  }
  if (tokenA && contactAtoBAdded) {
    try { await request('/api/contacts', {method: 'POST', token: tokenA, body: {action: 'delete', phone: phoneB}}); } catch {}
  }
  if (tokenB) {
    // Remove any test-post bookmarks left behind if a check failed midway.
    try {
      const savedPosts = (await request('/api/posts/saved', {token: tokenB})).data.posts || [];
      for (const id of createdPostsA) {
        if (savedPosts.some(post => post.id === id)) {
          await request('/api/posts/bookmark', {method: 'POST', token: tokenB, body: {postId: id}});
        }
      }
    } catch { /* cleanup is best-effort if server connectivity is lost */ }
  }
  if (tokenA) {
    for (const mediaId of createdStickersA) {
      try { await request('/api/stickers/delete', {method: 'POST', token: tokenA, body: {mediaId}}); } catch {}
    }
    for (const musicId of createdMusicA) {
      try { await request('/api/music/delete', {method: 'POST', token: tokenA, body: {musicId}}); } catch {}
    }
    for (const highlightId of createdHighlightsA) {
      try { await request('/api/highlights/delete', {method: 'POST', token: tokenA, body: {id: highlightId}}); } catch {}
    }
    for (const statusId of createdStatusesA) {
      if (tokenB) {
        for (const action of ['unpin', 'unarchive', 'remove']) {
          try { await request('/api/status/collections', {method: 'POST', token: tokenB, body: {statusId, action}}); } catch {}
        }
      }
      try { await request('/api/status/delete', {method: 'POST', token: tokenA, body: {statusId}}); } catch {}
    }
    for (const item of createdCommunities) {
      try { await request(item.kind === 'group' ? '/api/groups/delete' : '/api/channels/delete', {method: 'POST', token: tokenA, body: {id: item.id}}); } catch {}
    }
    for (const id of createdMessagesA) {
      try { await request('/api/messages/delete', {method: 'POST', token: tokenA, body: {id}}); } catch {}
    }
    for (const id of createdPostsA) {
      try { await request('/api/posts/archive', {method: 'POST', token: tokenA, body: {postId: id, action: 'restore'}}); } catch {}
      try { await request('/api/posts/delete', {method: 'POST', token: tokenA, body: {postId: id}}); } catch {}
    }
  }
}
