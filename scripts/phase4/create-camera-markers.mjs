// Deterministic, dependency-free BGR24 AVI fixtures for emulator -camera-* videofile: inputs.
// Usage: node scripts/phase4/create-camera-markers.mjs .tools/phase4-markers
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const out = resolve(process.argv[2] ?? '.tools/phase4-markers');
await mkdir(out, { recursive: true });
const width = 160, height = 90, fps = 30, frames = 120;
const frameBytes = width * height * 3;
function chunk(id, data) {
  const head = Buffer.alloc(8);
  head.write(id, 0, 4, 'ascii'); head.writeUInt32LE(data.length, 4);
  return Buffer.concat([head, data, ...(data.length % 2 ? [Buffer.alloc(1)] : [])]);
}
function list(type, entries) { return chunk('LIST', Buffer.concat([Buffer.from(type), ...entries])); }
function avi(front) {
  const main = Buffer.alloc(56);
  [1e6 / fps, frameBytes * fps, 0, 0x10, frames, 0, 1, frameBytes, width, height].forEach((v, i) => main.writeUInt32LE(Math.round(v), i * 4));
  const stream = Buffer.alloc(56);
  stream.write('vidsDIB ', 0, 'ascii');
  stream.writeUInt32LE(1, 20); stream.writeUInt32LE(fps, 24);
  stream.writeUInt32LE(frames, 32); stream.writeUInt32LE(frameBytes, 36);
  stream.writeUInt32LE(0xffffffff, 40);
  stream.writeInt16LE(width, 52); stream.writeInt16LE(height, 54);
  const format = Buffer.alloc(40);
  format.writeUInt32LE(40); format.writeInt32LE(width, 4); format.writeInt32LE(height, 8);
  format.writeUInt16LE(1, 12); format.writeUInt16LE(24, 14); format.writeUInt32LE(frameBytes, 20);
  const header = list('hdrl', [chunk('avih', main), list('strl', [chunk('strh', stream), chunk('strf', format)])]);
  const movie = [], index = []; let offset = 4;
  for (let frame = 0; frame < frames; frame++) {
    const pixels = Buffer.alloc(frameBytes);
    const bar = front ? (width - 1 - (frame * 3) % width) : (frame * 2) % width;
    for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
      const p = ((height - 1 - y) * width + x) * 3;
      const moving = Math.abs(x - bar) < 9;
      const identity = y < 12 && (front ? x < 24 : x > width - 25);
      const rgb = moving ? [245, 245, 245] : identity ? [20, 240, 20] : front ? [20, 20, 210] : [210, 20, 20];
      pixels[p] = rgb[2]; pixels[p + 1] = rgb[1]; pixels[p + 2] = rgb[0];
    }
    const entry = chunk('00db', pixels); movie.push(entry);
    const ix = Buffer.alloc(16); ix.write('00db'); ix.writeUInt32LE(0x10, 4);
    ix.writeUInt32LE(offset, 8); ix.writeUInt32LE(pixels.length, 12); index.push(ix);
    offset += entry.length;
  }
  return chunk('RIFF', Buffer.concat([Buffer.from('AVI '), header, list('movi', movie), chunk('idx1', Buffer.concat(index))]));
}
for (const [name, front] of [['rear-red.avi', false], ['front-blue.avi', true]]) {
  const data = avi(front); await writeFile(resolve(out, name), data);
  console.log(`${name}: ${width}x${height}, ${frames} frames, ${fps}fps, ${data.length} bytes`);
}
