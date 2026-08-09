// Rasterises the Orbit mark (see website/orbit/favicon.svg) into the bitmap
// formats that can't consume SVG: favicon.ico for older Safari / Windows, and
// apple-touch-icon.png for iOS home screens. No image library is installed, so
// this draws by signed distance and encodes the PNG by hand over zlib.
const zlib = require("zlib");
const fs = require("fs");
const path = require("path");

const VIOLET = [0x7c, 0x5c, 0xff];
const AQUA = [0x22, 0xd3, 0xee];
const INK = [0x0b, 0x0b, 0x12];

// Geometry in the same 34-unit space as the .logo element.
const BOX = 34, RX = 10;
const RING = { cx: 17, cy: 17, r: 8, w: 2 };
const SAT = { cx: 24.5, cy: 9.5, r: 3.5, halo: 3 };

// Distance from (x,y) to a rounded rect of side BOX — negative inside.
function roundedRect(x, y) {
  const qx = Math.abs(x - BOX / 2) - (BOX / 2 - RX);
  const qy = Math.abs(y - BOX / 2) - (BOX / 2 - RX);
  const outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
  return outside + Math.min(Math.max(qx, qy), 0) - RX;
}

function gradient(x, y) {
  // linear-gradient(120deg, violet, aqua): direction (sin120, -cos120), y down.
  const dx = 0.8660254, dy = -0.5;
  const len = BOX * Math.abs(dx) + BOX * Math.abs(dy);
  let t = ((x - BOX / 2) * dx + (y - BOX / 2) * dy) / len + 0.5;
  t = Math.min(1, Math.max(0, t));
  return VIOLET.map((c, i) => c + (AQUA[i] - c) * t);
}

function over(dst, src, alpha) {
  for (let i = 0; i < 3; i++) dst[i] = dst[i] * (1 - alpha) + src[i] * alpha;
  dst[3] = dst[3] * (1 - alpha) + alpha;
}

// One supersample: returns [r,g,b,a] with a in 0..1.
function sample(x, y) {
  const px = [0, 0, 0, 0];
  if (roundedRect(x, y) > 0) return px;
  over(px, gradient(x, y), 1);

  const dRing = Math.abs(Math.hypot(x - RING.cx, y - RING.cy) - RING.r);
  if (dRing <= RING.w / 2) over(px, INK, 0.9);

  const dSat = Math.hypot(x - SAT.cx, y - SAT.cy);
  if (dSat <= SAT.r + SAT.halo) over(px, AQUA, 1);
  if (dSat <= SAT.r) over(px, INK, 1);

  return px;
}

function render(size) {
  const SS = 4; // 4×4 supersampling stands in for anti-aliasing
  const buf = Buffer.alloc(size * size * 4);
  for (let py = 0; py < size; py++) {
    for (let px = 0; px < size; px++) {
      const acc = [0, 0, 0, 0];
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const x = ((px + (sx + 0.5) / SS) / size) * BOX;
          const y = ((py + (sy + 0.5) / SS) / size) * BOX;
          const s = sample(x, y);
          for (let i = 0; i < 4; i++) acc[i] += s[i];
        }
      }
      const n = SS * SS, o = (py * size + px) * 4;
      buf[o] = Math.round(acc[0] / n);
      buf[o + 1] = Math.round(acc[1] / n);
      buf[o + 2] = Math.round(acc[2] / n);
      buf[o + 3] = Math.round((acc[3] / n) * 255);
    }
  }
  return buf;
}

// --- minimal PNG encoder (RGBA8, filter 0) ---
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();

function crc32(buf) {
  let c = -1;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "latin1"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

function png(size, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8;  // bit depth
  ihdr[9] = 6;  // colour type: RGBA
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0; // filter: none
    rgba.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

// --- ICO container (PNG-in-ICO, understood since Vista / Safari 5) ---
function ico(entries) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(1, 2); // type: icon
  header.writeUInt16LE(entries.length, 4);
  let offset = 6 + entries.length * 16;
  const dir = [], data = [];
  for (const { size, buf } of entries) {
    const e = Buffer.alloc(16);
    e[0] = size >= 256 ? 0 : size;
    e[1] = size >= 256 ? 0 : size;
    e[4] = 1;              // colour planes
    e.writeUInt16LE(32, 6); // bits per pixel
    e.writeUInt32LE(buf.length, 8);
    e.writeUInt32LE(offset, 12);
    offset += buf.length;
    dir.push(e);
    data.push(buf);
  }
  return Buffer.concat([header, ...dir, ...data]);
}

const targets = process.argv.slice(2);
if (!targets.length) throw new Error("usage: node gen-icons.js <outdir>...");

const icoBuf = ico([16, 32, 48].map((size) => ({ size, buf: png(size, render(size)) })));
const touch = png(180, render(180));

for (const dir of targets) {
  fs.writeFileSync(path.join(dir, "favicon.ico"), icoBuf);
  fs.writeFileSync(path.join(dir, "apple-touch-icon.png"), touch);
  console.log("wrote favicon.ico + apple-touch-icon.png ->", dir);
}
