/** A file in public/, under the site's base path on GitHub Pages (/mi-notes in production). */
export const getAssetPath = (path: string) =>
  `${process.env.NODE_ENV === "production" ? "/mi-notes" : ""}${path}`;
