'use strict';
/* Locally authored QR Model 2 encoder: version 6, byte mode, level L, mask 0.
 * The bounded invitation URL is encoded entirely in this browser. No service,
 * image URL, dependency or network request receives invitation material. */
(() => {
  const SIZE = 41, DATA_BYTES = 136, BLOCK_BYTES = 68, ECC_BYTES = 18;
  function multiply(a, b) { let result = 0; for (; b; b >>>= 1) { if (b & 1) result ^= a; a <<= 1; if (a & 256) a ^= 0x11d; } return result; }
  function remainder(data) {
    let generator = [1], power = 1;
    for (let index = 0; index < ECC_BYTES; index++) {
      const next = Array(generator.length + 1).fill(0);
      for (let offset = 0; offset < generator.length; offset++) { next[offset] ^= generator[offset]; next[offset + 1] ^= multiply(generator[offset], power); }
      generator = next; power = multiply(power, 2);
    }
    const result = [...data, ...Array(ECC_BYTES).fill(0)];
    for (let index = 0; index < data.length; index++) { const coefficient = result[index]; for (let offset = 0; offset < generator.length; offset++) result[index + offset] ^= multiply(generator[offset], coefficient); }
    return result.slice(data.length);
  }
  function matrix(text) {
    const bytes = new TextEncoder().encode(text);
    if (!bytes.length || bytes.length > 134) throw new Error('QR_INVITATION_TOO_LONG');
    const bits = [], append = (value, length) => { for (let index = length - 1; index >= 0; index--) bits.push(value >>> index & 1); };
    append(4, 4); append(bytes.length, 8); for (const byte of bytes) append(byte, 8);
    append(0, Math.min(4, DATA_BYTES * 8 - bits.length)); while (bits.length % 8) bits.push(0);
    const data = []; for (let index = 0; index < bits.length; index += 8) data.push(bits.slice(index, index + 8).reduce((value, bit) => value * 2 + bit, 0));
    for (let pad = 0; data.length < DATA_BYTES; pad++) data.push(pad % 2 ? 0x11 : 0xec);
    const blocks = [data.slice(0, BLOCK_BYTES), data.slice(BLOCK_BYTES)], ecc = blocks.map(remainder), codewords = [];
    for (let index = 0; index < BLOCK_BYTES; index++) for (const block of blocks) codewords.push(block[index]);
    for (let index = 0; index < ECC_BYTES; index++) for (const block of ecc) codewords.push(block[index]);
    const cells = Array.from({length: SIZE}, () => Array(SIZE).fill(null));
    const finder = (top, left) => { for (let row = -1; row <= 7; row++) for (let column = -1; column <= 7; column++) {
      if (top + row < 0 || top + row >= SIZE || left + column < 0 || left + column >= SIZE) continue;
      cells[top + row][left + column] = row >= 0 && row <= 6 && (column === 0 || column === 6) || column >= 0 && column <= 6 && (row === 0 || row === 6) || row >= 2 && row <= 4 && column >= 2 && column <= 4;
    } };
    finder(0, 0); finder(0, SIZE - 7); finder(SIZE - 7, 0);
    for (let row = -2; row <= 2; row++) for (let column = -2; column <= 2; column++) cells[34 + row][34 + column] = Math.max(Math.abs(row), Math.abs(column)) !== 1;
    for (let index = 8; index < SIZE - 8; index++) { if (cells[index][6] === null) cells[index][6] = index % 2 === 0; if (cells[6][index] === null) cells[6][index] = index % 2 === 0; }
    const formatData = 1 << 3; let format = formatData << 10;
    for (let bit = 14; bit >= 10; bit--) if (format >>> bit & 1) format ^= 0x537 << (bit - 10);
    format = (formatData << 10 | format) ^ 0x5412;
    for (let index = 0; index < 15; index++) {
      const dark = Boolean(format >>> index & 1);
      cells[index < 6 ? index : index < 8 ? index + 1 : SIZE - 15 + index][8] = dark;
      cells[8][index < 8 ? SIZE - index - 1 : index < 9 ? 15 - index : 14 - index] = dark;
    }
    cells[SIZE - 8][8] = true;
    let bitIndex = 0, row = SIZE - 1, direction = -1;
    for (let column = SIZE - 1; column > 0; column -= 2) {
      if (column === 6) column--;
      while (true) {
        for (let offset = 0; offset < 2; offset++) if (cells[row][column - offset] === null) {
          const bit = bitIndex < codewords.length * 8 && Boolean(codewords[bitIndex >>> 3] >>> (7 - (bitIndex & 7)) & 1);
          cells[row][column - offset] = (row + column - offset) % 2 === 0 ? !bit : Boolean(bit); bitIndex++;
        }
        row += direction; if (row < 0 || row >= SIZE) { row -= direction; direction = -direction; break; }
      }
    }
    return cells;
  }
  function render(container, text) {
    const cells = matrix(text), ns = 'http://www.w3.org/2000/svg', svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 49 49'); svg.setAttribute('role', 'img'); svg.setAttribute('aria-label', '本次玩家邀请二维码');
    const background = document.createElementNS(ns, 'rect'); background.setAttribute('width', '49'); background.setAttribute('height', '49'); background.setAttribute('fill', '#fff'); svg.append(background);
    const path = document.createElementNS(ns, 'path'); let drawing = '';
    for (let row = 0; row < SIZE; row++) for (let column = 0; column < SIZE; column++) if (cells[row][column]) drawing += `M${column + 4} ${row + 4}h1v1h-1z`;
    path.setAttribute('d', drawing); path.setAttribute('fill', '#10251b'); path.setAttribute('shape-rendering', 'crispEdges'); svg.append(path); container.replaceChildren(svg);
  }
  window.LocalInviteQr = Object.freeze({render, matrix});
})();
