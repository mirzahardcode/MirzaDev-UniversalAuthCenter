// Test-only resolver hook. Maps a `.js` specifier to its `.ts` sibling when
// the `.js` file does not exist, so the TypeScript sources can be imported
// directly under Node's native type stripping.
export async function resolve(specifier, context, nextResolve) {
    if (specifier.endsWith(".js")) {
        try {
            return await nextResolve(specifier.replace(/\.js$/, ".ts"), context);
        } catch {
            // Fall through to the original specifier.
        }
    }
    return nextResolve(specifier, context);
}
