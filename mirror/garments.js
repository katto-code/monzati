// Prendas 3D procedurales para el espejo. Se reemplazan por modelos .glb del equipo cuando existan
// (ver docs/guia-modelado-3d.md): la colocación sobre el cuerpo es la misma.
import * as THREE from 'three';

/** Catálogo de demostración (mismo que la app). kind: BLOUSE | JACKET | DRESS */
export const CATALOG = [
  { id: 'p1', name: 'Blusa de seda', brand: 'Francesa', kind: 'BLOUSE', color: '#F4C2C2' },
  { id: 'p4', name: 'Blazer camel', brand: 'Taller Norte', kind: 'JACKET', color: '#C19A6B' },
  { id: 'p2', name: 'Vestido midi', brand: 'Nómada', kind: 'DRESS', color: '#5A0F1A' },
  { id: 'p5', name: 'Chamarra bomber', brand: 'Cobalto', kind: 'JACKET', color: '#556B2F' },
  { id: 'p6', name: 'Vestido de encaje', brand: 'Luna Roja', kind: 'DRESS', color: '#1C1C1C' },
  { id: 'p7', name: 'Blusa con moño', brand: 'Francesa', kind: 'BLOUSE', color: '#F0E6D2' },
  { id: 'p8', name: 'Playera oversize', brand: 'Urbe', kind: 'BLOUSE', color: '#FF5500' },
  { id: 'g5', name: 'Chamarra de mezclilla', brand: 'Mi closet', kind: 'JACKET', color: '#5B7C99' }
];

const UP = new THREE.Vector3(0, 1, 0);
const tmpA = new THREE.Vector3();
const tmpB = new THREE.Vector3();
const tmpM = new THREE.Matrix4();

/** Coloca un cilindro unitario (alto 1, radio 1) entre a y b con radio r. */
function placeSegment(mesh, a, b, r) {
  tmpA.subVectors(b, a);
  const len = tmpA.length();
  if (len < 1e-3) { mesh.visible = false; return; }
  mesh.visible = true;
  mesh.position.addVectors(a, b).multiplyScalar(0.5);
  mesh.scale.set(r, len, r);
  mesh.quaternion.setFromUnitVectors(UP, tmpA.normalize());
}

function placeJoint(mesh, p, r) {
  mesh.visible = true;
  mesh.position.copy(p);
  mesh.scale.setScalar(r);
}

function fabric(color) {
  return new THREE.MeshPhysicalMaterial({
    color,
    roughness: 0.78,
    metalness: 0,
    sheen: 1,
    sheenRoughness: 0.55,
    sheenColor: new THREE.Color(color).lerp(new THREE.Color('#ffffff'), 0.35),
    side: THREE.DoubleSide
  });
}

export class Garment3D {
  constructor(scene) {
    this.material = fabric('#F4C2C2');
    this.innerMaterial = new THREE.MeshStandardMaterial({ color: '#f2f0ec', roughness: 0.9 });

    // Torso: en coordenadas locales del cuerpo (x hombros, y arriba, z al frente), unidad = ancho de hombros.
    this.torso = new THREE.Group();
    const body = new THREE.CylinderGeometry(0.56, 0.5, 1, 40, 1, true);
    this.torsoMesh = new THREE.Mesh(body, this.material);
    const capGeo = new THREE.SphereGeometry(1, 40, 16, 0, Math.PI * 2, 0, Math.PI / 2);
    this.cap = new THREE.Mesh(capGeo, this.material);
    const skirtGeo = new THREE.CylinderGeometry(0.5, 1.0, 1, 48, 1, true);
    this.skirt = new THREE.Mesh(skirtGeo, this.material);
    this.inner = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), this.innerMaterial);
    this.torso.add(this.torsoMesh, this.cap, this.skirt, this.inner);
    scene.add(this.torso);

    // Mangas: en coordenadas de la escena (siguen hombro → codo → muñeca).
    const seg = new THREE.CylinderGeometry(1, 0.88, 1, 24, 1, true);
    const joint = new THREE.SphereGeometry(1, 20, 14);
    this.sleeves = ['L', 'R'].map(() => ({
      upper: new THREE.Mesh(seg, this.material),
      lower: new THREE.Mesh(seg, this.material),
      shoulder: new THREE.Mesh(joint, this.material),
      elbow: new THREE.Mesh(joint, this.material)
    }));
    this.sleeves.forEach(s => Object.values(s).forEach(m => scene.add(m)));

    this.kind = 'BLOUSE';
    this.setVisible(false);
  }

  setGarment(spec) {
    this.kind = spec.kind || 'BLOUSE';
    const c = new THREE.Color(spec.color || '#888888');
    this.material.color.copy(c);
    this.material.sheenColor.copy(c.clone().lerp(new THREE.Color('#ffffff'), 0.35));
    this.material.needsUpdate = true;
  }

  setVisible(v) {
    this.torso.visible = v;
    this.sleeves.forEach(s => Object.values(s).forEach(m => { m.visible = v; }));
  }

  /**
   * p: puntos del cuerpo en la escena (THREE.Vector3): ls, rs, le, re, lw, rw, lh, rh, lk, rk.
   * "l"/"r" son los lados del CUERPO, así la prenda gira con la persona (360°).
   */
  update(p) {
    if (!p || !p.ls || !p.rs || !p.lh || !p.rh) { this.setVisible(false); return; }
    this.setVisible(true);
    const kind = this.kind;

    const sMid = tmpA.addVectors(p.ls, p.rs).multiplyScalar(0.5).clone();
    const hMid = tmpB.addVectors(p.lh, p.rh).multiplyScalar(0.5).clone();
    const W = p.ls.distanceTo(p.rs);
    if (W < 4) { this.setVisible(false); return; }

    // Base del cuerpo: X = de hombro izquierdo a derecho, Y = arriba, Z = al frente.
    const X = new THREE.Vector3().subVectors(p.rs, p.ls).normalize();
    const Y = new THREE.Vector3().subVectors(sMid, hMid);
    Y.sub(X.clone().multiplyScalar(Y.dot(X))).normalize();
    const Z = new THREE.Vector3().crossVectors(X, Y).normalize();
    tmpM.makeBasis(X, Y, Z);
    this.torso.quaternion.setFromRotationMatrix(tmpM);
    this.torso.position.copy(sMid);
    this.torso.scale.setScalar(W);

    const torsoLen = sMid.distanceTo(hMid) / W;
    const hipRatio = p.lh.distanceTo(p.rh) / W;
    const hem = kind === 'JACKET' ? 0.22 : kind === 'BLOUSE' ? 0.1 : 0;
    const L = torsoLen * (1 + hem);
    const girth = kind === 'JACKET' ? 1.1 : 1.0;
    const depth = 0.66;

    this.torsoMesh.scale.set(girth * Math.max(0.9, hipRatio * 1.15), L, girth * depth);
    this.torsoMesh.position.set(0, -L / 2 - 0.02, 0);
    this.cap.scale.set(0.56 * girth, 0.16, 0.56 * girth * depth);
    this.cap.position.set(0, -0.02, 0);

    // Vestido: falda de la cadera a las rodillas.
    this.skirt.visible = kind === 'DRESS';
    if (kind === 'DRESS') {
      let K = 1.15;
      if (p.lk && p.rk) {
        const kMid = new THREE.Vector3().addVectors(p.lk, p.rk).multiplyScalar(0.5);
        K = Math.max(0.6, hMid.distanceTo(kMid) / W + 0.12);
      }
      this.skirt.scale.set(Math.max(0.95, hipRatio * 1.3), K, depth * 1.1);
      this.skirt.position.set(0, -L - K / 2 + 0.02, 0);
    }

    // Chamarra: playera blanca al frente (abierta).
    this.inner.visible = kind === 'JACKET';
    if (kind === 'JACKET') {
      this.inner.scale.set(0.24, L * 0.92, 1);
      this.inner.position.set(0, -L * 0.48, 0.5 * girth * depth + 0.005);
    }

    // Mangas
    const longSleeves = kind === 'JACKET';
    const shortLen = kind === 'DRESS' ? 0.3 : 0.48;
    const r = W * (longSleeves ? 0.15 : 0.16);
    [[p.ls, p.le, p.lw, -1], [p.rs, p.re, p.rw, 1]].forEach(([s, e, w], i) => {
      const sl = this.sleeves[i];
      if (!e) { Object.values(sl).forEach(m => { m.visible = false; }); return; }
      const start = s.clone().add(X.clone().multiplyScalar((i === 0 ? -1 : 1) * W * 0.03)).add(Y.clone().multiplyScalar(-W * 0.05));
      const end = longSleeves ? e : start.clone().lerp(e, shortLen);
      placeJoint(sl.shoulder, start, r * 1.02);
      placeSegment(sl.upper, start, end, r);
      if (longSleeves) {
        placeJoint(sl.elbow, e, r * 0.92);
        const wr = w || e.clone().add(e.clone().sub(start).multiplyScalar(0.85));
        placeSegment(sl.lower, e, wr, r * 0.85);
      } else {
        sl.elbow.visible = false;
        sl.lower.visible = false;
      }
    });
  }
}

/** Maniquí gris para el modo demo (sin cámara). */
export class Mannequin {
  constructor(scene) {
    this.material = new THREE.MeshStandardMaterial({ color: '#4a4744', roughness: 0.6, metalness: 0.1 });
    const seg = new THREE.CylinderGeometry(1, 1, 1, 20);
    const joint = new THREE.SphereGeometry(1, 20, 14);
    this.parts = {};
    ['torso', 'neck', 'lua', 'lla', 'rua', 'rla', 'lth', 'lsh', 'rth', 'rsh'].forEach(k => {
      this.parts[k] = new THREE.Mesh(seg, this.material);
      scene.add(this.parts[k]);
    });
    ['head', 'pelvis', 'ls', 'rs', 'le', 're', 'lk', 'rk', 'lh', 'rh'].forEach(k => {
      this.parts[k] = new THREE.Mesh(joint, this.material);
      scene.add(this.parts[k]);
    });
    this.setVisible(false);
  }

  setVisible(v) { Object.values(this.parts).forEach(m => { m.visible = v; }); }

  update(p, extra) {
    const W = p.ls.distanceTo(p.rs);
    const P = this.parts;
    const sMid = new THREE.Vector3().addVectors(p.ls, p.rs).multiplyScalar(0.5);
    const hMid = new THREE.Vector3().addVectors(p.lh, p.rh).multiplyScalar(0.5);
    placeSegment(P.torso, sMid, hMid, W * 0.3);
    P.torso.scale.x = W * 0.42; // torso ovalado: más ancho que profundo
    placeSegment(P.neck, sMid, extra.head, W * 0.12);
    placeJoint(P.head, extra.head, W * 0.26);
    placeJoint(P.pelvis, hMid, W * 0.3);
    placeJoint(P.ls, p.ls, W * 0.13); placeJoint(P.rs, p.rs, W * 0.13);
    placeSegment(P.lua, p.ls, p.le, W * 0.1); placeSegment(P.rua, p.rs, p.re, W * 0.1);
    placeJoint(P.le, p.le, W * 0.1); placeJoint(P.re, p.re, W * 0.1);
    placeSegment(P.lla, p.le, p.lw, W * 0.085); placeSegment(P.rla, p.re, p.rw, W * 0.085);
    placeJoint(P.lh, p.lh, W * 0.15); placeJoint(P.rh, p.rh, W * 0.15);
    placeSegment(P.lth, p.lh, p.lk, W * 0.14); placeSegment(P.rth, p.rh, p.rk, W * 0.14);
    placeJoint(P.lk, p.lk, W * 0.12); placeJoint(P.rk, p.rk, W * 0.12);
    placeSegment(P.lsh, p.lk, extra.lf, W * 0.11); placeSegment(P.rsh, p.rk, extra.rf, W * 0.11);
  }
}
