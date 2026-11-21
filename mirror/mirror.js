// Monzatti Mirror: espejo inteligente en tiempo real.
// Webcam → MediaPipe Pose (puntos 3D del cuerpo) → prenda 3D (Three.js) que sigue y gira con la persona.
// La prenda se elige desde la app (Supabase Realtime) o con el teclado.
import * as THREE from 'three';
import { FilesetResolver, PoseLandmarker } from '@mediapipe/tasks-vision';
import { CATALOG, Garment3D, Mannequin } from './garments.js';

const MIRROR_CODE = 'MONZATTI-MIRROR-01';
const WASM = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14/wasm';
const MODEL = 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task';

// Índices de MediaPipe Pose
const IDX = { ls: 11, rs: 12, le: 13, re: 14, lw: 15, rw: 16, lh: 23, rh: 24, lk: 25, rk: 26 };
const MIN_VISIBILITY = 0.5;
const SMOOTH = 0.5;

const $ = id => document.getElementById(id);
const video = $('video');
const canvas = $('scene');

// ───────────── Escena 3D ─────────────
const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
const scene = new THREE.Scene();
// Cámara ortográfica en píxeles de pantalla: x a la derecha, y hacia arriba (y = -pixelY), z hacia el espectador.
const camera = new THREE.OrthographicCamera(0, 1, 0, -1, -10000, 10000);
camera.position.z = 5000;

scene.add(new THREE.HemisphereLight('#ffffff', '#3a3530', 1.4));
const key = new THREE.DirectionalLight('#ffffff', 2.2);
key.position.set(-0.4, 0.8, 1);
scene.add(key);
const rim = new THREE.DirectionalLight('#ffe9c4', 0.9);
rim.position.set(0.8, 0.3, -0.6);
scene.add(rim);

const garment = new Garment3D(scene);
const mannequin = new Mannequin(scene);

let W = 0;
let H = 0;
function resize() {
  W = window.innerWidth;
  H = window.innerHeight;
  renderer.setSize(W, H, false);
  camera.left = 0; camera.right = W; camera.top = 0; camera.bottom = -H;
  camera.updateProjectionMatrix();
}
window.addEventListener('resize', resize);
resize();

// ───────────── Prenda seleccionada ─────────────
let current = 0;
function showGarment(spec, animate = true) {
  garment.setGarment(spec);
  $('garmentName').textContent = spec.name;
  $('garmentBrand').textContent = (spec.brand || '').toUpperCase();
  $('swatch').style.background = spec.color;
  if (animate) {
    const g = document.querySelector('.garment');
    g.classList.remove('swap'); void g.offsetWidth; g.classList.add('swap');
  }
}
function step(dir) {
  current = (current + dir + CATALOG.length) % CATALOG.length;
  showGarment(CATALOG[current]);
}
showGarment(CATALOG[0], false);

// ───────────── Estado ─────────────
let mode = 'loading'; // live | demo | loading
let landmarker = null;
let fps = 0;
let frames = 0;
let fpsStart = performance.now();
let lastSeen = 0;

function setStatus(kind, text) {
  const s = $('status');
  s.className = 'status ' + kind;
  s.querySelector('span').textContent = text;
}
function hideLoading() { $('loading').classList.add('hidden'); }

// ───────────── Cámara + pose ─────────────
async function startLive() {
  $('loadingText').textContent = 'Cargando detección de cuerpo…';
  const vision = await FilesetResolver.forVisionTasks(WASM);
  const options = delegate => ({
    baseOptions: { modelAssetPath: MODEL, delegate },
    runningMode: 'VIDEO',
    numPoses: 1,
    minPoseDetectionConfidence: 0.5,
    minTrackingConfidence: 0.5
  });
  try {
    landmarker = await PoseLandmarker.createFromOptions(vision, options('GPU'));
  } catch (e) {
    console.warn('GPU no disponible, usando CPU', e);
    landmarker = await PoseLandmarker.createFromOptions(vision, options('CPU'));
  }
  $('loadingText').textContent = 'Encendiendo cámara…';
  const stream = await navigator.mediaDevices.getUserMedia({
    video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } },
    audio: false
  });
  video.srcObject = stream;
  await video.play();
  mode = 'live';
  document.body.classList.remove('demo');
  hideLoading();
}

function startDemo(reason) {
  if (reason) console.info('Modo maniquí:', reason);
  mode = 'demo';
  document.body.classList.add('demo');
  $('hint').classList.add('hidden');
  hideLoading();
}

/** Convierte un punto normalizado del video a píxeles de pantalla (object-fit: cover + espejo). */
function toScreen(lm) {
  const vw = video.videoWidth || 1280;
  const vh = video.videoHeight || 720;
  const scale = Math.max(W / vw, H / vh);
  const offX = (W - vw * scale) / 2;
  const offY = (H - vh * scale) / 2;
  const x = W - (offX + lm.x * vw * scale); // espejo
  const y = offY + lm.y * vh * scale;
  return { x, y };
}

const smoothed = {};
function smoothPoint(key, v) {
  if (!v) { delete smoothed[key]; return null; }
  const prev = smoothed[key];
  smoothed[key] = prev ? prev.lerp(v, SMOOTH) : v.clone();
  return smoothed[key];
}

function bodyFromPose(result) {
  const lms = result.landmarks?.[0];
  const world = result.worldLandmarks?.[0];
  if (!lms || !world) return null;
  const screen = {};
  for (const [k, i] of Object.entries(IDX)) {
    if ((lms[i].visibility ?? 1) >= MIN_VISIBILITY) screen[k] = toScreen(lms[i]);
  }
  if (!screen.ls || !screen.rs) return null;

  // Píxeles por metro, para llevar la profundidad (z) del mundo a la misma escala.
  const pxShoulder = Math.hypot(screen.ls.x - screen.rs.x, screen.ls.y - screen.rs.y);
  const wShoulder = Math.hypot(world[11].x - world[12].x, world[11].y - world[12].y);
  const k = pxShoulder / Math.max(wShoulder, 0.05);

  const p = {};
  for (const [key, i] of Object.entries(IDX)) {
    const s = screen[key];
    p[key] = s ? new THREE.Vector3(s.x, -s.y, -world[i].z * k) : null;
  }
  // Si la cadera no se ve, se estima debajo de los hombros.
  if (!p.lh || !p.rh) {
    const width = p.ls.distanceTo(p.rs);
    p.lh = p.ls.clone().add(new THREE.Vector3(0, -width * 1.35, 0));
    p.rh = p.rs.clone().add(new THREE.Vector3(0, -width * 1.35, 0));
  }
  for (const key of Object.keys(IDX)) p[key] = smoothPoint(key, p[key]);
  return p;
}

// ───────────── Maniquí que gira 360° (sin cámara) ─────────────
function demoBody(t) {
  const u = Math.min(W, H) * 0.16;
  const cx = W / 2;
  const cy = H * 0.36;
  const angle = t * 0.0006;
  const swing = Math.sin(t * 0.0018) * 0.12;
  const local = {
    ls: [-0.5, 0, 0], rs: [0.5, 0, 0],
    le: [-0.66, -0.78, 0.05 + swing], re: [0.66, -0.78, 0.05 - swing],
    lw: [-0.7, -1.5, 0.18 + swing * 2], rw: [0.7, -1.5, 0.18 - swing * 2],
    lh: [-0.36, -1.38, 0], rh: [0.36, -1.38, 0],
    lk: [-0.3, -2.35, 0.04], rk: [0.3, -2.35, 0.04],
    head: [0, 0.62, 0.02], lf: [-0.3, -3.2, 0.06], rf: [0.3, -3.2, 0.06]
  };
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  const out = {};
  for (const [key, [x, y, z]] of Object.entries(local)) {
    out[key] = new THREE.Vector3(cx + (x * c + z * s) * u, -cy + y * u, (-x * s + z * c) * u);
  }
  return out;
}

// ───────────── Bucle ─────────────
function loop(now) {
  requestAnimationFrame(loop);
  let body = null;

  if (mode === 'live' && landmarker && video.readyState >= 2) {
    const result = landmarker.detectForVideo(video, now);
    body = bodyFromPose(result);
    if (body) lastSeen = now;
    mannequin.setVisible(false);
  } else if (mode === 'demo') {
    body = demoBody(now);
    mannequin.setVisible(true);
    mannequin.update(body, body);
  }

  garment.update(body);
  renderer.render(scene, camera);

  frames++;
  if (now - fpsStart >= 1000) {
    fps = frames;
    frames = 0;
    fpsStart = now;
  }
  if (mode === 'live') {
    const seen = now - lastSeen < 400;
    setStatus(seen ? 'live' : '', seen ? `EN VIVO · ${fps} FPS` : 'BUSCANDO CUERPO');
    $('hint').classList.toggle('hidden', seen);
  } else if (mode === 'demo') {
    setStatus('demo', `MANIQUÍ 360° · ${fps} FPS`);
  }
}
requestAnimationFrame(loop);

// ───────────── Teclado ─────────────
window.addEventListener('keydown', e => {
  if (e.key === 'ArrowRight') step(1);
  if (e.key === 'ArrowLeft') step(-1);
  if (e.key === 'd' || e.key === 'D') {
    if (mode === 'demo' && landmarker) { mode = 'live'; document.body.classList.remove('demo'); }
    else startDemo();
  }
  if (e.key === 'f' || e.key === 'F') {
    if (document.fullscreenElement) document.exitFullscreen(); else document.documentElement.requestFullscreen();
  }
});

// ───────────── Vínculo con la app (Supabase Realtime) ─────────────
async function connectApp() {
  let config;
  try {
    config = (await import('./config.js')).default;
  } catch {
    return; // sin config.js: el espejo funciona solo con el teclado
  }
  if (!config?.supabaseUrl || !config?.supabaseKey) return;
  try {
    const { createClient } = await import('@supabase/supabase-js');
    const client = createClient(config.supabaseUrl, config.supabaseKey);
    client
      .channel(`mirror:${config.mirrorCode || MIRROR_CODE}`)
      .on('broadcast', { event: 'projection' }, ({ payload }) => {
        if (!payload) return;
        if (payload.projecting === false) { garment.setVisible(false); return; }
        showGarment({
          name: payload.name,
          brand: payload.brand,
          kind: payload.kind,
          color: payload.color
        });
      })
      .subscribe(status => {
        const link = $('link');
        const ok = status === 'SUBSCRIBED';
        link.textContent = ok ? '● Vinculado a la app' : 'Sin vincular a la app';
        link.classList.toggle('on', ok);
      });
  } catch (e) {
    console.warn('No se pudo conectar con Supabase', e);
  }
}

// ───────────── Arranque ─────────────
const params = new URLSearchParams(location.search);
connectApp();
if (params.has('demo')) {
  startDemo('parámetro ?demo');
} else if (!navigator.mediaDevices?.getUserMedia) {
  startDemo('el navegador no permite cámara');
} else {
  startLive().catch(err => startDemo(err?.message || err));
}
