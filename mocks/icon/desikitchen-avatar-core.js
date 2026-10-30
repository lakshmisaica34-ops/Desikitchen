/*
 * DesiKitchen avatar core — procedural geometry + spring-driven character rig.
 * Same approach as the Grok Bot avatar: the filled body is a *generated* path
 * (recomputed every frame from a handful of parameters) and the two eyes are
 * independent white shapes with their own blink / gaze / squint channels.
 *
 * Coordinates are the source-PNG pixel space (desikitchen-mark-final.png, 1448x1086),
 * so the rest pose overlays the locked mark 1:1. Default viewBox: 424 216 600 600.
 *
 * Works in the browser (window.DKAvatar) and in Node (module.exports).
 */
(function (root) {
  'use strict';

  // ---------- rest geometry (measured from the locked PNG) ----------
  const REST = {
    // three hat puffs
    lx: 611, ly: 418, lr: 100.5,
    cx: 724, cy: 405, cr: 107,
    rx: 837, ry: 418, rr: 100.5,
    // straight hat band
    sideL: 556, sideR: 893, bottom: 632,
    legR: 7,                 // leg tip rounding
    // house (negative space)
    apexX: 724.5, apexY: 507, slope: 0.8197, apexR: 9,
    chimL: 798, chimR: 840, chimTop: 509, chimR_r: 1.5, chimJoin_r: 6,
    // pleat grooves (black strokes on the centre puff edge)
    grooveW: 6.5, grooveOff: 3.5, grooveEndL: 30, grooveEndR: 40,
    // window eyes
    eyeLx: 659, eyeRx: 791, eyeBottom: 731, eyeW: 58, eyeH: 85, eyeBR: 6,
  };
  const ANCHOR = { x: 724, y: 730 };   // ground point for squash / tilt
  const VIEWBOX = '424 216 600 600';

  const f = (n) => (Math.round(n * 100) / 100).toString();

  // ---------- geometry helpers ----------
  function circleIntersectUpper(x1, y1, r1, x2, y2, r2) {
    const dx = x2 - x1, dy = y2 - y1, d = Math.hypot(dx, dy);
    const a = (r1 * r1 - r2 * r2 + d * d) / (2 * d);
    const h = Math.sqrt(Math.max(0, r1 * r1 - a * a));
    const ux = dx / d, uy = dy / d;
    const px = x1 + a * ux, py = y1 + a * uy;
    const p1 = { x: px + h * -uy, y: py + h * ux };
    const p2 = { x: px - h * -uy, y: py - h * ux };
    return p1.y < p2.y ? p1 : p2;   // upper (smaller y)
  }
  const ang = (cx, cy, p) => Math.atan2(p.y - cy, p.x - cx);
  function arcTo(cx, cy, r, from, to) {
    // clockwise on screen (sweep=1), angles in radians
    let a0 = ang(cx, cy, from), a1 = ang(cx, cy, to);
    let sweep = a1 - a0; while (sweep < 0) sweep += Math.PI * 2;
    const large = sweep > Math.PI ? 1 : 0;
    return `A${f(r)} ${f(r)} 0 ${large} 1 ${f(to.x)} ${f(to.y)}`;
  }
  // polyline with quadratic-rounded corners; pts = [{x,y,r}], first/last unrounded
  function roundedPolyline(pts) {
    let d = '';
    for (let i = 1; i < pts.length; i++) {
      const p = pts[i];
      if (i === pts.length - 1 || !p.r) { d += `L${f(p.x)} ${f(p.y)}`; continue; }
      const a = pts[i - 1], b = pts[i + 1];
      const la = Math.hypot(a.x - p.x, a.y - p.y), lb = Math.hypot(b.x - p.x, b.y - p.y);
      const ca = Math.min(p.r, la / 2), cb = Math.min(p.r, lb / 2);
      const s = { x: p.x + (a.x - p.x) * ca / la, y: p.y + (a.y - p.y) * ca / la };
      const e = { x: p.x + (b.x - p.x) * cb / lb, y: p.y + (b.y - p.y) * cb / lb };
      d += `L${f(s.x)} ${f(s.y)}Q${f(p.x)} ${f(p.y)} ${f(e.x)} ${f(e.y)}`;
    }
    return d;
  }

  // ---------- hat body: one filled path, house cut out ----------
  function hatPath(g) {
    const yL = g.ly + Math.sqrt(Math.max(0, g.lr * g.lr - (g.lx - g.sideL) ** 2));
    const yR = g.ry + Math.sqrt(Math.max(0, g.rr * g.rr - (g.rx - g.sideR) ** 2));
    const pLC = circleIntersectUpper(g.lx, g.ly, g.lr, g.cx, g.cy, g.cr);
    const pCR = circleIntersectUpper(g.cx, g.cy, g.cr, g.rx, g.ry, g.rr);
    const roofL = (y) => g.apexX - (y - g.apexY) / g.slope * 1; // x on left roof
    const roofR = (y) => g.apexX + (y - g.apexY) / g.slope * 1;
    const yAtR = (x) => g.apexY + (x - g.apexX) * g.slope;      // y on right roof
    const yAtL = (x) => g.apexY + (g.apexX - x) * g.slope;
    // roof lines: x = apexX ± (y-apexY)/slope'  where slope' = dy/dx
    const sL = { x: g.sideL, y: yL };
    let d = `M${f(g.sideL)} ${f(g.bottom - g.legR)}L${f(sL.x)} ${f(sL.y)}`;
    d += arcTo(g.lx, g.ly, g.lr, sL, pLC);
    d += arcTo(g.cx, g.cy, g.cr, pLC, pCR);
    const sR = { x: g.sideR, y: yR };
    d += arcTo(g.rx, g.ry, g.rr, pCR, sR);
    const footR = { x: g.apexX + (g.bottom - g.apexY) / g.slope, y: g.bottom };
    const footL = { x: g.apexX - (g.bottom - g.apexY) / g.slope, y: g.bottom };
    const pts = [
      sR,
      { x: g.sideR, y: g.bottom, r: g.legR },
      { x: footR.x, y: g.bottom, r: g.legR },
      { x: g.chimR, y: yAtR(g.chimR), r: g.chimR_r },
      { x: g.chimR, y: g.chimTop, r: g.chimR_r },
      { x: g.chimL, y: g.chimTop, r: g.chimR_r },
      { x: g.chimL, y: yAtR(g.chimL), r: g.chimJoin_r },
      { x: g.apexX, y: g.apexY, r: g.apexR },
      { x: footL.x, y: g.bottom, r: g.legR },
      { x: g.sideL, y: g.bottom, r: g.legR },
      { x: g.sideL, y: g.bottom - g.legR },
    ];
    d += roundedPolyline(pts) + 'Z';
    return d;
  }

  function groovePaths(g) {
    const r = g.cr + g.grooveOff;
    const pLC = circleIntersectUpper(g.lx, g.ly, g.lr, g.cx, g.cy, g.cr);
    const pCR = circleIntersectUpper(g.cx, g.cy, g.cr, g.rx, g.ry, g.rr);
    // start points: project notch onto groove circle
    const a0 = ang(g.cx, g.cy, pLC), a1 = ang(g.cx, g.cy, pCR);
    const s0 = { x: g.cx + r * Math.cos(a0), y: g.cy + r * Math.sin(a0) };
    const s1 = { x: g.cx + r * Math.cos(a1), y: g.cy + r * Math.sin(a1) };
    const eL = { x: g.cx - Math.sqrt(r * r - g.grooveEndL ** 2), y: g.cy + g.grooveEndL };
    const eR = { x: g.cx + Math.sqrt(r * r - g.grooveEndR ** 2), y: g.cy + g.grooveEndR };
    // left groove runs counter-clockwise (sweep 0) from notch down to eL
    const left = `M${f(s0.x)} ${f(s0.y)}A${f(r)} ${f(r)} 0 0 0 ${f(eL.x)} ${f(eL.y)}`;
    const right = `M${f(s1.x)} ${f(s1.y)}A${f(r)} ${f(r)} 0 0 1 ${f(eR.x)} ${f(eR.y)}`;
    return { left, right };
  }

  // ---------- window eye: arched window that can blink, squint, smile ----------
  // open: 1 = full window, ~0.1 = closed bar. smile: px of upward bend (^ eye).
  function eyePath(cx, by, w, h, br, open, smile, sx) {
    const minH = 8;
    const hw = (w * (sx || 1)) / 2;
    const H = Math.max(minH, h * open);
    const b = Math.min(br, H / 2);
    const ry = Math.min(hw, H - b);
    const top = by - H;
    const k = 0.5523;
    const warp = (x, y) => {
      const u = Math.max(-1, Math.min(1, (x - cx) / hw));
      return [x, y - smile * (1 - u * u)];
    };
    const P = (x, y) => { const [a, c] = warp(x, y); return `${f(a)} ${f(c)}`; };
    const xl = cx - hw, xr = cx + hw;
    let d = `M${P(xl, top + ry)}`;
    d += `C${P(xl, top + ry - ry * k)} ${P(cx - hw * k, top)} ${P(cx, top)}`;
    d += `C${P(cx + hw * k, top)} ${P(xr, top + ry - ry * k)} ${P(xr, top + ry)}`;
    d += `L${P(xr, by - b)}`;
    d += `Q${P(xr, by)} ${P(xr - b, by)}`;
    // bottom edge as a cubic so the smile warp bends it smoothly
    d += `C${P(cx + hw * 0.3, by)} ${P(cx - hw * 0.3, by)} ${P(xl + b, by)}`;
    d += `Q${P(xl, by)} ${P(xl, by - b)}Z`;
    return d;
  }

  // ---------- springs ----------
  class Spring {
    constructor(v, k = 170, c = 18) { this.v = v; this.t = v; this.vel = 0; this.k = k; this.c = c; }
    set(t, k, c) { this.t = t; if (k) this.k = k; if (c) this.c = c; }
    step(dt) {
      // semi-implicit Euler, sub-stepped for stability
      const n = Math.ceil(dt / (1 / 240));
      const h = dt / n;
      for (let i = 0; i < n; i++) {
        const a = -this.k * (this.v - this.t) - this.c * this.vel;
        this.vel += a * h; this.v += this.vel * h;
      }
      return this.v;
    }
  }

  // deterministic RNG
  function mulberry32(a) {
    return function () {
      a |= 0; a = a + 0x6D2B79F5 | 0;
      let t = Math.imul(a ^ a >>> 15, 1 | a);
      t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
      return ((t ^ t >>> 14) >>> 0) / 4294967296;
    };
  }

  // blink curve, 0.26 s total: fast close (90 ms), hold (30 ms), ease-out open (140 ms)
  const BLINK = 0.26;
  function blinkCurve(t) {
    if (t < 0 || t > BLINK) return 1;
    const close = 0.09, hold = 0.03;
    if (t < close) { const u = t / close; return 1 - u * u; }           // ease-in close
    if (t < close + hold) return 0;
    const u = (t - close - hold) / (BLINK - close - hold);
    return 1 - Math.pow(1 - u, 3);                                       // ease-out open
  }

  // ---------- the rig ----------
  class Rig {
    constructor(opts = {}) {
      this.rand = mulberry32(opts.seed || 7);
      this.auto = opts.auto !== false;     // random gaze + blink scheduling
      this.grokTilt = !!opts.grokTilt;     // optional Grok-style 26° eye tilt
      this.t = 0;
      const S = (v, k, c) => new Spring(v, k, c);
      this.s = {
        gx: S(0, 120, 16), gy: S(0, 120, 16),          // gaze offset (px)
        openL: S(1, 260, 22), openR: S(1, 260, 22),    // state-driven lid opening
        smileL: S(0, 220, 18), smileR: S(0, 220, 18),
        eyeTilt: S(0, 160, 16),                         // deg
        tilt: S(0, 140, 11),                            // body tilt (deg) — underdamped, overshoots
        sqx: S(1, 260, 13), sqy: S(1, 260, 13),         // squash & stretch
        lift: S(0, 200, 14),                            // body hop (px, negative = up)
        breathe: S(1, 80, 14),                          // breathing amplitude multiplier
        ripple: S(0, 90, 14),                           // thinking puff ripple amplitude
      };
      this.state = 'idle';
      this.stateT = 0;
      this.blinks = [];          // start times of blinks
      this.nextBlink = 1.2;
      this.nextGaze = 0.8;
      this.thinkScan = 0;
    }

    blink(at) { this.blinks.push(at == null ? this.t : at); }
    gaze(x, y) { this.s.gx.set(x); this.s.gy.set(y); }

    setState(name) {
      this.state = name; this.stateT = 0;
      const s = this.s;
      // reset expression channels toward neutral
      s.openL.set(1); s.openR.set(1); s.smileL.set(0); s.smileR.set(0);
      s.eyeTilt.set(this.grokTilt ? 26 : 0); s.tilt.set(0); s.ripple.set(0); s.breathe.set(1);
      if (name === 'wink') {
        s.openR.set(0.06, 420, 26); s.smileR.set(13);
        s.openL.set(0.86); s.smileL.set(2);
        s.tilt.set(-5); s.gx.set(-3); s.gy.set(-2);
        // pop: squash then spring back
        s.sqx.v = 1.06; s.sqy.v = 0.93; s.sqx.vel = 0; s.sqy.vel = 0;
        s.lift.set(0); s.lift.vel = -160;
      } else if (name === 'thinking') {
        s.gx.set(11); s.gy.set(-12);
        s.openL.set(0.74); s.openR.set(0.8);
        s.eyeTilt.set(this.grokTilt ? 26 : 8);
        s.tilt.set(3); s.ripple.set(1); s.breathe.set(0.4);
        this.thinkScan = 0.9;
      } else if (name === 'happy') {
        s.openL.set(0.08, 420, 26); s.openR.set(0.08, 420, 26);
        s.smileL.set(14); s.smileR.set(14);
        s.gx.set(0); s.gy.set(-4);
      } else if (name === 'idle') {
        s.gx.set(0); s.gy.set(0);
      }
    }

    step(dt) {
      this.t += dt; this.stateT += dt;
      const s = this.s, r = this.rand, t = this.t;
      if (this.auto) {
        if (t >= this.nextBlink) {
          if (this.state !== 'happy' && this.state !== 'wink') {
            this.blink(t);
            if (r() < 0.18) this.blink(t + BLINK + 0.12);   // occasional double blink
          }
          this.nextBlink = t + 2.4 + r() * 3.2;
        }
        if (this.state === 'idle' && t >= this.nextGaze) {
          // saccade-like drift: mostly small, sometimes back to centre
          if (r() < 0.3) this.gaze(0, 0);
          else this.gaze((r() * 2 - 1) * 13, (r() * 2 - 1) * 7);
          this.nextGaze = t + 1.1 + r() * 2.2;
        }
        if (this.state === 'thinking' && this.stateT >= this.thinkScan) {
          // glance between two "ceiling" spots while thinking
          const alt = Math.round(this.stateT / 1.1) % 2;
          this.gaze(alt ? 4 : 12, alt ? -14 : -11);
          this.thinkScan = this.stateT + 1.1;
        }
        if (this.state === 'wink' && this.stateT > 0.9) this.setState('idle');
        if (this.state === 'happy' && this.stateT > 1.2) this.setState('idle');
      }
      if (this.state === 'happy') {
        // two little hops
        const ph = this.stateT;
        if (ph < 0.9) { const hop = Math.max(0, Math.sin(ph * Math.PI * 2 / 0.45)); s.lift.set(-14 * hop); s.sqy.set(1 + 0.05 * hop); s.sqx.set(1 - 0.03 * hop); }
        else { s.lift.set(0); s.sqx.set(1); s.sqy.set(1); }
      } else { s.sqx.set(1); s.sqy.set(1); s.lift.set(0); }
      for (const k in s) s[k].step(dt);
      this.blinks = this.blinks.filter((b) => t - b < BLINK + 0.05);
    }

    // returns all per-frame geometry
    frame() {
      const s = this.s, t = this.t, g = Object.assign({}, REST);
      // breathing: puffs swell with offset phases (period 3.2 s)
      const B = 1.6 * s.breathe.v, w = (Math.PI * 2) / 3.2;
      g.lr += B * Math.sin(w * t);
      g.cr += B * 1.15 * Math.sin(w * t - 0.7);
      g.rr += B * Math.sin(w * t - 1.4);
      g.cy -= 1.0 * s.breathe.v * Math.sin(w * t - 0.7);
      // thinking ripple: puffs bulge L→C→R like thought bubbles (period 1.2 s)
      const R = s.ripple.v, wr = (Math.PI * 2) / 1.2;
      if (Math.abs(R) > 1e-3) {
        const bump = (ph) => Math.pow(Math.max(0, Math.sin(wr * t - ph)), 2);
        g.lr += 5 * R * bump(0); g.ly -= 2 * R * bump(0);
        g.cr += 5 * R * bump(1.2); g.cy -= 3 * R * bump(1.2);
        g.rr += 5 * R * bump(2.4); g.ry -= 2 * R * bump(2.4);
      }
      // blinks (both eyes), multiplied with state lids
      let bl = 1; for (const b of this.blinks) bl = Math.min(bl, blinkCurve(t - b));
      const openL = Math.max(0.05, s.openL.v) * Math.max(0.06, bl);
      const openR = Math.max(0.05, s.openR.v) * Math.max(0.06, bl);
      // gaze: eyes translate; the far eye narrows slightly (fake parallax)
      const gx = s.gx.v, gy = s.gy.v;
      // saccade smear: eyes stretch a touch along fast horizontal motion (Grok eyes do this)
      const smear = Math.min(0.12, Math.abs(s.gx.vel) * 0.0016);
      const sxL = (1 - Math.max(0, gx) * 0.004) * (1 + smear), sxR = (1 + Math.min(0, gx) * 0.004) * (1 + smear);
      const eby = g.eyeBottom + gy;
      const eyeL = eyePath(g.eyeLx + gx, eby, g.eyeW, g.eyeH, g.eyeBR, openL, s.smileL.v, sxL);
      const eyeR = eyePath(g.eyeRx + gx, eby, g.eyeW, g.eyeH, g.eyeBR, openR, s.smileR.v, sxR);
      const eyeCy = eby - g.eyeH / 2;
      const et = s.eyeTilt.v;
      const body = `translate(${f(ANCHOR.x)} ${f(ANCHOR.y + s.lift.v)}) rotate(${f(s.tilt.v)}) scale(${f(s.sqx.v)} ${f(s.sqy.v)}) translate(${f(-ANCHOR.x)} ${f(-ANCHOR.y)})`;
      return {
        hat: hatPath(g),
        grooves: groovePaths(g),
        grooveW: g.grooveW,
        eyeL, eyeR,
        eyeLT: `rotate(${f(-et)} ${f(g.eyeLx + gx)} ${f(eyeCy)})`,
        eyeRT: `rotate(${f(-et)} ${f(g.eyeRx + gx)} ${f(eyeCy)})`,
        body,
      };
    }
  }

  function toSVG(fr, opts = {}) {
    const vb = opts.viewBox || VIEWBOX;
    const bg = opts.background === undefined ? '#000' : opts.background;
    const size = opts.size ? ` width="${opts.size}" height="${opts.size}"` : '';
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb}"${size}>` +
      (bg ? `<rect id="bg" x="-5000" y="-5000" width="10000" height="10000" fill="${bg}"/>` : '') +
      `<g id="body" transform="${fr.body}">` +
      `<path id="hat" fill="#fff" d="${fr.hat}"/>` +
      `<g id="grooves" fill="none" stroke="${bg || '#000'}" stroke-width="${fr.grooveW}" stroke-linecap="round">` +
      `<path id="groove-l" d="${fr.grooves.left}"/><path id="groove-r" d="${fr.grooves.right}"/></g>` +
      `<path id="eye-l" fill="#fff" transform="${fr.eyeLT}" d="${fr.eyeL}"/>` +
      `<path id="eye-r" fill="#fff" transform="${fr.eyeRT}" d="${fr.eyeR}"/>` +
      `</g></svg>`;
  }

  const api = { REST, ANCHOR, VIEWBOX, hatPath, groovePaths, eyePath, blinkCurve, BLINK, Spring, Rig, toSVG };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.DKAvatar = api;
})(typeof window !== 'undefined' ? window : globalThis);
