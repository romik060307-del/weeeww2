/* OHLI Social shell. Uses the existing authenticated RKM runtime through window.ohliRuntime. */
(function () {
  'use strict';

  var rt = window.ohliRuntime;
  if (!rt) { console.error('OHLI Social runtime bridge is unavailable.'); return; }

  var soundOn = localStorage.getItem('ohli:social-reel-sound') !== 'off', audioUnlocked = (function(){try{return sessionStorage.getItem('ohli:social-audio-unlocked') === 'on';}catch(e){return false;}})(), reelAutoplay = localStorage.getItem('ohli:social-reel-autoplay') !== 'off', transparentTheme = localStorage.getItem('ohli:social-transparent-reels') === 'on', reelNavBackground = localStorage.getItem('ohli:social-reel-nav-bg') === 'transparent' ? 'transparent' : 'black', antiZoom = localStorage.getItem('ohli:social-anti-zoom') === 'on', reelCommentsId = null, reelMode = 'foryou', needGesture = false, avatarVer = Date.now(), holdSpeedTimer = null, holdSpeedVideo = null, activeReelVideo = null;
  var icons = {
    play: '<path d="M7 4.5v15l13-7.5Z"/>', sound: '<path d="M4 9.5v5h4l5 4v-13l-5 4Z"/><path d="M16.5 9a4 4 0 0 1 0 6M19 6.5a8 8 0 0 1 0 11"/>', mute: '<path d="M4 9.5v5h4l5 4v-13l-5 4Z"/><path d="m17 9.5 5 5M22 9.5l-5 5"/>',
    home: '<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z"/>',
    video: '<rect x="3" y="5" width="13" height="14" rx="3"/><path d="m16 10 5-3v10l-5-3Z"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    story: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5.3"/><circle cx="12" cy="12" r="1.5"/>',
    search: '<circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.5 4.5"/>',
    live: '<circle cx="12" cy="12" r="2.5"/><path d="M7 7a7 7 0 0 0 0 10M17 7a7 7 0 0 1 0 10M4 4a11.3 11.3 0 0 0 0 16M20 4a11.3 11.3 0 0 1 0 16"/>',
    chat: '<path d="M21 11.5a8.5 8.5 0 0 1-12.3 7.6L4 20l.9-4.9A8.5 8.5 0 1 1 21 11.5Z"/><path d="M8 10h8M8 13.5h5"/>',
    contacts: '<circle cx="9" cy="8" r="3.2"/><path d="M3 20c.3-3.2 2.4-5 6-5s5.7 1.8 6 5M17 8h4M19 6v4M17 15c2.1.4 3.4 1.8 3.7 4"/>',
    groups: '<circle cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2.4"/><path d="M2.8 20c.3-3.7 2.4-5.5 6.2-5.5s5.9 1.8 6.2 5.5M16 14.5c3.1.1 4.8 1.8 5.2 4.7"/>',
    channel: '<path d="M4 9v6M8 6l12-3v18L8 18M8 6v12M4 9H2v6h2M8 18l2 4h4l-3-4"/>',
    bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
    music: '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>',
    profile: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1 1.4 2.4-2.4 2.4-2.5-1.4a7.9 7.9 0 0 1-2 .8l-.7 2.7h-3.4l-.7-2.7a7.9 7.9 0 0 1-2-.8l-2.5 1.4-2.4-2.4 1.4-2.5a7.9 7.9 0 0 1-.8-2L1.2 12v-3.4l2.7-.7a7.9 7.9 0 0 1 .8-2L3.3 3.4 5.7 1 8.2 2.4a7.9 7.9 0 0 1 2-.8L10.9-1h3.4l.7 2.7a7.9 7.9 0 0 1 2 .8l2.5-1.4 2.4 2.4-1.4 2.5a7.9 7.9 0 0 1 .8 2l2.7.7V12l-2.7.7a7.9 7.9 0 0 1-.9 2.3Z"/>',
    image: '<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8.5" cy="9" r="1.5"/><path d="m4 17 5-4 3.2 2.5 3.1-3 4.7 4"/>',
    send: '<path d="m21 3-7 18-4-7-7-4Z"/><path d="M21 3 10 14"/>',
    heart: '<path d="M20.8 8.8c0 5.1-8.8 11-8.8 11s-8.8-5.9-8.8-11A4.8 4.8 0 0 1 12 6.2a4.8 4.8 0 0 1 8.8 2.6Z"/>',
    comment: '<path d="M21 11.5a8.5 8.5 0 0 1-12.3 7.6L4 20l.9-4.9A8.5 8.5 0 1 1 21 11.5Z"/>',
    bookmark: '<path d="M6 4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v17l-6-4-6 4Z"/>',
    share: '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.7 10.6 6.6-4.2M8.7 13.4l6.6 4.2"/>',
    arrow: '<path d="M5 12h14M13 5l7 7-7 7"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    moon: '<path d="M20.5 14.1A8.5 8.5 0 0 1 9.9 3.5 8.6 8.6 0 1 0 20.5 14.1Z"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
    userPlus: '<circle cx="9" cy="8" r="3"/><path d="M3 20c.3-3.5 2.3-5 6-5s5.7 1.5 6 5M19 8v6M16 11h6"/>',
    refresh: '<path d="M20 7v5h-5M4 17v-5h5"/><path d="M5.5 9A7 7 0 0 1 18 6l2 1M4 17l2-1a7 7 0 0 0 12.5-3"/>',
    shield: '<path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Z"/><path d="m9 12 2 2 4-4"/>',
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>'
  };
  function ico(name) { return '<svg viewBox="0 0 24 24" aria-hidden="true">' + (icons[name] || icons.home) + '</svg>'; }
  function esc(value) { return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) { return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]); }); }
  function shortDate(value) { var d = new Date(Number(value) || value); if (isNaN(d.getTime())) return ''; var delta = Date.now() - d.getTime(); if (delta < 60000) return 'baru saja'; if (delta < 3600000) return Math.max(1, Math.floor(delta / 60000)) + ' mnt'; if (delta < 86400000) return Math.floor(delta / 3600000) + ' jam'; return d.toLocaleDateString('id-ID', {day:'numeric',month:'short'}); }
  function initial(name) { var words = String(name || 'OHLI').trim().split(/\s+/); return ((words[0] || 'O').charAt(0) + (words.length > 1 ? words[words.length - 1].charAt(0) : '')).toUpperCase(); }
  function avatar(name, phone, sizeClass) { var img = (phone && rt && rt.getToken && rt.getToken()) ? '<img src="/api/social-avatar?phone=' + encodeURIComponent(phone) + '&token=' + encodeURIComponent(rt.getToken()) + '&v=' + avatarVer + '" alt="" loading="lazy" onerror="this.remove()">' : ''; return '<span class="ohli-avatar ' + (sizeClass || '') + '" aria-hidden="true"><b>' + esc(initial(name || 'OHLI')) + '</b>' + img + '</span>'; }
  function socialHandle(user) { var raw=(user&&user.username)||((user&&user.name)||'').toLowerCase(); var slug=String(raw).normalize ? String(raw).normalize('NFKD').replace(/[\u0300-\u036f]/g,'') : String(raw); slug=slug.toLowerCase().replace(/[^a-z0-9._]+/g,'.').replace(/^[._]+|[._]+$/g,'').replace(/[._]{2,}/g,'.').slice(0,24); return slug||'pengguna-ohli'; }
  var SOCIAL_HISTORY_KEY='ohli:social-activity-history',SOCIAL_PENDING_KEY='ohli:social-activity-pending',socialHistoryDisplay=[];
  function getSocialHistory(){try{var v=JSON.parse(localStorage.getItem(SOCIAL_HISTORY_KEY)||'[]');return Array.isArray(v)?v:[];}catch(e){return[];}}
  function getPendingSocialHistory(){try{var v=JSON.parse(localStorage.getItem(SOCIAL_PENDING_KEY)||'[]');return Array.isArray(v)?v:[];}catch(e){return[];}}
  function savePendingSocialHistory(items){try{localStorage.setItem(SOCIAL_PENDING_KEY,JSON.stringify(items.slice(0,300)));}catch(e){}}
  var socialHistoryFlushPromise=null;
  function flushSocialHistory(){if(socialHistoryFlushPromise)return socialHistoryFlushPromise;socialHistoryFlushPromise=(async function(){while(true){var pending=getPendingSocialHistory();if(!pending.length)break;var item=pending[0];try{await rt.request('/api/activity/history',{method:'POST',body:{type:item.type,id:item.id||'',label:item.label||'Aktivitas OHLI',query:item.query||'',clientId:item.clientId||''}});var latest=getPendingSocialHistory().filter(function(x){return x.clientId!==item.clientId;});savePendingSocialHistory(latest);}catch(e){break;}}return getPendingSocialHistory().length;})();socialHistoryFlushPromise=socialHistoryFlushPromise.then(function(v){socialHistoryFlushPromise=null;return v;},function(){socialHistoryFlushPromise=null;return getPendingSocialHistory().length;});return socialHistoryFlushPromise;}
  function recordSocialActivity(type,id,label,extra){var now=Date.now(),key=String(id||''),item={type:type,id:key,label:String(label||'Aktivitas OHLI').slice(0,120),time:now,clientId:'ohac_'+now.toString(36)+'_'+Math.random().toString(36).slice(2,12)};if(extra&&typeof extra==='object'){if(extra.query)item.query=String(extra.query).slice(0,80);if(extra.page)item.page=String(extra.page);}var list=getSocialHistory();if(list.length&&list[0].type===type&&String(list[0].id||'')===key&&now-Number(list[0].time||0)<60000){list[0]=item;}else list.unshift(item);try{localStorage.setItem(SOCIAL_HISTORY_KEY,JSON.stringify(list.slice(0,300)));}catch(e){}var pending=getPendingSocialHistory();pending.push(item);savePendingSocialHistory(pending);flushSocialHistory().catch(function(){});}
  async function syncSocialHistory(){try{await flushSocialHistory();var d=await rt.request('/api/activity/history'),rows=(d.activities||[]).map(function(x){return {type:x.type,id:x.itemId||'',label:x.label||'Aktivitas OHLI',query:x.query||'',time:Number(x.time)||Date.now(),mediaId:x.mediaId||'',mediaType:x.mediaType||'',mimeType:x.mimeType||'',post:x.post||null,storyAvailable:!!x.storyAvailable};}),pending=getPendingSocialHistory().map(function(x){return {type:x.type,id:x.id||'',label:x.label||'Aktivitas OHLI',query:x.query||'',time:Number(x.time)||Date.now(),mediaId:'',post:null};}),by={};rows.concat(pending).forEach(function(x){var k=[x.type,x.id,x.query,x.time].join('|');by[k]=x;});rows=Object.keys(by).map(function(k){return by[k];}).sort(function(a,b){return Number(b.time||0)-Number(a.time||0);}).slice(0,300);localStorage.setItem(SOCIAL_HISTORY_KEY,JSON.stringify(rows));return rows;}catch(e){return getSocialHistory();}}
  if(typeof window!=='undefined'&&window.addEventListener)window.addEventListener('online',function(){flushSocialHistory().catch(function(){});});
  function recentSearchMarkup(){var recent=getSocialHistory().filter(function(x){return x.type==='search'&&x.query;}).filter(function(x,i,a){return a.findIndex(function(y){return y.query===x.query;})===i;}).slice(0,6);if(!recent.length)return '';return '<div class="ohli-recent-searches"><span>Pencarian terakhir</span>'+recent.map(function(x){return '<button type="button" class="ohli-recent-search" data-action="history-search" data-query="'+esc(x.query)+'">'+ico('search')+esc(x.query)+'</button>';}).join('')+'</div>'; }
  async function openSocialHistory(){var list=await syncSocialHistory(),labels={search:'Pencarian',profile:'Profil dilihat',post:'Postingan dilihat',photo:'Foto dilihat',video:'Video pendek ditonton',story:'Story dilihat',video_complete:'Video ditonton sampai selesai',video_skip:'Video dilewati',not_interested:'Tidak tertarik'};socialHistoryDisplay=list;var mediaItems=list.map(function(x,i){x._historyIndex=i;return x;}).filter(function(x){return !!x.mediaId&&(!!x.post||x.storyAvailable||x.type==='story');}).slice(0,180);var mediaHtml=mediaItems.length?'<section class="ohli-history-section"><div class="ohli-history-section-title">Foto & video yang pernah dilihat</div><div class="ohli-history-grid">'+mediaItems.map(function(x){var video=x.mediaType==='video'||String(x.mimeType||'').indexOf('video/')===0,url=x.post?rt.mediaUrl(x.mediaId):(video?rt.mediaUrl(x.mediaId):rt.imageUrl(x.mediaId));return '<button type="button" class="ohli-history-tile" data-action="history-open" data-history-index="'+x._historyIndex+'" aria-label="Buka '+esc(x.label)+'"><span class="ohli-history-square">'+(video?'<video src="'+esc(url)+'#t=0.1" muted playsinline preload="metadata"></video>':'<img src="'+esc(url)+'" alt="" loading="lazy" decoding="async">')+'<span class="ohli-history-media-badge">'+(video?ico('play'):ico('image'))+'</span></span><span class="ohli-history-tile-copy"><strong>'+esc(x.label||'Konten OHLI')+'</strong><small>'+esc(new Date(x.time).toLocaleString('id-ID',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}))+'</small></span></button>';}).join('')+'</div></section>':'<p class="ohli-hint">Belum ada foto atau video yang dapat diputar ulang. Konten yang sudah dihapus atau aksesnya dicabut tidak akan ditampilkan.</p>';var activityItems=list.slice(0,250);var activityHtml=activityItems.length?'<section class="ohli-history-section"><div class="ohli-history-section-title">Semua aktivitas</div><div class="ohli-history-list">'+activityItems.map(function(x,i){return '<button type="button" class="ohli-history-row" '+(x.mediaId?'data-action="history-open" data-history-index="'+i+'"':'')+'><span class="ohli-history-icon">'+ico(x.type==='search'?'search':(x.type==='video'||x.type==='video_complete'||x.type==='video_skip'?'video':(x.type==='photo'?'image':(x.type==='profile'?'profile':'home'))))+'</span><span class="ohli-history-copy"><strong>'+esc(x.label)+'</strong><small>'+esc(labels[x.type]||'Aktivitas')+' · '+esc(new Date(x.time).toLocaleString('id-ID',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'}))+'</small></span>'+(x.mediaId?ico('arrow'):'')+'</button>';}).join('')+'</div></section>':'<p class="ohli-hint">Belum ada aktivitas yang tercatat pada akun ini.</p>';showModal('Riwayat aktivitas','<p class="ohli-hint">Riwayat pencarian, profil, postingan, Story, foto, video, dan aktivitas tontonan tersimpan pada akun ini. Thumbnail berbentuk kotak 1:1; konten hanya bisa diputar ulang selama file masih ada dan izin aksesnya masih berlaku.</p>'+mediaHtml+activityHtml+(list.length?'<button type="button" class="ohli-btn ohli-btn-danger" data-action="clear-history">Hapus riwayat aktivitas</button>':''),'',null);} 
  function openHistoryMedia(item){if(!item||!item.mediaId){toast('Media riwayat tidak tersedia.');return;}if(item.post){openMedia(item.post);return;}var video=item.mediaType==='video'||String(item.mimeType||'').indexOf('video/')===0,url=video?rt.mediaUrl(item.mediaId):rt.imageUrl(item.mediaId),body=video?'<video src="'+esc(url)+'" controls autoplay playsinline style="width:100%;max-height:72vh;background:#000;border-radius:14px"></video>':'<img src="'+esc(url)+'" alt="Riwayat Story" loading="eager" style="width:100%;max-height:72vh;object-fit:contain;border-radius:14px">';showModal(item.label||'Riwayat tontonan',body+(item.query?'<p>'+esc(item.query)+'</p>':''),'',null);var v=overlayContent&&overlayContent.querySelector('video');if(v){var p=v.play();if(p&&p.catch)p.catch(function(){});}}

  function empty(icon, title, description, actionLabel, action) { return '<div class="ohli-empty"><div class="ohli-empty-icon">' + ico(icon || 'home') + '</div><h3>' + esc(title) + '</h3><p>' + esc(description) + '</p>' + (actionLabel ? '<div style="margin-top:16px"><button type="button" class="ohli-btn ohli-btn-primary" data-action="' + esc(action || '') + '">' + esc(actionLabel) + '</button></div>' : '') + '</div>'; }

  var shell, pageContent, pageTitle, pageDescription, pageActions, nav, searchInput, toastBox, overlay, overlayContent;
  var highlightThumbShape=localStorage.getItem('ohli:highlight-thumb-shape')||'circle', highlightThumbFit=localStorage.getItem('ohli:highlight-thumb-fit')||'cover';
  var musicViewMode = 'catalog', musicCache = [], pendingMusicTrack = null, socialActivityObserver = null, socialObservedRoot = null;
  var replyingComment = null, storyHoldConsumed=false, commentHoldTimer=null, commentHoldConsumed=false, profileVideoLayout = localStorage.getItem('ohli:social-profile-video-layout') || 'limited';
  var page = 'home', lastSocialPage = 'home', pageHistory = [], pageQuery = '', archiveMode = 'archive', posts = [], nextBefore = 0, hasMore = false, users = [], statusList = [], storyHistoryCache = [], groupItems = [], channelItems = [], notificationItems = [], profileTarget = '', profileData = null, openComments = {}, selectedFile = null, selectedStatusFile = null, videoObserver = null, storyIndex = 0, storyItems = [], storyGroups = [], storyGroupIndex = 0, activeHighlight = null, theme = 'light', liquidGlass = false, audioUnlockTap = false, videoSearchReturn = false, storyTimer = null, storyProgressTimer = null, storyOpenedAt = 0, storyDuration = 0, storyElapsed = 0, storyProgressStarted = 0, storyPaused = false, storyHoldTimer = null, storyHoldActive = false, avatarHoldTimer = null, avatarLongPressConsumed = false, presenceHeartbeatTimer = null, themePrefs = {}, cr = null, chatOpenRequest = 0;
  var pageTitles = {create:['Buat konten','Pilih foto atau video, edit, lalu bagikan.'],home:['Beranda','Temukan kabar, video, dan percakapan dari orang yang kamu ikuti.'],videos:['Video pendek','Tonton satu per satu; video dijeda saat keluar dari layar.'],story:['Story & pembaruan','Cerita yang masih aktif dari akun yang boleh kamu lihat.'],explore:['Jelajahi','Cari pengguna dan postingan publik dari server.'],live:['Live','Siaran langsung yang sedang aktif di server.'],contacts:['Kontak','Temukan orang dan lanjutkan percakapan pribadi.'],friends:['Teman','Kelola permintaan pertemanan dan orang yang terhubung denganmu.'],groups:['Grup & komunitas','Temukan ruang komunitas dan kelola keanggotaan.'],channels:['Channels','Ikuti kanal pengumuman satu-ke-banyak.'],notifications:['Notifikasi','Aktivitas akun yang tersimpan di server.'],profile:['Profil','Informasi akun dan postingan yang dapat kamu lihat.'],settings:['Pengaturan','Kelola tampilan, akun, privasi, dan koneksi.'],archive:['Arsip & tersimpan','Kelola postingan yang diarsipkan dan koleksi pribadi.'],music:['Musik','Jelajahi audio, simpan favorit, dan gunakan musik pada video.']};
  var navItems = [
    ['home','Beranda','home'],['videos','Video pendek','video'],['create','Buat konten','plus'],['story','Story','story'],['explore','Jelajahi','search'],['live','Live','live'],['chat','Chat','chat'],['contacts','Kontak','contacts'],['friends','Teman','contacts'],['groups','Grup & komunitas','groups'],['channels','Channels','channel'],['notifications','Notifikasi','bell'],['music','Musik','music'],['archive','Arsip & tersimpan','bookmark'],['profile','Profil','profile'],['settings','Pengaturan','settings']
  ];

  function makeShell() {
    shell = document.createElement('section'); shell.id = 'ohliSocialShell'; shell.hidden = true; shell.setAttribute('aria-label','OHLI Social'); shell.dataset.highlightShape=['circle','rounded','square'].indexOf(highlightThumbShape)>=0?highlightThumbShape:'circle'; shell.dataset.highlightFit=['cover','contain','top'].indexOf(highlightThumbFit)>=0?highlightThumbFit:'cover';
    shell.innerHTML = '<header class="ohli-topbar"><button type="button" class="ohli-brand" data-action="home" aria-label="OHLI Social, beranda"><span class="ohli-logo"></span><span><strong>OHLI Social</strong><span>Satu aplikasi, banyak cerita.</span></span></button><form class="ohli-global-search" id="ohliGlobalSearch" role="search">' + ico('search') + '<input id="ohliSearchInput" type="search" placeholder="Cari orang, postingan, topik…" aria-label="Pencarian global"><span class="ohli-sr-only">Tekan Enter untuk mencari</span></form><div class="ohli-topbar-spacer"></div><div class="ohli-status-pill"><span class="ohli-status-dot"></span><span id="ohliConnectionLabel">Menghubungkan…</span></div><button type="button" class="ohli-mobile-menu" data-action="more-menu" aria-label="Menu lain">' + ico('menu') + '</button><button type="button" class="ohli-topbar-notifications" data-action="navigate" data-page="notifications" aria-label="Notifikasi">' + ico('bell') + '</button><button type="button" class="ohli-user-top" data-action="my-profile" aria-label="Buka profil">' + avatar('OHLI','') + '<span class="ohli-user-label"><strong id="ohliTopName">Akun OHLI</strong><small id="ohliTopPhone"></small></span></button></header><aside class="ohli-sidebar" id="ohliGlobalNav" aria-label="Navigasi OHLI"><div class="ohli-nav-caption">Menu utama</div><div id="ohliPrimaryNav"></div><div class="ohli-nav-caption">Ruang sosial</div><div id="ohliCommunityNav"></div><div class="ohli-nav-spacer"></div><button type="button" class="ohli-theme-button" id="ohliThemeToggle">' + ico('sun') + '<span>Ganti tema</span></button><div class="ohli-nav-foot">OHLI Social<br><span>Komunikasi dan komunitas, dalam satu ruang.</span></div></aside><main class="ohli-stage"><div class="ohli-stage-head"><button type="button" class="ohli-internal-back" data-action="internal-back" aria-label="Kembali ke beranda">‹</button><div><h1 id="ohliPageTitle">Beranda</h1><p id="ohliPageDescription"></p></div><div class="ohli-stage-actions" id="ohliPageActions"></div></div><div id="ohliPageContent" class="ohli-page-content"></div></main><div class="ohli-modal-backdrop" id="ohliModalBackdrop" hidden><section class="ohli-modal" role="dialog" aria-modal="true" aria-labelledby="ohliModalTitle"><div class="ohli-modal-head"><h2 id="ohliModalTitle">OHLI Social</h2><button class="ohli-modal-close" type="button" data-action="close-modal" aria-label="Tutup">×</button></div><div id="ohliModalContent"></div></section></div><div class="ohli-story-viewer" id="ohliStoryViewer" hidden><section class="ohli-story-screen"><div class="ohli-story-progress"><span></span></div><div class="ohli-story-owner" id="ohliStoryOwner"></div><div class="ohli-story-media" id="ohliStoryMedia"></div><div class="ohli-story-foot" id="ohliStoryFoot"></div></section></div><div class="ohli-toast" id="ohliToast" hidden role="status" aria-live="polite"></div><input id="ohliPostFile" class="ohli-composer-file" type="file" accept="image/*,video/*"><input id="ohliStatusFile" class="ohli-composer-file" type="file" accept="image/*,video/*"><input id="ohliCameraFile" class="ohli-composer-file" type="file" accept="image/*,video/*" capture="environment">';
    document.body.insertBefore(shell, document.getElementById('modalBackdrop'));
    pageContent = shell.querySelector('#ohliPageContent'); pageTitle = shell.querySelector('#ohliPageTitle'); pageDescription = shell.querySelector('#ohliPageDescription'); pageActions = shell.querySelector('#ohliPageActions'); nav = shell.querySelector('#ohliGlobalNav'); searchInput = shell.querySelector('#ohliSearchInput'); toastBox = shell.querySelector('#ohliToast'); overlay = shell.querySelector('#ohliModalBackdrop'); overlayContent = shell.querySelector('#ohliModalContent');
    var pnav = shell.querySelector('#ohliPrimaryNav'), cnav = shell.querySelector('#ohliCommunityNav');
    navItems.forEach(function (item) {
      var button = document.createElement('button'); button.type = 'button'; button.className = 'ohli-nav-link'; button.dataset.page = item[0]; button.dataset.action = 'navigate'; button.innerHTML = ico(item[2]) + '<span>' + esc(item[1]) + '</span>';
      if (['home','videos','create','explore','profile'].indexOf(item[0]) >= 0) button.setAttribute('data-mobile-primary','true');
      if (['home','videos','create','story','explore','live','chat','notifications'].indexOf(item[0]) >= 0) pnav.appendChild(button); else cnav.appendChild(button);
    });
    shell.querySelector('#ohliGlobalSearch').addEventListener('submit', function (event) { event.preventDefault(); videoSearchReturn=false; pageQuery = searchInput.value.trim(); if(pageQuery)recordSocialActivity('search',pageQuery,pageQuery,{query:pageQuery}); setPage('explore'); });
    shell.querySelector('#ohliThemeToggle').addEventListener('click', toggleTheme);
    shell.addEventListener('click', handleClick);
    shell.addEventListener('submit', handleSubmit);
    shell.addEventListener('change', handleChange);
    shell.addEventListener('pointerdown', onReelHoldStart, {passive:true});
    shell.addEventListener('pointerdown', onStoryHoldStart, {passive:true});
    shell.addEventListener('pointerdown', onCommentHoldStart, {passive:true});
    shell.addEventListener('pointerdown', onAvatarHoldStart, {passive:true});
    document.addEventListener('pointerup', onReelHoldEnd, {passive:true});
    document.addEventListener('pointercancel', onReelHoldEnd, {passive:true});
    document.addEventListener('pointerup', onStoryHoldEnd, {passive:true});
    document.addEventListener('pointercancel', onStoryHoldEnd, {passive:true});
    document.addEventListener('pointerup', onCommentHoldEnd, {passive:true});
    document.addEventListener('pointercancel', onCommentHoldEnd, {passive:true});
    document.addEventListener('pointerup', onAvatarHoldEnd, {passive:true});
    document.addEventListener('pointercancel', onAvatarHoldEnd, {passive:true});
    shell.addEventListener('contextmenu',function(e){if(e.target.closest('.ohli-reel video,.ohli-vwrap video,.ohli-post-media[data-ohli-video],#ohliStoryMedia video')){e.preventDefault();return false;}},true);
    document.addEventListener('visibilitychange', function(){if(document.hidden){onReelHoldEnd();onStoryHoldEnd();onCommentHoldEnd();onAvatarHoldEnd();}});
    shell.querySelector('#ohliStoryViewer').addEventListener('click', function (event) { if (event.target.id === 'ohliStoryViewer') closeStory(); });
    shell.querySelector('#ohliStoryMedia').addEventListener('click',function(event){if(storyHoldConsumed||event.target.closest('button,a'))return;var r=this.getBoundingClientRect(),x=event.clientX-r.left;if(x<r.width*.32)stepStory(-1);else if(x>r.width*.68)stepStory(1);});
    shell.querySelector('#ohliModalBackdrop').addEventListener('click', function (event) { if (event.target === overlay) closeModal(); });
    addLegacyReturnButton();
    if(!presenceHeartbeatTimer)presenceHeartbeatTimer=setInterval(function(){if(shell&&!shell.hidden){try{rt.request('/api/presence/heartbeat',{method:'POST',body:{}}).catch(function(){});}catch(ignorePresenceTick){}}},45000);
    theme = (rt.getTheme && rt.getTheme()==='dark') ? 'dark' : 'light'; liquidGlass=false; applyTheme(); syncViewportZoom();
  }

  function addLegacyReturnButton() {
    var sideHead = document.querySelector('#app .side-head'); if (!sideHead || sideHead.querySelector('#ohliBackSocial')) return;
    var button = document.createElement('button'); button.type = 'button'; button.id = 'ohliBackSocial'; button.className = 'ohli-legacy-return'; button.innerHTML = ico('arrow') + '<span>OHLI Social</span>'; button.title = 'Kembali ke OHLI Social';
    button.addEventListener('click', function () { showShell(lastSocialPage || 'home'); }); sideHead.insertBefore(button, sideHead.firstChild);
  }

  function setShellVisible(visible) {
    shell.hidden = !visible;
    var app = document.getElementById('app'); if (app) app.hidden = visible;
    if (visible) { var me = rt.getMe(); if (me) { var topName=String(me.name||'').trim(),digits=topName.replace(/\D/g,''); if(digits.length>=8&&digits.length<=15&&digits===String(me.phone||'').replace(/\D/g,''))topName=me.username?'@'+me.username:'Akun OHLI'; shell.querySelector('#ohliTopName').textContent = topName || (me.username?'@'+me.username:'Akun OHLI'); shell.querySelector('#ohliTopPhone').textContent = '';  try{rt.request('/api/presence/heartbeat',{method:'POST',body:{}}).catch(function(){});}catch(ignorePresence){} } }
  }
  function showShell(targetPage, extra) { setShellVisible(true); setPage(targetPage || page || 'home', extra); }
  function goLegacy(which) {
    shell.hidden = true; var app = document.getElementById('app'); app.hidden = false; app.classList.remove('page-open','chat-open');
    if (which === 'contacts') rt.openContacts(); else if (which === 'calls') rt.openCalls(); else rt.openChat();
    var returnBtn = document.getElementById('ohliBackSocial'); if (returnBtn) returnBtn.style.display = '';
  }
  function rememberPageState() {
    return {page:page, phone:page==='profile'?profileTarget:'', query:pageQuery, archiveMode:archiveMode, feedMode:currentFeedMode, musicMode:musicViewMode};
  }
  function restorePageState(state) {
    if(!state)return;
    pageQuery=state.query||''; archiveMode=state.archiveMode||'archive'; currentFeedMode=state.feedMode||'public'; musicViewMode=state.musicMode||'catalog';
    setPage(state.page||'home',state.phone?{phone:state.phone}:undefined,{skipHistory:true});
  }
  function setPage(next, extra, options) {
    options=options||{}; next=next||'home';
    if(!options.skipHistory && page && page!==next && page!=='chat' && page!=='contacts' && page!=='calls') {
      pageHistory.push(rememberPageState()); if(pageHistory.length>40)pageHistory.shift();
    }
    if (next === 'chat') { if(page==='videos')pauseAllReels(); lastSocialPage = (page === 'chat' || page === 'contacts' || page === 'calls') ? lastSocialPage : page; page = 'chat'; goLegacy('chat'); return; }
    if (next === 'contacts') { if(page==='videos')pauseAllReels(); lastSocialPage = (page === 'chat' || page === 'contacts' || page === 'calls') ? lastSocialPage : page; page = 'contacts'; goLegacy('contacts'); return; }
    if (next === 'calls') { if(page==='videos')pauseAllReels(); lastSocialPage = (page === 'chat' || page === 'contacts' || page === 'calls') ? lastSocialPage : page; page = 'calls'; goLegacy('calls'); return; }
    var previousPage = page;
    page = next;
    if (previousPage === 'videos' && page !== 'videos') pauseAllReels();
    if (page === 'profile') profileTarget = (extra && extra.phone) ? extra.phone : ((rt.getMe() || {}).phone || '');
    else if (extra && extra.phone) profileTarget = extra.phone;
    setShellVisible(true);
    var backControl = shell.querySelector('.ohli-internal-back'); if (backControl) backControl.hidden = (page === 'home' && !pageHistory.length);
    shell.classList.toggle('ohli-full-reels', page === 'videos');
    shell.querySelectorAll('.ohli-nav-link').forEach(function (button) { button.classList.toggle('active', button.dataset.page === page || (page === 'profile' && button.dataset.page === 'profile')); });
    var meta = pageTitles[page] || pageTitles.home; pageTitle.textContent = meta[0]; pageDescription.textContent = meta[1]; pageActions.innerHTML = '';
    if (page === 'profile') pageActions.innerHTML = '<button class="ohli-btn" type="button" data-action="edit-profile">' + ico('settings') + ' Edit profil</button>';
    applyReelPreferences();
    renderPage();
  }
  function applyTheme() { if (!shell) return; theme = (rt.getTheme && ['dark','orange'].indexOf(rt.getTheme())>=0) ? rt.getTheme() : 'light'; var dark=theme==='dark',orange=theme==='orange',accent=orange?'#d6bd42':'#171713'; shell.classList.toggle('ohli-dark',dark);shell.classList.toggle('ohli-orange',orange);shell.classList.remove('ohli-liquid-glass');shell.style.setProperty('--ohli-blue',dark?'#efdb69':accent);shell.style.setProperty('--ohli-violet',dark?'#efdb69':'#d6bd42');shell.style.setProperty('--ohli-accent',dark?'#efdb69':'#e4ca42');shell.style.setProperty('--ohli-ink',dark?'#f4f1e5':'#1a1a16');shell.style.setProperty('--ohli-canvas',dark?'#171815':'#f1f0e8');shell.style.setProperty('--ohli-surface',dark?'#22231f':'#fffef8');shell.style.setProperty('--ohli-muted',dark?'#aaa797':'#77766d');shell.style.setProperty('--ohli-line',dark?'#383930':'#e6e2d6');shell.style.setProperty('--ohli-font-scale',String((themePrefs&&themePrefs.fontScale)||1));shell.style.setProperty('--ohli-density-gap','15px');var btn=shell.querySelector('#ohliThemeToggle');if(btn){btn.style.display='';btn.innerHTML=ico(dark?'sun':'moon')+'<span>'+(dark?'Mode terang':(orange?'Tema hangat':'Mode malam'))+'</span>'; }document.documentElement.dataset.ohliTheme=theme; applyCustomThemeColors(); }
  function toggleTheme(next) { var wanted=['dark','orange','light'].indexOf(next)>=0?next:(theme==='dark'?'light':'dark'); if(rt.setTheme)rt.setTheme(wanted); theme=wanted; applyTheme(); toast(wanted==='dark'?'Mode malam aktif.':(wanted==='orange'?'Tema Orange aktif.':'Mode terang aktif.')); if(page==='settings')renderSettings(); }
  function syncViewportZoom() {
    var meta = document.querySelector('meta[name="viewport"]');
    if (!meta) return;
    meta.setAttribute('content', antiZoom
      ? 'width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover,interactive-widget=resizes-content'
      : 'width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes,viewport-fit=cover,interactive-widget=resizes-content');
  }
  function applyReelPreferences() {
    if (!shell) return;
    shell.classList.remove('ohli-transparent-reels');
    shell.classList.toggle('ohli-reels-nav-transparent', reelNavBackground === 'transparent' && page === 'videos');
    shell.classList.toggle('ohli-reels-nav-black', reelNavBackground === 'black' && page === 'videos');
    shell.querySelectorAll('[data-action="toggle-sound"]').forEach(function (b) {
      b.innerHTML = ico(soundOn && audioUnlocked ? 'sound' : 'mute');
      b.setAttribute('aria-label', soundOn ? 'Matikan suara video' : 'Nyalakan suara video');
      b.title = soundOn ? 'Suara aktif' : 'Suara nonaktif';
    });
    refreshAudioButtons();
    syncViewportZoom();
  }
  function pauseAllReels() {
    if (!shell) return;
    shell.querySelectorAll('.ohli-reel video').forEach(function (v) { v.pause(); v.playbackRate = 1; });
    activeReelVideo = null;
  }
  function refreshSettingsControls() {
    var map = {
      'toggle-reel-sound': ['Suara Reels', soundOn],
      'toggle-reel-autoplay': ['Autoplay video pendek', reelAutoplay],
      'toggle-anti-zoom': ['Kunci layar web / Anti-zoom', antiZoom]
    };
    Object.keys(map).forEach(function (action) {
      var b = shell && shell.querySelector('[data-action="' + action + '"]');
      if (b) { b.innerHTML = '<span>' + map[action][0] + '</span><strong>' + (map[action][1] ? 'ON' : 'OFF') + '</strong>'; b.classList.toggle('is-on', !!map[action][1]); }
    });
  }
  function onReelHoldStart(event) {
    if (event.button != null && event.button !== 0) return;
    var reel = event.target && event.target.closest ? event.target.closest('.ohli-reel') : null;
    if (!reel || event.target.closest('button,a,input,textarea,select,.ohli-reel-info')) return;
    var video = reel.querySelector('video');
    if (!video) return;
    var rect = video.getBoundingClientRect();
    if (event.clientX < rect.left + rect.width * 0.55) return;
    clearTimeout(holdSpeedTimer);
    holdSpeedVideo = video;
    holdSpeedTimer = setTimeout(function () {
      if (!holdSpeedVideo || !holdSpeedVideo.isConnected) return;
      holdSpeedVideo.playbackRate = 2;
      var badge = reel.querySelector('.ohli-speed-indicator');
      if (badge) badge.classList.add('is-visible');
      if (navigator.vibrate) { try { navigator.vibrate(20); } catch (ignore) {} }
    }, 420);
  }
  function onReelHoldEnd() {
    clearTimeout(holdSpeedTimer); holdSpeedTimer = null;
    if (holdSpeedVideo) {
      holdSpeedVideo.playbackRate = 1;
      var reel = holdSpeedVideo.closest('.ohli-reel'), badge = reel && reel.querySelector('.ohli-speed-indicator');
      if (badge) badge.classList.remove('is-visible');
    }
    holdSpeedVideo = null;
  }

  function readCustomThemeColors(){try{var c=JSON.parse(localStorage.getItem('ohli:social-custom-colors')||'{}');return{icon:/^#[0-9a-f]{6}$/i.test(c.icon||'')?c.icon:'',text:/^#[0-9a-f]{6}$/i.test(c.text||'')?c.text:''};}catch(e){return{icon:'',text:''};}}
  function applyCustomThemeColors(){var c=readCustomThemeColors(),root=document.documentElement;if(root){root.classList.toggle('ohli-custom-icons',!!c.icon);root.classList.toggle('ohli-custom-text',!!c.text);if(c.icon)root.style.setProperty('--ohli-user-icon-color',c.icon);else root.style.removeProperty('--ohli-user-icon-color');if(c.text)root.style.setProperty('--ohli-user-text-color',c.text);else root.style.removeProperty('--ohli-user-text-color');}if(!shell)return;shell.classList.toggle('ohli-custom-icons',!!c.icon);shell.classList.toggle('ohli-custom-text',!!c.text);if(c.icon)shell.style.setProperty('--ohli-user-icon-color',c.icon);else shell.style.removeProperty('--ohli-user-icon-color');if(c.text)shell.style.setProperty('--ohli-user-text-color',c.text);else shell.style.removeProperty('--ohli-user-text-color');}
  function openThemeCustomizer(){var current=rt.getTheme?rt.getTheme():'light',colors=readCustomThemeColors();showModal('Tema / Tampilan','<div class="ohli-theme-note"><p>Pilih mode tampilan dan atur warna ikon serta tulisan OHLI. Pilihan warna tersimpan di browser/perangkat ini.</p><div class="ohli-theme-choice"><button type="button" class="ohli-btn '+(current==='light'?'ohli-btn-primary':'')+'" data-action="set-theme" data-theme="light">☼ Mode Terang</button><button type="button" class="ohli-btn '+(current==='dark'?'ohli-btn-primary':'')+'" data-action="set-theme" data-theme="dark">☾ Mode Malam</button><button type="button" class="ohli-btn '+(current==='orange'?'ohli-btn-primary':'')+'" data-action="set-theme" data-theme="orange">● Orange</button></div><div class="ohli-custom-color-grid"><label>Warna seluruh ikon<input id="ohliCustomIconColor" type="color" value="'+(colors.icon||'#262626')+'"></label><label>Warna tulisan utama<input id="ohliCustomTextColor" type="color" value="'+(colors.text||'#111111')+'"></label></div><div class="ohli-custom-color-actions"><button type="button" class="ohli-btn" data-action="theme-custom-reset">Reset warna</button><button type="button" class="ohli-btn ohli-btn-primary" data-action="theme-custom-save">Simpan warna</button></div></div>','',null);}
  function readDrafts(){try{return JSON.parse(localStorage.getItem('ohli:social-drafts')||'[]')||[];}catch(e){return[];}}
  function saveLocalDraft(kind,text){text=String(text||'').trim();if(!text){toast('Tulis teks atau caption dulu agar bisa disimpan.');return;}var a=readDrafts();a.unshift({id:'d'+Date.now()+Math.random().toString(16).slice(2,6),kind:kind==='story'?'story':'post',text:text,updatedAt:Date.now()});localStorage.setItem('ohli:social-drafts',JSON.stringify(a.slice(0,40)));toast('Draf tersimpan di perangkat ini. Media perlu dipilih lagi saat melanjutkan.');}
  function renderDrafts(target){var a=readDrafts();target.className='ohli-feed-column';target.innerHTML=a.length?a.map(function(d){return '<article class="ohli-card ohli-draft-card"><div><small>'+(d.kind==='story'?'DRAF STORY':'DRAF POSTINGAN')+' · '+esc(shortDate(d.updatedAt))+'</small><p>'+esc(d.text)+'</p></div><div class="ohli-draft-actions"><button type="button" class="ohli-btn ohli-btn-primary" data-action="use-draft" data-draft-id="'+esc(d.id)+'">Lanjutkan</button><button type="button" class="ohli-btn" data-action="delete-draft" data-draft-id="'+esc(d.id)+'">Hapus</button></div></article>';}).join(''):empty('bookmark','Belum ada draf','Simpan teks Story atau caption dari editor agar dapat dilanjutkan nanti.');}
  function useLocalDraft(id){var a=readDrafts(),d=a.find(function(x){return x.id===id;});if(!d)return;if(d.kind==='story'){setPage('story');var t=shell.querySelector('#ohliStoryText');if(t)t.value=d.text;}else{setPage('create');renderCreate('post');cr.kind='text';crEdit();var c=shell.querySelector('#crCap');if(c)c.value=d.text;}localStorage.setItem('ohli:social-drafts',JSON.stringify(a.filter(function(x){return x.id!==id;})));toast('Draf dibuka kembali.');}
  var toastTimeout = null;
  function toast(message) { toastBox.textContent = String(message || 'Selesai'); toastBox.hidden = false; if (toastTimeout) clearTimeout(toastTimeout); toastTimeout = setTimeout(function () { toastBox.hidden = true; }, 3300); }
  function apiError(error) { return (error && error.message) ? error.message : 'Permintaan belum berhasil. Periksa koneksi server dan coba lagi.'; }
  function showLoading() { pageContent.innerHTML = '<div class="ohli-loading"><div class="ohli-skeleton"></div><div class="ohli-skeleton"></div><div class="ohli-skeleton"></div></div>'; }
  function showError(message, retryAction) { pageContent.innerHTML = '<div class="ohli-error"><strong>Belum dapat memuat data.</strong><div style="margin:6px 0 12px">' + esc(message) + '</div><button type="button" class="ohli-btn" data-action="' + esc(retryAction || 'retry') + '">' + ico('refresh') + ' Coba lagi</button></div>'; }
  function renderPage() {
    if (shell) shell.setAttribute('data-page', page);
    var stg = shell && shell.querySelector('.ohli-stage'); if (stg) { stg.classList.toggle('is-reels', page === 'videos'); stg.scrollTop = 0; }
    if (videoObserver) { videoObserver.disconnect(); videoObserver = null; }
    if (page === 'home') return renderHome(true);
    if (page === 'create') return renderCreate();
    if (page === 'videos') return renderVideos();
    if (page === 'story') return renderStories();
    if (page === 'explore') return renderExplore();
    if (page === 'friends') return renderFriends();
    if (page === 'live') return renderLive();
    if (page === 'groups') return renderCommunities('group');
    if (page === 'channels') return renderCommunities('channel');
    if (page === 'notifications') return renderNotifications();
    if (page === 'music') return renderMusic();
    if (page === 'archive') return renderArchives();
    if (page === 'profile') return renderProfile(profileTarget || (rt.getMe() && rt.getMe().phone));
    if (page === 'settings') return renderSettings();
    pageContent.innerHTML = empty('home','Selamat datang di OHLI Social','Mulai dengan membuat postingan, mencari orang, atau masuk ke chat.', 'Buat postingan','compose-focus');
  }

  function composeCard() {
    var me = rt.getMe() || {};
    return '<form class="ohli-card ohli-compose" id="ohliComposeForm"><div class="ohli-compose-top">' + avatar(me.name,me.phone) + '<textarea id="ohliPostText" name="text" maxlength="2000" placeholder="Apa yang ingin kamu bagikan?" aria-label="Isi postingan"></textarea></div><div class="ohli-compose-divider"></div><div class="ohli-compose-bottom"><select id="ohliAudience" class="ohli-audience" aria-label="Audiens postingan"><option value="public">Publik</option><option value="followers">Pengikut</option><option value="private">Pribadi</option></select><button type="button" class="ohli-btn" data-action="pick-post-media">' + ico('image') + ' Foto / video</button><span class="ohli-hint" id="ohliSelectedFileLabel"></span><div class="ohli-compose-actions"><button class="ohli-btn ohli-btn-primary" type="submit" id="ohliPostSubmit">' + ico('send') + ' Posting</button></div></div></form>';
  }

  function homeBase() {
    return '<div class="ohli-home-grid"><div class="ohli-feed-column"><div class="ohli-feed-tabs"><button type="button" class="ohli-feed-tab active" data-action="feed-mode" data-mode="public">Untuk Kamu</button><button type="button" class="ohli-feed-tab" data-action="feed-mode" data-mode="following">Mengikuti</button></div><div id="ohliStoryStrip" class="ohli-story-flat"><div id="ohliStoryStripContent"></div></div><div id="ohliFeedList" class="ohli-feed-column"></div><div id="ohliFeedMore" style="text-align:center"></div></div><aside class="ohli-rail-column"><div class="ohli-card ohli-rail-card"><div class="ohli-rail-title"><span>Orang yang mungkin kamu kenal</span><button type="button" class="ohli-link-btn" data-action="refresh-suggestions">Acak</button></div><div id="ohliSuggestions"></div></div><div class="ohli-card ohli-rail-card"><div class="ohli-rail-title"><span>Jelajahi topik</span><small>14 hari terakhir</small></div><div id="ohliTrendingList"><p class="ohli-hint">Memuat topik yang sedang ramai…</p></div></div><div class="ohli-rail-footer">OHLI Social · Privasi dan keamanan tetap menjadi bagian dari pengalaman sosial.<br>Konten pada feed berasal dari server; bukan contoh/demo.</div></aside></div>';
  }

  var currentFeedMode = 'public';
  function observeSocialHistory(root){if(!root||typeof IntersectionObserver==='undefined')return;if(socialActivityObserver){socialActivityObserver.disconnect();socialActivityObserver=null;}socialObservedRoot=root;socialActivityObserver=new IntersectionObserver(function(entries){entries.forEach(function(entry){var card=entry.target,id=card.getAttribute('data-post-card')||'',post=findPost(id);if(!post||!isVid(post))return;var v=card.querySelector('video');if(!v)return;if(entry.isIntersecting&&entry.intersectionRatio>=0.65){if(card.dataset.historySeen!=='1'){card.dataset.historySeen='1';recordSocialActivity('post',id,post.authorName?'Postingan dari '+post.authorName:'Postingan OHLI');recordSocialActivity('video',id,'Video pendek dari '+(post.authorName||'OHLI'));}if(!v.dataset.ohliFypBound){v.dataset.ohliFypBound='1';v.dataset.ohliFypCompleted='0';var markFypComplete=function(){if(v.dataset.ohliFypCompleted!=='1'){v.dataset.ohliFypCompleted='1';recordSocialActivity('video_complete',id,'Video ditonton sampai akhir');}};v.addEventListener('ended',markFypComplete);v.addEventListener('timeupdate',function(){if(v.duration>0&&v.currentTime/v.duration>=0.95)markFypComplete();});}}else if(card.dataset.historySeen==='1'&&v.currentTime>2&&v.dataset.ohliFypCompleted!=='1'&&v.dataset.ohliFypSkipped!=='1'){v.dataset.ohliFypSkipped='1';recordSocialActivity('video_skip',id,'Video dilewati sebelum selesai');}});},{threshold:[0,0.65]});root.querySelectorAll('.ohli-post[data-post-card],.ohli-reel[data-post-card]').forEach(function(el){socialActivityObserver.observe(el);});}

  async function renderHome(reset) {
    pageContent.innerHTML = homeBase();
    var list = pageContent.querySelector('#ohliFeedList'); list.innerHTML = '<div class="ohli-loading"><div class="ohli-skeleton"></div><div class="ohli-skeleton"></div></div>';
    var storyTarget=pageContent.querySelector('#ohliStoryStripContent'),suggestionTarget=pageContent.querySelector('#ohliSuggestions'),trendTarget=pageContent.querySelector('#ohliTrendingList');
    if (reset) nextBefore = 0;
    pageContent.querySelectorAll('.ohli-feed-tab').forEach(function (b) { b.classList.toggle('active', b.dataset.mode === currentFeedMode); });
    var feedPath = '/api/feed?mode=' + encodeURIComponent(currentFeedMode) + (reset || !nextBefore ? '' : '&before=' + encodeURIComponent(nextBefore));
    /* Start independent sidebar requests together; the feed no longer waits for Story/users/trending in series. */
    var sideTasks=[
      rt.request('/api/status?surface=social').then(function(stories){statusList=stories.statuses||[];if(page==='home'&&storyTarget.isConnected)renderStoryStrip(storyTarget,statusList);}).catch(function(){}),
      rt.request('/api/social/users').then(function(ud){users=ud.users||[];if(page==='home'&&suggestionTarget.isConnected)renderSuggestions(suggestionTarget);}).catch(function(){}),
      rt.request('/api/trending').then(function(trendData){var topics=trendData.topics||[];if(page==='home'&&trendTarget.isConnected)trendTarget.innerHTML=topics.length?topics.map(function(t,i){var tag=String(t.tag||'').replace(/^#/,'');return '<button type="button" class="ohli-trend" data-action="search-topic" data-query="'+esc('#'+tag)+'"><small>Topik #'+(i+1)+' · '+Number(t.uses||0)+' postingan</small><strong>#'+esc(tag)+'</strong><span>Jelajahi postingan dengan tagar ini</span></button>';}).join(''):'<p class="ohli-hint">Belum ada tagar ramai. Tambahkan #topik pada postingan agar muncul di Jelajahi.</p>';}).catch(function(){if(page==='home'&&trendTarget.isConnected)trendTarget.innerHTML='<p class="ohli-hint">Topik belum bisa dimuat. Coba lagi nanti.</p>';})
    ];
    try {
      var data = await rt.request(feedPath); if(page!=='home'||!list.isConnected)return; var newPosts = data.posts || [];
      if (reset || !nextBefore) posts = newPosts; else { var seen = {}; posts.forEach(function (p) { seen[p.id] = true; }); newPosts.forEach(function (p) { if (!seen[p.id]) posts.push(p); }); }
      nextBefore = Number(data.nextBefore) || 0; hasMore = !!data.hasMore;
      list.innerHTML = posts.length ? posts.map(postCard).join('') : empty('home', currentFeedMode === 'following' ? 'Feed Mengikuti masih kosong' : 'Belum ada postingan', currentFeedMode === 'following' ? 'Ikuti akun lain untuk melihat postingannya di sini.' : 'Jadilah orang pertama yang membagikan cerita di OHLI Social.', 'Buat postingan','compose-focus');
      observeSocialHistory(list); if (reelAutoplay) initVideoObserver();
      var more = pageContent.querySelector('#ohliFeedMore'); if(more)more.innerHTML = hasMore ? '<button type="button" class="ohli-btn" data-action="load-more">Muat postingan lainnya</button>' : (posts.length ? '<p class="ohli-hint">Kamu sudah melihat postingan yang tersedia untuk halaman ini.</p>' : '');
    } catch (e) { if(page==='home'&&list.isConnected){list.innerHTML = ''; showError(apiError(e), 'retry');} }
    await Promise.allSettled(sideTasks);
  }

  function groupStoriesByPhone(list) {
    var groups = {}, order = [];
    (list || []).filter(function(s){return s && s.id && s.phone;}).forEach(function(s){
      if(!groups[s.phone]){groups[s.phone]=[];order.push(s.phone);} groups[s.phone].push(s);
    });
    return order.map(function(phone){var items=groups[phone].sort(function(a,b){return Number(a.time||0)-Number(b.time||0);});return {phone:phone,items:items,latest:items[items.length-1],isMine:items.some(function(s){return s.isMine===true;}),hasUnseen:items.some(function(s){return s.isMine!==true&&s.viewedByMe!==true;})};})
      .sort(function(a,b){return Number(b.latest.time||0)-Number(a.latest.time||0);});
  }
  function renderStoryStrip(target, list) {
    if (!target) return;
    var me = rt.getMe() || {}, groups = groupStoriesByPhone(list), ownGroup = null;
    groups.forEach(function(g){ if(g.isMine||g.phone===me.phone) ownGroup=g; });
    var html = '<div class="ohli-story-row">';
    if (ownGroup) {
      var ownLatest=ownGroup.latest;
      html += '<button type="button" class="ohli-story-item" data-action="view-story" data-story-id="'+esc(ownLatest.id)+'" data-story-phone="'+esc(me.phone)+'"><div class="ohli-story-ring ohli-story-own"><span>'+avatar(ownLatest.name||me.name,me.phone)+'</span><i>+</i></div><strong>Story kamu</strong><small>'+ownGroup.items.length+' Story</small></button>';
    } else {
      html += '<button type="button" class="ohli-story-item" data-action="navigate" data-page="story"><div class="ohli-story-ring ohli-story-add"><span>+</span></div><strong>Story kamu</strong><small>Tambah</small></button>';
    }
    groups.forEach(function(g){ if(g.isMine||g.phone===me.phone)return; var latest=g.latest;
      html += '<button type="button" class="ohli-story-item" data-action="view-story" data-story-id="'+esc(latest.id)+'" data-story-phone="'+esc(g.phone)+'"><div class="ohli-story-ring '+(g.hasUnseen?'is-unseen':'is-seen')+'"><span>'+avatar(latest.name||'Pengguna OHLI',g.phone)+'</span></div><strong>'+esc(latest.name||'Pengguna OHLI')+'</strong><small>'+g.items.length+' Story</small></button>';
    });
    html += '</div>'; target.innerHTML = html;
  }


  function renderSuggestions(target) {
    if (!target) return; var me = rt.getMe() || {}, list = (users || []).filter(function (u) { return u && u.phone !== me.phone; }).slice();
    for (var i=list.length-1;i>0;i--) { var j=Math.floor(Math.random()*(i+1)),tmp=list[i];list[i]=list[j];list[j]=tmp; } list=list.slice(0,5);
    target.innerHTML = list.length ? list.map(function (u) { var following=!!u.isFollowing, requested=!!u.followRequested; return '<div class="ohli-user-suggestion"><button type="button" class="ohli-suggestion-profile" data-action="profile" data-phone="' + esc(u.phone) + '" aria-label="Buka profil '+esc(u.name||'Pengguna OHLI')+'">' + avatar(u.name||'Pengguna OHLI',u.phone) + '<span class="ohli-user-copy"><strong>' + esc(u.name || 'Pengguna OHLI') + '</strong><small>@' + esc(socialHandle(u)) + '</small></span></button><button class="ohli-follow-mini" type="button" data-action="follow" data-phone="' + esc(u.phone) + '" data-following="'+(following?'1':'0')+'" data-requested="'+(requested?'1':'0')+'">'+(following?'Mengikuti':(requested?'Diminta':'Ikuti'))+'</button></div>'; }).join('') : '<p class="ohli-hint">Akun lain akan muncul di sini saat tersedia.</p>';
  }

  function postCard(p) {
    var me=rt.getMe()||{},isMine=!!p.isMine,isVideo=p.mediaType==='video'||(p.mimeType||'').indexOf('video/')===0,media='';
    if(p.mediaId)media=isVideo?'<div class="ohli-vwrap paused"'+vStyle(p)+'><video class="ohli-post-media" src="'+esc(rt.mediaUrl(p.mediaId))+'#t=0.1" playsinline loop preload="metadata" data-ohli-video="true"'+vAttrs(p)+' aria-label="Video postingan"></video>'+mediaOverlay(p)+'<span class="ohli-vplay">'+ico('play')+'</span><button type="button" class="ohli-vsound" data-action="toggle-sound" aria-label="Suara">'+ico(soundOn&&audioUnlocked?'sound':'mute')+'</button></div>':'<img class="ohli-post-media" src="'+esc(rt.mediaUrl(p.mediaId))+'" alt="Media postingan" loading="lazy">';
    var comments=openComments[p.id]?'<div class="ohli-post-comments">'+commentsHtml(p)+(p.commentsDisabled?'<p class="ohli-comments-locked">Komentar dinonaktifkan oleh pemilik postingan.</p>':commentFormHtml(p.id))+'</div>':'';
    var audioLine=(p.audioId&&!isVideo&&p.audioMediaId)?'<div class="ohli-post-audio ohli-photo-audio"><span>♫ '+esc(p.audioTitle||'Audio OHLI')+(p.audioArtist?' · '+esc(p.audioArtist):'')+'</span><audio controls preload="none" src="'+esc(rt.mediaUrl(p.audioMediaId))+'" aria-label="Putar musik pada foto"></audio></div>':(p.audioId?'<button type="button" class="ohli-post-audio" data-action="music-detail" data-music-id="'+esc(p.audioId)+'">♫ '+esc(p.audioTitle||'Audio OHLI')+' <span>›</span></button>':'');
    var downloadAction=isVideo&&p.allowDownload!==false?'<button type="button" class="ohli-btn" data-action="download-video" data-post-id="'+esc(p.id)+'">↓<span>Download</span></button>':'';
    return '<article class="ohli-card ohli-post" data-post-card="'+esc(p.id)+'"><div class="ohli-post-head"><button type="button" class="ohli-post-author" data-action="profile" data-phone="'+esc(p.author)+'">'+avatar(p.authorName,p.author)+'<span class="ohli-post-author-copy"><strong>'+esc(p.authorName||'Pengguna OHLI')+'</strong><small>@'+esc(socialHandle({username:p.authorUsername,name:p.authorName}))+' · '+esc(shortDate(p.createdAt))+' · '+esc(p.audience||'public')+'</small></span></button>'+(isMine?'<button type="button" class="ohli-post-menu" data-action="post-menu" data-post-id="'+esc(p.id)+'" data-archived="'+(p.archived?'1':'0')+'" aria-label="Kelola postingan">⋯</button>':'')+'</div>'+(p.text?'<p class="ohli-post-text">'+esc(p.text)+'</p>':'')+audioLine+media+'<div class="ohli-post-stats"><span>'+ico('heart')+' '+Number(p.likesCount||0)+' suka</span><span>'+Number(p.commentsCount||0)+' komentar</span><span>'+Number(p.repostsCount||0)+' posting ulang</span><span>'+Number(p.sharesCount||0)+' dibagikan</span></div><div class="ohli-post-actions"><button type="button" class="ohli-btn '+(p.liked?'is-active is-liked':'')+'" data-action="like" data-post-id="'+esc(p.id)+'">'+ico('heart')+'<span>'+(p.liked?'Disukai':'Suka')+'</span></button><button type="button" class="ohli-btn" data-action="comments" data-post-id="'+esc(p.id)+'">'+ico('comment')+'<span>Komentar</span></button><button type="button" class="ohli-btn '+(p.saved?'is-active':'')+'" data-action="bookmark" data-post-id="'+esc(p.id)+'">'+ico('bookmark')+'<span>Simpan</span></button><button type="button" class="ohli-btn '+(p.reposted?'is-active':'')+'" data-action="repost" data-post-id="'+esc(p.id)+'">'+ico('refresh')+'<span>Posting ulang</span></button><button type="button" class="ohli-btn" data-action="share-post" data-post-id="'+esc(p.id)+'">'+ico('share')+'<span>Bagikan</span></button>'+downloadAction+'</div>'+comments+'</article>';
  }

  function isVid(p) { return p.mediaType === 'video' || (p.mimeType || '').indexOf('video/') === 0; }
  function reelCard(p) {
    var id = esc(p.id);
    function b(a, ic, n, extra) { return '<button type="button" class="ohli-reel-btn' + (extra || '') + '" data-action="' + a + '" data-post-id="' + id + '">' + ico(ic) + (n == null ? '' : '<small>' + n + '</small>') + '</button>'; }
    return '<article class="ohli-reel" data-post-card="' + id + '"><div class="ohli-vwrap paused"><video data-video-src="' + esc(rt.mediaUrl(p.mediaId)) + '" playsinline loop preload="none" data-ohli-video="true"' + vAttrs(p) + '></video>' + mediaOverlay(p) + '<span class="ohli-vplay">' + ico('play') + '</span></div><div class="ohli-reel-shade" aria-hidden="true"></div>'
      + '<div class="ohli-reel-info">' + (p.audioId ? '<button type="button" class="ohli-reel-audio" data-action="music-detail" data-music-id="' + esc(p.audioId) + '">' : '<button type="button" class="ohli-reel-audio" data-action="music-original-detail" data-post-id="' + id + '">') + '<span aria-hidden="true">♫</span><span>' + esc((p.audioTitle || p.soundTitle || (metaOf(p) || {}).audioTitle || (metaOf(p) || {}).soundTitle || ('Audio asli · @' + socialHandle({username:p.authorUsername,name:p.authorName})))) + '</span>›</button><button type="button" class="ohli-reel-author" data-action="profile" data-phone="' + esc(p.author) + '">@' + esc(socialHandle({username:p.authorUsername,name:p.authorName})) + '</button><p>' + esc(p.text || '') + '</p></div>'
      + '<div class="ohli-speed-indicator" aria-hidden="true">2× &gt;&gt;</div><div class="ohli-reel-side"><div class="ohli-reel-profile-wrap"><button type="button" class="ohli-reel-av" data-action="profile" data-phone="' + esc(p.author) + '" aria-label="Profil">' + avatar(p.authorName, p.author) + '</button>' + (p.author!==((rt.getMe()||{}).phone) && !p.isFollowing ? '<span class="ohli-reel-follow is-indicator" aria-hidden="true"></span>' : '') + '</div>'
      + b('like', 'heart', Number(p.likesCount || 0), p.liked ? ' is-liked' : '') + b('reel-comments', 'comment', Number(p.commentsCount || 0)) + b('bookmark', 'bookmark', null, p.saved ? ' is-liked' : '')
      + b('repost', 'refresh', Number(p.repostsCount || 0), p.reposted ? ' is-on' : '') + b('share-post', 'share', Number(p.sharesCount || 0)) + (p.isMine?'':'<button type="button" class="ohli-reel-btn ohli-reel-not-interested" data-action="not-interested" data-post-id="'+id+'" aria-label="Tidak tertarik" title="Tidak tertarik">'+ico('close')+'</button>')
      + '<button type="button" class="ohli-reel-btn" data-action="toggle-sound" aria-label="Suara">' + ico(soundOn && audioUnlocked ? 'sound' : 'mute') + '</button></div><div class="ohli-reel-bar"><i></i></div></article>';
  }
  function syncReels() {
    (posts || []).forEach(function (p) {
      var r = pageContent.querySelector('.ohli-reel[data-post-card="' + p.id + '"]'); if (!r) return;
      function set(a, n, on, cls) { var b = r.querySelector('[data-action="' + a + '"]'); if (!b) return; if (on != null) b.classList.toggle(cls || 'is-liked', !!on); var sm = b.querySelector('small'); if (sm && n != null) sm.textContent = n; }
      set('like', Number(p.likesCount || 0), p.liked); set('reel-comments', Number(p.commentsCount || 0)); set('bookmark', null, p.saved); set('repost', Number(p.repostsCount || 0), p.reposted, 'is-on'); set('share-post', Number(p.sharesCount || 0));
    });
  }
  function openMedia(p) {
    recordSocialActivity('post',p.id,p.authorName||'Postingan');
    recordSocialActivity(isVid(p)?'video':'photo',p.id,(isVid(p)?'Video dari ':'Foto dari ')+(p.authorName||'OHLI'));
    var u = esc(rt.mediaUrl(p.mediaId));
    showModal('', (isVid(p) ? '<div class="ohli-vwrap paused"><video src="' + u + '" playsinline loop data-ohli-video="true" style="width:100%;max-height:70vh;border-radius:12px;background:#000"></video><span class="ohli-vplay">' + ico('play') + '</span></div>' : '<img src="' + u + '" alt="" style="width:100%;border-radius:12px">') + (p.text ? '<p class="ohli-post-text">' + esc(p.text) + '</p>' : ''), '', null);
    var v = overlayContent.querySelector('video'); if (v) { v.muted = !soundOn; var pr = v.play(); if (pr && pr.catch) pr.catch(function () { v.muted = true; v.play().catch(function () {}); }); }
  }
  function mediaGrid(ps) {
    if (!ps.length) return empty('profile', 'Belum ada media', 'Foto dan video yang diposting akan tampil di sini.');
    return '<div class="ohli-media-grid">' + ps.map(function (p) { var u = esc(rt.mediaUrl(p.mediaId)); return '<button type="button" class="ohli-media-tile" data-action="open-media" data-post-id="' + esc(p.id) + '">' + (isVid(p) ? '<video src="' + u + '#t=0.1" muted playsinline preload="metadata"></video><span class="ohli-tile-badge">' + ico('play') + '</span>' : '<img src="' + u + '" alt="" loading="lazy">') + '</button>'; }).join('') + '</div>';
  }
  function renderProfileMedia(target,ps){var mode=profileVideoLayout==='transparent'?'transparent':'limited';target.className='ohli-profile-media-view '+(mode==='transparent'?'is-transparent':'is-limited');target.innerHTML='<div class="ohli-profile-media-controls"><span>Tampilan video</span><button type="button" class="ohli-btn '+(mode==='transparent'?'ohli-btn-primary':'')+'" data-action="profile-media-mode" data-mode="transparent">Transparan</button><button type="button" class="ohli-btn '+(mode==='limited'?'ohli-btn-primary':'')+'" data-action="profile-media-mode" data-mode="limited">Terbatas</button></div>'+mediaGrid(ps);}
  async function renderVideos() {
    if (videoObserver) { videoObserver.disconnect(); videoObserver = null; }
    activeReelVideo = null;
    showLoading();
    try {
      var data = await rt.request('/api/feed?mode=' + (reelMode === 'following' ? 'following' : 'public') + '&limit=8');
      posts = data.posts || []; var vs = posts.filter(function (p) { return p.mediaId && isVid(p); });
      var reelCursor = Number(data.nextBefore) || 0, reelHasMore = !!data.hasMore, reelLoadingMore = false;
      var top = '<div class="ohli-reels-top"><button type="button" class="ohli-reel-live" data-action="navigate" data-page="live" aria-label="Live">' + ico('live') + '<span>LIVE</span></button><div class="ohli-reels-tabs"><button type="button" class="' + (reelMode === 'following' ? 'on' : '') + '" data-action="reel-mode" data-mode="following">Mengikuti</button><button type="button" class="' + (reelMode !== 'following' ? 'on' : '') + '" data-action="reel-mode" data-mode="foryou">Untuk Kamu</button></div><div class="ohli-reel-top-actions"><button type="button" class="ohli-reel-notifications" data-action="navigate" data-page="notifications" aria-label="Notifikasi">' + ico('bell') + '</button><button type="button" class="ohli-reel-search" data-action="reel-search" aria-label="Cari">' + ico('search') + '</button></div></div>';
      pageContent.innerHTML = '<div class="ohli-reels-wrap">' + top + (vs.length ? '<div class="ohli-reels">' + vs.map(reelCard).join('') + '</div>' : '<div class="ohli-reels-empty">' + empty('video', reelMode === 'following' ? 'Belum ada video dari akun yang kamu ikuti' : 'Belum ada video pendek', 'Unggah video lewat tombol + di bawah.', 'Buat konten', 'compose-focus') + '</div>') + '</div>';
      var reelsEl = pageContent.querySelector('.ohli-reels');
      async function loadMoreReels() {
        if (!reelsEl || reelLoadingMore || !reelHasMore || !reelCursor || page !== 'videos') return;
        reelLoadingMore = true;
        try {
          var moreData = await rt.request('/api/feed?mode=' + (reelMode === 'following' ? 'following' : 'public') + '&limit=8&before=' + encodeURIComponent(reelCursor));
          var known = {}; posts.forEach(function (p) { known[p.id] = true; });
          var morePosts = (moreData.posts || []).filter(function (p) { return p.mediaId && isVid(p) && !known[p.id]; });
          moreData.posts && moreData.posts.forEach(function (p) { if (!known[p.id]) posts.push(p); });
          if (morePosts.length) reelsEl.insertAdjacentHTML('beforeend', morePosts.map(reelCard).join(''));
          reelCursor = Number(moreData.nextBefore) || 0; reelHasMore = !!moreData.hasMore;
          observeSocialHistory(pageContent); initVideoObserver();
        } catch (e) { toast('Video berikutnya belum bisa dimuat. Geser lagi untuk mencoba.'); }
        finally { reelLoadingMore = false; }
      }
      if (reelsEl) reelsEl.addEventListener('scroll', function () {
        if (reelsEl.scrollHeight - reelsEl.scrollTop - reelsEl.clientHeight < Math.max(900, reelsEl.clientHeight * 1.4)) loadMoreReels();
      }, {passive:true});
      applyReelPreferences();
      observeSocialHistory(pageContent); initVideoObserver();
    } catch (e) { showError(apiError(e), 'retry'); }
  }
  function wantMuted(v) { return !soundOn || !audioUnlocked || v.dataset.mute === '1'; }
  function refreshAudioButtons() {
    if (!shell) return;
    shell.querySelectorAll('[data-action="toggle-sound"]').forEach(function (button) {
      var scope=button.closest('.ohli-reel')||button.closest('.ohli-vwrap'),video=scope&&scope.querySelector('video');
      var audible=!!(soundOn&&video&&!video.muted);
      button.innerHTML=ico(audible?'sound':'mute');
      button.setAttribute('aria-label',audible?'Matikan suara video':'Nyalakan suara video');
      button.title=audible?'Suara aktif':'Suara nonaktif';
    });
  }
  function playVid(v) {
    if (!v || !v.isConnected) return;
    if (activeReelVideo && activeReelVideo !== v) { activeReelVideo.pause(); activeReelVideo.playbackRate = 1; }
    activeReelVideo = v;
    if (!v.getAttribute('src') && v.dataset.videoSrc) { v.setAttribute('src', v.dataset.videoSrc); v.load(); }
    v.muted = wantMuted(v); v.playbackRate = 1;
    var pr = v.play();
    if (pr && pr.then) pr.then(refreshAudioButtons).catch(function () {
      if (!v.muted) {
        v.muted = true;
        if (soundOn && !needGesture) { needGesture = true; toast('Browser membatasi suara otomatis. Sentuh layar sekali untuk membuka audio.'); }
        v.play().catch(function () {});
      }
    });
    refreshAudioButtons();
  }
  function resetVideoObserver() {
    if (videoObserver) { videoObserver.disconnect(); videoObserver = null; }
    if (pageContent) pageContent.querySelectorAll('video[data-ohli-obs]').forEach(function (v) { v.removeAttribute('data-ohli-obs'); });
    if (reelAutoplay) initVideoObserver();
  }
  function initVideoObserver() {
    if (!pageContent || !('IntersectionObserver' in window)) return;
    var vids = pageContent.querySelectorAll('video[data-ohli-video="true"]:not([data-ohli-obs])'); if (!vids.length) return;
    if (!videoObserver) videoObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        var video = entry.target;
        var isFeedVideo = page === 'home' && !!video.closest('[data-post-card]');
        var isShortVideo = page === 'videos' && !!video.closest('.ohli-reel');
        var requiredVisibility = isFeedVideo ? 0.62 : 0.8;
        if (entry.isIntersecting && entry.intersectionRatio >= requiredVisibility && reelAutoplay && (isFeedVideo || isShortVideo)) {
          pageContent.querySelectorAll('video[data-ohli-video="true"]').forEach(function (other) {
            if (other !== video) { other.pause(); other.playbackRate = 1; }
          });
          playVid(video);
        } else if (!entry.isIntersecting || entry.intersectionRatio < requiredVisibility || (!isFeedVideo && !isShortVideo)) {
          video.pause(); video.playbackRate = 1;
          if (activeReelVideo === video) activeReelVideo = null;
        }
      });
    }, {threshold: [0, 0.4, 0.62, 0.8, 1]});
    vids.forEach(function (video) { video.setAttribute('data-ohli-obs', '1'); videoObserver.observe(video); });
  }

  async function renderStories() {
    pageContent.innerHTML = '<div class="ohli-home-grid"><div class="ohli-feed-column"><form class="ohli-card ohli-compose" id="ohliStoryForm"><div class="ohli-compose-top">' + avatar((rt.getMe() || {}).name,(rt.getMe() || {}).phone) + '<textarea id="ohliStoryText" name="text" maxlength="240" placeholder="Bagikan pembaruan singkat…" aria-label="Teks Story"></textarea></div><div class="ohli-compose-divider"></div><div class="ohli-story-publish-options"><label class="ohli-field">Dibagikan ke<select id="ohliStoryDestination" name="destination"><option value="social">OHLI Social saja</option><option value="chat">Status kontak di Chat saja</option><option value="both">OHLI Social + Chat</option></select></label><label class="ohli-field">Siapa yang bisa melihat<select id="ohliStoryAudience" name="audience"><option value="public">Publik · semua akun</option><option value="followers">Pengikut</option><option value="contacts">Kontak tersimpan</option><option value="private">Hanya saya</option></select></label></div><div class="ohli-compose-bottom"><button type="button" class="ohli-btn" data-action="pick-story-media">' + ico('image') + ' Foto / video</button><button type="button" class="ohli-btn" data-action="capture-story">' + ico('camera') + ' Kamera</button><button type="button" class="ohli-btn" data-action="create-story-editor">Edit Story</button><span id="ohliStoryFileLabel" class="ohli-hint"></span><div class="ohli-compose-actions"><button type="button" class="ohli-btn" data-action="save-story-draft">Simpan draf</button><button type="submit" id="ohliStorySubmit" class="ohli-btn ohli-btn-primary">Bagikan Story</button></div></div><p class="ohli-hint">Story aktif hingga 24 jam. Publik dapat dilihat oleh semua akun OHLI kecuali akun yang diblokir.</p></form><div class="ohli-story-history-entry"><button type="button" class="ohli-btn" data-action="open-story-history">◷ Riwayat Story</button><span class="ohli-hint">Story yang berakhir setelah 24 jam tersimpan di sini.</span></div><div class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Story yang tersedia</div><div id="ohliAllStories"></div></div></div><aside class="ohli-rail-column"><div class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Privasi Story</div><p class="ohli-hint">Pilih tujuan distribusi dan audiens sebelum membagikan. Story lama tetap mengikuti aturan visibilitas sebelumnya.</p><button type="button" class="ohli-btn" data-action="legacy-settings">Lihat pengaturan</button></div></aside></div>';
    var target = pageContent.querySelector('#ohliAllStories'); target.innerHTML = '<div class="ohli-loading"><div class="ohli-skeleton"></div></div>';
    try { var data = await rt.request('/api/status?surface=social'); statusList = data.statuses || []; var me=rt.getMe()||{},storyGroups=groupStoriesByPhone(statusList); target.innerHTML = storyGroups.length ? storyGroups.map(function(g){var s=g.latest;return '<button type="button" class="ohli-list-card ohli-card" style="width:100%;text-align:left" data-action="view-story" data-story-id="'+esc(s.id)+'">'+avatar(s.name||socialHandle({username:s.username,name:s.name}),s.phone)+'<span class="ohli-list-copy"><strong>'+((s.isMine||s.phone===me.phone)?'Story saya':esc(s.name||('@'+socialHandle({username:s.username,name:s.name}))))+'</strong><p>'+esc(s.text||(s.mediaType==='video'?'Video Story':'Story foto'))+'</p><span class="ohli-list-meta">'+g.items.length+' Story · terbaru '+esc(shortDate(s.time))+'</span></span>'+ico('arrow')+'</button>';}).join('') : empty('story','Belum ada Story','Story yang baru dibuat oleh kamu atau kontakmu akan muncul di sini.'); } catch(e) { target.innerHTML = '<div class="ohli-error">' + esc(apiError(e)) + '</div>'; }
  }

  async function viewStory(status) {
    activeHighlight = null;
    recordSocialActivity('story',status.id,'Story dari '+(status.name||('@'+socialHandle({username:status.username,name:status.name}))));
    var groups=groupStoriesByPhone(statusList);
    storyGroups=groups;
    storyGroupIndex=Math.max(0,groups.findIndex(function(g){return g.phone===status.phone;}));
    var selected=groups[storyGroupIndex];
    storyItems=selected?selected.items.slice():[status];
    storyIndex=Math.max(0,storyItems.findIndex(function(s){return s.id===status.id;}));
    pauseAllReels();
    var viewer=shell.querySelector('#ohliStoryViewer');viewer.hidden=false;await renderStoryFrame();
  }
  function clearStoryTimers(){if(storyTimer){clearTimeout(storyTimer);storyTimer=null;}if(storyProgressTimer){clearInterval(storyProgressTimer);storyProgressTimer=null;}}
  async function renderStoryFrame(){
    clearStoryTimers();var me=rt.getMe()||{},status=storyItems[storyIndex];if(!status)return closeStory();
    var owner=shell.querySelector('#ohliStoryOwner'),media=shell.querySelector('#ohliStoryMedia'),foot=shell.querySelector('#ohliStoryFoot'),progress=shell.querySelector('.ohli-story-progress');
    if(!status.isMine&&status.phone!==me.phone){rt.request('/api/status/view',{method:'POST',body:{statusId:status.id}}).then(function(){status.viewedByMe=true;}).catch(function(){});}
    if(progress)progress.innerHTML=storyItems.map(function(item,index){return '<span class="ohli-story-progress-segment '+(index<storyIndex?'is-complete':(index===storyIndex?'is-current':''))+'" data-index="'+index+'"><i></i></span>';}).join('');
    var ownerLabel=activeHighlight?(activeHighlight.ownerName||status.name||'Sorotan'):((status.isMine||status.phone===me.phone)?'Story kamu':(status.name||('@'+socialHandle({username:status.username,name:status.name}))));
    owner.innerHTML='<button type="button" class="ohli-story-owner-profile" data-action="story-owner-profile" data-phone="'+esc(status.phone)+'">'+avatar(status.name||ownerLabel,status.phone)+'<span><strong>'+esc(ownerLabel)+'</strong><small>'+esc(activeHighlight?(activeHighlight.name||'Sorotan'):shortDate(status.time))+'</small></span></button><button type="button" class="ohli-modal-close ohli-story-close" data-action="close-story" aria-label="Tutup">×</button>';
    var isStoryVideo=!!(status.mediaId&&(status.mediaType==='video'||(status.mimeType||'').indexOf('video/')===0));
    var mediaUrl=status.mediaId?((isStoryVideo||status.fileSize||status.mimeType)?rt.mediaUrl(status.mediaId):('/api/image?token='+encodeURIComponent(rt.getToken())+'&id='+encodeURIComponent(status.mediaId))):'';
    media.innerHTML=(status.mediaId?(isStoryVideo?'<video src="'+esc(mediaUrl)+'" autoplay playsinline preload="auto" '+vAttrs(status)+' '+vStyle(status)+'></video>':'<img src="'+esc(mediaUrl)+'" alt="Media Story">')+mediaOverlay(status):'')+(status.text?'<p class="ohli-story-text">'+esc(status.text)+'</p>':'')+'<button type="button" class="ohli-story-tap-zone is-prev" data-action="story-tap-left" aria-label="Story sebelumnya"></button><button type="button" class="ohli-story-tap-zone is-next" data-action="story-tap-right" aria-label="Story selanjutnya"></button>';
    var own=!!status.isMine||status.phone===me.phone;
    if(activeHighlight){foot.innerHTML='<span class="ohli-story-stats">'+(storyIndex+1)+' / '+storyItems.length+' · '+esc(activeHighlight.name||'Sorotan')+'</span>'+(own?'<button type="button" class="ohli-btn" data-action="story-viewers" data-story-id="'+esc(status.id)+'">Penonton</button><button type="button" class="ohli-btn" data-action="delete-highlight-item" data-highlight-id="'+esc(activeHighlight.id)+'" data-story-id="'+esc(status.id)+'">Hapus dari Sorotan</button>':'');}
    else {var activeOwnStory=own&&statusList.some(function(item){return item&&item.id===status.id;});foot.innerHTML='<span class="ohli-story-stats">'+(storyIndex+1)+' / '+storyItems.length+' · '+Number(status.likesCount||0)+' suka</span>'+(own?'<button type="button" class="ohli-btn" data-action="story-viewers" data-story-id="'+esc(status.id)+'">Penonton</button><button type="button" class="ohli-btn" data-action="story-highlight" data-story-id="'+esc(status.id)+'">+ Sorotan</button>'+(activeOwnStory?'<button type="button" class="ohli-btn ohli-btn-danger" data-action="delete-own-story" data-story-id="'+esc(status.id)+'">Hapus Story</button>':''):'<button type="button" class="ohli-btn '+(status.liked?'is-active is-liked':'')+'" data-action="like-story" data-story-id="'+esc(status.id)+'">'+(status.liked?'♥ Disukai':'♡ Suka')+'</button><button type="button" class="ohli-btn" data-action="story-collection" data-story-id="'+esc(status.id)+'" data-kind="pin">Pin Story</button><button type="button" class="ohli-btn" data-action="story-collection" data-story-id="'+esc(status.id)+'" data-kind="archive">Arsip</button><form id="ohliStoryReplyForm" class="ohli-story-reply-form" data-story-owner="'+esc(status.phone)+'"><input name="reply" maxlength="1000" placeholder="Kirim pesan…" autocomplete="off" required><button type="submit" aria-label="Kirim balasan Story">'+ico('send')+'</button></form>');}
    if(own&&!activeHighlight){rt.request('/api/status/views?statusId='+encodeURIComponent(status.id)).then(function(stats){var statEl=foot.querySelector('.ohli-story-stats');if(statEl&&storyItems[storyIndex]===status)statEl.textContent=(storyIndex+1)+' / '+storyItems.length+' · '+Number(status.likesCount||0)+' suka · '+stats.uniqueViewers+' penonton · '+stats.totalViews+' tayangan';}).catch(function(){});}
    var video=media.querySelector('video'),duration=video?0:(status.mediaId?5000:6000);storyOpenedAt=Date.now();
    if(video){
      video.muted=wantMuted(video);
      video.addEventListener('ended',function(){stepStory(1);},{once:true});
      video.addEventListener('loadedmetadata',function(){if(video.duration&&isFinite(video.duration)){duration=Math.max(4500,video.duration*1000);startStoryProgress(duration);}},{once:true});
      video.addEventListener('error',function(){if(!video.isConnected)return;clearStoryTimers();var failed=media.querySelector('.ohli-story-load-error');if(!failed){failed=document.createElement('div');failed.className='ohli-story-load-error';failed.innerHTML='<strong>Video Story tidak dapat dimuat</strong><span>Periksa koneksi lalu coba lagi.</span><button type="button" data-action="story-retry">Coba lagi</button>';media.appendChild(failed);}},{once:true});
      if(video.readyState>=1&&video.duration&&isFinite(video.duration))duration=Math.max(4500,video.duration*1000);
      var storyPlay=video.play();if(storyPlay&&storyPlay.catch)storyPlay.catch(function(){if(!video.isConnected)return;video.muted=true;video.play().catch(function(){});if(soundOn&&!audioUnlocked)needGesture=true;});
    }
    startStoryProgress(duration||15000);
  }
  function startStoryProgress(duration){clearStoryTimers();storyDuration=Math.max(1000,Number(duration)||5000);storyElapsed=0;storyProgressStarted=Date.now();storyPaused=false;var host=shell.querySelector('.ohli-story-progress'),segment=host&&host.querySelector('.ohli-story-progress-segment[data-index="'+storyIndex+'"] i');if(host){host.querySelectorAll('.ohli-story-progress-segment').forEach(function(el,index){var fill=el.querySelector('i');if(fill)fill.style.width=index<storyIndex?'100%':'0%';});}storyProgressTimer=setInterval(function(){if(segment){var elapsed=storyElapsed+(storyPaused?0:Date.now()-storyProgressStarted);var pct=Math.min(100,elapsed/storyDuration*100);segment.style.width=pct+'%';}},80);storyTimer=setTimeout(function(){if(!storyPaused)stepStory(1);},storyDuration);}
  async function stepStory(delta){
    var next=storyIndex+delta;
    if(next>=0&&next<storyItems.length){storyIndex=next;await renderStoryFrame();return;}
    var groupNext=storyGroupIndex+(delta<0?-1:1);
    if(groupNext<0)return;
    if(groupNext>=storyGroups.length){closeStory();return;}
    storyGroupIndex=groupNext;storyItems=(storyGroups[groupNext]&&storyGroups[groupNext].items||[]).slice();storyIndex=delta<0?Math.max(0,storyItems.length-1):0;
    await renderStoryFrame();
  }
  function closeStory(){clearStoryTimers();if(storyHoldTimer){clearTimeout(storyHoldTimer);storyHoldTimer=null;}storyHoldActive=false;storyPaused=false;storyElapsed=0;var viewer=shell&&shell.querySelector('#ohliStoryViewer');if(viewer){viewer.hidden=true;var media=viewer.querySelector('#ohliStoryMedia');if(media){var v=media.querySelector('video');if(v){v.pause();v.removeAttribute('src');}media.innerHTML='';}}var strip=shell&&shell.querySelector('#ohliStoryStripContent');if(strip)renderStoryStrip(strip,statusList);storyItems=[];storyGroups=[];storyGroupIndex=0;activeHighlight=null;if(page==='home'||page==='videos')resetVideoObserver();}


  async function renderExplore() {
    await syncSocialHistory();
    var q = pageQuery || searchInput.value.trim(); pageQuery = q;
    pageContent.innerHTML = '<div class="ohli-card" style="padding:17px"><form id="ohliExploreForm" class="ohli-comment-form" style="margin:0"><input name="q" value="' + esc(q) + '" maxlength="80" placeholder="Cari nama, username, atau kata di postingan…" aria-label="Cari di OHLI"><button type="submit" aria-label="Cari">' + ico('search') + '</button></form>'+(!q?recentSearchMarkup():'')+'</div><section class="ohli-result-section"><h2>Topik yang sedang ramai</h2><div id="ohliExploreTrending" class="ohli-trending-grid"><p class="ohli-hint">Memuat tagar aktif…</p></div></section><div id="ohliSearchResults"></div>';
    var trendingTarget=pageContent.querySelector('#ohliExploreTrending');
    try { var td=await rt.request('/api/trending'),topics=td.topics||[]; trendingTarget.innerHTML=topics.length?topics.map(function(t,i){var tag=String(t.tag||'').replace(/^#/,'');return '<button type="button" class="ohli-trend" data-action="search-topic" data-query="'+esc('#'+tag)+'"><small>Topik #'+(i+1)+' · '+Number(t.uses||0)+' postingan</small><strong>#'+esc(tag)+'</strong><span>Buka postingan dengan tagar ini</span></button>';}).join(''):'<p class="ohli-hint">Belum ada tagar ramai. Gunakan #tagar pada postingan agar topiknya muncul di sini.</p>'; } catch(e) { trendingTarget.innerHTML='<p class="ohli-hint">Topik belum tersedia saat ini.</p>'; }
    var target = pageContent.querySelector('#ohliSearchResults');
    if (!q) { target.innerHTML = empty('search','Mulai pencarian','Masukkan nama pengguna atau kata kunci. Pilih tagar di atas untuk membuka topik populer.'); return; }
    target.innerHTML = '<div class="ohli-loading"><div class="ohli-skeleton"></div><div class="ohli-skeleton"></div></div>';
    try { var data = await rt.request('/api/search?q=' + encodeURIComponent(q)); var userHtml = (data.users || []).map(function (u) { var following=!!u.isFollowing,requested=!!u.followRequested; return '<div class="ohli-card ohli-list-card"><button type="button" class="ohli-post-author" data-action="profile" data-phone="' + esc(u.phone) + '">' + avatar(u.name||'Pengguna OHLI',u.phone) + '<span class="ohli-list-copy"><strong>' + esc(u.name || 'Pengguna OHLI') + '</strong><p>@' + esc(socialHandle(u)) + '</p></span></button><button type="button" class="ohli-btn ' + (following?'':'ohli-btn-primary') + '" data-action="follow" data-phone="' + esc(u.phone) + '" data-following="'+(following?'1':'0')+'" data-requested="'+(requested?'1':'0')+'">'+(following?'Mengikuti':(requested?'Diminta':'Ikuti'))+'</button></div>'; }).join(''); var postHtml = (data.posts || []).map(postCard).join(''); target.innerHTML = (userHtml ? '<section class="ohli-result-section"><h2>Pengguna (' + data.users.length + ')</h2><div class="ohli-result-list">' + userHtml + '</div></section>' : '') + (postHtml ? '<section class="ohli-result-section"><h2>Postingan (' + data.posts.length + ')</h2><div class="ohli-feed-column">' + postHtml + '</div></section>' : '') + (!userHtml && !postHtml ? empty('search','Tidak ada hasil','Coba kata kunci yang berbeda atau cek ejaan nama akun.') : ''); posts = (data.posts || []); }
    catch (e) { target.innerHTML = '<div class="ohli-error">' + esc(apiError(e)) + '</div>'; }
  }


  async function renderFriends() {
    showLoading();
    try {
      var pair = await Promise.all([rt.request('/api/friends'), rt.request('/api/friends/requests')]);
      var friends = pair[0].friends || [], incoming = pair[1].incoming || [], sent = pair[1].sent || [];
      function personRow(u, mode) {
        var id = u.phone || u.socialId || u.publicId || '', name = u.name || 'Pengguna OHLI';
        var state = mode === 'friend' ? (u.online ? 'Online' : 'Offline') : (mode === 'incoming' ? 'Meminta berteman' : 'Menunggu jawaban');
        var actions = mode === 'incoming'
          ? '<button type="button" class="ohli-btn" data-action="friend-reject" data-phone="' + esc(id) + '">Tolak</button><button type="button" class="ohli-btn ohli-btn-primary" data-action="friend-accept" data-phone="' + esc(id) + '">Terima</button>'
          : mode === 'sent'
            ? '<button type="button" class="ohli-btn" data-action="friend-cancel" data-phone="' + esc(id) + '">Batalkan</button>'
            : '<button type="button" class="ohli-btn ohli-btn-primary" data-action="open-chat-user" data-social-id="' + esc(id) + '">Chat</button><button type="button" class="ohli-btn" data-action="profile" data-phone="' + esc(id) + '">Profil</button><button type="button" class="ohli-btn ohli-btn-danger" data-action="friend-remove" data-phone="' + esc(id) + '">Hapus</button>';
        return '<div class="ohli-card ohli-list-card ohli-friend-row">' + avatar(name, id) + '<div class="ohli-list-copy"><strong>' + esc(name) + '</strong><p>@' + esc(socialHandle(u)) + ' · ' + state + '</p></div><div class="ohli-friend-actions">' + actions + '</div></div>';
      }
      var incomingHtml = incoming.map(function (u) { return personRow(u, 'incoming'); }).join('');
      var sentHtml = sent.map(function (u) { return personRow(u, 'sent'); }).join('');
      var friendsHtml = friends.map(function (u) { return personRow(u, 'friend'); }).join('');
      pageContent.innerHTML = '<div class="ohli-friends-toolbar"><div><strong>' + friends.length + ' teman</strong><span>' + incoming.length + ' permintaan masuk · ' + sent.length + ' terkirim</span></div><button type="button" class="ohli-btn" data-action="refresh-friends">' + ico('refresh') + ' Perbarui</button><button type="button" class="ohli-btn ohli-btn-primary" data-action="find-friends">' + ico('search') + ' Cari teman</button></div>' +
        '<section class="ohli-result-section"><h2>Permintaan masuk (' + incoming.length + ')</h2><div class="ohli-result-list">' + (incomingHtml || '<p class="ohli-hint">Belum ada permintaan pertemanan baru.</p>') + '</div></section>' +
        '<section class="ohli-result-section"><h2>Permintaan terkirim (' + sent.length + ')</h2><div class="ohli-result-list">' + (sentHtml || '<p class="ohli-hint">Tidak ada permintaan yang menunggu jawaban.</p>') + '</div></section>' +
        '<section class="ohli-result-section"><h2>Teman saya (' + friends.length + ')</h2><div class="ohli-result-list">' + (friendsHtml || empty('users', 'Belum ada teman', 'Cari akun OHLI dan kirim permintaan pertemanan untuk mulai terhubung.', 'Cari orang', 'find-friends')) + '</div></section>' +
        '<p class="ohli-hint" style="margin-top:14px">Status online hanya ditampilkan sesuai pengaturan privasi masing-masing pengguna.</p>';
    } catch (e) { showError(apiError(e), 'refresh-friends'); }
  }

  async function renderLive() {
    showLoading();
    try { var data = await rt.request('/api/live'); var lives = data.lives || []; pageContent.innerHTML = '<div style="display:flex;gap:9px;flex-wrap:wrap;margin-bottom:16px"><button type="button" class="ohli-btn ohli-btn-primary" data-action="start-live">' + ico('live') + ' Buka Studio LIVE</button><button type="button" class="ohli-btn" data-action="retry">' + ico('refresh') + ' Perbarui daftar</button></div>' + (lives.length ? '<div class="ohli-feed-column">' + lives.map(function (l) { return '<div class="ohli-card ohli-list-card">' + avatar(l.hostName,l.host) + '<div class="ohli-list-copy"><strong>' + esc(l.title || 'Siaran Live') + '</strong><p>' + esc(l.hostName || l.host) + '</p><div class="ohli-list-meta">' + Number(l.viewerCount || 0) + ' penonton saat ini · koneksi peer-to-peer</div></div><button type="button" class="ohli-btn ohli-btn-primary" data-action="join-live" data-live-id="' + esc(l.id) + '">Tonton</button></div>'; }).join('') + '</div>' : empty('live','Tidak ada Live aktif','Ketika seseorang memulai Live dari browser yang mendukung WebRTC, siaran akan muncul di sini. Server ini hanya menyediakan signaling peer-to-peer, bukan SFU relay.')) + '<p class="ohli-hint" style="margin-top:15px">Live dapat memerlukan HTTPS, izin kamera/mikrofon, STUN/TURN, koneksi host yang stabil, dan kapasitas upload yang cukup. Menutup tab host dapat mengakhiri aliran media.</p>'; }
    catch (e) { showError(apiError(e), 'retry'); }
  }

  async function renderCommunities(kind) {
    var label = kind === 'group' ? 'grup' : 'channel', endpoint = kind === 'group' ? '/api/groups' : '/api/channels'; showLoading();
    try {
      var data = await rt.request(endpoint); var items = data.items || []; if (kind === 'group') groupItems = items; else channelItems = items;
      pageContent.innerHTML = '<div style="display:flex;justify-content:flex-end;margin-bottom:14px"><button type="button" class="ohli-btn ohli-btn-primary" data-action="create-community" data-kind="' + kind + '">' + ico('plus') + ' Buat ' + (kind === 'group' ? 'grup' : 'channel') + '</button></div>' + (items.length ? '<div class="ohli-feed-column">' + items.map(function (item) { var joined = kind === 'group' ? item.joined : item.following; var action = kind === 'group' ? 'join-community' : 'follow-channel'; return '<div class="ohli-card ohli-list-card"><div class="ohli-avatar">' + ico(kind === 'group' ? 'groups' : 'channel') + '</div><div class="ohli-list-copy"><strong>' + esc(item.name) + '</strong><p>' + esc(item.description || (kind === 'group' ? 'Komunitas OHLI' : 'Channel OHLI')) + '</p><span class="ohli-list-meta">' + Number(item.memberCount || 0) + ' anggota · ' + esc(item.privacy || 'public') + '</span></div>' + (joined ? '<button type="button" class="ohli-btn ohli-btn-primary" data-action="' + (kind === 'group' ? 'open-group' : 'open-channel') + '" data-id="' + esc(item.id) + '">' + (kind === 'group' ? 'Buka chat' : 'Buka') + '</button>' : '') + '<button type="button" class="ohli-btn ' + (joined ? '' : 'ohli-btn-primary') + '" data-action="' + action + '" data-id="' + esc(item.id) + '" data-joined="' + (joined ? '1' : '0') + '">' + (joined ? (kind === 'group' ? 'Keluar' : 'Diikuti') : (kind === 'group' ? 'Gabung' : 'Ikuti')) + '</button></div>'; }).join('') + '</div>' : empty(kind === 'group' ? 'groups' : 'channel', 'Belum ada ' + label, 'Buat ' + label + ' pertama atau buka halaman lagi ketika akun lain sudah membuat ruang publik.', 'Buat ' + label, 'create-community:' + kind));
    } catch (e) { showError(apiError(e), 'retry'); }
  }

  function profileHighlightStrip(highlights,self) {
    var list=highlights||[],html='<div class="ohli-profile-highlights" aria-label="Sorotan profil">';
    if(self)html+='<button type="button" class="ohli-profile-highlight" data-action="navigate" data-page="story"><span class="ohli-profile-highlight-ring add"><i>+</i></span><strong>Baru</strong></button>';
    list.forEach(function(h){var cover=h.mediaId?'<img src="'+esc(rt.mediaUrl(h.mediaId))+'" alt="" loading="lazy">':'<b>OHLI</b>';html+='<button type="button" class="ohli-profile-highlight" data-action="view-highlight" data-id="'+esc(h.id)+'"><span class="ohli-profile-highlight-ring">'+cover+'</span><strong>'+esc(h.name||'Sorotan')+'</strong></button>';});
    if(!self&&!list.length)return '';
    return html+'</div>';
  }
  async function renderProfile(targetPhone) {
    var me = rt.getMe() || {}; targetPhone = targetPhone || me.phone; profileTarget = targetPhone; showLoading();
    try {
      var data = await rt.request('/api/profile?phone=' + encodeURIComponent(targetPhone)); profileData = data;
      var u = data.user || {}, self = !!u.isSelf || u.phone === me.phone, locked = !!data.locked, hi = data.highlights || [];
      var followText = data.isFollowing ? 'Mengikuti' : (data.followRequested ? 'Diminta' : '+ Ikuti');
      var friendControl = data.isFriend ? '<button type="button" class="ohli-btn" data-action="friend-remove" data-phone="' + esc(u.phone) + '">Teman ✓</button>' : (data.friendRequestSent ? '<button type="button" class="ohli-btn" data-action="friend-cancel" data-phone="' + esc(u.phone) + '">Permintaan terkirim</button>' : (data.friendRequestReceived ? '<button type="button" class="ohli-btn ohli-btn-primary" data-action="friend-accept" data-phone="' + esc(u.phone) + '">Terima teman</button><button type="button" class="ohli-btn" data-action="friend-reject" data-phone="' + esc(u.phone) + '">Tolak</button>' : '<button type="button" class="ohli-btn ohli-btn-primary" data-action="friend-request" data-phone="' + esc(u.phone) + '">+ Tambah teman</button>'));
      var profileStories=[], profileLive=false;
      var profileAux=await Promise.all([rt.request('/api/status?surface=social').catch(function(){return {statuses:[]};}),rt.request('/api/live').catch(function(){return {lives:[]};})]);
      profileStories=(profileAux[0].statuses||[]).filter(function(st){return self?!!st.isMine:st.phone===u.phone;}).sort(function(a,b){return Number(a.time||0)-Number(b.time||0);});
      profileLive=(profileAux[1].lives||[]).some(function(l){return l.host===u.phone&&l.active!==false;});
      var hasProfileStory=profileStories.length>0,profileStorySeen=hasProfileStory&&profileStories.every(function(st){return !!st.viewedByMe;}),profileAvatarRing=(hasProfileStory?' has-story '+(profileStorySeen?'story-seen':'story-new'):'')+(profileLive?' is-live':'');
      if(!self)recordSocialActivity('profile',u.phone,u.name||socialHandle(u),{phone:u.phone});
      var hero = '<section class="ohli-card ohli-profile-hero"><div class="ohli-cover" style="background-image:url(/api/cover?phone=' + encodeURIComponent(u.phone) + '&token=' + encodeURIComponent(rt.getToken()) + '&v=' + avatarVer + '),linear-gradient(135deg,#243b7a,#8b5cf6)">' + (self ? '<button type="button" class="ohli-cover-menu" data-action="navigate" data-page="settings" aria-label="Pengaturan OHLI Social">' + ico('menu') + '</button>' : '') + '</div><div class="ohli-profile-summary"><button type="button" class="ohli-profile-avatar-button'+profileAvatarRing+'" data-action="profile-avatar" data-phone="' + esc(u.phone) + '" data-name="' + esc(u.name || 'Pengguna OHLI') + '" data-story-phone="'+(hasProfileStory?esc(self?me.phone:u.phone):'')+'" data-live="'+(profileLive?'1':'0')+'" aria-label="'+(hasProfileStory?'Buka Story atau tahan untuk foto profil':'Lihat foto profil')+'">' + avatar(u.name||'Pengguna OHLI',u.phone) + (data.user&&data.user.online?'<i class="ohli-online-indicator" aria-label="Sedang aktif"></i>':'') + '</button><div class="ohli-profile-title"><div><h2>' + esc(u.name || 'Pengguna OHLI') + (u.privateAccount ? ' <small class="ohli-private-tag">PRIVAT</small>' : '') + (data.user&&data.user.online?' <i class="ohli-online-indicator-inline" aria-label="Online"></i>':'') + '</h2><p>@' + esc(socialHandle(u)) + (self ? ' · Profil kamu' : '') + '</p></div><div style="display:flex;gap:7px;flex-wrap:wrap">' + (self ? '<button type="button" class="ohli-btn ohli-btn-primary" data-action="edit-profile">Edit profil</button>' : '<button type="button" class="ohli-btn ' + (data.isFollowing ? '' : 'ohli-btn-primary') + '" data-action="follow" data-phone="' + esc(u.phone) + '" data-following="' + (data.isFollowing ? '1' : '0') + '" data-requested="' + (data.followRequested ? '1' : '0') + '">' + esc(followText) + '</button>' + friendControl + (data.canDirectMessage!==false?'<button type="button" class="ohli-btn" data-action="open-chat-user" data-social-id="' + esc(u.socialId||u.publicId||u.phone) + '" data-phone="' + esc(u.phone) + '">Chat</button>':'') ) + '</div></div><p class="ohli-profile-bio">' + esc(u.about || 'Belum menambahkan bio.') + '</p>' + profileHighlightStrip(hi,self) + '<div class="ohli-profile-stats"><button type="button" data-action="profile-stat" data-type="posts"><strong>' + Number(data.postsCount || 0) + '</strong><small>Postingan</small></button><button type="button" data-action="show-follow-list" data-type="followers" data-phone="' + esc(u.phone) + '" ' + (!data.canSeeFollowers ? 'disabled title="Daftar dibatasi"' : '') + '><strong>' + Number(data.followersCount || 0) + '</strong><small>Pengikut</small></button><button type="button" data-action="show-follow-list" data-type="following" data-phone="' + esc(u.phone) + '" ' + (!data.canSeeFollowing ? 'disabled title="Daftar dibatasi"' : '') + '><strong>' + Number(data.followingCount || 0) + '</strong><small>Mengikuti</small></button>' + (self ? '<button type="button" data-action="profile-views"><strong>' + Number(data.profileViewsCount || 0) + '</strong><small>Tayangan profil</small></button>' : '') + '</div><div style="margin-top:16px;display:flex;gap:8px;flex-wrap:wrap"><button class="ohli-btn" data-action="copy-profile" type="button">' + ico('share') + ' Salin username</button>' + (self ? '<button class="ohli-btn" data-action="contact-qr" data-phone="' + esc(me.phone) + '" type="button">▦ QR kontak</button>' : '<button class="ohli-btn ohli-btn-danger" data-action="block-user" data-phone="' + esc(u.phone) + '" type="button">Blokir</button>') + (self ? '<button class="ohli-btn" data-action="profile-privacy" type="button">' + ico('shield') + ' Privasi</button>' : '') + '</div></div></section>';
      var tabs = locked ? '<div class="ohli-tab-strip"><button class="active" type="button" data-action="profile-tab" data-tab="about">Tentang</button></div>' : '<div class="ohli-tab-strip"><button class="active" type="button" data-action="profile-tab" data-tab="posts">Postingan</button><button type="button" data-action="profile-tab" data-tab="media">Media</button><button type="button" data-action="profile-tab" data-tab="highlights">Sorotan</button>' + (data.canSeeLikes ? '<button type="button" data-action="profile-tab" data-tab="liked">Disukai</button>' : '') + '<button type="button" data-action="profile-tab" data-tab="reposted">Posting ulang</button>' + (self ? '<button type="button" data-action="profile-tab" data-tab="saved">Disimpan</button><button type="button" data-action="profile-tab" data-tab="drafts">Draf</button>' : (data.canSeeSaved ? '<button type="button" data-action="profile-tab" data-tab="saved">Disimpan</button>' : '')) + '<button type="button" data-action="profile-tab" data-tab="about">Tentang</button></div>';
      var lockedHtml = locked ? '<div class="ohli-card ohli-privacy-lock"><div class="ohli-privacy-lock-icon">' + ico('shield') + '</div><strong>Akun ini privat</strong><p>Ikuti akun ini dan tunggu persetujuan untuk melihat postingan serta Sorotan.</p></div>' : '';
      pageContent.innerHTML = hero + tabs + '<div id="ohliProfileContent" class="ohli-feed-column">' + (locked ? lockedHtml : ((data.posts || []).map(postCard).join('') || empty('profile','Belum ada postingan','Postingan yang dapat dilihat pada profil ini akan ditampilkan di sini.'))) + '</div>'; observeSocialHistory(pageContent);
    } catch (e) { showError(apiError(e), 'retry'); }
  }

  async function renderArchives() {
    showLoading();
    var endpoint = archiveMode === 'saved' ? '/api/posts/saved' : '/api/posts/archive';
    try {
      var data = await rt.request(endpoint); posts = data.posts || [];
      pageContent.innerHTML = '<div class="ohli-feed-tabs"><button type="button" class="ohli-feed-tab ' + (archiveMode === 'archive' ? 'active' : '') + '" data-action="archive-mode" data-mode="archive">Arsip postingan</button><button type="button" class="ohli-feed-tab ' + (archiveMode === 'saved' ? 'active' : '') + '" data-action="archive-mode" data-mode="saved">Tersimpan</button></div>' + (posts.length ? '<div class="ohli-feed-column">' + posts.map(postCard).join('') + '</div>' : empty('bookmark', archiveMode === 'saved' ? 'Belum ada postingan tersimpan' : 'Arsip masih kosong', archiveMode === 'saved' ? 'Tekan Simpan pada postingan untuk mengumpulkannya secara pribadi.' : 'Arsip menyembunyikan postingan milikmu dari feed dan profil tanpa menghapusnya.'));
    } catch (e) { showError(apiError(e), 'retry'); }
  }

  async function renderNotifications() {
    showLoading();
    try {
      var data=await rt.request('/api/notifications'); notificationItems=data.notifications||[];
      var unread=notificationItems.filter(function(n){return !n.read;}).length;
      ['notifications','profile'].forEach(function(key){var item=shell.querySelector('.ohli-nav-link[data-page="'+key+'"]');if(!item)return;var old=item.querySelector('.ohli-nav-badge');if(old)old.remove();if(unread){var b=document.createElement('span');b.className='ohli-nav-badge';b.textContent=unread>9?'9+':String(unread);item.appendChild(b);}});[shell.querySelector('.ohli-topbar-notifications'),shell.querySelector('.ohli-reel-notifications')].forEach(function(item){if(!item)return;var old=item.querySelector('.ohli-nav-badge');if(old)old.remove();if(unread){var b=document.createElement('span');b.className='ohli-nav-badge';b.textContent=unread>9?'9+':String(unread);item.appendChild(b);}});
      pageContent.innerHTML='<div class="ohli-feed-column"><div class="ohli-card ohli-rail-card ohli-notification-head"><div><strong>'+unread+' belum dibaca</strong><p class="ohli-hint">Suka Story, komentar, pengikut, dan aktivitas akun tersimpan di server.</p></div><button type="button" class="ohli-btn" data-action="mark-all-read">Tandai semua dibaca</button></div>'+(notificationItems.length?'<div class="ohli-notification-list">'+notificationItems.map(function(n){var detail=n.type==='story_like'?'menyukai Story kamu':(n.text||'melakukan aktivitas');return '<button type="button" class="ohli-notification '+(!n.read?'unread':'')+'" data-action="open-notification" data-id="'+esc(n.id)+'" data-actor="'+esc(n.actor||'')+'" data-type="'+esc(n.type||'')+'" data-object-id="'+esc(n.objectId||'')+'">'+avatar(n.actorName||n.actor,n.actor)+'<span class="ohli-notification-copy"><strong>'+esc(n.actorName||n.actor||'OHLI')+'</strong><span>'+esc(detail)+'</span><small>'+esc(shortDate(n.time))+'</small></span><span class="ohli-notification-mark">'+(n.type==='story_like'?'♥':'•')+'</span></button>';}).join('')+'</div>':empty('bell','Belum ada notifikasi','Aktivitas dari pengguna lain akan muncul di sini.'))+'</div>';
    } catch(e){showError(apiError(e),'retry');}
  }

  function formatAudioDuration(seconds){seconds=Math.max(0,Number(seconds)||0);if(!seconds)return 'Durasi tidak tersedia';return Math.floor(seconds/60)+':'+String(Math.floor(seconds%60)).padStart(2,'0');}
  function musicTrackCard(t, picker) {
    var saved=!!t.saved;
    return '<article class="ohli-card ohli-music-card"><div class="ohli-music-icon">♫</div><div class="ohli-music-main"><strong>'+esc(t.title||'Audio OHLI')+'</strong><small>'+esc(t.artist||t.ownerName||'Pengguna OHLI')+' · '+formatAudioDuration(t.durationSeconds)+' · '+Number(t.uses||0)+' penggunaan'+(t.isMine?' · Audio Saya':'')+'</small><audio controls preload="none" src="'+esc(rt.mediaUrl(t.mediaId))+'"></audio></div><div class="ohli-music-actions">'+(picker?'<button type="button" class="ohli-btn ohli-btn-primary" data-action="music-attach" data-music-id="'+esc(t.id)+'">Gunakan</button>':'<button type="button" class="ohli-btn" data-action="music-detail" data-music-id="'+esc(t.id)+'">Detail</button><button type="button" class="ohli-btn '+(saved?'is-active':'')+'" data-action="music-save" data-music-id="'+esc(t.id)+'">'+(saved?'Tersimpan':'Simpan')+'</button><button type="button" class="ohli-btn" data-action="music-use" data-music-id="'+esc(t.id)+'">Gunakan audio</button>'+(t.isMine?'<button type="button" class="ohli-btn ohli-btn-danger" data-action="music-delete" data-music-id="'+esc(t.id)+'">Hapus audio</button>':'') )+'</div></article>';
  }

  function readAudioDuration(file){return new Promise(function(resolve){var url='',audio=document.createElement('audio'),done=false,timer=setTimeout(finish,6500);function finish(){if(done)return;done=true;clearTimeout(timer);audio.removeAttribute('src');audio.load();if(url)URL.revokeObjectURL(url);resolve(Number.isFinite(audio.duration)&&audio.duration>0?Math.min(7200,Math.round(audio.duration)):0);}try{url=URL.createObjectURL(file);audio.preload='metadata';audio.onloadedmetadata=finish;audio.onerror=finish;audio.src=url;}catch(e){finish();}});}

  async function renderMusic() {
    pageContent.innerHTML='<div class="ohli-feed-column"><section class="ohli-card ohli-music-upload"><div class="ohli-rail-title">Tambah musik ke katalog</div><p class="ohli-hint">Unggah audio milikmu atau audio yang memang boleh kamu bagikan. Format MP3, AAC/M4A, OGG, WAV, atau audio WebM.</p><form id="ohliMusicUploadForm"><div class="ohli-music-upload-grid"><label class="ohli-field">Judul audio<input name="title" maxlength="100" placeholder="Nama lagu / audio"></label><label class="ohli-field">Artis / sumber<input name="artist" maxlength="80" placeholder="Nama pembuat"></label><label class="ohli-field ohli-music-file">File audio<input name="file" type="file" accept="audio/*" required></label><button type="submit" class="ohli-btn ohli-btn-primary">Unggah audio</button></div></form></section><div class="ohli-feed-tabs"><button type="button" class="ohli-feed-tab '+(musicViewMode==='catalog'?'active':'')+'" data-action="music-mode" data-mode="catalog">Jelajahi musik</button><button type="button" class="ohli-feed-tab '+(musicViewMode==='favorites'?'active':'')+'" data-action="music-mode" data-mode="favorites">Favorit</button><button type="button" class="ohli-feed-tab '+(musicViewMode==='mine'?'active':'')+'" data-action="music-mode" data-mode="mine">Audio Saya</button></div><div id="ohliMusicList"><div class="ohli-loading"><div class="ohli-skeleton"></div></div></div></div>';
    var target=pageContent.querySelector('#ohliMusicList');
    try {var res=await rt.request(musicViewMode==='favorites'?'/api/music/favorites':(musicViewMode==='mine'?'/api/music/mine':'/api/music'));musicCache=res.music||[];target.innerHTML=musicCache.length?musicCache.map(function(t){return musicTrackCard(t,false);}).join(''):empty('music',musicViewMode==='favorites'?'Belum ada audio favorit':(musicViewMode==='mine'?'Audio Saya masih kosong':'Belum ada musik di katalog'),musicViewMode==='favorites'?'Simpan audio dari katalog agar mudah digunakan saat membuat video.':(musicViewMode==='mine'?'Audio impor akan tersimpan di sini dan tetap tersedia setelah masuk kembali.':'Jadilah yang pertama mengunggah audio yang boleh dibagikan.'));}
    catch(e){target.innerHTML='<div class="ohli-error">'+esc(apiError(e))+'</div>';}
  }

  async function showMusicDetail(id) {
    try {var d=await rt.request('/api/music/detail?id='+encodeURIComponent(id)),t=d.music,clips=d.videos||[];var html='<div class="ohli-music-detail"><div class="ohli-music-icon big">♫</div><h3>'+esc(t.title)+'</h3><p class="ohli-hint">'+esc(t.artist||t.ownerName)+' · '+Number(t.uses||0)+' video menggunakan audio ini</p><audio controls preload="metadata" src="'+esc(rt.mediaUrl(t.mediaId))+'"></audio><div class="ohli-music-actions"><button type="button" class="ohli-btn '+(t.saved?'is-active':'')+'" data-action="music-save" data-from="detail" data-music-id="'+esc(t.id)+'">'+(t.saved?'Hapus dari koleksi':'Simpan musik')+'</button><button type="button" class="ohli-btn ohli-btn-primary" data-action="music-use" data-music-id="'+esc(t.id)+'">Gunakan audio ini</button></div><h4>Video yang memakai audio ini</h4><div class="ohli-music-uses">'+(clips.length?clips.map(function(p){return '<div class="ohli-music-use-row">'+(p.mediaId?'<img src="'+esc(rt.mediaUrl(p.mediaId))+'" alt="" loading="lazy">':'<span class="ohli-music-icon">♫</span>')+'<span><strong>'+esc(p.authorName||'Pengguna OHLI')+'</strong><small>'+esc(p.text||'Video OHLI')+'</small></span></div>';}).join(''):'<p class="ohli-hint">Belum ada video yang menggunakan audio ini.</p>')+'</div></div>';showModal('Detail audio',html,'',null);}
    catch(e){toast(apiError(e));}
  }

  function showOriginalAudioDetail(postId) {
    var p=findPost(postId);if(!p||!p.mediaId||!isVid(p)){toast('Video sumber sudah tidak tersedia.');return;}
    var title=p.audioTitle||p.soundTitle||('Audio asli · '+(p.authorName||p.author));
    var html='<div class="ohli-music-detail ohli-original-audio-detail"><div class="ohli-vinyl"><span>♫</span></div><h3>'+esc(title)+'</h3><p class="ohli-hint">Suara asli dari video @'+esc(p.authorName||'Pengguna OHLI')+'. Simpan ke koleksi atau gunakan pada video kamu.</p><div class="ohli-original-preview"><video id="ohliOriginalAudioPreview" playsinline loop muted preload="metadata" src="'+esc(rt.mediaUrl(p.mediaId))+'"></video><button type="button" data-action="toggle-original-preview" aria-label="Putar pratinjau">Ⅱ</button></div><div class="ohli-music-actions"><button type="button" class="ohli-btn" data-action="music-original-save" data-post-id="'+esc(p.id)+'">Simpan suara</button><button type="button" class="ohli-btn ohli-btn-primary" data-action="music-original-use" data-post-id="'+esc(p.id)+'">Gunakan audio ini</button></div></div>';
    showModal('Audio asli',html,'',null);var v=overlayContent.querySelector('#ohliOriginalAudioPreview');if(v){v.muted=true;var pr=v.play();if(pr&&pr.catch)pr.catch(function(){});}
  }
  async function importOriginalVideoAudio(postId,saveOnly) {
    var p=findPost(postId);if(!p||!p.mediaId)throw new Error('Video sumber tidak tersedia.');
    /* Create/resume AudioContext synchronously in the user's click gesture. The extractor no longer calls video.play(), which browsers may reject after an await. */
    var AC=window.AudioContext||window.webkitAudioContext,activationContext=null,activationReady=Promise.resolve();
    if(!AC)throw new Error('Browser ini belum menyediakan Web Audio untuk mengambil suara video.');
    try{activationContext=new AC();if(activationContext.state==='suspended'){activationReady=activationContext.resume();if(activationReady&&activationReady.catch)activationReady.catch(function(){});}}catch(ctxError){throw new Error('AudioContext tidak dapat diaktifkan. Ketuk kembali tombol Gunakan/Simpan audio.');}
    toast('Mengambil suara asli dari video…');
    var response;try{response=await fetch(rt.mediaUrl(p.mediaId),{credentials:'same-origin',cache:'no-store'});}catch(fetchError){try{activationContext.close();}catch(ignore){}throw fetchError;}
    if(!response.ok){try{activationContext.close();}catch(ignore){}throw new Error('Video sumber tidak dapat diunduh dari server.');}
    var blob;try{blob=await response.blob();}catch(blobError){try{activationContext.close();}catch(ignore){}throw blobError;}var mime=blob.type||p.mimeType||'video/mp4';
    var sourceFile=new File([blob],(p.authorName||'OHLI')+'-original-video.'+(mime.indexOf('webm')>=0?'webm':'mp4'),{type:mime});
    var title=p.audioTitle||p.soundTitle||('Audio asli · '+(p.authorName||p.author));
    var audioFile=await extractAudioFromVideo(sourceFile,activationContext,activationReady);
    var uploaded=await rt.uploadMusic(audioFile,title,p.authorName||p.author||'');
    if(!uploaded||!uploaded.music)throw new Error('Audio berhasil diambil tetapi gagal disimpan ke katalog.');
    musicCache.unshift(uploaded.music);
    if(saveOnly){var saved=await rt.request('/api/music/favorites',{method:'POST',body:{musicId:uploaded.music.id,action:'save'}});uploaded.music.saved=true;closeModal();toast(saved&&saved.saved===false?'Suara sudah ada di koleksi.':'Suara asli disimpan ke koleksi musikmu.');return;}
    closeModal();
    if(page==='create'&&cr&&cr.kind==='video'){cr.audioTrack=uploaded.music;crEdit();toast('Suara asli siap dipakai di video.');}
    else{pendingMusicTrack=uploaded.music;setPage('create');toast('Suara asli siap dipakai. Pilih video dari galeri.');}
  }

  var musicPickerMode = 'trending';
  function audioPickerTab(mode, label) { return '<button type="button" class="ohli-audio-source-tab '+(musicPickerMode===mode?'active':'')+'" data-action="music-source-tab" data-source="'+mode+'">'+label+'</button>'; }
  function renderAudioPickerSource(mode) {
    musicPickerMode=mode;
    var target=overlayContent&&overlayContent.querySelector('#ohliAudioSourceContent'); if(!target)return;
    overlayContent.querySelectorAll('.ohli-audio-source-tab').forEach(function(b){b.classList.toggle('active',b.dataset.source===mode);});
    if(mode==='trending'){
      var hot=(musicCache||[]).slice().sort(function(a,b){return Number(b.uses||0)-Number(a.uses||0);});
      target.innerHTML='<div class="ohli-music-picker-list">'+(hot.length?hot.map(function(t){return musicTrackCard(t,true);}).join(''):'<p class="ohli-hint">Belum ada musik tren. Musik yang sering digunakan akan muncul di sini.</p>')+'</div><p class="ohli-hint">Urutan tren mengikuti jumlah penggunaan audio pada video OHLI.</p>';
    } else if(mode==='saved'){
      target.innerHTML='<div class="ohli-loading"><div class="ohli-skeleton"></div></div>';
      rt.request('/api/music/favorites').then(function(d){var saved=d.music||[];saved.forEach(function(t){t.saved=true;if(!musicCache.some(function(x){return x.id===t.id;}))musicCache.push(t);});if(!target.isConnected)return;target.innerHTML='<div class="ohli-music-picker-list">'+(saved.length?saved.map(function(t){return musicTrackCard(t,true);}).join(''):'<p class="ohli-hint">Kamu belum menyimpan musik. Simpan audio dari video atau halaman Musik.</p>')+'</div>';}).catch(function(e){if(target.isConnected)target.innerHTML='<p class="ohli-error">'+esc(apiError(e))+'</p>';});
    } else if(mode==='mine'){
      target.innerHTML='<div class="ohli-loading"><div class="ohli-skeleton"></div></div>';
      rt.request('/api/music/mine').then(function(d){var own=d.music||[];own.forEach(function(t){if(!musicCache.some(function(x){return x.id===t.id;}))musicCache.push(t);});if(!target.isConnected)return;target.innerHTML='<div class="ohli-music-picker-list">'+(own.length?own.map(function(t){return musicTrackCard(t,true);}).join(''):'<p class="ohli-hint">Belum ada Audio Saya. Impor file yang boleh kamu gunakan.</p>')+'</div>';}).catch(function(e){if(target.isConnected)target.innerHTML='<p class="ohli-error">'+esc(apiError(e))+'</p>';});
    } else if(mode==='video'){
      target.innerHTML='<section class="ohli-audio-source-panel"><div class="ohli-audio-source-icon">▣</div><strong>Ambil musik dari video galeri</strong><p>Pilih video dari perangkat. OHLI mengambil audionya, lalu menyimpannya sebagai audio yang bisa dipilih lagi.</p><label class="ohli-btn ohli-btn-primary ohli-audio-file-button">Pilih video<input id="ohliMusicExtractFile" type="file" accept="video/*"></label><small>Hasil mengikuti dukungan browser, biasanya WebM/Opus. Tidak melakukan konversi paksa ke MP3/AAC.</small></section>';
    } else {
      target.innerHTML='<section class="ohli-audio-source-panel"><div class="ohli-audio-source-icon">♫</div><strong>Musik dari file perangkat</strong><p>Tambahkan MP3, AAC/M4A, OGG, WAV, atau format audio yang didukung server.</p><label class="ohli-btn ohli-btn-primary ohli-audio-file-button">Pilih file audio<input id="ohliMusicLocalFile" type="file" accept="audio/*"></label></section>';
    }
  }
  async function openAudioPicker() {
    try {
      var d=await rt.request('/api/music'); musicCache=d.music||[]; musicPickerMode='trending';
      var body='<p class="ohli-hint">Pilih suara untuk video kamu. Audio Saya disimpan pada server akun ini.</p><div class="ohli-audio-source-tabs">'+audioPickerTab('trending','Musik tren')+audioPickerTab('saved','Disimpan')+audioPickerTab('mine','Audio Saya')+audioPickerTab('video','Dari video')+audioPickerTab('file','Dari file')+'</div><div id="ohliAudioSourceContent"></div>';
      showModal('Tambahkan suara',body,'',null); renderAudioPickerSource('trending');
    }catch(e){toast(apiError(e));}
  }

  function chooseRecorderMime(video) {
    if(!window.MediaRecorder)return '';
    var types=video?['video/webm;codecs=vp9,opus','video/webm;codecs=vp8,opus','video/webm','video/mp4;codecs=avc1.42E01E,mp4a.40.2','video/mp4']:['audio/webm;codecs=opus','audio/ogg;codecs=opus','audio/mp4'];
    for(var i=0;i<types.length;i++){try{if(MediaRecorder.isTypeSupported(types[i]))return types[i];}catch(e){}}
    return '';
  }

  function extractAudioFromVideo(file, activationContext, activationReady) {
    return new Promise(function(resolve,reject){
      var AC=window.AudioContext||window.webkitAudioContext;
      if(!AC){reject(new Error('Browser ini belum mendukung Web Audio untuk ekstraksi audio.'));return;}
      var ctx=activationContext||null,source=null,destination=null,recorder=null,chunks=[],settled=false,ready=activationReady||Promise.resolve();
      try{if(!ctx)ctx=new AC();if(!activationContext&&ctx.state==='suspended'){ready=ctx.resume();if(ready&&ready.catch)ready.catch(function(){});}}catch(e){reject(new Error('AudioContext tidak dapat dibuat pada browser ini.'));return;}
      function cleanup(){try{if(source)source.disconnect();}catch(e){}try{if(destination)destination.stream.getTracks().forEach(function(t){t.stop();});}catch(e){}if(ctx&&ctx.state!=='closed')try{ctx.close();}catch(e){}}
      function fail(message){if(settled)return;settled=true;try{if(source)source.stop();}catch(e){}try{if(recorder&&recorder.state!=='inactive')recorder.stop();}catch(e){}cleanup();reject(new Error(message));}
      if(!window.MediaRecorder){cleanup();reject(new Error('Browser ini belum mendukung perekaman audio hasil ekstraksi. Gunakan Chrome/Edge terbaru.'));return;}
      var reader=new FileReader();
      reader.onerror=function(){fail('File video tidak dapat dibaca.');};
      reader.onload=async function(){
        try{
          await ready;if(ctx.state!=='running')throw new Error('Browser belum mengizinkan pemrosesan audio. Ketuk kembali tombol Simpan/Gunakan audio, lalu izinkan suara.');
          var raw=reader.result;
          var decoded=await ctx.decodeAudioData(raw.slice?raw.slice(0):raw);
          if(!decoded||!decoded.length||!decoded.duration)throw new Error('Video tidak memiliki trek audio yang dapat dibaca.');
          destination=ctx.createMediaStreamDestination();source=ctx.createBufferSource();source.buffer=decoded;source.connect(destination);
          var tracks=destination.stream.getAudioTracks();if(!tracks.length)throw new Error('Trek audio tidak dapat dibuat.');
          var mime=chooseRecorderMime(false),options=mime?{mimeType:mime}:undefined;
          recorder=new MediaRecorder(destination.stream,options);
          recorder.ondataavailable=function(e){if(e.data&&e.data.size)chunks.push(e.data);};
          recorder.onerror=function(){fail('Browser gagal merekam trek audio.');};
          recorder.onstop=function(){if(settled)return;settled=true;var type=recorder.mimeType||mime||'audio/webm';var blob=new Blob(chunks,{type:type});cleanup();if(!blob.size){reject(new Error('Hasil ekstraksi audio kosong.'));return;}var ext=type.indexOf('ogg')>=0?'.ogg':(type.indexOf('mp4')>=0?'.m4a':'.webm');resolve(new File([blob],file.name.replace(/\.[^.]+$/,'')+'-audio'+ext,{type:type}));};
          source.onended=function(){if(recorder&&recorder.state!=='inactive')recorder.stop();};
          recorder.start(500);source.start(0);
        }catch(e){fail(e&&e.message?e.message:'Ekstraksi audio gagal.');}
      };
      try{reader.readAsArrayBuffer(file);}catch(e){fail('File video tidak dapat dibaca.');}
    });
  }

  function renderPhotoWithMusic(imageFile, track, settings) {
    settings=settings||{};
    return new Promise(function(resolve,reject){
      if(!window.MediaRecorder||!HTMLCanvasElement.prototype.captureStream){reject(new Error('Browser ini belum mendukung ekspor foto menjadi video. Coba Safari/iOS terbaru atau Chrome terbaru.'));return;}
      var AC=window.AudioContext||window.webkitAudioContext;if(!AC){reject(new Error('Web Audio tidak tersedia pada browser ini.'));return;}
      var image=new Image(),canvas=document.createElement('canvas'),ctx2=canvas.getContext('2d'),audioCtx,source,gain,destination,stream,recorder,chunks=[],timer=null,drawTimer=null,settled=false,objectUrl=URL.createObjectURL(imageFile);
      var duration=Math.max(3,Math.min(180,Number(settings.duration)||Number(track.durationSeconds||track.duration)||15));
      var crop=settings.crop||'orig',ratio=crop==='9:16'?9/16:(crop==='4:5'?4/5:(crop==='1:1'?1:0));
      function cleanup(){if(timer)clearTimeout(timer);if(drawTimer)clearInterval(drawTimer);try{if(recorder&&recorder.state!=='inactive')recorder.stop();}catch(e){}try{if(source)source.stop();}catch(e){}try{if(stream)stream.getTracks().forEach(function(t){t.stop();});}catch(e){}try{if(audioCtx&&audioCtx.state!=='closed')audioCtx.close();}catch(e){}try{URL.revokeObjectURL(objectUrl);}catch(e){}}
      function fail(message){if(settled)return;settled=true;cleanup();reject(new Error(message));}
      image.onerror=function(){fail('Foto tidak dapat dibaca. Pilih JPG, PNG, HEIC yang didukung browser, atau ekspor ulang dari galeri.');};
      image.onload=async function(){
        try{
          var w=720,h=ratio?Math.round(720/ratio):Math.max(1,Math.round(720*image.naturalHeight/image.naturalWidth));if(h>1920){h=1920;w=ratio?Math.round(h*ratio):Math.round(h*image.naturalWidth/image.naturalHeight);}canvas.width=w;canvas.height=h;
          audioCtx=new AC();var resumePromise=audioCtx.resume();
          var response=await fetch(rt.mediaUrl(track.mediaId),{credentials:'same-origin'});if(!response.ok)throw new Error('Musik tidak dapat diunduh dari server. Periksa koneksi dan coba lagi.');
          var audioBuffer=await audioCtx.decodeAudioData(await response.arrayBuffer());if(!audioBuffer||!audioBuffer.duration)throw new Error('File musik tidak memiliki audio yang dapat diproses.');
          await resumePromise;if(audioCtx.state!=='running')throw new Error('Browser belum mengaktifkan audio. Ketuk tombol Bagikan lagi setelah mengaktifkan suara.');
          source=audioCtx.createBufferSource();source.buffer=audioBuffer;source.loop=true;gain=audioCtx.createGain();gain.gain.value=Math.max(0,Math.min(1,Number(settings.musicVolume==null?0.85:settings.musicVolume)));destination=audioCtx.createMediaStreamDestination();source.connect(gain);gain.connect(destination);
          function draw(){ctx2.fillStyle='#000';ctx2.fillRect(0,0,w,h);var scale=Math.max(w/image.naturalWidth,h/image.naturalHeight),dw=image.naturalWidth*scale,dh=image.naturalHeight*scale;ctx2.drawImage(image,(w-dw)/2,(h-dh)/2,dw,dh);(settings.items||[]).forEach(function(it){ctx2.save();ctx2.fillStyle=it.c||'#fff';ctx2.font='bold '+Math.max(22,Math.round(Number(it.s||7)*w/100))+'px system-ui,sans-serif';ctx2.textAlign='center';ctx2.textBaseline='middle';ctx2.shadowColor='#0009';ctx2.shadowBlur=5;ctx2.fillText(String(it.t||'').slice(0,60),w*Number(it.x||50)/100,h*Number(it.y||50)/100,w*.9);ctx2.restore();});}
          draw();stream=canvas.captureStream(24);destination.stream.getAudioTracks().forEach(function(t){stream.addTrack(t);});var mime=chooseRecorderMime(true),opts=mime?{mimeType:mime,videoBitsPerSecond:3500000}:undefined;recorder=new MediaRecorder(stream,opts);
          recorder.ondataavailable=function(e){if(e.data&&e.data.size)chunks.push(e.data);};recorder.onerror=function(){fail('Browser gagal merender video foto dan musik.');};
          recorder.onstop=function(){if(settled)return;settled=true;var type=recorder.mimeType||mime||'video/webm',blob=new Blob(chunks,{type:type});cleanup();if(!blob.size){reject(new Error('Hasil video kosong. Coba browser lain.'));return;}var ext=type.indexOf('mp4')>=0?'.mp4':'.webm';resolve(new File([blob],'ohli-photo-music'+ext,{type:type,lastModified:Date.now()}));};
          recorder.start(500);source.start(0);drawTimer=setInterval(draw,1000/24);timer=setTimeout(function(){if(recorder&&recorder.state!=='inactive')recorder.stop();},duration*1000);
        }catch(e){fail(e&&e.message?e.message:'Gagal membuat video dari foto dan musik.');}
      };
      image.src=objectUrl;
    });
  }

  function showPhotoVideoPreview(file,button){
    if(!cr||!file)return;cr.renderedFile=file;try{if(cr.renderedUrl)URL.revokeObjectURL(cr.renderedUrl);}catch(e){}cr.renderedUrl=URL.createObjectURL(file);
    var stage=pageContent.querySelector('#crStage');if(stage){stage.classList.add('ohli-vwrap');stage.innerHTML='<video src="'+esc(cr.renderedUrl)+'" controls playsinline preload="metadata" style="width:100%;height:100%;object-fit:contain;background:#000"></video>';}
    var hint=pageContent.querySelector('#crAudioStatus');if(hint)hint.textContent='Pratinjau video hasil gabungan sudah siap. Periksa gambar dan suara sebelum menerbitkan.';
    button.disabled=false;button.textContent=cr.target==='story'?'Bagikan Story video':'Posting video';toast('Pratinjau video selesai. Periksa hasilnya lalu tekan tombol sekali lagi untuk mengunggah.');
  }
  function mixVideoWithMusic(videoFile, track, settings) {
    return new Promise(function(resolve,reject){
      var capture=HTMLVideoElement.prototype.captureStream||HTMLVideoElement.prototype.mozCaptureStream;
      if(!capture||!window.MediaRecorder){reject(new Error('Pencampuran audio belum didukung browser ini. Gunakan Chrome atau Edge terbaru.'));return;}
      var url=URL.createObjectURL(videoFile),v=document.createElement('video'),a=new Audio(rt.mediaUrl(track.mediaId)),ctx=null,rec=null,chunks=[],done=false,stream=null;
      var AC=window.AudioContext||window.webkitAudioContext;if(!AC){reject(new Error('Web Audio tidak tersedia.'));URL.revokeObjectURL(url);return;}
      try{ctx=new AC();}catch(ctxError){reject(new Error('AudioContext tidak dapat dibuat pada browser ini.'));URL.revokeObjectURL(url);return;}
      var resumeAudio=ctx.resume();
      v.src=url;v.preload='auto';v.playsInline=true;v.muted=false;a.preload='auto';a.loop=true;
      function cleanup(){try{v.pause();a.pause();}catch(e){}try{if(ctx)ctx.close();}catch(e){}try{if(stream)stream.getTracks().forEach(function(t){t.stop();});}catch(e){}URL.revokeObjectURL(url);}
      function fail(message){if(done)return;done=true;cleanup();reject(new Error(message));}
      v.onerror=function(){fail('Video sumber gagal dibaca.');};a.onerror=function(){fail('Audio musik tidak dapat dimuat dari server.');};
      v.onloadedmetadata=async function(){
        try{
          await new Promise(function(res,rej){if(a.readyState>=2)res();else{a.oncanplay=res;a.onloadeddata=res;a.onerror= function(){rej(new Error('Audio gagal dimuat.'));};}});
          var vs=ctx.createMediaElementSource(v),as=ctx.createMediaElementSource(a),vg=ctx.createGain(),ag=ctx.createGain(),dest=ctx.createMediaStreamDestination();
          vg.gain.value=settings.mute?0:Math.max(0,Math.min(1,Number(settings.originalVolume == null ? 0.2 : settings.originalVolume)));ag.gain.value=Math.max(0,Math.min(1,Number(settings.musicVolume == null ? 0.85 : settings.musicVolume)));
          vs.connect(vg);vg.connect(dest);as.connect(ag);ag.connect(dest);
          var captureStream=capture.call(v);var videoTracks=captureStream.getVideoTracks();if(!videoTracks.length)throw new Error('Browser tidak dapat menangkap gambar video.');
          stream=new MediaStream(videoTracks.concat(dest.stream.getAudioTracks()));var mime=chooseRecorderMime(true),opts=mime?{mimeType:mime}:undefined;rec=new MediaRecorder(stream,opts);
          rec.ondataavailable=function(e){if(e.data&&e.data.size)chunks.push(e.data);};rec.onerror=function(){fail('Browser gagal menggabungkan video dan musik.');};
          rec.onstop=function(){if(done)return;done=true;var type=rec.mimeType||mime||'video/webm';var blob=new Blob(chunks,{type:type});cleanup();if(!blob.size){reject(new Error('Hasil gabungan kosong.'));return;}resolve(new File([blob],'ohli-video-mix'+(type.indexOf('mp4')>=0?'.mp4':'.webm'),{type:type}));};
          await resumeAudio;v.currentTime=Math.max(0,Number(settings.start||0));a.currentTime=0;rec.start(1000);await Promise.all([v.play(),a.play()]);
          v.ontimeupdate=function(){var end=Number(settings.end||0);if(end>0&&v.currentTime>=end&&rec.state!=='inactive')rec.stop();};v.onended=function(){if(rec.state!=='inactive')rec.stop();};
        }catch(e){if(rec&&rec.state!=='inactive')try{rec.stop();}catch(ignore){}fail(e.message||'Pencampuran audio gagal.');}
      };
    });
  }

  async function openStoryCollections(){try{var pair=await Promise.all([rt.request('/api/status/collections?mode=pinned'),rt.request('/api/status/collections?mode=archived')]),map={};(pair[0].items||[]).forEach(function(x){map[x.statusId]=x;});(pair[1].items||[]).forEach(function(x){map[x.statusId]=x;});var items=Object.keys(map).map(function(k){return map[k];}).sort(function(a,b){return Number(b.savedAt||0)-Number(a.savedAt||0);});var html=items.length?'<div class="ohli-story-collection-list">'+items.map(function(x){var isVideo=(x.mediaType==='video'||(x.mimeType||'').indexOf('video/')===0),src=x.mediaId?(isVideo?rt.mediaUrl(x.mediaId):rt.imageUrl(x.mediaId)):'';return '<article class="ohli-card ohli-collection-row">'+(x.mediaId?(isVideo?'<video src="'+esc(src)+'" controls playsinline preload="metadata"></video>':'<img src="'+esc(src)+'" alt="Story tersimpan">'):'')+'<div><strong>'+esc(x.ownerName||'Story teman')+'</strong><p>'+esc(x.text||'Story tanpa teks')+'</p><small>'+esc(shortDate(x.storyTime))+' · '+(x.pinned?'Disematkan':'Tidak dipin')+' · '+(x.archived?'Diarsipkan':'Belum diarsipkan')+'</small><div class="ohli-collection-actions">'+(x.pinned?'<button class="ohli-btn" data-action="story-collection-manage" data-story-id="'+esc(x.statusId)+'" data-kind="unpin">Lepas pin</button>':'<button class="ohli-btn" data-action="story-collection-manage" data-story-id="'+esc(x.statusId)+'" data-kind="pin">Pin</button>')+(x.archived?'<button class="ohli-btn" data-action="story-collection-manage" data-story-id="'+esc(x.statusId)+'" data-kind="unarchive">Keluarkan arsip</button>':'<button class="ohli-btn" data-action="story-collection-manage" data-story-id="'+esc(x.statusId)+'" data-kind="archive">Arsipkan</button>')+'<button class="ohli-btn ohli-btn-danger" data-action="story-collection-manage" data-story-id="'+esc(x.statusId)+'" data-kind="remove">Hapus</button></div></div></article>';}).join('')+'</div>':'<p class="ohli-hint">Belum ada Story teman yang dipin atau diarsipkan. Saat melihat Story teman, gunakan tombol Pin atau Arsip.</p>';showModal('Koleksi Story teman',html,'',null);}catch(e){toast(apiError(e));}}

  async function renderSettings() {
    var me = rt.getMe() || {}, connection = 'Memeriksa server…';
    pageContent.innerHTML = '<div class="ohli-home-grid"><div class="ohli-feed-column"><section class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Akun dan identitas</div><div class="ohli-list-card" style="padding:0 0 14px">' + avatar(me.name,me.phone) + '<div class="ohli-list-copy"><strong>' + esc(me.name || 'Akun OHLI') + '</strong><p>Identitas sosial: @' + esc(socialHandle(me)) + '</p></div><button class="ohli-btn" data-action="edit-profile">Edit</button></div><div class="ohli-settings-tools"><button type="button" class="ohli-btn" data-action="contact-qr" data-phone="' + esc(me.phone || '') + '">▦ QR kontak saya</button><button type="button" class="ohli-btn" data-action="scan-contact-qr">Pindai QR teman</button></div></section><section class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Privasi & keamanan</div><p class="ohli-hint">Atur akun privat, siapa yang boleh melihat daftar pengikut, akun yang diikuti, postingan disukai, dan koleksi tersimpan.</p><button type="button" class="ohli-btn ohli-btn-primary" data-action="profile-privacy">Atur privasi akun</button><button type="button" class="ohli-btn" data-action="follow-requests" style="margin-left:7px">Permintaan mengikuti</button><button type="button" class="ohli-btn" data-action="blocked-list" style="margin-left:7px">Daftar diblokir</button><div class="ohli-settings-sub"><h3>Ganti kata sandi</h3><label class="ohli-field">Kata sandi saat ini<input id="ohliCurrentPassword" type="password" autocomplete="current-password" minlength="6"></label><label class="ohli-field">Kata sandi baru<input id="ohliNewPassword" type="password" autocomplete="new-password" minlength="6" maxlength="100"></label><button type="button" class="ohli-btn ohli-btn-primary" data-action="change-password">Simpan kata sandi baru</button></div></section><section class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Tema / Tampilan</div><p class="ohli-hint">Mode terang, malam, dan Orange tetap tersedia. Kamu juga dapat mengatur warna ikon dan tulisan tanpa mengubah tata letak.</p><div class="ohli-setting-toggles"><button type="button" class="ohli-setting-toggle" data-action="set-theme" data-theme="light"><span>Mode terang</span><strong>' + ((rt.getTheme&&rt.getTheme()==='light')?'AKTIF':'PILIH') + '</strong></button><button type="button" class="ohli-setting-toggle" data-action="set-theme" data-theme="dark"><span>Mode malam</span><strong>' + ((rt.getTheme&&rt.getTheme()==='dark')?'AKTIF':'PILIH') + '</strong></button><button type="button" class="ohli-setting-toggle" data-action="set-theme" data-theme="orange"><span>Tema Orange</span><strong>' + ((rt.getTheme&&rt.getTheme()==='orange')?'AKTIF':'PILIH') + '</strong></button><button type="button" class="ohli-btn" data-action="theme-custom">Pengaturan tema</button></div></section><section class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Video & layar</div><p class="ohli-hint">Preferensi ini disimpan di browser/perangkat ini. Browser tetap dapat meminta sentuhan pertama sebelum mengizinkan suara autoplay.</p><div class="ohli-setting-toggles"><button type="button" class="ohli-setting-toggle" data-action="toggle-reel-sound"><span>Suara Reels</span><strong>' + (soundOn?'ON':'OFF') + '</strong></button><button type="button" class="ohli-setting-toggle" data-action="toggle-reel-autoplay"><span>Autoplay video pendek</span><strong>' + (reelAutoplay?'ON':'OFF') + '</strong></button><button type="button" class="ohli-setting-toggle" data-action="toggle-anti-zoom"><span>Kunci layar web / Anti-zoom</span><strong>' + (antiZoom?'ON':'OFF') + '</strong></button><button type="button" class="ohli-setting-toggle" data-action="set-reel-nav-bg" data-mode="black"><span>Latar navigasi video · Hitam</span><strong>' + (reelNavBackground==='black'?'AKTIF':'PILIH') + '</strong></button><button type="button" class="ohli-setting-toggle" data-action="set-reel-nav-bg" data-mode="transparent"><span>Latar navigasi video · Transparan</span><strong>' + (reelNavBackground==='transparent'?'AKTIF':'PILIH') + '</strong></button></div><p class="ohli-hint">Pengaturan latar navigasi hanya mengubah latar bar video pendek. Ikon navigasi tetap putih dan dapat digunakan.</p><p class="ohli-hint">Anti-zoom membatasi pinch zoom halaman. Nonaktifkan kembali untuk mengizinkan pembesaran teks/halaman.</p></section><section class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Tampilan Sorotan</div><p class="ohli-hint">Atur bentuk dan posisi thumbnail Sorotan di profil. Pengaturan ini hanya mengubah tampilan, bukan file Story asli.</p><label class="ohli-field">Bentuk thumbnail<select id="ohliHighlightShape"><option value="circle" '+(highlightThumbShape==='circle'?'selected':'')+'>Lingkaran</option><option value="rounded" '+(highlightThumbShape==='rounded'?'selected':'')+'>Sudut membulat</option><option value="square" '+(highlightThumbShape==='square'?'selected':'')+'>Kotak</option></select></label><label class="ohli-field">Tampilan gambar<select id="ohliHighlightFit"><option value="cover" '+(highlightThumbFit==='cover'?'selected':'')+'>Isi penuh</option><option value="contain" '+(highlightThumbFit==='contain'?'selected':'')+'>Tampilkan seluruh gambar</option><option value="top" '+(highlightThumbFit==='top'?'selected':'')+'>Fokus bagian atas</option></select></label></section><section class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Chat & perangkat</div><p class="ohli-hint">Nomor akun pribadi, arsip, ekspor, dan preferensi pesan dikelola dari menu Chat.</p><button type="button" class="ohli-btn" data-action="legacy-settings">Buka pengaturan chat</button><div class="ohli-settings-sub"><h3>Notifikasi Android</h3><p class="ohli-hint">Atur izin notifikasi serta nada pesan, panggilan, dan aktivitas melalui pengaturan Android.</p><button type="button" class="ohli-btn" data-action="open-native-notification-settings">Atur notifikasi dan nada</button></div></section><section class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Aktivitas</div><p class="ohli-hint">Riwayat pencarian, profil, postingan, foto, dan video tersimpan di akun pada server agar bisa dibuka dari perangkat lain.</p><button type="button" class="ohli-btn ohli-btn-primary" data-action="open-history">Buka riwayat aktivitas</button><button type="button" class="ohli-btn" data-action="open-story-collections" style="margin-left:7px">Koleksi Story teman</button></section><section class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Koneksi dan keamanan</div><p class="ohli-hint">APK ini adalah aplikasi klien. Layanan online diakses melalui OHLI Social Netlify; salinan HTML lokal dipakai saat perangkat offline. Pesan teks tertunda disimpan di HP dan dicoba kirim ulang ketika koneksi kembali.</p><div id="ohliHealthState" class="ohli-hint">' + esc(connection) + '</div><div id="ohliPerformanceGrid" class="ohli-performance-grid"><div class="ohli-performance-metric"><small>Status mesin AI</small><strong>Memuat…</strong></div></div><p class="ohli-performance-note">Angka ini berasal dari endpoint pemantauan layanan online dan hanya tersedia bila endpoint tersebut disediakan oleh backend.</p><button type="button" class="ohli-btn" data-action="check-health">Periksa performa sekarang</button></section></div><aside class="ohli-rail-column"><div class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Fitur sosial aktif</div><div class="ohli-trend"><small>Fitur yang sudah memiliki alur server</small><strong>Privasi akun, pengikut, tayangan profil, Sorotan, Story, LIVE, chat grup, Channel, QR kontak, Disukai, Posting Ulang</strong></div><div class="ohli-trend"><small>Batas infrastruktur</small><strong>Fitur LIVE dan panggilan mengikuti kemampuan backend online. Push notification saat proses aplikasi benar-benar dihentikan, TURN/SFU skala besar, backup eksternal, dan layanan OTP tetap memerlukan konfigurasi server tambahan.</strong></div></div></aside></div>';
    refreshSettingsControls();
    checkHealth();
  }

  async function checkHealth() { var target = shell.querySelector('#ohliHealthState'); if (!target) return; var label = shell.querySelector('#ohliConnectionLabel'),grid=shell.querySelector('#ohliPerformanceGrid'); try { var r = await fetch('/health', {cache:'no-store'}); var d = await r.json(); if (!r.ok || !d.ok) throw new Error('Health check gagal'); var perfResult = {}; try { perfResult = await rt.request('/api/performance'); } catch (ignored) {} var perf = perfResult.performance || {}; var engine = perf.mode ? ' · OHLI AI '+perf.mode+' · risiko '+(perf.overloadRiskPercent||0)+'% · error '+(perf.failureRatePercent||0)+'%' : ' · OHLI AI menunggu sampel'; target.textContent = 'Server menjawab · '+(d.service || 'OHLI Social')+' v'+(d.version || '?')+engine; if(grid){var cells=[['Risiko beban',Number(perf.overloadRiskPercent||0)+'%'],['Rata-rata respons',Number(perf.averageLatencyMs||0)+' ms'],['Antrean request',Number(perf.queue||0)+' / '+Number(perf.queueCapacity||0)],['Worker aktif',Number(perf.workerActive||0)+' / '+Number(perf.workerMax||0)],['Tekanan RAM',Number(perf.heapPressurePercent||0)+'%'],['Sampel belajar',Number(perf.modelSamples||0).toLocaleString('id-ID')]];grid.innerHTML=cells.map(function(x){return '<div class="ohli-performance-metric"><small>'+esc(x[0])+'</small><strong>'+esc(x[1])+'</strong></div>';}).join('');} if(label)label.textContent = 'Server tersambung'; } catch (e) { target.textContent = 'Server tidak dapat dijangkau atau sesi perlu diperbarui.'; if(grid)grid.innerHTML='<div class="ohli-performance-metric"><small>Status mesin AI</small><strong>Tidak terhubung</strong></div>'; if(label)label.textContent = 'Server bermasalah'; } }

  function showModal(title, body, submitLabel, onSubmit) {
    shell.querySelector('#ohliModalTitle').textContent = title; overlay.classList.remove('ohli-thread-modal'); if(/^Chat ·|^Channel ·/.test(title))overlay.classList.add('ohli-thread-modal'); overlayContent.innerHTML = body + (submitLabel ? '<div class="ohli-modal-actions"><button type="button" class="ohli-btn" data-action="close-modal">Batal</button><button type="button" class="ohli-btn ohli-btn-primary" id="ohliModalSubmit">' + esc(submitLabel) + '</button></div>' : ''); overlay.hidden = false;
    var submit = shell.querySelector('#ohliModalSubmit'); if (submit && onSubmit) submit.addEventListener('click', async function () { submit.disabled = true; try { await onSubmit(overlayContent); } catch (e) { toast(apiError(e)); } finally { submit.disabled = false; } });
    var first = overlayContent.querySelector('input,textarea,select,button'); if (first) setTimeout(function () { first.focus(); }, 25);
  }
  function closeModal() { stopQrScanner(); overlay.hidden = true; overlay.classList.remove('ohli-thread-modal'); overlayContent.innerHTML = ''; }

  async function handleClick(event) {
    var button = event.target.closest('[data-action]'); if (!button || !shell.contains(button)) return; var action = button.dataset.action;
    try {
      if (action === 'navigate') { event.preventDefault(); if (!overlay.hidden) closeModal(); if((button.dataset.page||'home')!=='explore')videoSearchReturn=false; setPage(button.dataset.page || 'home'); }
      else if (action === 'internal-back') { if (page === 'explore' && videoSearchReturn) { videoSearchReturn = false; setPage('videos',{},{skipHistory:true}); } else if(pageHistory.length){var prior=pageHistory.pop();restorePageState(prior);} else if(page!=='home'){setPage('home',undefined,{skipHistory:true});} else { /* Sudah di beranda utama OHLI Social; jangan tutup seluruh web. */ } }
      else if (action === 'reel-search') { videoSearchReturn = true; pageQuery = ''; searchInput.value = ''; setPage('explore'); }
      else if (action === 'home') setPage('home');
      else if (action === 'more-menu') { var menuHtml = '<div class="ohli-nav-picker">' + navItems.filter(function () { return true; }).map(function (it) { return '<button type="button" class="ohli-btn" data-action="navigate" data-page="' + esc(it[0]) + '">' + ico(it[2]) + esc(it[1]) + '</button>'; }).join('') + '</div>'; showModal('Menu OHLI Social', menuHtml, '', null); }
      else if (action === 'my-profile') { profileTarget = (rt.getMe()||{}).phone || ''; setPage('profile', {phone:profileTarget}); }
      else if (action === 'profile-avatar') { if(avatarLongPressConsumed){avatarLongPressConsumed=false;return;} if(button.dataset.storyPhone){await openUserStory(button.dataset.storyPhone);return;} if(button.dataset.live==='1'){setPage('live');return;} showProfileAvatarPreview(button.dataset.phone,button.dataset.name); }
      else if (action === 'compose-focus') setPage('create');
      else if (action === 'pick-post-media') shell.querySelector('#ohliPostFile').click();
      else if (action === 'pick-story-media') shell.querySelector('#ohliStatusFile').click();
      else if (action === 'capture-post' || action === 'capture-story') { var cam=shell.querySelector('#ohliCameraFile');cam.dataset.target=action==='capture-story'?'story':'post';cam.click(); }
      else if (action === 'create-story-editor') { setPage('create'); renderCreate('story'); }
      else if (action === 'save-story-draft') { saveLocalDraft('story',shell.querySelector('#ohliStoryText')?shell.querySelector('#ohliStoryText').value:''); }
      else if (action === 'save-create-draft') { saveLocalDraft(cr&&cr.target==='story'?'story':'post',shell.querySelector('#crCap')?shell.querySelector('#crCap').value:''); }
      else if (action === 'feed-mode') { currentFeedMode = button.dataset.mode || 'public'; nextBefore = 0; shell.querySelectorAll('.ohli-feed-tab').forEach(function (b) { b.classList.toggle('active', b === button); }); await renderHome(true); }
      else if (action === 'refresh-suggestions') { try { var freshUsers=await rt.request('/api/social/users'); users=freshUsers.users||users; renderSuggestions(shell.querySelector('#ohliSuggestions')); } catch(e) { toast(apiError(e)); } }
      else if (action === 'view-story') { var s = statusList.find(function (x) { return x.id === button.dataset.storyId; }); if (s) viewStory(s); }
      else if (action === 'story-tap-left') await stepStory(-1);
      else if (action === 'story-tap-right') await stepStory(1);
      else if (action === 'close-story') closeStory();
      else if (action === 'story-prev') await stepStory(-1);
      else if (action === 'story-next') await stepStory(1);
      else if (action === 'story-retry') await renderStoryFrame();
      else if (action === 'story-owner-profile') { var ownerPhone = button.dataset.phone; closeStory(); setPage('profile', {phone:ownerPhone}); }
      else if (action === 'close-modal') closeModal();
      else if (action === 'archive-mode') { archiveMode = button.dataset.mode === 'saved' ? 'saved' : 'archive'; await renderArchives(); }
      else if (action === 'post-menu') { var targetPost = findPost(button.dataset.postId); if (!targetPost) { toast('Postingan tidak ditemukan pada tampilan ini. Muat ulang halaman.'); return; } var archived = targetPost.archived === true || button.dataset.archived === '1'; var menuBody = '<p class="ohli-hint">Kelola postingan milikmu. Perubahan akan disimpan ke server.</p><div class="ohli-post-manage-menu"><button type="button" class="ohli-btn" data-action="edit-post" data-post-id="' + esc(targetPost.id) + '">Edit teks dan audiens</button><button type="button" class="ohli-btn" data-action="toggle-comments-settings" data-post-id="' + esc(targetPost.id) + '">' + (targetPost.commentsDisabled ? 'Aktifkan komentar' : 'Nonaktifkan komentar') + '</button>' + (isVid(targetPost) ? '<button type="button" class="ohli-btn" data-action="toggle-download-settings" data-post-id="' + esc(targetPost.id) + '">' + (targetPost.allowDownload === false ? 'Izinkan download video' : 'Nonaktifkan download video') + '</button>' : '') + '<button type="button" class="ohli-btn" data-action="archive-post" data-post-id="' + esc(targetPost.id) + '" data-archived="' + (archived ? '1' : '0') + '">' + (archived ? 'Pulihkan dari arsip' : 'Arsipkan postingan') + '</button><button type="button" class="ohli-btn ohli-btn-danger" data-action="delete-post" data-post-id="' + esc(targetPost.id) + '">Hapus postingan</button></div>'; showModal('Kelola postingan',menuBody,'',null); }
      else if (action === 'edit-post') { var pEdit = findPost(button.dataset.postId); if (!pEdit) return; var editBody = '<form id="ohliEditPostForm"><div class="ohli-field"><label for="ohliEditPostText">TEKS / CAPTION</label><textarea id="ohliEditPostText" name="text" maxlength="2000">' + esc(pEdit.text || '') + '</textarea></div><div class="ohli-field"><label for="ohliEditPostAudience">AUDIENS</label><select id="ohliEditPostAudience" name="audience"><option value="public" ' + (pEdit.audience === 'public' ? 'selected' : '') + '>Publik</option><option value="followers" ' + (pEdit.audience === 'followers' ? 'selected' : '') + '>Pengikut</option><option value="private" ' + (pEdit.audience === 'private' ? 'selected' : '') + '>Pribadi</option></select></div></form>'; showModal('Edit postingan',editBody,'Simpan perubahan',async function(content){var form=content.querySelector('#ohliEditPostForm');if(!form.reportValidity())return;var fd=new FormData(form);var updated=await rt.request('/api/posts/edit',{method:'POST',body:{postId:pEdit.id,text:fd.get('text'),audience:fd.get('audience')}});updatePost(updated.post);closeModal();toast('Postingan diperbarui di server.');if(page==='archive')await renderArchives();else if(page==='profile')await renderProfile(profileTarget);else await rerenderContent();}); }
      else if (action === 'archive-post') { var arch = await rt.request('/api/posts/archive',{method:'POST',body:{postId:button.dataset.postId,action:button.dataset.archived === '1' ? 'restore' : 'archive'}}); closeModal(); toast(arch.archived ? 'Postingan dipindahkan ke arsip.' : 'Postingan dipulihkan dari arsip.'); if(page==='archive')await renderArchives();else if(page==='profile')await renderProfile(profileTarget);else await renderHome(true); }
      else if (action === 'toggle-sound') { if(audioUnlockTap){audioUnlockTap=false;soundOn=true;}else{soundOn=!soundOn;} localStorage.setItem('ohli:social-reel-sound', soundOn ? 'on' : 'off'); pageContent.querySelectorAll('video[data-ohli-video]').forEach(function (v) { v.muted = wantMuted(v); if (soundOn && !v.paused) { var playPromise=v.play(); if(playPromise&&playPromise.catch)playPromise.catch(function(){v.muted=true;}); } }); var storySound=shell.querySelector('#ohliStoryMedia video');if(storySound){storySound.muted=wantMuted(storySound);if(soundOn&&!storySound.paused)storySound.play().catch(function(){storySound.muted=true;});} applyReelPreferences(); }
      else if (action === 'reel-comments') openReelComments(button.dataset.postId);
      else if (action === 'open-media') { var mp = findPost(button.dataset.postId); if (mp) openMedia(mp); }
      else if (action === 'repost') { var rd = await rt.request('/api/posts/repost', {method:'POST',body:{postId:button.dataset.postId}}); updatePost(rd.post); toast(rd.reposted ? 'Diposting ulang ke jaringanmu.' : 'Posting ulang dibatalkan.'); await rerenderContent(); }
      else if (action === 'reel-mode') { reelMode = button.dataset.mode === 'following' ? 'following' : 'foryou'; renderVideos(); }
      else if (action === 'not-interested') { var ignoredPost=findPost(button.dataset.postId);if(ignoredPost){recordSocialActivity('not_interested',ignoredPost.id,'Tidak tertarik dengan postingan');posts=posts.filter(function(x){return x.id!==ignoredPost.id;});await rerenderContent();toast('Preferensi FYP diperbarui.');} }
      else if (action === 'sheet-close') closeSheet();
      else if (action === 'share-friend') shareDo('friend', button.dataset.phone, button);
      else if (action === 'share-wa') shareDo('wa');
      else if (action === 'share-copy') shareDo('copy');
      else if (action === 'share-native') shareDo('native');
      else if (action === 'share-download') shareDo('download');
      else if (action === 'like') { var d = await rt.request('/api/posts/like', {method:'POST',body:{postId:button.dataset.postId}}); updatePost(d.post); await rerenderContent(); }
      else if (action === 'comments') openReelComments(button.dataset.postId);
      else if (action === 'bookmark') { var bdata = await rt.request('/api/posts/bookmark', {method:'POST',body:{postId:button.dataset.postId}}); var p = findPost(button.dataset.postId); if (p) p.saved = !!bdata.saved; toast(bdata.saved ? 'Postingan disimpan ke koleksi pribadi.' : 'Postingan dikeluarkan dari koleksi pribadi.'); await rerenderContent(); }
      else if (action === 'share-post') openShare(button.dataset.postId);
      else if (action === 'delete-post') { if (confirm('Hapus postingan ini? Tindakan ini tidak dapat dibatalkan.')) { await rt.request('/api/posts/delete',{method:'POST',body:{postId:button.dataset.postId}}); posts = posts.filter(function (p) { return p.id !== button.dataset.postId; }); if (profileData) profileData.posts = (profileData.posts || []).filter(function (p) { return p.id !== button.dataset.postId; }); closeModal(); toast('Postingan dihapus dari server.'); if(page==='archive')await renderArchives();else if(page==='profile')await renderProfile(profileTarget);else await rerenderContent(); } }
      else if (action === 'load-more') { var next = nextBefore; if (!next || !hasMore) return; button.disabled = true; button.textContent = 'Memuat…'; var data = await rt.request('/api/feed?mode=' + encodeURIComponent(currentFeedMode) + '&before=' + encodeURIComponent(next)); var current = {}; posts.forEach(function (p) { current[p.id] = true; }); (data.posts || []).forEach(function (p) { if (!current[p.id]) posts.push(p); }); nextBefore = Number(data.nextBefore) || 0; hasMore = !!data.hasMore; var feedList = pageContent.querySelector('#ohliFeedList'); if (feedList) feedList.innerHTML = posts.length ? posts.map(postCard).join('') : empty('home','Belum ada postingan','Bagikan cerita pertamamu.','Buat postingan','compose-focus'); var moreBox = pageContent.querySelector('#ohliFeedMore'); if (moreBox) moreBox.innerHTML = hasMore ? '<button type="button" class="ohli-btn" data-action="load-more">Muat postingan lainnya</button>' : '<p class="ohli-hint">Kamu sudah melihat postingan yang tersedia untuk halaman ini.</p>'; }
      else if (action === 'profile') { closeModal(); setPage('profile',{phone:button.dataset.phone}); }
      else if (action === 'friend-request' || action === 'friend-accept' || action === 'friend-reject' || action === 'friend-cancel' || action === 'friend-remove') {
        var friendTarget = button.dataset.phone;
        if (!friendTarget) return;
        var friendAction = action.replace('friend-', '');
        if (friendAction === 'remove' && !confirm('Hapus pengguna ini dari daftar teman?')) return;
        await rt.request('/api/friends', {method:'POST', body:{target:friendTarget, action:friendAction}});
        var friendMessages = {request:'Permintaan pertemanan dikirim.', accept:'Permintaan diterima. Kalian sekarang berteman.', reject:'Permintaan pertemanan ditolak.', cancel:'Permintaan pertemanan dibatalkan.', remove:'Pengguna dihapus dari daftar teman.'};
        toast(friendMessages[friendAction] || 'Daftar teman diperbarui.');
        if (page === 'profile' && profileTarget === friendTarget) await renderProfile(friendTarget);
        else if (page === 'friends') await renderFriends();
        else if (page === 'explore') await renderExplore();
        else await rerenderContent();
      }
      else if (action === 'refresh-friends') await renderFriends();
      else if (action === 'find-friends') { pageQuery = ''; searchInput.value = ''; setPage('explore'); }
      else if (action === 'follow') { var currentMe = rt.getMe() || {}, followTarget = button.dataset.phone, isFollowing = button.dataset.following === '1'; if (!followTarget) return; var followData = await rt.request('/api/follow',{method:'POST',body:{target:followTarget,action:isFollowing?'unfollow':'follow'}}); toast(followData.requested ? 'Permintaan mengikuti telah dikirim.' : (followData.following ? 'Akun berhasil diikuti.' : 'Kamu berhenti mengikuti akun ini.')); users = (users || []).map(function(u){ if(u.phone===followTarget){u.isFollowing=!!followData.following;u.followRequested=!!followData.requested;} return u; }); posts = (posts || []).map(function(p){if(p.author===followTarget){p.isFollowing=!!followData.following;p.followRequested=!!followData.requested;}return p;}); if (page === 'profile' && profileTarget === followTarget) await renderProfile(followTarget); else if(page==='home') await renderHome(false); else if(page==='videos') await renderVideos(); else await rerenderContent(); }
      else if (action === 'search-topic') { pageQuery = button.dataset.query || ''; searchInput.value = pageQuery; setPage('explore'); }
      else if (action === 'start-live') rt.startLive();
      else if (action === 'join-live') { var live = (await rt.request('/api/live')).lives || []; var selected = live.find(function (l) { return l.id === button.dataset.liveId; }); if (selected) rt.joinLive(selected); else toast('Siaran tidak lagi aktif.'); }
      else if (action === 'create-community' || action.indexOf('create-community:') === 0) { var kind = button.dataset.kind || action.split(':')[1] || 'group'; await showCommunityForm(kind); }
      else if (action === 'join-community' || action === 'follow-channel') { var kind2 = action === 'join-community' ? 'group' : 'channel', endpoint = kind2 === 'group' ? '/api/groups/join' : '/api/channels/follow'; var dataJoin = await rt.request(endpoint,{method:'POST',body:{id:button.dataset.id,action:button.dataset.joined === '1' ? (kind2 === 'group' ? 'leave' : 'unfollow') : (kind2 === 'group' ? 'join' : 'follow')}}); toast(dataJoin.joined ? 'Kamu sudah bergabung.' : (kind2 === 'group' ? 'Kamu keluar dari grup.' : 'Kamu berhenti mengikuti channel.')); if(dataJoin.joined){if(kind2==='group')await openGroupChat(button.dataset.id);else await openChannel(button.dataset.id);}else await renderCommunities(kind2); }
      else if (action === 'group-members') await showGroupMembers(button.dataset.id);
      else if (action === 'group-add-members') await showGroupMemberPicker(button.dataset.id);
      else if (action === 'group-remove-member') { if(confirm('Keluarkan anggota ini dari grup?')){await rt.request('/api/groups/members',{method:'POST',body:{groupId:button.dataset.id,action:'remove',target:button.dataset.target}});toast('Anggota dikeluarkan dari grup.');await showGroupMembers(button.dataset.id);} }
      else if (action === 'mark-all-read') { await rt.request('/api/notifications/read',{method:'POST',body:{}}); toast('Notifikasi ditandai sudah dibaca.'); await renderNotifications(); }
      else if (action === 'open-notification') { await rt.request('/api/notifications/read',{method:'POST',body:{id:button.dataset.id}}); if (button.dataset.type === 'story_like' && button.dataset.objectId) { try { var freshStories=await rt.request('/api/status?surface=social'); statusList=freshStories.statuses||[]; var likedStory=statusList.find(function(x){return x.id===button.dataset.objectId;}); if(likedStory){setPage('home');viewStory(likedStory);}else if(button.dataset.actor)setPage('profile',{phone:button.dataset.actor});else setPage('home'); } catch(storyErr){setPage('home');} } else if (button.dataset.actor) setPage('profile',{phone:button.dataset.actor}); else setPage('home'); }
      else if (action === 'toggle-comments-settings') { var cp=findPost(button.dataset.postId); if(!cp)return; var setting=await rt.request('/api/posts/comments/settings',{method:'POST',body:{postId:cp.id,disabled:!cp.commentsDisabled}}); updatePost(setting.post); closeModal(); toast(setting.commentsDisabled?'Komentar postingan dinonaktifkan.':'Komentar postingan diaktifkan kembali.'); await rerenderContent(); }
      else if (action === 'toggle-download-settings') { var dpSet=findPost(button.dataset.postId); if(!dpSet)return; var dl=await rt.request('/api/posts/download-settings',{method:'POST',body:{postId:dpSet.id,allowDownload:dpSet.allowDownload===false}}); updatePost(dl.post); closeModal(); toast(dl.allowDownload?'Unduhan video diizinkan.':'Unduhan video dinonaktifkan.'); await rerenderContent(); }
      else if (action === 'download-video') { var downPost=findPost(button.dataset.postId); if(downPost)await downloadVideoWithWatermark(downPost); }
      else if (action === 'comment-menu') { var cpost=findPost(button.dataset.postId), citem=findComment(cpost,button.dataset.commentId), meComment=rt.getMe()||{}; if(!cpost||!citem)return; var isOwnComment=!!citem.isMine, canDeleteComment=isOwnComment||!!cpost.isMine||!!cpost.canModerateComments; var cbody='<div class="ohli-post-manage-menu">'+(isOwnComment?'<button type="button" class="ohli-btn" data-action="edit-comment" data-post-id="'+esc(cpost.id)+'" data-comment-id="'+esc(citem.id)+'">Edit komentar</button>':'')+(canDeleteComment?'<button type="button" class="ohli-btn ohli-btn-danger" data-action="delete-comment" data-post-id="'+esc(cpost.id)+'" data-comment-id="'+esc(citem.id)+'">Hapus komentar</button>':'')+'</div>'; if(cbody.indexOf('data-action')>=0)showModal('Kelola komentar',cbody,'',null); }
      else if (action === 'like-comment') { var lp=findPost(button.dataset.postId); if(!lp)return; var lr=await rt.request('/api/posts/comments/like',{method:'POST',body:{postId:lp.id,commentId:button.dataset.commentId}}); updatePost(lr.post); if(page==='videos')openReelComments(lp.id);else await rerenderContent(); }
      else if (action === 'reply-comment') { replyToComment(button.dataset.postId,button.dataset.commentId); }
      else if (action === 'comment-sticker-picker') await openCommentStickerPicker(button.dataset.postId);
      else if (action === 'use-builtin-comment-sticker') await sendBuiltinCommentSticker(button.dataset.postId,button.dataset.emoji);
      else if (action === 'save-comment-sticker') { await rt.request('/api/stickers/favorites',{method:'POST',body:{mediaId:button.dataset.mediaId,action:'save'}}); toast('Stiker disimpan ke koleksi.'); await openCommentStickerPicker(button.dataset.postId); }
      else if (action === 'use-saved-comment-sticker') { await sendCommentSticker(button.dataset.postId,button.dataset.mediaId); }
      else if (action === 'save-imported-comment-sticker') { await saveImportedSticker(false); }
      else if (action === 'send-imported-comment-sticker') { await saveImportedSticker(true); }
      else if (action === 'delete-my-comment-sticker') { await deleteOwnSticker(button.dataset.mediaId,button.dataset.postId); }
      else if (action === 'edit-comment') { var ep=findPost(button.dataset.postId), ec=findComment(ep,button.dataset.commentId); if(!ep||!ec)return; var editCommentBody='<form id="ohliEditCommentForm"><label class="ohli-field">Komentar<textarea name="text" maxlength="500" required>'+esc(ec.text)+'</textarea></label></form>'; showModal('Edit komentar',editCommentBody,'Simpan',async function(content){var form=content.querySelector('#ohliEditCommentForm');if(!form.reportValidity())return;var updated=await rt.request('/api/posts/comments/edit',{method:'POST',body:{postId:ep.id,commentId:ec.id,text:form.elements.text.value.trim()}});updatePost(updated.post);closeModal();toast('Komentar diperbarui.');if(page==='videos'){openReelComments(ep.id);}else await rerenderContent();}); }
      else if (action === 'delete-comment') { var dp=findPost(button.dataset.postId); if(!dp)return; if(confirm('Hapus komentar ini?')){var dd=await rt.request('/api/posts/comments/delete',{method:'POST',body:{postId:dp.id,commentId:button.dataset.commentId}});updatePost(dd.post);closeModal();toast('Komentar dihapus.');if(page==='videos')openReelComments(dp.id);else await rerenderContent();} }
      else if (action === 'music-mode') { musicViewMode=['favorites','mine'].indexOf(button.dataset.mode)>=0?button.dataset.mode:'catalog'; await renderMusic(); }
      else if (action === 'music-save') { var savedData=await rt.request('/api/music/favorites',{method:'POST',body:{musicId:button.dataset.musicId,action:'toggle'}}); toast(savedData.saved?'Musik disimpan ke koleksi.':'Musik dihapus dari koleksi.'); if(page==='music')await renderMusic(); else if(button.dataset.from==='detail')await showMusicDetail(button.dataset.musicId); }
      else if (action === 'music-source-tab') renderAudioPickerSource(button.dataset.source || 'trending');
      else if (action === 'music-detail') await showMusicDetail(button.dataset.musicId);
      else if (action === 'music-original-detail') showOriginalAudioDetail(button.dataset.postId);
      else if (action === 'toggle-original-preview') { var pv=overlayContent.querySelector('#ohliOriginalAudioPreview');if(pv){if(pv.paused){pv.play().catch(function(){});button.textContent='Ⅱ';}else{pv.pause();button.textContent='▶';}} }
      else if (action === 'music-original-use' || action === 'music-original-save') await importOriginalVideoAudio(button.dataset.postId,action==='music-original-save');
      else if (action === 'music-use' || action === 'music-attach') { var chosen=(musicCache||[]).find(function(t){return t.id===button.dataset.musicId;}); if(!chosen){var md=await rt.request('/api/music/detail?id='+encodeURIComponent(button.dataset.musicId));chosen=md.music;} if(!chosen)throw new Error('Musik tidak ditemukan.'); if(action==='music-attach'||(page==='create'&&cr&&cr.kind!=='text')){if(!cr)throw new Error('Buka editor foto atau video terlebih dahulu.');cr.audioTrack=chosen;closeModal();crEdit();toast('Musik ditambahkan ke konten.');}else{pendingMusicTrack=chosen;closeModal();setPage('create');toast('Musik siap digunakan. Pilih foto atau video dari galeri.');} }
      else if (action === 'music-delete') { if(confirm('Hapus audio milikmu? Audio yang sudah dipakai postingan tidak dapat dihapus.')){await rt.request('/api/music/delete',{method:'POST',body:{musicId:button.dataset.musicId}});musicCache=musicCache.filter(function(t){return t.id!==button.dataset.musicId;});toast('Audio milikmu dihapus.');await renderMusic();} }
      else if (action === 'open-audio-picker') openAudioPicker();
      else if (action === 'detach-composer-audio') { if(cr){cr.audioTrack=null;crEdit();toast('Musik tambahan dilepas.');} }
      else if (action === 'edit-profile') openEditProfile();
      else if (action === 'copy-profile') { var u = profileData && profileData.user, handle='@'+socialHandle(u||{}); if (navigator.clipboard) { await navigator.clipboard.writeText(handle); toast('Username profil disalin.'); } else toast(handle); }
      else if (action === 'profile-privacy') await showPrivacySettings();
      else if (action === 'open-chat-user') { var requestId=++chatOpenRequest; var socialId=button.dataset.socialId||button.dataset.phone||(profileData&&profileData.user&&(profileData.user.socialId||profileData.user.publicId||profileData.user.phone))||profileTarget; var target=await rt.resolveChatUser(socialId); if(requestId!==chatOpenRequest)return; var targetPhone=String(target&&target.phone||'').replace(/[^0-9]/g,''); var currentPhone=String((rt.getMe()||{}).phone||'').replace(/[^0-9]/g,''); if(!/^\d{8,15}$/.test(targetPhone)||targetPhone===currentPhone)throw new Error('Akun tujuan chat tidak valid. Buka kembali profil teman lalu coba lagi.'); lastSocialPage=page; setPage('chat'); await rt.openChatWith(targetPhone); if(requestId!==chatOpenRequest)return; var returnBtn=document.getElementById('ohliBackSocial'); if(returnBtn)returnBtn.style.display=''; }
      else if (action === 'profile-stat') { var pc = shell.querySelector('#ohliProfileContent'); if (pc) pc.scrollIntoView({behavior:'smooth',block:'start'}); }
      else if (action === 'profile-story-open') { await openUserStory(button.dataset.phone); }
      else if (action === 'profile-live-open') { setPage('live'); }
      else if (action === 'show-follow-list') await showFollowList(button.dataset.phone,button.dataset.type);
      else if (action === 'profile-views') await showProfileViews();
      else if (action === 'like-story') { var sl = await rt.request('/api/status/like',{method:'POST',body:{statusId:button.dataset.storyId}}); var stx = statusList.find(function(x){return x.id===button.dataset.storyId;}); if(stx){stx.liked=sl.liked;stx.likesCount=sl.likesCount;} var sb = shell.querySelector('#ohliStoryFoot [data-action="like-story"]'); if(sb){sb.classList.toggle('is-liked',sl.liked);sb.textContent=sl.liked?'♥ Disukai':'♡ Suka';} var stats = shell.querySelector('.ohli-story-stats');if(stats)stats.textContent=sl.likesCount+' suka'; }
      else if (action === 'story-viewers') await showStoryViewers(button.dataset.storyId);
      else if (action === 'story-highlight') createHighlightFromStory(button.dataset.storyId);
      else if (action === 'open-story-history') await openStoryHistory();
      else if (action === 'view-history-story') await openHistoryStory(button.dataset.storyId);
      else if (action === 'history-story-viewers') await showStoryViewers(button.dataset.storyId);
      else if (action === 'history-story-highlight') createHighlightFromStory(button.dataset.storyId);
      else if (action === 'repost-history-story') await repostHistoryStory(button.dataset.storyId);
      else if (action === 'delete-history-story') await deleteHistoryStory(button.dataset.storyId);
      else if (action === 'delete-own-story') await deleteOwnStory(button.dataset.storyId);
      else if (action === 'view-highlight') { var hh=(profileData.highlights||[]).find(function(x){return x.id===button.dataset.id;}); if(hh)openHighlight(hh); }
      else if (action === 'edit-highlight') await editHighlight(button.dataset.id);
      else if (action === 'delete-highlight') await deleteHighlight(button.dataset.id);
      else if (action === 'delete-highlight-item') await deleteHighlightItem(button.dataset.highlightId,button.dataset.storyId);
      else if (action === 'contact-qr') openContactQr(button.dataset.phone || (rt.getMe()||{}).phone);
      else if (action === 'scan-contact-qr') chooseContactQrMethod();
      else if (action === 'start-contact-qr-camera') openContactScanner();
      else if (action === 'manual-contact-qr') showManualContactAdd('Masukkan hasil pemindaian QR atau nomor OHLI.');
      else if (action === 'change-password') await submitPasswordChange();
      else if (action === 'follow-requests') await showFollowRequests();
      else if (action === 'block-user') { var blockPhone=button.dataset.phone; if(!blockPhone)return; if(confirm('Blokir akun ini? Akun tersebut tidak dapat melihat profilmu atau menghubungimu.')){await rt.request('/api/blocks',{method:'POST',body:{phone:blockPhone,action:'block'}});closeStory();toast('Akun diblokir.');setPage('home');} }
      else if (action === 'blocked-list') await showBlockedList();
      else if (action === 'unblock-user') { await rt.request('/api/blocks',{method:'POST',body:{phone:button.dataset.phone,action:'unblock'}});toast('Blokir dibuka.');await showBlockedList(); }
      else if (action === 'follow-request-approve' || action === 'follow-request-reject') { await rt.request('/api/follow/requests',{method:'POST',body:{user:button.dataset.phone,action:action==='follow-request-approve'?'approve':'reject'}});toast(action==='follow-request-approve'?'Permintaan diizinkan.':'Permintaan ditolak.');await showFollowRequests(); }
      else if (action === 'refresh-group') await openGroupChat(button.dataset.id);
      else if (action === 'open-group') await openGroupChat(button.dataset.id);
      else if (action === 'create-group-poll') {var gid=button.dataset.id;var pollBody='<form id="ohliGroupPollForm"><label class="ohli-field">Pertanyaan<input name="question" maxlength="180" required placeholder="Apa pendapatmu?"></label><label class="ohli-field">Pilihan (satu per baris, 2–6 pilihan)<textarea name="options" rows="5" required placeholder="Pilihan pertama\nPilihan kedua"></textarea></label></form>';showModal('Buat polling grup',pollBody,'Terbitkan polling',async function(content){var f=content.querySelector('#ohliGroupPollForm');if(!f.reportValidity())return;var options=f.elements.options.value.split(/\r?\n/).map(function(x){return x.trim();}).filter(Boolean);if(options.length<2||options.length>6)throw new Error('Polling harus memiliki 2 sampai 6 pilihan.');await rt.request('/api/groups/messages',{method:'POST',body:{groupId:gid,kind:'poll',question:f.elements.question.value.trim(),options:options}});closeModal();await openGroupChat(gid);});}
      else if (action === 'group-vote') {await rt.request('/api/groups/vote',{method:'POST',body:{groupId:button.dataset.id,messageId:button.dataset.messageId,optionId:button.dataset.optionId}});await openGroupChat(button.dataset.id);}
      else if (action === 'channel-react') {await rt.request('/api/channels/react',{method:'POST',body:{channelId:button.dataset.channelId,postId:button.dataset.postId,emoji:button.dataset.emoji}});await openChannel(button.dataset.channelId);}
      else if (action === 'open-channel') await openChannel(button.dataset.id);

      else if (action === 'profile-tab') { var tab = button.dataset.tab, target = shell.querySelector('#ohliProfileContent'); shell.querySelectorAll('[data-action="profile-tab"]').forEach(function (b) { b.classList.toggle('active', b === button); }); if (!target || !profileData) return; if(tab==='drafts'){renderDrafts(target);return;} var ps = profileData.posts || [], qphone = encodeURIComponent(profileTarget); target.className = 'ohli-feed-column'; target.innerHTML = '<div class="ohli-loading"><div class="ohli-skeleton"></div></div>'; if (tab === 'saved' || tab === 'liked' || tab === 'reposted') { var ep = tab === 'saved' ? '/api/posts/saved?phone=' + qphone : (tab === 'liked' ? '/api/posts/liked?phone=' + qphone : '/api/posts/reposted?phone=' + qphone); var collection = await rt.request(ep); posts = collection.posts || []; target.innerHTML = posts.length ? posts.map(postCard).join('') : empty(tab === 'saved' ? 'bookmark' : 'heart', tab === 'saved' ? 'Belum ada yang disimpan' : (tab === 'liked' ? 'Belum ada postingan disukai' : 'Belum ada postingan ulang'), 'Konten akan muncul di sini ketika tersedia.'); return; } if (tab === 'media') { ps = ps.filter(function (p) { return !!p.mediaId; }); renderProfileMedia(target,ps); return; } if (tab === 'highlights') { var hi = profileData.highlights || [], ownHighlights=profileTarget === (rt.getMe()||{}).phone; target.className = 'ohli-highlight-grid'; target.innerHTML = (ownHighlights ? '<div class="ohli-story-history-entry"><button type="button" class="ohli-btn" data-action="open-story-history">◷ Riwayat Story</button><span class="ohli-hint">Foto, video, tanggal, penonton dan Story lama.</span></div>' : '') + (hi.length ? hi.map(function (h) { return '<div class="ohli-highlight-card-wrap"><button class="ohli-highlight-card" type="button" data-action="view-highlight" data-id="' + esc(h.id) + '"><span>' + (h.mediaId ? '<img src="' + esc(rt.mediaUrl(h.mediaId)) + '" alt="" loading="lazy" decoding="async">' : '<b>OHLI</b>') + '</span><strong>' + esc(h.name) + '</strong></button>' + (ownHighlights ? '<div class="ohli-highlight-manage"><button type="button" class="ohli-btn" data-action="edit-highlight" data-id="' + esc(h.id) + '">Edit</button><button type="button" class="ohli-btn" data-action="delete-highlight" data-id="' + esc(h.id) + '">Hapus</button></div>' : '') + '</div>'; }).join('') : empty('story','Belum ada Sorotan','Simpan Story milikmu ke Sorotan agar tetap tampil di profil.')); return; } if (tab === 'media') return; if (tab === 'about') { target.innerHTML = '<div class="ohli-card ohli-rail-card"><div class="ohli-rail-title">Tentang</div><p class="ohli-profile-bio">' + esc((profileData.user && profileData.user.about) || 'Belum menambahkan bio.') + '</p><p class="ohli-hint">Username OHLI Social berbeda dari nomor akun Chat.</p></div>'; return; } target.innerHTML = ps.length ? ps.map(postCard).join('') : empty('profile','Belum ada konten','Konten pada tab ini akan muncul ketika tersedia.'); }
      else if (action === 'use-draft') useLocalDraft(button.dataset.draftId);
      else if (action === 'delete-draft') {var remaining=readDrafts().filter(function(d){return d.id!==button.dataset.draftId;});localStorage.setItem('ohli:social-drafts',JSON.stringify(remaining));var draftTarget=shell.querySelector('#ohliProfileContent');if(draftTarget)renderDrafts(draftTarget);toast('Draf dihapus dari perangkat ini.');}
      else if (action === 'theme-custom') openThemeCustomizer();
      else if (action === 'theme-custom-save') { var iconColor=shell.querySelector('#ohliCustomIconColor'),textColor=shell.querySelector('#ohliCustomTextColor');var prefs={icon:iconColor?iconColor.value:'',text:textColor?textColor.value:''};if(!/^#[0-9a-f]{6}$/i.test(prefs.icon)||!/^#[0-9a-f]{6}$/i.test(prefs.text))throw new Error('Pilih warna ikon dan tulisan yang valid.');localStorage.setItem('ohli:social-custom-colors',JSON.stringify(prefs));applyCustomThemeColors();closeModal();toast('Warna ikon dan tulisan disimpan.');}
      else if (action === 'theme-custom-reset') { localStorage.removeItem('ohli:social-custom-colors');applyCustomThemeColors();closeModal();toast('Warna ikon dan tulisan dikembalikan ke bawaan.');}
      else if (action === 'set-theme') { toggleTheme(button.dataset.theme); if(!overlay.hidden)closeModal(); }
      else if (action === 'profile-media-mode') { profileVideoLayout=button.dataset.mode==='transparent'?'transparent':'limited'; localStorage.setItem('ohli:social-profile-video-layout',profileVideoLayout); var mt=shell.querySelector('#ohliProfileContent'); if(mt&&profileData)renderProfileMedia(mt,(profileData.posts||[]).filter(function(p){return !!p.mediaId;})); }
      else if (action === 'toggle-reel-sound') { soundOn=!soundOn;localStorage.setItem('ohli:social-reel-sound',soundOn?'on':'off');if(soundOn){var current=activeReelVideo;if(current){current.muted=wantMuted(current);var soundPromise=current.play();if(soundPromise&&soundPromise.catch)soundPromise.catch(function(){current.muted=true;});}}else if(activeReelVideo)activeReelVideo.muted=true;refreshSettingsControls();applyReelPreferences(); }
      else if (action === 'toggle-reel-autoplay') { reelAutoplay=!reelAutoplay;localStorage.setItem('ohli:social-reel-autoplay',reelAutoplay?'on':'off');if(!reelAutoplay)pauseAllReels();resetVideoObserver();refreshSettingsControls(); }
      else if (action === 'toggle-anti-zoom') { antiZoom=!antiZoom;localStorage.setItem('ohli:social-anti-zoom',antiZoom?'on':'off');syncViewportZoom();refreshSettingsControls();toast(antiZoom?'Anti-zoom diaktifkan pada browser ini.':'Zoom browser diizinkan kembali.'); }
      else if (action === 'set-reel-nav-bg') { reelNavBackground=button.dataset.mode==='transparent'?'transparent':'black';localStorage.setItem('ohli:social-reel-nav-bg',reelNavBackground);applyReelPreferences();if(page==='settings')await renderSettings();toast(reelNavBackground==='transparent'?'Navigasi video transparan aktif.':'Latar hitam navigasi video aktif.'); }
      else if (action === 'toggle-theme') toggleTheme();
      else if (action === 'legacy-settings') { goLegacy('chat'); rt.openLegacySettings(); }
      else if (action === 'open-native-notification-settings') { if(window.OHLI_ANDROID&&window.OHLI_ANDROID.openNotificationSettings)window.OHLI_ANDROID.openNotificationSettings();else toast('Pengaturan notifikasi tersedia melalui Info Aplikasi Android.'); }
      else if (action === 'check-health') await checkHealth();
      else if (action === 'open-history') await openSocialHistory();
      else if (action === 'history-open') {var ix=Number(button.dataset.historyIndex);if(Number.isInteger(ix)&&socialHistoryDisplay[ix])openHistoryMedia(socialHistoryDisplay[ix]);else toast('Konten riwayat tidak ditemukan.');}
      else if (action === 'clear-history') { if(confirm('Hapus seluruh riwayat aktivitas akun ini dari server?')){await rt.request('/api/activity/history/clear',{method:'POST',body:{}});localStorage.removeItem(SOCIAL_HISTORY_KEY);await openSocialHistory();toast('Riwayat aktivitas akun dihapus.');} }
      else if (action === 'open-story-collections') await openStoryCollections();
      else if (action === 'story-collection' || action === 'story-collection-manage') { await rt.request('/api/status/collections',{method:'POST',body:{statusId:button.dataset.storyId,action:button.dataset.kind}});toast(button.dataset.kind==='pin'?'Story dipin ke koleksi.':(button.dataset.kind==='archive'?'Story diarsipkan.':'Koleksi Story diperbarui.'));if(action==='story-collection-manage')await openStoryCollections(); }
      else if (action === 'history-search') {pageQuery=button.dataset.query||'';searchInput.value=pageQuery;if(pageQuery)recordSocialActivity('search',pageQuery,pageQuery,{query:pageQuery});closeModal();setPage('explore');}
      else if (action === 'retry') await renderPage();
    } catch (e) { toast(apiError(e)); }
  }

  async function handleSubmit(event) {
    var form = event.target;
    if (form.id === 'ohliComposeForm') {
      event.preventDefault(); var text = form.querySelector('[name="text"]').value.trim(), audience = shell.querySelector('#ohliAudience').value, file = selectedFile;
      if (!text && !file) { toast('Tulis postingan atau pilih foto/video terlebih dahulu.'); return; }
      var submit = form.querySelector('#ohliPostSubmit'); submit.disabled = true; submit.textContent = file ? 'Mengunggah…' : 'Memposting…';
      try { if (file) await rt.uploadPostMedia(file,text,audience); else await rt.request('/api/posts',{method:'POST',body:{text:text,audience:audience}}); selectedFile = null; nextBefore = 0; toast('Postingan berhasil disimpan di server.'); await renderHome(true); }
      catch (e) { toast(apiError(e)); }
      finally { var button = shell.querySelector('#ohliPostSubmit'); if (button) { button.disabled = false; button.innerHTML = ico('send') + ' Posting'; } }
    } else if (form.id === 'ohliStoryForm') {
      event.preventDefault(); var storyText = form.querySelector('[name="text"]').value.trim(), storyFile = selectedStatusFile;
      if (!storyText && !storyFile) { toast('Isi teks Story atau pilih foto/video.'); return; }
      var sb = form.querySelector('#ohliStorySubmit'); sb.disabled = true; sb.textContent = storyFile ? 'Mengunggah…' : 'Membagikan…';
      try { var storyAudience=form.querySelector('#ohliStoryAudience').value,storyDestination=form.querySelector('#ohliStoryDestination').value; if (storyFile) await rt.uploadStatusMedia(storyFile,storyText,'',storyAudience,storyDestination); else await rt.request('/api/status',{method:'POST',body:{text:storyText,audience:storyAudience,destination:storyDestination}}); selectedStatusFile = null; toast('Story berhasil dibagikan.'); await renderStories(); }
      catch (e) { toast(apiError(e)); }
      finally { var liveBtn = shell.querySelector('#ohliStorySubmit'); if (liveBtn) { liveBtn.disabled = false; liveBtn.textContent = 'Bagikan Story'; } }
    } else if (form.id === 'ohliMusicUploadForm') { event.preventDefault(); var musicFile=form.querySelector('[name="file"]').files[0], musicTitle=form.elements.title.value.trim(), musicArtist=form.elements.artist.value.trim(); if(!musicFile){toast('Pilih file audio dahulu.');return;} if(!/^audio\//i.test(musicFile.type)){toast('File harus berupa audio yang valid.');return;} if(musicFile.size>30*1024*1024){toast('Audio maksimal 30 MB.');return;} try{var duration=await readAudioDuration(musicFile),uploaded=await rt.uploadMusic(musicFile,musicTitle,musicArtist,duration);musicCache.unshift(uploaded.music);musicViewMode='mine';toast('Audio tersimpan di Audio Saya.');await renderMusic();}catch(musicErr){toast(apiError(musicErr));}
    } else if (form.id === 'ohliExploreForm') { event.preventDefault(); pageQuery = form.querySelector('[name="q"]').value.trim(); searchInput.value = pageQuery; if(pageQuery)recordSocialActivity('search',pageQuery,pageQuery,{query:pageQuery}); await renderExplore();
    } else if (form.id === 'ohliStoryReplyForm') { event.preventDefault(); var storyReply=form.querySelector('[name="reply"]'), replyText=storyReply.value.trim(), recipient=form.getAttribute('data-story-owner'); if(!replyText||!recipient)return; var replyButton=form.querySelector('button[type="submit"]'); replyButton.disabled=true; try{await rt.request('/api/messages',{method:'POST',body:{recipient:recipient,text:'Balasan Story: '+replyText}});storyReply.value='';toast('Balasan Story dikirim ke chat.');}catch(replyError){toast(apiError(replyError));}finally{replyButton.disabled=false;}
    } else if (form.hasAttribute('data-comment-post')) {
      event.preventDefault(); var comment = form.querySelector('[name="comment"]').value.trim(), postId = form.getAttribute('data-comment-post'); if (!comment) return;
      try { var parentId=replyingComment&&replyingComment.postId===postId?replyingComment.id:'';var data = await rt.request('/api/posts/comment',{method:'POST',body:{postId:postId,text:comment,parentId:parentId||''}}); replyingComment=null; updatePost(data.post); await rerenderContent(); }
      catch (e) { toast(apiError(e)); }
    }
  }

  var pendingStickerImport = null;
  async function previewImportedSticker(file, postId) {
    if (!file || !/^image\/(png|jpeg|webp|gif)$/i.test(file.type || '')) throw new Error('Gunakan gambar PNG, JPG, WebP, atau GIF.');
    if (file.size <= 0 || file.size > 2 * 1024 * 1024) throw new Error('Ukuran stiker harus 1 byte sampai 2 MB.');
    var previewUrl = await new Promise(function(resolve,reject){var reader=new FileReader();reader.onload=function(){resolve(String(reader.result||''));};reader.onerror=function(){reject(new Error('Pratinjau gambar tidak dapat dibaca.'));};reader.readAsDataURL(file);});
    pendingStickerImport={file:file,postId:String(postId||'')};
    var body='<div class="ohli-sticker-preview"><p class="ohli-hint">Periksa gambar sebelum disimpan. File akan diunggah ke server akunmu, bukan hanya disimpan sementara di browser.</p><img src="'+previewUrl+'" alt="Pratinjau stiker"><strong>'+esc(file.name||'Stiker dari galeri')+'</strong><small>'+Math.max(1,Math.ceil(file.size/1024))+' KB · '+esc(file.type)+'</small><div class="ohli-sticker-preview-actions"><button type="button" class="ohli-btn ohli-btn-primary" data-action="save-imported-comment-sticker">Simpan ke Stiker Saya</button>'+(postId?'<button type="button" class="ohli-btn" data-action="send-imported-comment-sticker">Simpan & kirim</button>':'')+'<button type="button" class="ohli-btn" data-action="close-modal">Batal</button></div></div>';
    showModal('Pratinjau stiker',body,'',null);
  }
  async function saveImportedSticker(sendNow) {
    if(!pendingStickerImport||!pendingStickerImport.file){toast('Pilih ulang gambar stiker dari galeri.');return;}
    var pending=pendingStickerImport,buttons=overlayContent.querySelectorAll('button[data-action="save-imported-comment-sticker"],button[data-action="send-imported-comment-sticker"]');
    buttons.forEach(function(b){b.disabled=true;});
    try {
      var uploaded=await rt.uploadCommentSticker(pending.file),mediaId=(uploaded.sticker&&uploaded.sticker.mediaId)||uploaded.mediaId;
      if(!mediaId)throw new Error('Server tidak mengembalikan ID stiker.');
      pendingStickerImport=null;
      if(sendNow&&pending.postId){closeModal();await sendCommentSticker(pending.postId,mediaId);return;}
      toast('Stiker tersimpan di Stiker Saya.');
      if(pending.postId){await openCommentStickerPicker(pending.postId);}else{closeModal();}
    } catch(e) {buttons.forEach(function(b){b.disabled=false;});throw e;}
  }
  async function deleteOwnSticker(mediaId,postId) {
    if(!mediaId||!confirm('Hapus stiker ini dari Stiker Saya? Stiker yang sudah dipakai dalam komentar tidak dapat dihapus.'))return;
    await rt.request('/api/stickers/delete',{method:'POST',body:{mediaId:mediaId}});
    toast('Stiker dihapus dari koleksi.');
    if(postId)await openCommentStickerPicker(postId);
  }

  async function handleChange(event) {
    var target = event.target;
    if(target.id==='ohliHighlightShape'||target.id==='ohliHighlightFit'){if(target.id==='ohliHighlightShape')highlightThumbShape=['circle','rounded','square'].indexOf(target.value)>=0?target.value:'circle';else highlightThumbFit=['cover','contain','top'].indexOf(target.value)>=0?target.value:'cover';localStorage.setItem('ohli:highlight-thumb-shape',highlightThumbShape);localStorage.setItem('ohli:highlight-thumb-fit',highlightThumbFit);if(shell){shell.dataset.highlightShape=highlightThumbShape;shell.dataset.highlightFit=highlightThumbFit;}toast('Tampilan thumbnail Sorotan diperbarui.');}
    if(target.id==='crOriginalVolume'){cr.originalVolume=Math.max(0,Math.min(1,Number(target.value)/100));var ol=pageContent.querySelector('#crOriginalVolumeValue');if(ol)ol.textContent=Math.round(cr.originalVolume*100)+'%';}
    if(target.id==='crMusicVolume'){cr.musicVolume=Math.max(0,Math.min(1,Number(target.value)/100));var ml=pageContent.querySelector('#crMusicVolumeValue');if(ml)ml.textContent=Math.round(cr.musicVolume*100)+'%';}
    if(target.id==='crPhotoDuration'){cr.photoDuration=Math.max(3,Math.min(180,Number(target.value)||3));var dl=pageContent.querySelector('#crPhotoDurationValue');if(dl)dl.textContent=cr.photoDuration+' detik';}
    if (target.hasAttribute('data-comment-sticker-file')) { var stickerFile=target.files&&target.files[0],formComment=target.closest('[data-comment-post]'),stickerPost=target.dataset.postId||(formComment&&formComment.getAttribute('data-comment-post')); if(!stickerFile)return; if(!/^image\//i.test(stickerFile.type)){toast('Pilih file gambar untuk stiker.');target.value='';return;} if(stickerFile.size>2*1024*1024){toast('Ukuran stiker maksimal 2 MB.');target.value='';return;} try{await previewImportedSticker(stickerFile,stickerPost||'');}catch(stickerError){toast(apiError(stickerError));}finally{target.value='';} }
    if (target.id === 'ohliPostFile') { selectedFile = target.files && target.files[0] || null; var label = shell.querySelector('#ohliSelectedFileLabel'); if (label) label.textContent = selectedFile ? selectedFile.name + ' · ' + Math.ceil(selectedFile.size/1024/1024) + ' MB' : ''; if (selectedFile && selectedFile.size > 16*1024*1024) { toast('Ukuran maksimum media adalah 16 MB.'); selectedFile = null; target.value = ''; if (label) label.textContent = ''; } else if(selectedFile){startCreateWithFile(selectedFile,'post');} }
    if (target.id === 'ohliStatusFile') { selectedStatusFile = target.files && target.files[0] || null; var statusLabel = shell.querySelector('#ohliStoryFileLabel'); if (statusLabel) statusLabel.textContent = selectedStatusFile ? selectedStatusFile.name + ' · ' + Math.ceil(selectedStatusFile.size/1024/1024) + ' MB' : ''; if (selectedStatusFile && selectedStatusFile.size > 16*1024*1024) { toast('Ukuran maksimum media adalah 16 MB.'); selectedStatusFile = null; target.value = ''; if (statusLabel) statusLabel.textContent = ''; } else if(selectedStatusFile){startCreateWithFile(selectedStatusFile,'story');} }
    if (target.id === 'ohliCameraFile') { var camFile=target.files&&target.files[0],camTarget=target.dataset.target||'post';target.value='';if(camFile)startCreateWithFile(camFile,camTarget); }
    if (target.id === 'ohliMusicLocalFile' || target.id === 'ohliMusicExtractFile') {
      var picked=target.files&&target.files[0]; target.value=''; if(!picked)return;
      try { var audioFile=picked; var title=picked.name.replace(/\.[^.]+$/,''); if(target.id==='ohliMusicExtractFile'){toast('Mengambil trek audio dari video…');audioFile=await extractAudioFromVideo(picked);title=title+' · audio';}
        if(!/^audio\//i.test(audioFile.type)){throw new Error('Format audio tidak dikenali browser.');}if(audioFile.size>30*1024*1024)throw new Error('Audio maksimal 30 MB.');var duration=await readAudioDuration(audioFile),up=await rt.uploadMusic(audioFile,title,(rt.getMe()||{}).name||'',duration);musicCache.unshift(up.music);
        if(cr&&cr.kind!=='text'&&page==='create'){cr.audioTrack=up.music;cr.photoDuration=cr.kind==='image'?Math.max(3,Math.min(180,Number(up.music.durationSeconds||up.music.duration)||15)):cr.photoDuration;closeModal();crEdit();toast('Audio tersimpan di server dan ditambahkan ke media.');}
        else{musicViewMode='mine';if(page==='music')await renderMusic();toast('Audio tersimpan di Audio Saya.');}
      } catch(musicUploadError){toast(apiError(musicUploadError));}
    }
  }


  async function downloadVideoWithWatermark(post) {
    if (!post || !post.mediaId || !isVid(post)) return;
    if (post.allowDownload === false) { toast('Pemilik video menonaktifkan unduhan.'); return; }
    if (!rt.downloadMediaUrl) { toast('Endpoint unduhan tidak tersedia pada server ini.'); return; }
    if (!window.MediaRecorder || !HTMLCanvasElement.prototype.captureStream) { toast('Browser ini belum mendukung pembuatan salinan video ber-watermark. Coba browser terbaru.'); return; }
    toast('Menyiapkan video unduhan ber-watermark OHLI…');
    var sourceUrl=rt.downloadMediaUrl(post.mediaId), blobUrl='', recorder=null, video=document.createElement('video'), canvas=document.createElement('canvas'), chunks=[], stream=null, audioStream=null, frame=0, audioContext=null;
    try {
      var response=await fetch(sourceUrl,{credentials:'same-origin'});
      if(!response.ok){var errData={};try{errData=await response.json();}catch(ignore){}throw new Error(errData.error||('Download gagal (HTTP '+response.status+').'));}
      var sourceBlob=await response.blob();
      blobUrl=URL.createObjectURL(sourceBlob);video.src=blobUrl;video.playsInline=true;video.preload='auto';video.muted=false;
      await new Promise(function(resolve,reject){video.onloadedmetadata=resolve;video.onerror=function(){reject(new Error('Video sumber tidak dapat dibaca browser.'));};});
      var w=video.videoWidth||720,h=video.videoHeight||1280,scale=Math.min(1,1280/Math.max(w,h));
      canvas.width=Math.max(2,Math.round(w*scale));canvas.height=Math.max(2,Math.round(h*scale));
      var ctx=canvas.getContext('2d');if(!ctx)throw new Error('Canvas tidak tersedia.');
      stream=canvas.captureStream(30);
      if(typeof video.captureStream==='function'||typeof video.mozCaptureStream==='function'){
        audioStream=(video.captureStream&&video.captureStream())||(video.mozCaptureStream&&video.mozCaptureStream());
        if(audioStream)audioStream.getAudioTracks().forEach(function(t){stream.addTrack(t);});
      } else if(window.AudioContext||window.webkitAudioContext) {
        audioContext=new (window.AudioContext||window.webkitAudioContext)();
        var sourceNode=audioContext.createMediaElementSource(video),destination=audioContext.createMediaStreamDestination();
        sourceNode.connect(destination);sourceNode.connect(audioContext.destination);
        destination.stream.getAudioTracks().forEach(function(t){stream.addTrack(t);});
      }
      var mime=['video/mp4;codecs=avc1.42E01E,mp4a.40.2','video/mp4','video/webm;codecs=vp9,opus','video/webm;codecs=vp8,opus','video/webm'].find(function(m){return MediaRecorder.isTypeSupported(m);});
      if(!mime)throw new Error('Browser ini tidak mendukung encoder video MP4/WebM untuk watermark.');
      recorder=new MediaRecorder(stream,{mimeType:mime,videoBitsPerSecond:Math.min(6000000,Math.max(1400000,canvas.width*canvas.height*4))});
      recorder.ondataavailable=function(e){if(e.data&&e.data.size)chunks.push(e.data);};
      var username='@'+socialHandle({username:post.authorUsername,name:post.authorName});
      function draw(){if(video.readyState>=2){ctx.drawImage(video,0,0,canvas.width,canvas.height);var pad=Math.max(12,canvas.width*.025),font=Math.max(14,canvas.width*.027);ctx.font='700 '+font+'px system-ui,sans-serif';var line1='OHLI',line2=username,tw=Math.max(ctx.measureText(line1).width,ctx.measureText(line2).width),boxW=tw+pad*2,boxH=font*2.45,x=pad,y=canvas.height-pad-boxH;ctx.fillStyle='rgba(0,0,0,.28)';ctx.beginPath();if(ctx.roundRect)ctx.roundRect(x,y,boxW,boxH,Math.max(8,font*.4));else ctx.rect(x,y,boxW,boxH);ctx.fill();ctx.fillStyle='rgba(255,255,255,.92)';ctx.fillText(line1,x+pad,y+font*1.05);ctx.font='500 '+Math.max(10,font*.62)+'px system-ui,sans-serif';ctx.fillStyle='rgba(255,255,255,.84)';ctx.fillText(line2,x+pad,y+font*2.05);}if(!video.paused&&!video.ended)frame=requestAnimationFrame(draw);}
      recorder.start(500);
      if(audioContext&&audioContext.state==='suspended')await audioContext.resume();
      await video.play();draw();
      await new Promise(function(resolve,reject){video.onended=resolve;recorder.onerror=function(){reject(new Error('Pembuatan video watermark gagal.'));};});
      if(frame)cancelAnimationFrame(frame);
      await new Promise(function(resolve){recorder.onstop=resolve;recorder.stop();});
      var ext=mime.indexOf('mp4')>=0?'mp4':'webm',out=new Blob(chunks,{type:mime.split(';')[0]}),url=URL.createObjectURL(out),a=document.createElement('a'),base=(post.authorUsername||post.authorName||'ohli').replace(/[^a-z0-9_-]/gi,'_');
      a.href=url;a.download='OHLI-'+base+'-'+String(post.id).slice(0,8)+'.'+ext;document.body.appendChild(a);a.click();a.remove();setTimeout(function(){URL.revokeObjectURL(url);},60000);
      toast('Salinan video ber-watermark OHLI berhasil dibuat. Video asli di feed tidak berubah.');
    } catch(e) {
      if(frame)cancelAnimationFrame(frame);
      if(recorder&&recorder.state!=='inactive')try{recorder.stop();}catch(ignoreStop){}
      toast(apiError(e));
    } finally {
      video.pause();if(blobUrl)URL.revokeObjectURL(blobUrl);
      if(stream)stream.getTracks().forEach(function(t){try{t.stop();}catch(ignoreTrack){}});
      if(audioStream)audioStream.getTracks().forEach(function(t){try{t.stop();}catch(ignoreAudio){}});
      if(audioContext)try{audioContext.close();}catch(ignoreContext){}
    }
  }

  function findPost(id) { var found = (posts || []).find(function (p) { return p.id === id; }); if (found) return found; return profileData && (profileData.posts || []).find(function (p) { return p.id === id; }) || null; }
  function updatePost(updated) { if (!updated) return; var found = false; posts = (posts || []).map(function (p) { if (p.id === updated.id) { found = true; return updated; } return p; }); if (!found && page === 'profile' && profileData) profileData.posts = (profileData.posts || []).map(function (p) { return p.id === updated.id ? updated : p; }); }
  async function rerenderContent() {
    var stg = shell.querySelector('.ohli-stage'), keep = stg ? stg.scrollTop : 0, l0 = pageContent.querySelector('#ohliFeedList'); if (l0) l0.style.minHeight = l0.offsetHeight + 'px';
    try { await rerenderInner(); } finally {
      if (stg) { stg.scrollTop = keep; requestAnimationFrame(function () { stg.scrollTop = keep; }); } if (l0) setTimeout(function () { l0.style.minHeight = ''; }, 900);
      var cs = shell.querySelector('.ohli-sheet-bg[data-cm]'); if (cs) { var inp = cs.querySelector('input[name="comment"]'); if (inp) inp.value = ''; openReelComments(cs.getAttribute('data-cm')); }
    }
  }
  async function rerenderInner() { if (page === 'music') return renderMusic(); if (page === 'notifications') return renderNotifications(); if (page === 'archive') return renderArchives(); if (page === 'home') { var list = pageContent.querySelector('#ohliFeedList'); if (list) list.innerHTML = posts.length ? posts.map(postCard).join('') : empty('home','Belum ada postingan','Bagikan cerita pertamamu.','Buat postingan','compose-focus'); } else if (page === 'videos') { syncReels(); } else if (page === 'explore') await renderExplore(); else if (page === 'profile') await renderProfile(profileTarget); }

  async function openUserStory(ownerPhone){var storyData=await rt.request('/api/status?surface=social');statusList=storyData.statuses||[];var ownerStory=statusList.filter(function(st){return st.phone===ownerPhone||(st.isMine&&ownerPhone===(rt.getMe()||{}).phone);}).sort(function(a,b){return Number(a.time||0)-Number(b.time||0);});if(ownerStory.length)await viewStory(ownerStory[ownerStory.length-1]);else toast('Story ini sudah berakhir.');}
  function showProfileAvatarPreview(phone,name){var avPhone=phone||'',avName=name||'Profil OHLI',avSrc='/api/social-avatar?phone='+encodeURIComponent(avPhone)+'&token='+encodeURIComponent(rt.getToken())+'&v='+avatarVer;showModal(avName,'<div class="ohli-profile-photo-preview"><img src="'+esc(avSrc)+'" alt="Foto profil '+esc(avName)+'" onerror="this.style.display=\'none\'"><p>@'+esc(socialHandle({name:avName}))+'</p></div>','',null);}
  function onAvatarHoldStart(event){var b=event.target.closest('.ohli-profile-avatar-button');if(!b||!shell.contains(b))return;clearTimeout(avatarHoldTimer);avatarHoldTimer=setTimeout(function(){avatarLongPressConsumed=true;showProfileAvatarPreview(b.dataset.phone,b.dataset.name);},480);}
  function onAvatarHoldEnd(){if(avatarHoldTimer){clearTimeout(avatarHoldTimer);avatarHoldTimer=null;}}
  function replyToComment(postId,commentId){
    var p=findPost(postId),c=findComment(p,commentId);if(!p||!c)return;
    replyingComment={postId:p.id,id:c.id,name:c.name||'Pengguna'};
    openReelComments(p.id);
    var inp=shell.querySelector('.ohli-sheet-bg[data-cm="'+p.id+'"] .ohli-sheet-form [name=comment]');
    if(inp){inp.placeholder='Balas '+(c.name||'komentar')+'…';inp.focus();}
    var list=shell.querySelector('.ohli-sheet-bg[data-cm="'+p.id+'"] .ohli-sheet-list');
    var row=list&&list.querySelector('.ohli-comment[data-comment-id="'+c.id+'"]');if(row&&row.scrollIntoView)row.scrollIntoView({block:'nearest'});
  }
  function onCommentHoldStart(event){
    if(event.button!=null&&event.button!==0)return;
    var row=event.target&&event.target.closest?event.target.closest('.ohli-comment[data-post-id][data-comment-id]'):null;
    if(!row||!row.closest('.ohli-sheet-bg[data-cm],.ohli-post-comments')||event.target.closest('button,a,input,textarea,select'))return;
    clearTimeout(commentHoldTimer);
    commentHoldTimer=setTimeout(function(){commentHoldConsumed=true;replyToComment(row.dataset.postId,row.dataset.commentId);if(navigator.vibrate)try{navigator.vibrate(18);}catch(ignoreVibrate){}setTimeout(function(){commentHoldConsumed=false;},450);},520);
  }
  function onCommentHoldEnd(){if(commentHoldTimer){clearTimeout(commentHoldTimer);commentHoldTimer=null;}}
  function onStoryHoldStart(event){var viewer=shell&&shell.querySelector('#ohliStoryViewer');if(!viewer||viewer.hidden)return;if(event.target.closest('button:not(.ohli-story-tap-zone),a,input,textarea,form,.ohli-story-owner,.ohli-story-foot'))return;var media=shell.querySelector('#ohliStoryMedia'),r=media&&media.getBoundingClientRect();if(!r||event.clientX<r.left+r.width*.25||event.clientX>r.right-r.width*.25)return;clearTimeout(storyHoldTimer);storyHoldTimer=setTimeout(function(){storyHoldActive=true;storyHoldConsumed=true;pauseStoryPlayback();},170);}
  function onStoryHoldEnd(){if(storyHoldTimer){clearTimeout(storyHoldTimer);storyHoldTimer=null;}if(storyHoldActive){storyHoldActive=false;resumeStoryPlayback();setTimeout(function(){storyHoldConsumed=false;},350);}}
  function pauseStoryPlayback(){if(storyPaused)return;storyElapsed+=Math.max(0,Date.now()-storyProgressStarted);storyPaused=true;if(storyTimer){clearTimeout(storyTimer);storyTimer=null;}var viewer=shell&&shell.querySelector('#ohliStoryViewer'),v=viewer&&viewer.querySelector('video');if(v&&!v.paused)v.pause();}
  function resumeStoryPlayback(){if(!storyPaused)return;var viewer=shell&&shell.querySelector('#ohliStoryViewer'),v=viewer&&viewer.querySelector('video');storyPaused=false;storyProgressStarted=Date.now();var remaining=Math.max(0,storyDuration-storyElapsed);if(v&&v.paused)v.play().catch(function(){});if(remaining<=20){stepStory(1);return;}storyTimer=setTimeout(function(){stepStory(1);},remaining);}

  async function showPrivacySettings() {
    var me=rt.getMe()||{}, current={};
    try { var fresh=await rt.request('/api/profile?phone='+encodeURIComponent(me.phone)); profileData=fresh; current=(fresh.user&&fresh.user.privacy)||{}; } catch(e) { toast(apiError(e)); return; }
    function opt(name,label,def,mode){var v=current[name]||def;return '<label class="ohli-field">'+label+'<select name="'+name+'">'+(mode==='account'?'<option value="public" '+(v==='public'?'selected':'')+'>Akun publik</option><option value="private" '+(v==='private'?'selected':'')+'>Akun privat</option>':mode==='friends'?'<option value="everyone" '+(v==='everyone'?'selected':'')+'>Semua orang</option><option value="friends" '+(v==='friends'?'selected':'')+'>Hanya teman</option><option value="private" '+(v==='private'?'selected':'')+'>Tidak seorang pun</option>':'<option value="everyone" '+(v==='everyone'?'selected':'')+'>Semua orang</option><option value="followers" '+(v==='followers'?'selected':'')+'>Pengikut</option><option value="private" '+(v==='private'?'selected':'')+'>Hanya saya</option>')+'</select></label>'; }
    var body='<form id="ohliPrivacyForm"><p class="ohli-hint">Akun privat hanya menampilkan postingan dan Sorotan kepada pengikut yang sudah disetujui. Nomor akun tidak ditampilkan di profil OHLI Social.</p><h3 class="ohli-privacy-section-title">Profil & aktivitas</h3>'+opt('account','Visibilitas akun','public','account')+opt('activity','Siapa yang dapat melihat status aktif (titik hijau)','everyone','friends')+opt('followers','Siapa yang dapat melihat daftar pengikut','everyone')+opt('following','Siapa yang dapat melihat daftar mengikuti','everyone')+opt('likes','Siapa yang dapat melihat postingan yang kamu sukai','everyone')+opt('saved','Siapa yang dapat melihat koleksi tersimpan','private')+'<h3 class="ohli-privacy-section-title">Pesan langsung</h3>'+opt('directMessages','Siapa yang dapat mengirim chat dari profil','everyone','friends')+'</form>';
    showModal('Privasi akun',body,'Simpan privasi',async function(content){var f=content.querySelector('#ohliPrivacyForm');if(!f.reportValidity())return;var d=Object.fromEntries(new FormData(f).entries());await rt.request('/api/settings/privacy',{method:'POST',body:d});closeModal();toast('Pengaturan privasi diperbarui.');if(page==='profile')await renderProfile(me.phone);else await renderSettings();});
  }

  async function showFollowList(phone,type){
    try{var d=await rt.request('/api/followers?phone='+encodeURIComponent(phone)+'&type='+encodeURIComponent(type));var list=d.users||[];var html=list.length?list.map(function(u){return '<div class="ohli-list-card">'+avatar(u.name||'Pengguna OHLI',u.phone)+'<div class="ohli-list-copy"><strong>'+esc(u.name||'Pengguna OHLI')+'</strong><p>@'+esc(socialHandle(u))+'</p></div><button class="ohli-btn" data-action="profile" data-phone="'+esc(u.phone)+'">Profil</button></div>';}).join(''):'<p class="ohli-hint">Daftar ini masih kosong.</p>';showModal(type==='followers'?'Pengikut':'Mengikuti','<div class="ohli-list-stack">'+html+'</div>','',null);}catch(e){toast(apiError(e));}
  }

  async function showProfileViews(){try{var d=await rt.request('/api/profile/views');var list=d.viewers||[];var html=list.length?list.map(function(v){return '<div class="ohli-list-card">'+avatar(v.name||'Pengguna OHLI',v.phone)+'<div class="ohli-list-copy"><strong>'+esc(v.name||'Pengguna OHLI')+'</strong><p>'+Number(v.views||1)+' kali dilihat · terakhir '+esc(shortDate(v.lastSeen))+'</p></div><button class="ohli-btn" data-action="profile" data-phone="'+esc(v.phone)+'">Profil</button></div>';}).join(''):'<p class="ohli-hint">Belum ada kunjungan profil yang tercatat.</p>';showModal('Tayangan profil','<p class="ohli-hint">'+Number(d.viewsCount||0)+' tayangan dari '+Number(d.uniqueViewers||0)+' akun unik. Penghitungan dibatasi satu catatan per akun per hari.</p><div class="ohli-list-stack">'+html+'</div>','',null);}catch(e){toast(apiError(e));}}

  async function showStoryViewers(id){try{var d=await rt.request('/api/status/views?statusId='+encodeURIComponent(id));var list=(d.viewers||[]).slice().sort(function(a,b){return Number(b.views||1)-Number(a.views||1)||Number(b.lastSeen||0)-Number(a.lastSeen||0);});var html=list.length?list.map(function(v){return '<div class="ohli-list-card">'+avatar(v.name||'Pengguna OHLI',v.phone)+'<div class="ohli-list-copy"><strong>'+esc(v.name||'Pengguna OHLI')+'</strong><p><b>'+Number(v.views||1)+'×</b> tayangan · terakhir '+esc(shortDate(v.lastSeen))+'</p></div></div>';}).join(''):'<p class="ohli-hint">Belum ada penonton selain kamu.</p>';var likes=(d.likes||[]).map(function(v){return '<div class="ohli-list-card">'+avatar(v.name||'Pengguna OHLI',v.phone)+'<div class="ohli-list-copy"><strong>'+esc(v.name||'Pengguna OHLI')+'</strong><p>Menyukai Story · '+esc(shortDate(v.time))+'</p></div><span class="ohli-history-like">♥</span></div>';}).join('');showModal('Penonton & suka Story','<p class="ohli-hint">'+d.uniqueViewers+' akun unik · '+d.totalViews+' total tayangan termasuk replay.</p><h3 class="ohli-privacy-section-title">Penonton</h3><div class="ohli-list-stack">'+html+'</div><h3 class="ohli-privacy-section-title">Menyukai Story ('+(d.likes||[]).length+')</h3><div class="ohli-list-stack">'+(likes||'<p class="ohli-hint">Belum ada yang menyukai Story ini.</p>')+'</div>','',null);}catch(e){toast(apiError(e));}}

  async function createHighlightFromStory(id){
    var story=statusList.find(function(s){return s.id===id;})||(storyHistoryCache||[]).find(function(s){return s.id===id;});if(!story)return;
    try{
      var data=await rt.request('/api/highlights?phone='+encodeURIComponent((rt.getMe()||{}).phone||'')),existing=data.highlights||[];
      var options=existing.map(function(h){return '<option value="'+esc(h.id)+'">'+esc(h.name||'Sorotan')+' · '+Number((h.items||[]).length||h.itemCount||1)+' Story</option>';}).join('');
      var body='<form id="ohliHighlightForm"><label class="ohli-field">Simpan ke<select name="mode" id="ohliHighlightMode">'+(existing.length?'<option value="existing" selected>Sorotan yang sudah ada</option>':'')+'<option value="new" '+(!existing.length?'selected':'')+'>Buat Sorotan baru</option></select></label>'+(existing.length?'<div id="ohliHighlightExisting"><label class="ohli-field">Pilih Sorotan<select name="highlightId">'+options+'</select></label></div>':'')+'<div id="ohliHighlightNew" '+(existing.length?'hidden':'')+'><label class="ohli-field">Nama Sorotan<input name="name" maxlength="40" '+(!existing.length?'required':'')+' value="Momen spesial"></label><label class="ohli-field">Siapa yang dapat melihat<select name="visibility"><option value="public">Publik</option><option value="followers">Pengikut</option><option value="friends">Teman</option><option value="private">Hanya saya</option></select></label></div><p class="ohli-hint">Story aktif maupun dari Riwayat bisa disimpan ke Sorotan. Sorotan tetap ada di profil setelah Story 24 jam berakhir.</p></form>';
      showModal('Simpan ke Sorotan',body,'Simpan',async function(content){var f=content.querySelector('#ohliHighlightForm');if(!f.reportValidity())return;var selectedMode=f.elements.mode.value,payload={statusId:id};if(selectedMode==='existing'){if(!existing.length||!f.elements.highlightId||!f.elements.highlightId.value)throw new Error('Pilih Sorotan terlebih dahulu.');payload.highlightId=f.elements.highlightId.value;}else{var name=f.elements.name.value.trim();if(!name)throw new Error('Masukkan nama Sorotan.');payload.name=name;payload.visibility=f.elements.visibility.value;}await rt.request('/api/highlights',{method:'POST',body:payload});closeModal();toast(selectedMode==='existing'?'Story ditambahkan ke Sorotan yang dipilih.':'Sorotan baru berhasil dibuat.');await renderProfile((rt.getMe()||{}).phone);});
      var modeSelect=overlayContent.querySelector('#ohliHighlightMode');if(modeSelect)modeSelect.addEventListener('change',function(){var isNew=this.value==='new',existingWrap=overlayContent.querySelector('#ohliHighlightExisting'),newWrap=overlayContent.querySelector('#ohliHighlightNew'),name=overlayContent.querySelector('input[name="name"]');if(existingWrap)existingWrap.hidden=isNew;if(newWrap)newWrap.hidden=!isNew;if(name)name.required=isNew;});
    }catch(e){toast(apiError(e));}
  }

  function formatStoryHistoryDate(value){var date=new Date(Number(value)||value);return isNaN(date.getTime())?'Tanggal tidak diketahui':date.toLocaleString('id-ID',{day:'2-digit',month:'long',year:'numeric',hour:'2-digit',minute:'2-digit'});}
  function storyHistoryDay(value){var d=new Date(Number(value)||value);if(isNaN(d.getTime()))return 'Tanggal tidak diketahui';return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}
  function renderStoryHistoryRows(rows){var groups={};(rows||[]).forEach(function(s){var key=storyHistoryDay(s.historyAt||s.time);(groups[key]||(groups[key]=[])).push(s);});var keys=Object.keys(groups).sort().reverse();return keys.length?keys.map(function(day){var date=day==='Tanggal tidak diketahui'?day:new Date(day+'T12:00:00').toLocaleDateString('id-ID',{weekday:'long',day:'2-digit',month:'long',year:'numeric'});return '<section class="ohli-story-history-day"><h3>'+esc(date)+'</h3><div class="ohli-story-history-list">'+groups[day].map(function(s){var isVideo=s.mediaId&&(s.mediaType==='video'||String(s.mimeType||'').indexOf('video/')===0),media=s.mediaId?(isVideo?'<video src="'+esc(rt.mediaUrl(s.mediaId))+'#t=0.1" muted playsinline controls preload="metadata" aria-label="Pratinjau video Story"></video>':'<img src="'+esc(rt.mediaUrl(s.mediaId))+'" loading="lazy" decoding="async" alt="Story">'):'<div class="ohli-history-text">'+esc(s.text||'Story teks')+'</div>';return '<article class="ohli-story-history-card"><div class="ohli-story-history-media">'+media+'</div><div class="ohli-story-history-copy"><strong>'+esc(s.text|| (isVideo?'Video Story':(s.mediaId?'Foto Story':'Story teks')) )+'</strong><small>'+esc(formatStoryHistoryDate(s.historyAt||s.time))+'</small><small>'+Number(s.viewsCount||0)+' tayangan</small><button type="button" class="ohli-btn" data-action="view-history-story" data-story-id="'+esc(s.id)+'">Buka Story penuh</button><div class="ohli-story-history-actions"><button type="button" class="ohli-btn" data-action="history-story-viewers" data-story-id="'+esc(s.id)+'">Penonton</button><button type="button" class="ohli-btn" data-action="history-story-highlight" data-story-id="'+esc(s.id)+'">+ Sorotan</button><button type="button" class="ohli-btn" data-action="repost-history-story" data-story-id="'+esc(s.id)+'">Bagikan lagi</button><button type="button" class="ohli-btn" data-action="delete-history-story" data-story-id="'+esc(s.id)+'">Hapus</button></div></div></article>';}).join('')+'</div></section>';}).join(''):'<p class="ohli-hint">Tidak ada Story pada tanggal ini.</p>';}
  async function openStoryHistory(){
    try{
      var data=await rt.request('/api/status/history');storyHistoryCache=data.stories||[];
      var html='<div class="ohli-story-history-tools"><label class="ohli-field">Cari tanggal Story<input type="date" id="ohliStoryHistoryDate"></label><button type="button" class="ohli-btn" id="ohliStoryHistoryAll">Tampilkan semua tanggal</button><button type="button" class="ohli-btn" data-action="open-story-collections">Story teman tersimpan</button></div><div id="ohliStoryHistoryRows">'+renderStoryHistoryRows(storyHistoryCache)+'</div>';
      showModal('Riwayat Story', '<p class="ohli-hint">Story aktif hilang dari beranda setelah 24 jam, tetapi riwayat ini menyimpan foto/video aslinya beserta tanggal. Pilih tanggal untuk menyaring.</p>'+html,'',null);
      var dateInput=overlayContent.querySelector('#ohliStoryHistoryDate'),allBtn=overlayContent.querySelector('#ohliStoryHistoryAll'),rows=overlayContent.querySelector('#ohliStoryHistoryRows');
      if(dateInput)dateInput.addEventListener('change',function(){rows.innerHTML=renderStoryHistoryRows(storyHistoryCache.filter(function(x){return storyHistoryDay(x.historyAt||x.time)===dateInput.value;}));});
      if(allBtn)allBtn.addEventListener('click',function(){if(dateInput)dateInput.value='';rows.innerHTML=renderStoryHistoryRows(storyHistoryCache);});
    }catch(e){toast(apiError(e));}
  }
  async function openHistoryStory(id){
    var story=(storyHistoryCache||[]).find(function(s){return s.id===id;});if(!story)return;
    var me=rt.getMe()||{},copy=Object.assign({},story,{phone:me.phone,name:me.name,isMine:true});activeHighlight=null;storyItems=[copy];storyGroups=[{phone:me.phone,items:storyItems}];storyGroupIndex=0;storyIndex=0;pauseAllReels();closeModal();var viewer=shell.querySelector('#ohliStoryViewer');viewer.hidden=false;await renderStoryFrame();
  }
  async function repostHistoryStory(id){try{await rt.request('/api/status/history/repost',{method:'POST',body:{statusId:id}});toast('Story dibagikan ulang dan aktif selama 24 jam.');var fresh=await rt.request('/api/status?surface=social');statusList=fresh.statuses||[];if(page==='story')await renderStories();else if(page==='home'||page==='videos'){var strip=shell.querySelector('#ohliStoryStripContent');if(strip)renderStoryStrip(strip,statusList);}await openStoryHistory();}catch(e){toast(apiError(e));}}
  async function deleteHistoryStory(id){if(!confirm('Hapus Story ini dari Riwayat? Sorotan yang sudah menyimpan salinannya tetap dipertahankan.'))return;try{await rt.request('/api/status/history/delete',{method:'POST',body:{statusId:id}});storyHistoryCache=storyHistoryCache.filter(function(s){return s.id!==id;});toast('Story dihapus dari Riwayat.');await openStoryHistory();}catch(e){toast(apiError(e));}}
  async function deleteOwnStory(id){if(!confirm('Hapus Story ini dari Story aktif? Salinannya di Riwayat dan Sorotan tetap tersimpan.'))return;try{await rt.request('/api/status/delete',{method:'POST',body:{statusId:id}});statusList=statusList.filter(function(s){return s.id!==id;});closeStory();var fresh=await rt.request('/api/status?surface=social');statusList=fresh.statuses||[];if(page==='story')await renderStories();else if(page==='home'||page==='videos'){var strip=shell.querySelector('#ohliStoryStripContent');if(strip)renderStoryStrip(strip,statusList);}else if(page==='profile')await renderProfile(profileTarget||((rt.getMe()||{}).phone));toast('Story aktif dihapus. Riwayat dan Sorotan tetap ada.');}catch(e){toast(apiError(e));}}
  async function editHighlight(id){
    try{
      var data=await rt.request('/api/highlights?phone='+encodeURIComponent((rt.getMe()||{}).phone||'')),h=(data.highlights||[]).find(function(x){return x.id===id;});
      if(!h)throw new Error('Sorotan tidak ditemukan.');
      var items=h.items||[],coverId=h.coverStatusId||(items[0]&&items[0].id)||'';
      var coverOptions=items.map(function(item,i){var label=(item.text||('Story '+(i+1))).slice(0,42);var itemId=item.id||item.statusId;return '<option value="'+esc(itemId)+'" '+(coverId===itemId?'selected':'')+'>'+esc(label)+'</option>';}).join('');
      var body='<form id="ohliEditHighlightForm"><label class="ohli-field">Nama Sorotan<input name="name" maxlength="40" value="'+esc(h.name||'Sorotan')+'" required></label><label class="ohli-field">Privasi<select name="visibility"><option value="public" '+(h.visibility==='public'?'selected':'')+'>Publik</option><option value="followers" '+(h.visibility==='followers'?'selected':'')+'>Pengikut</option><option value="friends" '+(h.visibility==='friends'?'selected':'')+'>Teman</option><option value="private" '+(h.visibility==='private'?'selected':'')+'>Hanya saya</option></select></label><label class="ohli-field">Sampul Sorotan<select name="coverStatusId">'+(coverOptions||'<option value="">Belum ada Story</option>')+'</select></label><p class="ohli-hint">Pilih foto atau video dari isi Sorotan sebagai thumbnail profil.</p></form>';
      showModal('Edit Sorotan',body,'Simpan',async function(content){var f=content.querySelector('#ohliEditHighlightForm');if(!f.reportValidity())return;await rt.request('/api/highlights/edit',{method:'POST',body:{id:id,name:f.elements.name.value.trim(),visibility:f.elements.visibility.value,coverStatusId:f.elements.coverStatusId.value}});closeModal();toast('Nama, privasi, dan sampul Sorotan diperbarui.');await renderProfile((rt.getMe()||{}).phone);});
    }catch(e){toast(apiError(e));}
  }
  async function deleteHighlight(id){if(!confirm('Hapus seluruh Sorotan ini dari profil? Story asli di Riwayat tidak ikut dihapus.'))return;try{await rt.request('/api/highlights/delete',{method:'POST',body:{id:id}});toast('Sorotan dihapus. Story asli tetap ada di Riwayat.');await renderProfile(profileTarget||((rt.getMe()||{}).phone));}catch(e){toast(apiError(e));}}
  async function deleteHighlightItem(collectionId,statusId){if(!confirm('Hapus Story ini dari Sorotan? Story asli tetap ada di Riwayat.'))return;try{await rt.request('/api/highlights/item/delete',{method:'POST',body:{highlightId:collectionId,statusId:statusId}});toast('Story dihapus dari Sorotan.');closeStory();await renderProfile((rt.getMe()||{}).phone);}catch(e){toast(apiError(e));}}

  function openHighlight(h){
    if(!h)return;
    var ownerPhone=h.owner||((profileData&&profileData.user&&profileData.user.phone)||profileTarget),ownerName=h.ownerName||((profileData&&profileData.user&&profileData.user.name)||'Sorotan'),items=Array.isArray(h.items)?h.items.slice():[];
    if(!items.length){items=[{id:h.statusId||h.id,mediaId:h.mediaId||'',mediaType:h.mediaType||'',mimeType:h.mimeType||'',fileName:h.fileName||'',fileSize:h.fileSize||0,text:h.text||'',time:h.storyTime||h.createdAt||Date.now()}];}
    items=items.map(function(item){var copy=Object.assign({},item);copy.phone=ownerPhone;copy.name=ownerName;copy.isMine=!!h.isMine;copy.time=Number(copy.time||copy.storyTime||h.createdAt)||Date.now();return copy;});
    activeHighlight=h;storyItems=items;storyGroups=[{phone:ownerPhone,items:items}];storyGroupIndex=0;storyIndex=0;pauseAllReels();recordSocialActivity('story',h.id,'Sorotan '+(h.name||'OHLI'));
    var viewer=shell.querySelector('#ohliStoryViewer');viewer.hidden=false;renderStoryFrame();
  }

  async function submitPasswordChange(){var a=shell.querySelector('#ohliCurrentPassword'),b=shell.querySelector('#ohliNewPassword');if(!a||!b)return;if(a.value.length<6||b.value.length<6){toast('Isi kata sandi saat ini dan kata sandi baru minimal 6 karakter.');return;}try{await rt.request('/api/password',{method:'POST',body:{currentPassword:a.value,newPassword:b.value}});a.value='';b.value='';toast('Kata sandi berhasil diganti. Perangkat lain telah dikeluarkan.');}catch(e){toast(apiError(e));}}

  async function showBlockedList(){
    try{var d=await rt.request('/api/blocks'),list=d.users||[];var html=list.length?list.map(function(u){return '<div class="ohli-list-card">'+avatar(u.name||'Pengguna OHLI',u.phone)+'<div class="ohli-list-copy"><strong>'+esc(u.name||'Pengguna OHLI')+'</strong><p>@'+esc(socialHandle(u))+'</p></div><button type="button" class="ohli-btn" data-action="unblock-user" data-phone="'+esc(u.phone)+'">Buka blokir</button></div>';}).join(''):'<p class="ohli-hint">Belum ada akun yang diblokir.</p>';showModal('Daftar akun diblokir','<div class="ohli-list-stack">'+html+'</div>','',null);}catch(e){toast(apiError(e));}
  }
  async function showFollowRequests(){try{var d=await rt.request('/api/follow/requests'),list=d.requests||[];var html=list.length?list.map(function(u){return '<div class="ohli-list-card">'+avatar(u.name||'Pengguna OHLI',u.phone)+'<div class="ohli-list-copy"><strong>'+esc(u.name||'Pengguna OHLI')+'</strong><p>@'+esc(socialHandle(u))+'</p></div><button class="ohli-btn" data-action="follow-request-reject" data-phone="'+esc(u.phone)+'">Tolak</button><button class="ohli-btn ohli-btn-primary" data-action="follow-request-approve" data-phone="'+esc(u.phone)+'">Terima</button></div>';}).join(''):'<p class="ohli-hint">Belum ada permintaan mengikuti.</p>';showModal('Permintaan mengikuti','<div class="ohli-list-stack">'+html+'</div>','',null);}catch(e){toast(apiError(e));}}

  async function openGroupChat(id){
    try{var g=(groupItems||[]).find(function(x){return x.id===id;});var d=await rt.request('/api/groups/messages?groupId='+encodeURIComponent(id));g=d.group||g||{name:'Grup'};var list=d.messages||[];var body='<div class="ohli-thread-toolbar"><span>'+Number(g.memberCount||0)+' anggota · ruang obrolan</span><div class="ohli-thread-toolbar-actions"><button type="button" class="ohli-btn" data-action="group-members" data-id="'+esc(id)+'">Anggota</button>'+(g.isOwner?'<button type="button" class="ohli-btn" data-action="group-add-members" data-id="'+esc(id)+'">Tambah teman</button>':'')+'<button type="button" class="ohli-btn" data-action="create-group-poll" data-id="'+esc(id)+'">Buat polling</button></div></div><div class="ohli-group-thread" id="ohliGroupThread" role="log" aria-live="polite">'+(list.length?list.map(function(m){var mine=!!m.isMine||m.sender===(rt.getMe()||{}).phone;if(m.kind==='poll'){var opts=m.options||[];return '<div class="ohli-group-message ohli-group-poll '+(mine?'mine':'')+'"><b>'+esc(mine?'Kamu':m.name||'Anggota')+'</b><strong>'+esc(m.question||m.text||'Polling grup')+'</strong><div class="ohli-poll-options">'+opts.map(function(o){var votes=o.votes||[],count=votes.length,selected=!!o.votedByMe||votes.indexOf((rt.getMe()||{}).phone)>=0;return '<button type="button" class="ohli-poll-option '+(selected?'selected':'')+'" data-action="group-vote" data-id="'+esc(id)+'" data-message-id="'+esc(m.id)+'" data-option-id="'+esc(o.id)+'"><span>'+esc(o.text||o.label||'Pilihan')+'</span><b>'+count+'</b></button>';}).join('')+'</div><small>'+esc(shortDate(m.time))+' · '+opts.reduce(function(n,o){return n+(o.votes||[]).length;},0)+' suara</small></div>';}return '<div class="ohli-group-message '+(mine?'mine':'')+'"><b>'+esc(mine?'Kamu':m.name||'Anggota OHLI')+'</b><p>'+esc(m.text||'')+'</p><small>'+esc(shortDate(m.time))+'</small></div>';}).join(''):'<p class="ohli-hint">Belum ada pesan. Mulai percakapan grup.</p>')+'</div><form id="ohliGroupMessageForm" class="ohli-thread-composer"><input name="text" maxlength="2000" placeholder="Tulis pesan grup…" required><button type="submit">Kirim</button></form>';
      showModal('Chat · '+(g.name||'Grup'),body,'',null);var form=overlayContent.querySelector('#ohliGroupMessageForm');form.addEventListener('submit',async function(e){e.preventDefault();var input=form.elements.text,text=input.value.trim();if(!text)return;try{await rt.request('/api/groups/messages',{method:'POST',body:{groupId:id,text:text}});input.value='';await openGroupChat(id);}catch(err){toast(apiError(err));}});
    }catch(e){toast(apiError(e));}
  }

  async function showGroupMembers(id){
    try{var data=await rt.request('/api/groups/members?groupId='+encodeURIComponent(id)),g=data.group||{},members=data.members||[];var html=members.length?members.map(function(m){return '<div class="ohli-list-card">'+avatar(m.name,m.id)+'<div class="ohli-list-copy"><strong>'+esc(m.name||'Anggota OHLI')+(m.isOwner?' · Pemilik':(m.isMe?' · Kamu':''))+'</strong><p>@'+esc(m.username||'pengguna-ohli')+'</p></div>'+(g.isOwner&&!m.isOwner?'<button type="button" class="ohli-btn ohli-btn-danger" data-action="group-remove-member" data-id="'+esc(id)+'" data-target="'+esc(m.id)+'">Keluarkan</button>':'')+'</div>';}).join(''):'<p class="ohli-hint">Belum ada anggota.</p>';showModal('Anggota grup · '+esc(g.name||'OHLI'),'<div class="ohli-list-stack">'+html+'</div>'+(g.isOwner?'<p class="ohli-hint">Hanya pemilik grup yang dapat menambahkan atau mengeluarkan anggota.</p>':''),'',null);}catch(e){toast(apiError(e));}
  }
  async function showGroupMemberPicker(id){
    try{var pair=await Promise.all([loadGroupInviteOptions(),rt.request('/api/groups/members?groupId='+encodeURIComponent(id))]),contacts=pair[0],g=pair[1].group||{};if(!g.isOwner){toast('Hanya pemilik grup yang dapat menambahkan anggota.');return;}var body='<p class="ohli-hint">Pilih Kontak Chat atau koneksi Social. Anggota yang sudah ada akan dilewati otomatis.</p><div class="ohli-group-contact-picker">'+(contacts.length?contacts.map(function(c){return '<label class="ohli-group-contact-choice"><input type="checkbox" name="memberPhones" value="'+esc(c.id)+'"><span class="ohli-avatar">'+esc(initial(c.name))+'</span><span><b>'+esc(c.name)+'</b><small>'+(c.username?'@'+esc(c.username)+' · ':'')+esc(c.meta)+'</small></span></label>';}).join(''):'<p class="ohli-hint">Belum ada kontak atau koneksi Social yang dapat ditambahkan.</p>')+'</div>';showModal('Tambah teman ke grup',body,'Tambahkan',async function(content){var phones=Array.from(content.querySelectorAll('input[name="memberPhones"]:checked')).map(function(x){return x.value;});if(!phones.length){toast('Pilih setidaknya satu teman.');return;}var result=await rt.request('/api/groups/members',{method:'POST',body:{groupId:id,action:'add',phones:phones}});closeModal();toast(result.added+' teman ditambahkan'+(result.skipped?' · '+result.skipped+' dilewati':'')+'.');await openGroupChat(id);});}catch(e){toast(apiError(e));}
  }

  async function openChannel(id){
    try{var c=(channelItems||[]).find(function(x){return x.id===id;});var d=await rt.request('/api/channels/posts?channelId='+encodeURIComponent(id));c=d.channel||c||{name:'Channel'};var list=d.posts||[],self=!!c.isOwner||(rt.getMe()||{}).phone===(c.owner||'');var composer=self?'<form id="ohliChannelPostForm" class="ohli-thread-composer"><input name="text" maxlength="2000" placeholder="Tulis pengumuman Channel…" required><button type="submit">Kirim</button></form>':'';var body='<div class="ohli-thread-toolbar"><span>Channel · siaran satu arah</span></div>'+composer+'<div class="ohli-channel-posts" id="ohliChannelPosts">'+(list.length?list.map(function(p){var reactions=p.reactionSummary||[];return '<article class="ohli-card ohli-channel-post"><strong>'+esc(p.authorName||c.ownerName||'Admin')+'</strong><small>'+esc(shortDate(p.createdAt))+'</small><p>'+esc(p.text)+'</p><div class="ohli-channel-reactions">'+reactions.map(function(r){return '<button type="button" class="'+(r.mine?'active':'')+'" data-action="channel-react" data-channel-id="'+esc(id)+'" data-post-id="'+esc(p.id)+'" data-emoji="'+esc(r.emoji)+'">'+esc(r.emoji)+' <span>'+Number(r.count||0)+'</span></button>';}).join('')+'</div></article>';}).join(''):'<p class="ohli-hint">Belum ada pengumuman dari Channel ini.</p>')+'</div>';
      showModal('Channel · '+(c.name||'OHLI'),body,'',null);var form=overlayContent.querySelector('#ohliChannelPostForm');if(form)form.addEventListener('submit',async function(e){e.preventDefault();var input=form.elements.text,text=input.value.trim();if(!text)return;try{await rt.request('/api/channels/posts',{method:'POST',body:{channelId:id,text:text}});input.value='';await openChannel(id);}catch(err){toast(apiError(err));}});
    }catch(e){toast(apiError(e));}
  }

  var qrStream=null,qrLoop=false,qrReader=null;
  var doubleTapPending={};
  function openContactQr(phone){var me=rt.getMe()||{};phone=phone||me.phone;if(phone!==me.phone){toast('QR profil akun lain belum dapat diterbitkan.');return;}var url='/api/contact-qr?token='+encodeURIComponent(rt.getToken())+'&baseUrl='+encodeURIComponent(location.origin)+'&v='+Date.now();showModal('QR kontak OHLI','<div class="ohli-qr-card"><img src="'+url+'" alt="QR kontak OHLI"><strong>'+esc(me.name||'Akun OHLI')+'</strong><span>'+esc(me.phone||'')+'</span><p>Pindai kode ini untuk menambahkan kontak OHLI.</p><button type="button" class="ohli-btn" data-action="scan-contact-qr">Pindai QR teman</button></div>','',null);}
  async function stopQrScanner(){qrLoop=false;if(qrReader){try{qrReader.reset();}catch(e){}qrReader=null;}if(qrStream){qrStream.getTracks().forEach(function(t){t.stop();});qrStream=null;}}
  function parseContactQr(raw){var value=String(raw||'').trim();try{var d=JSON.parse(value);if(d.type==='ohli-contact'&&/^\d{8,15}$/.test(String(d.phone||'')))return d;}catch(ignoreJson){}try{var u=new URL(value,location.href),phone=u.searchParams.get('ohli_profile')||u.searchParams.get('phone')||'';if(/^\d{8,15}$/.test(phone))return {type:'ohli-contact',phone:phone,name:u.searchParams.get('ohli_name')||''};}catch(ignoreUrl){}var plain=value.replace(/[^0-9]/g,'');if(/^\d{8,15}$/.test(plain))return {type:'ohli-contact',phone:plain,name:''};throw new Error('Kode QR tidak dikenali. Pindai QR profil OHLI atau masukkan tautan/nomor yang benar.');}
  async function importQrContact(raw){var d=parseContactQr(raw);try{await rt.request('/api/contacts',{method:'POST',body:{phone:d.phone,alias:d.name||''}});}catch(contactError){if(!/sudah|exists|terdaftar|duplicate/i.test(contactError.message||''))throw contactError;}await stopQrScanner();closeModal();toast('Profil '+(d.name||d.phone)+' dibuka.');setPage('profile',{phone:d.phone});}
  function showManualContactAdd(message){var body='<p class="ohli-hint">'+esc(message||'Masukkan nomor OHLI atau tempel tautan QR profil.')+'</p><label class="ohli-field">Kode QR / tautan / nomor<input id="ohliManualQrPhone" inputmode="text" maxlength="500" placeholder="https://.../?ohli_profile=… atau nomor OHLI"></label>';showModal('Masukkan kode kontak',body,'Buka profil',async function(content){await importQrContact(content.querySelector('#ohliManualQrPhone').value);});}
  function chooseContactQrMethod(){showModal('Pindai QR kontak','<div class="ohli-qr-methods"><button type="button" class="ohli-btn ohli-btn-primary" data-action="start-contact-qr-camera">▣ Gunakan kamera</button><button type="button" class="ohli-btn" data-action="manual-contact-qr">Masukkan kode / tautan</button></div><p class="ohli-hint">Kamera memerlukan HTTPS atau localhost. Hasil pemindaian dari aplikasi lain juga dapat ditempel di sini.</p>','',null);}
  function loadQrFallback(){return new Promise(function(resolve,reject){if(window.ZXingBrowser||window.ZXing){resolve(window.ZXingBrowser||window.ZXing);return;}var script=document.createElement('script');script.src='https://unpkg.com/@zxing/browser@0.1.5/umd/index.min.js';script.async=true;script.onload=function(){var api=window.ZXingBrowser||window.ZXing;if(api)resolve(api);else reject(new Error('Library pemindai tidak tersedia'));};script.onerror=function(){reject(new Error('Library pemindai QR tidak dapat dimuat'));};document.head.appendChild(script);});}
  async function openContactScanner(){
    await stopQrScanner();
    if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia){showManualContactAdd('Browser menolak akses kamera. Gunakan HTTPS atau localhost, lalu izinkan kamera.');return;}
    showModal('Pindai QR kontak','<video id="ohliQrVideo" autoplay playsinline muted></video><p class="ohli-hint" id="ohliQrState">Meminta izin kamera…</p><button type="button" class="ohli-btn" data-action="close-modal">Tutup pemindai</button>','',null);
    try{
      qrStream=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'}},audio:false});
      var video=overlayContent.querySelector('#ohliQrVideo');video.srcObject=qrStream;await video.play();
      if('BarcodeDetector' in window){var detector=new BarcodeDetector({formats:['qr_code']});qrLoop=true;var busy=false;async function tick(){if(!qrLoop||!overlay||overlay.hidden||busy)return;try{var codes=await detector.detect(video);if(codes&&codes.length){busy=true;await importQrContact(codes[0].rawValue);return;}}catch(e){}if(qrLoop)requestAnimationFrame(tick);}requestAnimationFrame(tick);}
      else{
        var api=await loadQrFallback(),Reader=api.BrowserMultiFormatReader||api.BrowserQRCodeReader;if(!Reader)throw new Error('Pemindai QR tidak kompatibel');
        qrReader=new Reader();qrLoop=true;qrReader.decodeFromVideoElement(video,function(result,err){if(!qrLoop||!result)return;qrLoop=false;var raw=result.getText?result.getText():String(result.text||'');importQrContact(raw).catch(function(e){toast(apiError(e));});});
      }
      var note=overlayContent.querySelector('#ohliQrState');if(note)note.textContent='Arahkan kamera belakang ke QR kontak OHLI.';
    }catch(e){await stopQrScanner();var secureHint=(!window.isSecureContext&&location.hostname!=='localhost'&&location.hostname!=='127.0.0.1')?'Kamera browser memerlukan HTTPS atau localhost. Alamat http://IP-lokal tidak mendapat izin kamera. ':'';var reason=e&&e.name==='NotAllowedError'?'Izin kamera ditolak. Buka izin situs di browser lalu coba lagi.':(e&&e.name==='NotFoundError'?'Kamera tidak ditemukan pada perangkat ini.':(e&&e.name==='NotReadableError'?'Kamera sedang dipakai aplikasi lain. Tutup aplikasi itu lalu coba lagi.':(e.message||'periksa izin kamera')));showManualContactAdd(secureHint+reason+' Masukkan nomor teman secara manual.');}
  }

  async function loadGroupInviteOptions(){
    var pair=await Promise.all([rt.request('/api/contacts'),rt.request('/api/social/users')]),contacts=pair[0].contacts||[],social=pair[1].users||[],items=[],seen={};
    contacts.forEach(function(c){var id=c.socialId||c.phone;if(!id||seen[id])return;seen[id]=true;var name=c.alias||c.name||'Pengguna OHLI',digits=String(name).replace(/\D/g,'');if(digits.length>=8&&digits.length<=15&&digits===String(c.phone||'').replace(/\D/g,''))name=c.username?'@'+c.username:'Pengguna OHLI';items.push({id:id,name:name,username:c.username||'',meta:'Kontak Chat'});});
    social.filter(function(u){return !!(u.isFollowing||u.followsYou);}).forEach(function(u){var id=u.phone;if(!id||seen[id])return;seen[id]=true;items.push({id:id,name:u.name||'Pengguna OHLI',username:u.username||'',meta:u.isFollowing&&u.followsYou?'Teman Social':(u.isFollowing?'Mengikuti':'Pengikut')});});
    return items;
  }
  async function showCommunityForm(kind) {
    var isGroup=kind==='group',title=isGroup?'Buat grup/komunitas':'Buat Channel',contacts=[];
    if(isGroup){try{contacts=await loadGroupInviteOptions();}catch(e){toast('Daftar teman belum dapat dimuat: '+apiError(e));}}
    var picker=isGroup?'<div class="ohli-field"><label>TAMBAHKAN TEMAN</label><div class="ohli-group-contact-picker">'+(contacts.length?contacts.map(function(c){return '<label class="ohli-group-contact-choice"><input type="checkbox" name="memberPhones" value="'+esc(c.id)+'"><span class="ohli-avatar">'+esc(initial(c.name))+'</span><span><b>'+esc(c.name)+'</b><small>'+(c.username?'@'+esc(c.username)+' · ':'')+esc(c.meta)+'</small></span></label>';}).join(''):'<p class="ohli-hint">Belum ada kontak atau koneksi Social. Kamu tetap bisa membuat grup dan menambahkan teman nanti.</p>')+'</div><small class="ohli-hint">Pilih kontak Chat atau akun Social yang kamu ikuti / mengikuti kamu.</small></div>':'';
    var body='<form id="ohliCommunityForm"><div class="ohli-field"><label for="ohliCommunityName">NAMA</label><input id="ohliCommunityName" name="name" maxlength="70" minlength="2" required placeholder="Nama '+(isGroup?'grup':'channel')+'"></div><div class="ohli-field"><label for="ohliCommunityDescription">DESKRIPSI</label><textarea id="ohliCommunityDescription" name="description" maxlength="500" placeholder="Tentang '+(isGroup?'grup':'channel')+'"></textarea></div><div class="ohli-field"><label for="ohliCommunityPrivacy">AKSES</label><select id="ohliCommunityPrivacy" name="privacy"><option value="public">Publik</option><option value="private">Privat</option></select></div>'+picker+'<p class="ohli-hint">'+(isGroup?'Anggota grup dapat mengirim pesan, melihat anggota, dan mengikuti polling. Pemilik dapat menambahkan atau mengeluarkan anggota.':'Pemilik Channel menerbitkan pengumuman satu arah dan pengikut dapat memberi reaksi.')+'</p></form>';
    showModal(title,body,'Buat',async function(content){var form=content.querySelector('#ohliCommunityForm'),data=Object.fromEntries(new FormData(form).entries());if(!form.reportValidity())return;if(isGroup)data.memberPhones=Array.from(form.querySelectorAll('input[name="memberPhones"]:checked')).map(function(x){return x.value;});var result=await rt.request(isGroup?'/api/groups':'/api/channels',{method:'POST',body:data});closeModal();toast((isGroup?'Grup':'Channel')+' dibuat.'+(result.addedMembers?' '+result.addedMembers+' teman ditambahkan.':''));await renderCommunities(kind);if(isGroup&&result.item)await openGroupChat(result.item.id);});
  }

  // Legacy bridge so users can switch back without losing the existing chat screen/API.
  window.addEventListener('ohli-authenticated', function () { if (!shell) makeShell(); profileTarget = (rt.getMe() || {}).phone; showShell('home'); checkHealth(); try{var qp=new URLSearchParams(location.search),qrPhone=qp.get('ohli_profile');if(qrPhone&&/^\d{8,15}$/.test(qrPhone)){history.replaceState(null,'',location.pathname+location.hash);setPage('profile',{phone:qrPhone});}}catch(ignoreDeepLink){} });
  window.addEventListener('ohli-logged-out', function () { if (shell) shell.hidden = true; var app = document.getElementById('app'); if (app) app.hidden = true; });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { if (!shell) makeShell(); }); else makeShell();
  window.ohliSocial = { openHome:function(){ if (!shell) makeShell(); showShell('home'); }, navigate:function(p,phone){ if (!shell) makeShell(); showShell(p, phone ? {phone:phone} : undefined); } };



  function unlockPageAudio(event) {
    if(audioUnlocked||!shell||shell.hidden||!event||event.isTrusted===false)return;
    audioUnlocked=true;needGesture=false;try{sessionStorage.setItem('ohli:social-audio-unlocked','on');}catch(e){}
    var soundButton=event.target&&event.target.closest?event.target.closest('[data-action="toggle-sound"]'):null;
    if(soundButton){audioUnlockTap=true;soundOn=true;localStorage.setItem('ohli:social-reel-sound','on');}
    if(!soundOn){refreshAudioButtons();return;}
    var hit=event.target&&event.target.closest?event.target.closest('#ohliStoryMedia,.ohli-vwrap'):null;
    var current=hit&&hit.querySelector('video');
    if(!current)current=shell.querySelector('#ohliStoryMedia video')||activeReelVideo||shell.querySelector('video[data-ohli-video="true"]');
    if(current&&current.isConnected&&current.dataset.mute!=='1'){
      current.muted=false;var promise=current.play();if(promise&&promise.then)promise.then(refreshAudioButtons).catch(function(){current.muted=true;refreshAudioButtons();});
    }
    refreshAudioButtons();
  }
  document.addEventListener('pointerdown',unlockPageAudio,true);
  document.addEventListener('keydown',unlockPageAudio,true);

  document.addEventListener('keydown',function(e){var v=shell&&shell.querySelector('#ohliStoryViewer');if(!v||v.hidden)return;if(e.key==='ArrowLeft'){e.preventDefault();stepStory(-1);}else if(e.key==='ArrowRight'){e.preventDefault();stepStory(1);}else if(e.key==='Escape')closeStory();});
  (function bindStorySwipe(){var start=null;document.addEventListener('touchstart',function(e){var media=e.target.closest&&e.target.closest('#ohliStoryMedia');if(media&&e.touches&&e.touches.length===1)start={x:e.touches[0].clientX,y:e.touches[0].clientY};},{passive:true});document.addEventListener('touchend',function(e){if(!start)return;var t=e.changedTouches&&e.changedTouches[0];if(t){var dx=t.clientX-start.x,dy=t.clientY-start.y;if(Math.abs(dx)>45&&Math.abs(dx)>Math.abs(dy)*1.25)stepStory(dx<0?1:-1);}start=null;},{passive:true});})();

  /* v1.7: ketuk video = putar/jeda; ikon & progres; pasang pengamat video untuk semua halaman */
  document.addEventListener('click', function (e) {
    var w = e.target.closest && e.target.closest('#ohliSocialShell .ohli-vwrap'); if (!w || e.target.closest('[data-action]')) return;
    var v = w.querySelector('video'); if (!v) return;
    if (v.paused) playVid(v); else if (v.muted && soundOn && v.dataset.mute !== '1') { v.muted = false; needGesture = false; } else v.pause();
  });

  var lastMediaTap={id:'',time:0};
  async function doubleTapLike(postId,host){var p=findPost(postId);if(!p||p.liked||doubleTapPending[postId])return;doubleTapPending[postId]=true;try{var d=await rt.request('/api/posts/like',{method:'POST',body:{postId:postId}});updatePost(d.post);if(host){var heart=document.createElement('div');heart.className='ohli-double-heart';heart.textContent='♥';host.appendChild(heart);setTimeout(function(){heart.remove();},850);}await rerenderContent();}catch(e){toast(apiError(e));}finally{setTimeout(function(){delete doubleTapPending[postId];},450);}}
  document.addEventListener('click',function(e){var media=e.target.closest&&e.target.closest('.ohli-post-media, .ohli-reel .ohli-vwrap');if(!media||e.target.closest('[data-action]'))return;var card=media.closest('[data-post-card]');if(!card)return;var id=card.getAttribute('data-post-card'),now=Date.now();if(lastMediaTap.id===id&&now-lastMediaTap.time<360){lastMediaTap={id:'',time:0};doubleTapLike(id,media.parentElement||card);}else lastMediaTap={id:id,time:now};});

    function vstate(e, paused) { var v = e.target; if (!v || !v.matches || !v.matches('video[data-ohli-video]')) return; var w = v.closest('.ohli-vwrap'); if (w) w.classList.toggle('paused', paused); }
  document.addEventListener('play', function (e) { vstate(e, false); }, true);
  document.addEventListener('pause', function (e) { vstate(e, true); }, true);
  document.addEventListener('volumechange', refreshAudioButtons, true);
  document.addEventListener('timeupdate', function (e) { var v = e.target, r = v && v.closest && v.closest('.ohli-reel'); if (r && v.duration) r.querySelector('.ohli-reel-bar i').style.width = (v.currentTime / v.duration * 100) + '%'; }, true);
  var vTimer = 0; new MutationObserver(function () { clearTimeout(vTimer); vTimer = setTimeout(initVideoObserver, 120); }).observe(document.body, {childList: true, subtree: true});

  /* ===== v1.9 ===== */
  function metaOf(p) { try { return p && p.meta ? (typeof p.meta === 'string' ? JSON.parse(p.meta) : p.meta) : null; } catch (e) { return null; } }
  function mediaOverlay(p) { var m = metaOf(p); if (!m || !m.items || !m.items.length) return ''; return '<div class="ohli-ovl">' + m.items.map(function (it) { return '<span class="ohli-ovl-item" style="left:' + (+it.x || 50) + '%;top:' + (+it.y || 50) + '%;font-size:' + (+it.s || 7) + 'cqw;color:' + esc(it.c || '#fff') + '">' + esc(it.t) + '</span>'; }).join('') + '</div>'; }
  function vAttrs(p) { var m = metaOf(p) || {}; return (m.mute ? ' data-mute="1"' : '') + (m.start ? ' data-start="' + (+m.start) + '"' : '') + (m.end ? ' data-end="' + (+m.end) + '"' : ''); }
  function vStyle(p) { var m = metaOf(p) || {}, r = {'1:1': '1/1', '4:5': '4/5', '9:16': '9/16'}[m.crop]; return r ? ' style="aspect-ratio:' + r + '"' : ''; }
  document.addEventListener('timeupdate', function (e) { var v = e.target; if (v && v.dataset && v.dataset.end && v.currentTime >= +v.dataset.end) v.currentTime = +v.dataset.start || 0; }, true);
  document.addEventListener('loadedmetadata', function (e) { var v = e.target; if (v && v.dataset && v.dataset.start) v.currentTime = +v.dataset.start; }, true);

  /* lembar bawah: komentar, bagikan, edit profil */
  function openSheet(html) { closeSheet(); var bg = document.createElement('div'); bg.className = 'ohli-sheet-bg'; bg.innerHTML = '<div class="ohli-sheet" role="dialog">' + html + '</div>'; shell.appendChild(bg); bg.addEventListener('click', function (e) { if (e.target === bg) closeSheet(); }); syncCommentSheetViewport(bg); return bg; }
  function syncCommentSheetViewport(bg){var vv=window.visualViewport;if(!bg||!vv)return;var sheet=bg.querySelector('.ohli-sheet');if(!sheet)return;var update=function(){if(!sheet.isConnected)return;sheet.style.maxHeight=Math.max(260,Math.min(vv.height-6,window.innerHeight-6))+'px';};bg._ohliViewportUpdate=update;update();vv.addEventListener('resize',update);vv.addEventListener('scroll',update);}
  function closeSheet() { var b = shell.querySelector('.ohli-sheet-bg'); if (b) {var vv=window.visualViewport;if(vv&&b._ohliViewportUpdate){vv.removeEventListener('resize',b._ohliViewportUpdate);vv.removeEventListener('scroll',b._ohliViewportUpdate);}b.remove();} reelCommentsId = null; }
  var X = '<button type="button" data-action="sheet-close" aria-label="Tutup">✕</button>';
  function findComment(post,id){return post&&(post.comments||[]).find(function(c){return c.id===id;})||null;}
  function commentFormHtml(postId){return '<form class="ohli-comment-form ohli-sheet-form" data-comment-post="'+esc(postId)+'"><div class="ohli-comment-tools"><button type="button" class="ohli-btn" data-action="comment-sticker-picker" data-post-id="'+esc(postId)+'" aria-label="Pilih stiker">☺ Stiker</button><input type="file" class="ohli-sticker-file" data-comment-sticker-file="1" accept="image/*" aria-label="Impor stiker"></div><input name="comment" maxlength="500" placeholder="Tambahkan komentar…" autocomplete="off"><button type="submit" aria-label="Kirim">'+ico('send')+'</button></form>';}
  function commentsHtml(p){var me=rt.getMe()||{},all=p.comments||[],html='';function draw(parent,depth){all.filter(function(c){return (c.parentId||'')===parent;}).forEach(function(c){var canManage=!!c.isMine||!!p.isMine,sticker=c.mediaId?'<img class="ohli-comment-sticker" src="'+esc(rt.mediaUrl(c.mediaId))+'" alt="Stiker komentar" loading="lazy">':'';html+='<div class="ohli-comment-thread" style="--comment-depth:'+Math.min(depth,4)+'"><div class="ohli-comment" data-post-id="'+esc(p.id)+'" data-comment-id="'+esc(c.id)+'">'+avatar(c.name,c.phone)+'<div class="ohli-comment-copy"><strong>'+esc(c.name||'Pengguna OHLI')+(c.editedAt?' <small>(diedit)</small>':'')+'</strong>'+(c.replyToName?'<small class="ohli-reply-to">membalas '+esc(c.replyToName)+'</small>':'')+(c.text?'<p>'+esc(c.text)+'</p>':'')+sticker+'<div class="ohli-comment-actions"><button type="button" data-action="reply-comment" data-post-id="'+esc(p.id)+'" data-comment-id="'+esc(c.id)+'">Balas</button><button type="button" class="'+(c.liked?'is-liked':'')+'" data-action="like-comment" data-post-id="'+esc(p.id)+'" data-comment-id="'+esc(c.id)+'">♥ '+Number(c.likesCount||0)+'</button>'+(c.mediaId?'<button type="button" data-action="save-comment-sticker" data-post-id="'+esc(p.id)+'" data-media-id="'+esc(c.mediaId)+'">Simpan stiker</button>':'')+'</div></div>'+(canManage?'<button type="button" class="ohli-comment-menu" data-action="comment-menu" data-post-id="'+esc(p.id)+'" data-comment-id="'+esc(c.id)+'" aria-label="Kelola komentar">⋯</button>':'')+'</div></div>';draw(c.id,depth+1);});}draw('',0);if(!html){all.forEach(function(c){if(c.parentId&&!findComment(p,c.parentId)){var old=c.parentId;c.parentId='';}});draw('',0);}return html||'<p class="ohli-hint" style="text-align:center">Belum ada komentar. Jadilah yang pertama.</p>';}
  async function openCommentStickerPicker(postId){var built=['😂','❤️','🔥','👏','😍','😭','🤣','👍','🥰','🎉','😎','✨'],mine=[],favorites=[];try{var library=await rt.request('/api/stickers');mine=library.mine||[];favorites=library.favorites||[];}catch(e){toast(apiError(e));}
    function stickerButtons(list,own){return list.map(function(x){return '<div class="ohli-sticker-item"><button type="button" class="ohli-saved-sticker" data-action="use-saved-comment-sticker" data-post-id="'+esc(postId)+'" data-media-id="'+esc(x.mediaId)+'"><img src="'+esc(rt.mediaUrl(x.mediaId))+'" alt="Stiker"></button>'+(own?'<button type="button" class="ohli-sticker-delete" data-action="delete-my-comment-sticker" data-post-id="'+esc(postId)+'" data-media-id="'+esc(x.mediaId)+'" aria-label="Hapus stiker">×</button>':'<button type="button" class="ohli-sticker-save" data-action="save-comment-sticker" data-post-id="'+esc(postId)+'" data-media-id="'+esc(x.mediaId)+'">☆</button>')+'</div>';}).join('');}
    var body='<p class="ohli-hint">Stiker bawaan, Stiker Saya, dan Favorit tersimpan di server akunmu.</p><h4>Stiker bawaan</h4><div class="ohli-comment-sticker-grid">'+built.map(function(x){return '<button type="button" class="ohli-builtin-sticker" data-action="use-builtin-comment-sticker" data-post-id="'+esc(postId)+'" data-emoji="'+esc(x)+'">'+x+'</button>';}).join('')+'</div><h4>Stiker Saya ('+mine.length+')</h4><div class="ohli-comment-sticker-grid">'+(mine.length?stickerButtons(mine,true):'<p class="ohli-hint">Belum ada stiker impor.</p>')+'</div><h4>Favorit ('+favorites.length+')</h4><div class="ohli-comment-sticker-grid">'+(favorites.length?stickerButtons(favorites,false):'<p class="ohli-hint">Belum ada stiker favorit.</p>')+'</div><label class="ohli-btn ohli-sticker-import">＋ Impor gambar dari galeri<input id="ohliCommentStickerFile" type="file" accept="image/png,image/jpeg,image/webp,image/gif" data-comment-sticker-file="1"></label>';showModal('Stiker komentar',body,'',null);var file=overlayContent.querySelector('#ohliCommentStickerFile');if(file)file.dataset.postId=postId;}

  async function sendCommentSticker(postId,mediaId){var parent=replyingComment&&replyingComment.postId===postId?replyingComment.id:'';var d=await rt.request('/api/posts/comment',{method:'POST',body:{postId:postId,mediaId:mediaId,parentId:parent||''}});replyingComment=null;updatePost(d.post);closeModal();await rerenderContent();toast('Stiker dikirim sebagai komentar.');}
  async function sendBuiltinCommentSticker(postId,emoji){var canvas=document.createElement('canvas');canvas.width=320;canvas.height=320;var ctx=canvas.getContext('2d');ctx.clearRect(0,0,320,320);ctx.font='220px "Apple Color Emoji","Segoe UI Emoji",sans-serif';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(emoji,160,170);var blob=await new Promise(function(resolve,reject){canvas.toBlob(function(b){if(b)resolve(b);else reject(new Error('Stiker gagal dibuat.'));},'image/png');});var file=new File([blob],'ohli-sticker.png',{type:'image/png'});var up=await rt.uploadCommentSticker(file);var id=(up.sticker&&up.sticker.mediaId)||up.mediaId;if(!id)throw new Error('ID stiker tidak tersedia.');closeModal();await sendCommentSticker(postId,id);}
  function openReelComments(id){var p=findPost(id);if(!p)return;var open=shell.querySelector('.ohli-sheet-bg[data-cm="'+id+'"]');var formHtml=p.commentsDisabled?'<p class="ohli-comments-locked">Komentar dinonaktifkan oleh pemilik postingan.</p>':commentFormHtml(id);if(open){var l=open.querySelector('.ohli-sheet-list');l.innerHTML=commentsHtml(p);open.querySelector('.ohli-sheet-title').textContent=Number(p.commentsCount||0)+' komentar';var oldForm=open.querySelector('.ohli-sheet-form,.ohli-comments-locked');if(oldForm)oldForm.outerHTML=formHtml;else open.querySelector('.ohli-sheet').insertAdjacentHTML('beforeend',formHtml);l.scrollTop=l.scrollHeight;return;}var bg=openSheet('<div class="ohli-sheet-head"><strong class="ohli-sheet-title">'+Number(p.commentsCount||0)+' komentar</strong>'+X+'</div><div class="ohli-sheet-list">'+commentsHtml(p)+'</div>'+formHtml);bg.setAttribute('data-cm',id);reelCommentsId=id;}

  async function openShare(id) {
    var p = findPost(id); if (!p) return;
    var downloadOption = p.allowDownload!==false && isVid(p) ? '<button type="button" data-action="share-download">↓ Download video</button>' : '';
    var bg = openSheet('<div class="ohli-sheet-head"><strong>Bagikan</strong>' + X + '</div><div class="ohli-share-friends" id="shFriends"><span class="ohli-hint">Memuat teman…</span></div><div class="ohli-share-grid"><button type="button" data-action="share-wa">WhatsApp</button><button type="button" data-action="share-copy">Salin tautan</button><button type="button" data-action="share-native">Lainnya…</button>' + downloadOption + '</div>');
    bg.setAttribute('data-share', id);
    try { var c = await rt.request('/api/contacts'), list = c.contacts || c.users || [], t = bg.querySelector('#shFriends'); if (!t) return;
      t.innerHTML = list.length ? list.slice(0, 30).map(function (u) { return '<button type="button" data-action="share-friend" data-phone="' + esc(u.phone) + '">' + avatar(u.alias || u.name, u.phone) + '<span>' + esc((u.alias || u.name || u.phone).slice(0, 10)) + '</span></button>'; }).join('') : '<span class="ohli-hint">Belum ada kontak tersimpan. Gunakan WhatsApp atau salin tautan.</span>';
    } catch (e) { var t2 = bg.querySelector('#shFriends'); if (t2) t2.innerHTML = '<span class="ohli-hint">Daftar teman belum bisa dimuat.</span>'; }
  }
  async function shareDo(kind, phone, btn) {
    var bg = shell.querySelector('.ohli-sheet-bg[data-share]'); if (!bg) return; var id = bg.getAttribute('data-share'), p = findPost(id); if (!p) return;
    var link = location.origin + '/#post-' + encodeURIComponent(id), msg = (p.text ? p.text.slice(0, 80) + '\n' : '') + link, ok = false;
    try {
      if (kind === 'friend') { await rt.request('/api/messages', {method: 'POST', body: {recipient: phone, text: 'Lihat postingan ini di OHLI Social:\n' + link}}); toast('Terkirim ke teman.'); ok = true; }
      else if (kind === 'wa') { window.open('https://wa.me/?text=' + encodeURIComponent(msg), '_blank'); ok = true; }
      else if (kind === 'copy') { try { await navigator.clipboard.writeText(link); } catch (e) { var ta = document.createElement('textarea'); ta.value = link; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); ta.remove(); } toast('Tautan disalin.'); ok = true; }
      else if (kind === 'native') { if (navigator.share) { await navigator.share({title: 'OHLI Social', text: p.text || 'Postingan di OHLI Social', url: link}); ok = true; } else toast('Browser ini belum mendukung menu bagikan.'); }
      else if (kind === 'download') { if (p.allowDownload===false || !isVid(p)) { toast('Unduhan tidak diizinkan untuk video ini.'); return; } await downloadVideoWithWatermark(p); closeSheet(); return; }
    } catch (e) { if (e && e.name !== 'AbortError') toast(apiError(e)); }
    if (ok) { try { var d = await rt.request('/api/posts/share', {method: 'POST', body: {postId: id}}); updatePost(d.post); } catch (e2) { } closeSheet(); await rerenderContent(); }
  }
  async function openHashPost() {
    var m = /^#post-(.+)$/.exec(location.hash || ''); if (!m || !shell || !rt.getToken()) return; var id = decodeURIComponent(m[1]);
    try { var d = await rt.request('/api/feed?mode=public'), p = (d.posts || []).find(function (x) { return x.id === id; }); if (!p) { toast('Postingan tidak ditemukan atau tidak bisa kamu akses.'); return; }
      if (!findPost(id)) posts.push(p); setShellVisible(true); openSheet('<div class="ohli-sheet-head"><strong>Postingan</strong>' + X + '</div><div class="ohli-sheet-list">' + postCard(p) + '</div>'); history.replaceState(null, '', location.pathname);
    } catch (e) { }
  }
  window.addEventListener('load', function () { setTimeout(openHashPost, 2500); });

  /* edit profil: foto, latar (cover), hapus */
  function imgToB64(file, w, h, maxBytes) { return new Promise(function (res, rej) { var u = URL.createObjectURL(file), im = new Image(); im.onload = function () { var c = document.createElement('canvas'); c.width = w; c.height = h; var x = c.getContext('2d'), k = Math.max(w / im.width, h / im.height), dw = im.width * k, dh = im.height * k; x.drawImage(im, (w - dw) / 2, (h - dh) / 2, dw, dh); URL.revokeObjectURL(u); var q = 0.86, d; do { d = c.toDataURL('image/jpeg', q); q -= 0.08; } while (d.length * 0.75 > maxBytes && q > 0.3); res(d.split(',')[1]); }; im.onerror = function () { rej(new Error('Foto tidak dapat dibaca. Gunakan JPG atau PNG.')); }; im.src = u; }); }
  function openEditProfile() {
    var me = rt.getMe() || {}, tk = encodeURIComponent(rt.getToken()), ph = encodeURIComponent(me.phone || '');
    var bg = openSheet('<div class="ohli-sheet-head"><strong>Edit profil OHLI Social</strong>' + X + '</div><div class="ohli-sheet-list ohli-ep"><div class="ohli-ep-cover" id="epCover" style="background-image:url(/api/cover?phone=' + ph + '&token=' + tk + '&v=' + avatarVer + '),linear-gradient(135deg,#243b7a,#8b5cf6)"></div><div class="ohli-ep-av" id="epAv">' + avatar(me.name, me.phone, 'ohli-avatar-xl') + '</div><div class="ohli-ep-btns"><button type="button" class="ohli-btn" id="epAvPick">Ganti foto profil Chat</button><button type="button" class="ohli-btn" id="epAvDel">Hapus foto Chat</button><button type="button" class="ohli-btn" id="epCvPick">Ganti latar</button><button type="button" class="ohli-btn" id="epCvDel">Hapus latar</button></div><input type="file" id="epAvFile" accept="image/*" hidden><input type="file" id="epCvFile" accept="image/*" hidden><label class="ohli-field">Foto profil OHLI Social<select id="epSocialAvatarMode"><option value="same" '+((me.socialAvatarMode||'same')==='same'?'selected':'')+'>Samakan dengan foto Chat</option><option value="separate" '+(me.socialAvatarMode==='separate'?'selected':'')+'>Pisahkan foto Chat dan Social</option></select></label><button type="button" class="ohli-btn" id="epSocialAvPick">Unggah foto khusus OHLI Social</button><input type="file" id="epSocialAvFile" accept="image/*" hidden><p class="ohli-hint">Mengubah foto Chat tidak akan menghapus foto khusus Social. Nomor akun hanya untuk Chat dan pengaturan, bukan username publik.</p><label class="ohli-field">Nama<input id="epName" maxlength="40" value="' + esc(me.name || '') + '"></label><label class="ohli-field">Username OHLI Social<input id="epUsername" maxlength="24" minlength="3" pattern="[a-zA-Z0-9._]+" value="' + esc(me.username || socialHandle(me)) + '" placeholder="username"></label><label class="ohli-field">Bio<textarea id="epAbout" rows="2" maxlength="120">' + esc(me.about || '') + '</textarea></label><button type="button" class="ohli-btn ohli-btn-primary" id="epSave">Simpan</button></div>');
    function $(id) { return bg.querySelector('#' + id); }
    function refresh() { avatarVer = Date.now(); $('epCover').style.backgroundImage = 'url(/api/cover?phone=' + ph + '&token=' + tk + '&v=' + avatarVer + '),linear-gradient(135deg,#243b7a,#8b5cf6)'; $('epAv').innerHTML = avatar(me.name, me.phone, 'ohli-avatar-xl'); }
    async function send(path, body, okMsg) { try { await rt.request(path, {method: 'POST', body: body}); refresh(); toast(okMsg); } catch (e) { toast(apiError(e)); } }
    $('epAvPick').onclick = function () { $('epAvFile').click(); }; $('epCvPick').onclick = function () { $('epCvFile').click(); }; $('epSocialAvPick').onclick=function(){$('epSocialAvFile').click();}; $('epSocialAvatarMode').onchange=async function(){try{var mode=this.value;await rt.request('/api/social-avatar-mode',{method:'POST',body:{mode:mode}});me.socialAvatarMode=mode;avatarVer=Date.now();toast(mode==='same'?'Foto Social disamakan dengan foto Chat.':'Foto Social dipisahkan. Unggah foto khusus bila diperlukan.');if(page==='profile')await renderProfile(me.phone);}catch(e){toast(apiError(e));}}; $('epSocialAvFile').onchange=async function(){var f=this.files[0];if(!f)return;try{await rt.request('/api/social-profile-photo',{method:'POST',body:{data:await imgToB64(f,400,400,170*1024)}});me.socialAvatarMode='separate';$('epSocialAvatarMode').value='separate';avatarVer=Date.now();toast('Foto OHLI Social diperbarui.');if(page==='profile')await renderProfile(me.phone);}catch(e){toast(apiError(e));}};
    $('epAvFile').onchange = async function () { var f = this.files[0]; if (!f) return; try { await send('/api/profile-photo', {data: await imgToB64(f, 400, 400, 170 * 1024)}, 'Foto profil diganti.'); } catch (e) { toast(apiError(e)); } };
    $('epCvFile').onchange = async function () { var f = this.files[0]; if (!f) return; try { await send('/api/cover-photo', {data: await imgToB64(f, 1000, 340, 170 * 1024)}, 'Latar profil diganti.'); } catch (e) { toast(apiError(e)); } };
    $('epAvDel').onclick = function () { send('/api/photo/remove', {kind: 'avatar'}, 'Foto profil dihapus.'); }; $('epCvDel').onclick = function () { send('/api/photo/remove', {kind: 'cover'}, 'Latar profil dihapus.'); };
    $('epSave').onclick = async function () { var name = $('epName').value.trim(), about = $('epAbout').value.trim(), username=$('epUsername').value.trim().replace(/^@+/, '').toLowerCase(); if (!name) { toast('Nama tidak boleh kosong.'); return; } if(!/^[a-z0-9._]{3,24}$/.test(username)){toast('Username harus 3–24 karakter: huruf, angka, titik, atau garis bawah.');return;}
      try { await rt.request('/api/profile', {method: 'POST', body: {name: name, about: about, username:username}}); me.name = name; me.about = about; me.username=username; var tn = shell.querySelector('#ohliTopName'); if (tn) tn.textContent = name; closeSheet(); toast('Profil disimpan.'); if (page === 'profile') setPage('profile', {phone: me.phone}); } catch (e) { toast(apiError(e)); } };
  }

  /* geser dari tepi kiri (Beranda) = menu pintasan */
  var swX = 0, swY = 0, swOn = false;
  document.addEventListener('touchstart', function (e) { var t = e.touches[0]; swOn = !!(shell && !shell.hidden && page === 'home' && t.clientX < 26); swX = t.clientX; swY = t.clientY; }, {passive: true});
  document.addEventListener('touchend', function (e) { if (!swOn) return; swOn = false; var t = e.changedTouches[0]; if (t.clientX - swX > 70 && Math.abs(t.clientY - swY) < 60) { var m = shell.querySelector('[data-action="more-menu"]'); if (m) m.click(); } }, {passive: true});

  /* ===== Buat konten: pilih foto/video, edit (teks, stiker, potong, crop, suara), bagikan ===== */
  var cr = null, CR_STICKERS = ['😀', '😂', '😍', '🔥', '❤️', '👍', '🎉', '😎', '🙏', '✨', '🌸', '🎵'];
  function renderCreate(targetKind) {
    cr = {target:targetKind||'post',file: null, kind: '', items: [], crop: 'orig', mute: false, start: 0, end: 0, dur: 0, url: '', audioTrack:pendingMusicTrack, originalVolume:0.22, musicVolume:0.85, photoDuration:0}; pendingMusicTrack=null;
    var isStory=cr.target==='story';
    pageContent.innerHTML = '<div class="ohli-create"><div class="ohli-card ohli-create-pick"><div class="ohli-empty-icon">' + ico('plus') + '</div><h3>'+(isStory?'Buat Story':'Buat konten baru')+'</h3><p class="ohli-hint">Pilih foto atau video dari galeri, atau gunakan kamera. Tambahkan musik agar foto dirender menjadi video dengan suara menyatu. Untuk gerakan Live Photo asli, pilih MOV hasil ekspor Live Photo dari Photos; file HEIC/JPG saja tidak membawa rekaman geraknya.</p><button type="button" class="ohli-btn ohli-btn-primary" id="crPick">' + ico('image') + ' Pilih foto / video</button><button type="button" class="ohli-btn" id="crCamera">' + ico('camera') + ' Buka kamera</button><button type="button" class="ohli-btn" id="crText">'+(isStory?'Tulis Story teks':'Tulis postingan teks')+'</button><input type="file" id="crFile" accept="image/*,video/*" hidden></div></div>';
    var f = pageContent.querySelector('#crFile'); pageContent.querySelector('#crPick').onclick = function () { f.click(); };
    pageContent.querySelector('#crCamera').onclick = function () { var cam=shell.querySelector('#ohliCameraFile');cam.dataset.target=isStory?'story':'post';cam.click(); };
    f.onchange = function () { var file = f.files[0]; if (!file) return; cr.file = file; cr.kind = file.type.indexOf('video/') === 0 ? 'video' : 'image'; cr.url = URL.createObjectURL(file); crEdit(); };
    pageContent.querySelector('#crText').onclick = function () { cr.kind = 'text'; crEdit(); };
  }
  function startCreateWithFile(file,targetKind) { if(!file)return;var target=targetKind||'post';if(page!=='create')setPage('create');if(!cr||cr.target!==target){var keepAudio=cr&&cr.audioTrack;renderCreate(target);if(target!=='story'&&!cr.audioTrack&&keepAudio)cr.audioTrack=keepAudio;}cr.file=file;cr.kind=file.type&&file.type.indexOf('video/')===0?'video':'image';cr.url=URL.createObjectURL(file);crEdit(); }
  function crStageSize() {
    var st = pageContent.querySelector('#crStage'); if (!st) return; var m = st.querySelector('img,video'); if (!m) return;
    var nat = cr.kind === 'video' ? (m.videoWidth && m.videoHeight ? m.videoWidth / m.videoHeight : 9 / 16) : (m.naturalWidth && m.naturalHeight ? m.naturalWidth / m.naturalHeight : 1);
    var r = {'1:1': 1, '4:5': 0.8, '9:16': 9 / 16}[cr.crop] || nat; st.style.aspectRatio = String(r); st.style.width = 'min(100%, calc(62vh * ' + r + '))';
  }
  function crEdit() {
    var root = pageContent.querySelector('.ohli-create'), isM = cr.kind !== 'text', isV = cr.kind === 'video';
    var editorMedia = '';
    if (isM) {
      editorMedia = '<div class="ohli-ed-stage ' + (isV ? 'ohli-vwrap' : '') + '" id="crStage">' +
        (isV ? '<video src="' + cr.url + '" autoplay muted loop playsinline></video>' : '<img src="' + cr.url + '" alt="">') +
        '<div class="ohli-ovl" id="crOvl"></div></div>' +
        '<div class="ohli-ed-tools-scroll"><div class="ohli-ed-tools"><button type="button" data-t="text">Aa Teks</button>' +
        '<button type="button" data-t="sticker">😊 Stiker</button>' +
        (isV ? '<button type="button" data-t="trim">✂ Potong</button>' : '') +
        '<button type="button" data-t="crop" id="crCropBtn">▢ Crop: Asli</button>' +
        (isV ? '<button type="button" data-t="mute" id="crMuteBtn">🔊 Suara: nyala</button>' : '') +
        (isM ? '<button type="button" data-action="open-audio-picker">♫ Tambah Musik</button>' : '') +
        '<button type="button" data-t="del" id="crDel" disabled>🗑 Hapus</button></div></div><div id="crPanel"></div>' +
        (isM ? '<div id="crAudioStatus" class="ohli-hint">' +
          (cr.audioTrack ? '♫ ' + esc(cr.audioTrack.title || 'Audio OHLI') + ' · ' + esc(cr.audioTrack.artist || cr.audioTrack.ownerName || '') +
            ' <button type="button" class="ohli-btn" data-action="detach-composer-audio">Lepas audio</button>' :
            'Tambahkan musik untuk mengubah foto menjadi video dengan audio menyatu.') + '</div>' +
          (cr.audioTrack ? '<div class="ohli-audio-mix-controls"><label class="ohli-field">Suara asli <input id="crOriginalVolume" type="range" min="0" max="100" value="'+Math.round(cr.originalVolume*100)+'" '+(isV?'':'disabled')+'><output id="crOriginalVolumeValue">'+(isV?Math.round(cr.originalVolume*100):0)+'%</output></label><label class="ohli-field">Musik tambahan <input id="crMusicVolume" type="range" min="0" max="100" value="'+Math.round(cr.musicVolume*100)+'"><output id="crMusicVolumeValue">'+Math.round(cr.musicVolume*100)+'%</output></label>'+(isV?'':'<label class="ohli-field">Durasi video foto <input id="crPhotoDuration" type="range" min="3" max="180" step="1" value="'+Math.round(cr.photoDuration||Math.max(3,Math.min(180,Number(cr.audioTrack.durationSeconds||cr.audioTrack.duration)||15)))+'"><output id="crPhotoDurationValue">'+Math.round(cr.photoDuration||Math.max(3,Math.min(180,Number(cr.audioTrack.durationSeconds||cr.audioTrack.duration)||15)))+' detik</output></label>')+'</div>' : '') : '');
    }
    root.innerHTML = '<div class="ohli-editor">' + editorMedia +
      '<textarea id="crCap" rows="3" maxlength="2000" placeholder="' + (cr.target === 'story' ? 'Tulis teks Story…' : 'Tulis keterangan…') + '"></textarea>' +
      '<div class="ohli-ed-row">' + (cr.target === 'story' ? '' : '<select id="crAud"><option value="public">Publik</option><option value="followers">Pengikut</option><option value="private">Hanya saya</option></select>') +
      (isV && cr.target !== 'story' ? '<label class="ohli-download-setting"><input id="crAllowDownload" type="checkbox" checked> Izinkan orang mengunduh video</label>' : '') +
      '<button type="button" class="ohli-btn" id="crDraft">Simpan draf</button><button type="button" class="ohli-btn" id="crBack">Ganti</button><button type="button" class="ohli-btn ohli-btn-primary" id="crGo">' +
      (cr.target === 'story' ? 'Bagikan Story' : 'Bagikan') + '</button></div></div>';
    var q = function (id) { return root.querySelector(id); }, med = isM ? q('#crStage').querySelector('img,video') : null, panel = q('#crPanel');
    if (med) { med.onload = crStageSize; med.onloadedmetadata = function () { cr.dur = med.duration || 0; cr.end = cr.dur; crStageSize(); }; crStageSize(); }
    function addItem(t, sz) { cr.items.push({t: t, x: 50, y: 50, s: sz, c: '#ffffff'}); crRenderItems(); }
    function crRenderItems() { q('#crOvl').innerHTML = cr.items.map(function (it, i) { return '<span class="ohli-ovl-item" data-i="' + i + '" style="left:' + it.x + '%;top:' + it.y + '%;font-size:' + it.s + 'cqw;color:' + esc(it.c) + '">' + esc(it.t) + '</span>'; }).join(''); }
    if (isM) {
      q('#crOvl').addEventListener('pointerdown', function (e) {
        var el = e.target.closest('.ohli-ovl-item'); q('#crOvl').querySelectorAll('.ohli-ovl-item').forEach(function (n) { n.classList.toggle('sel', n === el); }); q('#crDel').disabled = !el; if (!el) return;
        var i = +el.dataset.i, st = q('#crStage').getBoundingClientRect(); try { el.setPointerCapture(e.pointerId); } catch (x) { }
        function mv(ev) { cr.items[i].x = Math.max(4, Math.min(96, (ev.clientX - st.left) / st.width * 100)); cr.items[i].y = Math.max(4, Math.min(96, (ev.clientY - st.top) / st.height * 100)); el.style.left = cr.items[i].x + '%'; el.style.top = cr.items[i].y + '%'; }
        function up() { el.removeEventListener('pointermove', mv); el.removeEventListener('pointerup', up); } el.addEventListener('pointermove', mv); el.addEventListener('pointerup', up);
      });
      root.querySelector('.ohli-ed-tools').addEventListener('click', function (e) {
        var b = e.target.closest('[data-t]'); if (!b) return; var t = b.dataset.t; panel.innerHTML = '';
        if (t === 'text') { var v = prompt('Tulis teks:'); if (v && v.trim()) addItem(v.trim().slice(0, 60), 7); }
        else if (t === 'sticker') { panel.innerHTML = '<div class="ohli-ed-stickers">' + CR_STICKERS.map(function (s2) { return '<button type="button" data-s="' + s2 + '">' + s2 + '</button>'; }).join('') + '</div>'; panel.onclick = function (ev) { var sb = ev.target.closest('[data-s]'); if (sb) addItem(sb.dataset.s, 16); }; }
        else if (t === 'trim' && cr.dur) { panel.innerHTML = '<label class="ohli-hint">Mulai <input type="range" id="crS" min="0" max="' + cr.dur + '" step="0.1" value="' + cr.start + '"></label><label class="ohli-hint">Selesai <input type="range" id="crE" min="0" max="' + cr.dur + '" step="0.1" value="' + cr.end + '"></label><p class="ohli-hint">Video diputar hanya pada bagian ini.</p>';
          var rs = panel.querySelector('#crS'), re2 = panel.querySelector('#crE'); function upd() { cr.start = Math.min(+rs.value, +re2.value - 0.5); cr.end = Math.max(+re2.value, cr.start + 0.5); med.dataset.start = cr.start; med.dataset.end = cr.end; med.currentTime = cr.start; } rs.oninput = upd; re2.oninput = upd; }
        else if (t === 'crop') { var o = ['orig', '1:1', '4:5', '9:16'], nm = {orig: 'Asli', '1:1': '1:1', '4:5': '4:5', '9:16': '9:16'}; cr.crop = o[(o.indexOf(cr.crop) + 1) % 4]; b.textContent = '▢ Crop: ' + nm[cr.crop]; crStageSize(); }
        else if (t === 'mute') { cr.mute = !cr.mute; b.textContent = cr.mute ? '🔇 Suara: mati' : '🔊 Suara: nyala'; if (med) med.muted = cr.mute; }
        else if (t === 'del') { var sel = q('#crOvl').querySelector('.ohli-ovl-item.sel'); if (sel) { cr.items.splice(+sel.dataset.i, 1); crRenderItems(); q('#crDel').disabled = true; } }
      });
    }
    q('#crBack').onclick = function(){try{if(cr.renderedUrl)URL.revokeObjectURL(cr.renderedUrl);}catch(e){}renderCreate(cr.target);};
    q('#crDraft').onclick=function(){saveLocalDraft(cr.target==='story'?'story':'post',q('#crCap').value);};
    q('#crGo').onclick = async function () {
      var btn = this, cap = q('#crCap').value.trim(), aud = q('#crAud')?q('#crAud').value:'public', allowDownload = q('#crAllowDownload') ? q('#crAllowDownload').checked : true; if (cr.kind === 'text' && !cap) { toast('Tulis sesuatu dulu.'); return; }
      btn.disabled = true; btn.textContent = 'Mengunggah…';
      try {
        var meta = {}; if (cr.items.length) meta.items = cr.items.map(function (it) { return {t: it.t, x: +it.x.toFixed(1), y: +it.y.toFixed(1), s: it.s, c: it.c}; });
        if (cr.crop !== 'orig') meta.crop = cr.crop; if (cr.mute && !cr.audioTrack) meta.mute = 1; if (!cr.audioTrack && cr.start > 0.05) meta.start = +cr.start.toFixed(2); if (!cr.audioTrack && cr.dur && cr.end < cr.dur - 0.05) meta.end = +cr.end.toFixed(2);
        var m = Object.keys(meta).length ? JSON.stringify(meta) : ''; if (m.length > 1400) throw new Error('Terlalu banyak teks/stiker. Kurangi dulu.');
        if(cr.target==='story'){
          if(cr.kind==='text') await rt.request('/api/status',{method:'POST',body:{text:cap}});
          else { var sf=cr.file; if(cr.audioTrack&&cr.kind==='image'){if(!cr.renderedFile){toast('Membuat video dari foto dan musik…');sf=await renderPhotoWithMusic(cr.file,cr.audioTrack,{duration:cr.photoDuration||Number(cr.audioTrack.durationSeconds||cr.audioTrack.duration)||15,crop:cr.crop,items:cr.items,musicVolume:cr.musicVolume});showPhotoVideoPreview(sf,btn);return;}sf=cr.renderedFile;}else if(cr.kind==='image'&&(cr.items.length||cr.crop!=='orig'))sf=await crBakeImage();else if(cr.audioTrack&&cr.kind==='video'){toast('Menggabungkan audio dan video…');sf=await mixVideoWithMusic(cr.file,cr.audioTrack,{start:cr.start,end:cr.end,mute:cr.mute,originalVolume:cr.mute?0:cr.originalVolume,musicVolume:cr.musicVolume});}await rt.uploadStatusMedia(sf,cap,cr.kind==='video'?m:''); }
          toast('Story berhasil dibagikan.'); setPage('story');
        } else {
          if (cr.kind === 'text') await rt.request('/api/posts', {method: 'POST', body: {text: cap, audience: aud}});
          else if (cr.kind === 'image') { var imagePublish=cr.file;if(cr.audioTrack){if(!cr.renderedFile){toast('Membuat video dari foto dan musik…');imagePublish=await renderPhotoWithMusic(cr.file,cr.audioTrack,{duration:cr.photoDuration||Number(cr.audioTrack.durationSeconds||cr.audioTrack.duration)||15,crop:cr.crop,items:cr.items,musicVolume:cr.musicVolume});showPhotoVideoPreview(imagePublish,btn);return;}imagePublish=cr.renderedFile;}else if(cr.items.length||cr.crop!=='orig')imagePublish=await crBakeImage();await rt.uploadPostMedia(imagePublish,cap,aud,m,cr.audioTrack?cr.audioTrack.id:'',true); }
          else { var publishVideo=cr.file; if(cr.audioTrack){toast('Menggabungkan audio dan video…');publishVideo=await mixVideoWithMusic(cr.file,cr.audioTrack,{start:cr.start,end:cr.end,mute:cr.mute,originalVolume:cr.mute?0:cr.originalVolume,musicVolume:cr.musicVolume});} await rt.uploadPostMedia(publishVideo,cap,aud,m,cr.audioTrack?cr.audioTrack.id:'',allowDownload); }
          toast('Berhasil dibagikan.'); setPage('home');
        }
      } catch (e) { toast(apiError(e)); btn.disabled = false; btn.textContent = cr.target==='story'?'Bagikan Story':'Bagikan'; }
    };
  }
  function crBakeImage() {
    return new Promise(function (res, rej) {
      var img = new Image(); img.onload = function () {
        var r = {'1:1': 1, '4:5': 0.8, '9:16': 9 / 16}[cr.crop] || (img.width / img.height), W = Math.min(1440, cr.crop === 'orig' ? img.width : 1080), H = Math.round(W / r), c = document.createElement('canvas'); c.width = W; c.height = H;
        var x = c.getContext('2d'), k = Math.max(W / img.width, H / img.height), dw = img.width * k, dh = img.height * k; x.drawImage(img, (W - dw) / 2, (H - dh) / 2, dw, dh); x.textAlign = 'center'; x.textBaseline = 'middle';
        cr.items.forEach(function (it) { var fs = it.s / 100 * W; x.font = '800 ' + fs + 'px system-ui,sans-serif'; x.fillStyle = it.c || '#fff'; x.shadowColor = 'rgba(0,0,0,.6)'; x.shadowBlur = fs / 6; x.fillText(it.t, W * it.x / 100, H * it.y / 100); });
        c.toBlob(function (b) { b ? res(new File([b], 'foto.jpg', {type: 'image/jpeg'})) : rej(new Error('Gagal memproses foto.')); }, 'image/jpeg', 0.9);
      }; img.onerror = function () { rej(new Error('Foto tidak bisa diedit di browser ini. Hapus teks/crop lalu coba lagi.')); }; img.src = cr.url;
    });
  }
})();

/* v1.8: tandai layar percakapan (chat-open) supaya bar tab HP disembunyikan dan kolom pesan selalu tampil */
(function () {
  var app = document.getElementById('app'); if (!app) return; var q = 0;
  function sync() {
    q = 0; var cm = app.querySelector('.main .compose');
    var open = !!(cm && !cm.classList.contains('hidden') && app.classList.contains('page-open'));
    if (app.classList.contains('chat-open') !== open) app.classList.toggle('chat-open', open);
  }
  new MutationObserver(function () { if (!q) q = requestAnimationFrame(sync); }).observe(app, {subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'hidden']});
  sync();
})();

/* v1.9: balas ala WhatsApp (bar kutipan), kutipan di balon, dan pengecil foto latar */
(function () {
  window.shrinkImage = function (file, maxW, q) { return new Promise(function (res, rej) { var u = URL.createObjectURL(file), im = new Image(); im.onload = function () { var k = Math.min(1, maxW / im.width), c = document.createElement('canvas'); c.width = Math.round(im.width * k); c.height = Math.round(im.height * k); c.getContext('2d').drawImage(im, 0, 0, c.width, c.height); URL.revokeObjectURL(u); res(c.toDataURL('image/jpeg', q || 0.7)); }; im.onerror = function () { rej(new Error('x')); }; im.src = u; }); };
  window.setReplyTarget = function (name, text) {
    window.__reply = {name: String(name || ''), text: String(text || '')}; var c = document.querySelector('#app .main .compose'); if (!c) return;
    var bar = document.getElementById('waReplyBar'); if (!bar) { bar = document.createElement('div'); bar.id = 'waReplyBar'; bar.className = 'wa-replybar'; c.parentNode.insertBefore(bar, c); }
    bar.innerHTML = '<div class="q"><b></b><span></span></div><button type="button" aria-label="Batalkan balasan">✕</button>'; bar.querySelector('b').textContent = window.__reply.name; bar.querySelector('span').textContent = window.__reply.text.slice(0, 140); bar.querySelector('button').onclick = window.clearReply;
    var i = document.getElementById('messageInput'); if (i) i.focus();
  };
  window.clearReply = function () { window.__reply = null; var b = document.getElementById('waReplyBar'); if (b) b.remove(); };
  var mb = document.getElementById('mainBody');
  if (mb) new MutationObserver(function () {
    mb.querySelectorAll('.bubble-text:not([data-q])').forEach(function (el) {
      el.setAttribute('data-q', '1'); var m = /^↩\uFE0F?\s([^:\n]{1,40}):\s([^\n]*)\n([\s\S]*)$/.exec(el.textContent || ''); if (!m) return;
      el.textContent = ''; var q = document.createElement('div'); q.className = 'wa-quote'; var b = document.createElement('b'); b.textContent = m[1]; var s = document.createElement('span'); s.textContent = m[2]; q.appendChild(b); q.appendChild(s);
      var r = document.createElement('div'); r.textContent = m[3]; el.appendChild(q); el.appendChild(r);
    });
  }).observe(mb, {childList: true, subtree: true});
})();
