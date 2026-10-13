// Test-only loader registration.
//
// Source files use explicit `.js` specifiers (required for the ESM/Vercel
// build and for `tsc` with moduleResolution "Bundler"). Node's native
// type-stripping does not rewrite a `.js` specifier to its `.ts` sibling, so
// this hook lets the test suite import the TypeScript sources directly without
// adding a build step or a runtime dependency.
import { register } from "node:module";
import { pathToFileURL } from "node:url";

register("./ts-resolve-hooks.mjs", pathToFileURL(`${import.meta.dirname}/`));
