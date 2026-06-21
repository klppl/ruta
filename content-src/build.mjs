import { build } from "esbuild";
import { existsSync } from "node:fs";

const entry = "src/content.ts";
const outfile = "../app/src/main/assets/content.js";

if (!existsSync(entry)) {
  console.error(
    `No ${entry} found. The checked-in ${outfile} is currently hand-authored.\n` +
      `Create src/content.ts (and modules) to enable bundling.`,
  );
  process.exit(1);
}

await build({
  entryPoints: [entry],
  bundle: true,
  format: "iife",
  target: ["es2017"],
  outfile,
  legalComments: "none",
  minify: false,
});

console.log(`Built ${outfile}`);
