// Generates logo.svg: a controller and two drawers that touch (glowing seams), and one drawer set
// apart that stays dark. Isometric, transparent background.
// node logo/logo.mjs  ->  logo/logo.svg
import { writeFileSync } from 'node:fs';

const A = 62;                                   // half the width of a block's top
const X = [A, A / 2], Y = [-A, A / 2], Z = [0, -A];
const O = [0, 0];                               // grid origin; the viewBox is fitted afterwards
const p = (i, j, k) => [O[0] + i * X[0] + j * Y[0] + k * Z[0], O[1] + i * X[1] + j * Y[1] + k * Z[1]];
const pts = list => list.map(q => q.map(n => n.toFixed(1)).join(',')).join(' ');
const poly = (list, fill) => `<polygon points="${pts(list)}" fill="${fill}"/>`;
const top = (i, fill) => poly([p(i, 0, 1), p(i + 1, 0, 1), p(i + 1, 1, 1), p(i, 1, 1)], fill);
const side = (i, fill) => poly([p(i + 1, 0, 0), p(i + 1, 1, 0), p(i + 1, 1, 1), p(i + 1, 0, 1)], fill);
// A 100x100 square mapped onto the front face (+y side): u along X, v downwards.
const front = (i, body) => {
    const o = p(i, 1, 1);
    return `<g transform="matrix(${X[0] / 100},${X[1] / 100},0,${A / 100},${o[0].toFixed(1)},${o[1].toFixed(1)})">${body}</g>`;
};

const DIAMOND = `<polygon points="50,28 70,48 50,72 30,48" fill="#5FE3E0"/><polygon points="50,28 70,48 30,48" fill="#B4F7F4"/>`;

function drawer(i, lit) {
    const c = (r, g, b) => lit ? `rgb(${r},${g},${b})` : `rgb(${Math.round(r * .5)},${Math.round(g * .5)},${Math.round(b * .5)})`;
    return top(i, c(204, 160, 102)) + side(i, c(122, 88, 50))
        + front(i, `<rect width="100" height="100" fill="${c(172, 128, 76)}"/>`
            + `<rect x="12" y="12" width="76" height="76" fill="${c(112, 80, 44)}"/>`
            + `<g opacity="${lit ? 1 : 0.35}">${DIAMOND}</g>`);
}

function controller(i) {
    return top(i, '#BAC1C9') + side(i, '#5C646D')
        + front(i, `<rect width="100" height="100" fill="#8F979F"/>`
            + `<rect x="12" y="12" width="76" height="76" fill="#262C33"/>`
            + `<circle cx="50" cy="50" r="18" fill="none" stroke="#5FE3D2" stroke-width="9"/>`);
}

const seam = (i) => {
    const [a, b] = [p(i, 1, 0.14), p(i, 1, 0.86)];
    return `<line x1="${a[0].toFixed(1)}" y1="${a[1].toFixed(1)}" x2="${b[0].toFixed(1)}" y2="${b[1].toFixed(1)}" stroke="#5FE3D2" stroke-width="7" stroke-linecap="round" filter="url(#glow)"/>`;
};

// Square viewBox around every corner of the four blocks, with a margin for the outline and glow.
const corners = [0, 1, 2, 4.6].flatMap(i => [0, 1].flatMap(dx => [0, 1].flatMap(dy => [0, 1].map(dz => p(i + dx, dy, dz)))));
const [minX, maxX] = [Math.min(...corners.map(c => c[0])), Math.max(...corners.map(c => c[0]))];
const [minY, maxY] = [Math.min(...corners.map(c => c[1])), Math.max(...corners.map(c => c[1]))];
const size = Math.max(maxX - minX, maxY - minY) + 12;
const vb = [((minX + maxX - size) / 2).toFixed(1), ((minY + maxY - size) / 2).toFixed(1), size.toFixed(1)];

// drawer | controller | drawer, then a gap wide enough to read in isometric, then a drawer that does not touch.
const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb.join(" ")} ${vb[2]}" width="512" height="512">
<defs><filter id="glow" filterUnits="userSpaceOnUse" x="${vb[0]}" y="${vb[1]}" width="${vb[2]}" height="${vb[2]}"><feGaussianBlur stdDeviation="4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>
<g stroke="#14191E" stroke-width="2.5" stroke-linejoin="round">${drawer(0, true)}${controller(1)}${drawer(2, true)}${drawer(4.6, false)}</g>
${seam(1)}${seam(2)}
</svg>`;
writeFileSync(new URL('./logo.svg', import.meta.url), svg);
