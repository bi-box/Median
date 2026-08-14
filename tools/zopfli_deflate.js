'use strict';

const fs = require('fs');
const modulePath = process.argv[2];
const input = process.argv[3];
const output = process.argv[4];
const iterations = Number(process.argv[5] || 30);
if (!modulePath || !input || !output) throw new Error('module, input and output are required');
const zopfli = require(modulePath);
zopfli.deflateAsync(fs.readFileSync(input), {numiterations: iterations}).then(data => {
  fs.writeFileSync(output, data);
});
