import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';

const root = process.cwd();
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
const checks = [];
function check(name, condition, detail = '') {
  checks.push({ name, pass: Boolean(condition), detail });
}

const manifest = read('app/src/main/AndroidManifest.xml');
const gradle = read('app/build.gradle');
const mainJava = read('app/src/main/java/com/rk/server/MainActivity.java');
const replyJava = read('app/src/main/java/com/rk/server/NotificationReplyReceiver.java');
const html = read('app/src/main/assets/index.html');
const socialJs = read('app/src/main/assets/ohli-social.js');
const workflow = read('.github/workflows/build-apk.yml');

check('Android client uses Netlify origin', mainJava.includes('https://ohlisocial.netlify.app/'));
check('MainActivity loads the site in WebView', /webView\.loadUrl\(SITE_URL\)/.test(mainJava));
check('Local HTML is served from APK assets', /assetResponse\("index\.html"/.test(mainJava) && fs.existsSync(path.join(root, 'app/src/main/assets/index.html')));
check('Local CSS and JavaScript are packaged', mainJava.includes('assetResponse("ohli-social.css"') && mainJava.includes('assetResponse("ohli-social.js"') && fs.existsSync(path.join(root, 'app/src/main/assets/ohli-social.css')) && fs.existsSync(path.join(root, 'app/src/main/assets/ohli-social.js')));
check('Legacy server service is not declared in client manifest', !manifest.includes('ServerService') && !manifest.includes('CloudflareTunnelService'));
check('WebView has DOM storage and JavaScript', mainJava.includes('settings.setDomStorageEnabled(true)') && mainJava.includes('settings.setJavaScriptEnabled(true)'));
check('Offline session cache and restoration exist', html.includes('function saveOfflineSnapshot()') && html.includes('function restoreOfflineSession(') && html.includes('startPolling();window.dispatchEvent(new Event(\'ohli-authenticated\'))'));
check('Offline startup restores cache without waiting for API timeout', /if\(!isNativeOnline\(\)\)\{if\(hasLocalCache\)restoreOfflineSession/.test(html));
check('Offline text outbox persists per account', html.includes('function getOfflineOutbox()') && html.includes('function queueOfflineTextMessage(') && html.includes('function flushOfflineOutbox()'));
check('Offline queue has reconnect retry and client ID', html.includes('window.ohliClientOnlineSync=function()') && html.includes('clientId:item.clientId'));
check('Native notification channels have three tones', ['ohli_message.wav','ohli_call.wav','ohli_activity.wav'].every(f => fs.statSync(path.join(root, 'app/src/main/res/raw', f)).size > 1000));
check('Native inline reply receiver is declared', manifest.includes('NotificationReplyReceiver') && replyJava.includes('RemoteInput.getResultsFromIntent'));
check('Native reply keeps same idempotency key when retried', replyJava.includes('queueReply(context, peer, reply, clientId)') && replyJava.includes('sendReply(token, peer, reply, clientId)'));
check('Native notification reply uses Netlify API', replyJava.includes('MainActivity.SITE_URL + "api/messages"'));
check('Camera, microphone and location permissions declared', ['CAMERA','RECORD_AUDIO','ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION'].every(p => manifest.includes('android.permission.' + p)));
check('Notification and media permissions declared', manifest.includes('POST_NOTIFICATIONS') && manifest.includes('READ_MEDIA_IMAGES') && manifest.includes('READ_MEDIA_VIDEO'));
check('Notifications can open Android channel settings', mainJava.includes('openNotificationSettings') && socialJs.includes('open-native-notification-settings'));
check('Native message notifications do not depend on Web Notification permission', html.includes('document.hidden&&((window.OHLI_ANDROID&&window.OHLI_ANDROID.showMessageNotification)'));
check('App icon and notification icon are packaged', fs.existsSync(path.join(root, 'app/src/main/res/drawable/rk_icon.png')) && fs.existsSync(path.join(root, 'app/src/main/res/drawable/ohli_notification_icon.png')));
check('Minimum Android version matches native WebView features', /minSdk\s+21/.test(gradle));
check('Version is OHLI client 3.7.0', /versionName\s+"3\.7\.0"/.test(gradle));
check('GitHub Actions builds debug APK and uploads artifact', workflow.includes(':app:assembleDebug') && workflow.includes('actions/upload-artifact@v4'));
check('Backend source has retry idempotency support', /clientId/.test(read('app/src/main/java/com/rk/server/ServerService.java')) && /duplicateMessage/.test(read('app/src/main/java/com/rk/server/ServerService.java')));
check('Netlify API proxy is explicitly not fabricated', !fs.existsSync(path.join(root, 'netlify.toml')) && !fs.existsSync(path.join(root, '_redirects')));

const inline = [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)].map(m => m[1]);
let inlineJsValid = true;
let inlineJsError = '';
try { inline.forEach((source, i) => new vm.Script(source, { filename: `inline-script-${i+1}.js` })); }
catch (e) { inlineJsValid = false; inlineJsError = e.message; }
check('All inline JavaScript blocks parse', inlineJsValid, inlineJsError);
let socialJsValid = true;
let socialJsError = '';
try { new vm.Script(socialJs, { filename: 'ohli-social.js' }); }
catch (e) { socialJsValid = false; socialJsError = e.message; }
check('Social UI JavaScript parses', socialJsValid, socialJsError);

const failed = checks.filter(x => !x.pass);
for (const item of checks) console.log(`${item.pass ? 'PASS' : 'FAIL'}  ${item.name}${item.detail ? ` — ${item.detail}` : ''}`);
console.log(`\n${checks.length - failed.length}/${checks.length} pemeriksaan source lulus.`);
if (failed.length) process.exit(1);
