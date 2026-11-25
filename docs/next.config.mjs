/** @type {import('next').NextConfig} */
const isProd = process.env.NODE_ENV === "production";

const nextConfig = {
  output: "export",
  basePath: isProd ? "/mi-notes" : "", // served from mi-app-hub.github.io/mi-notes
  trailingSlash: true,
  images: {
    unoptimized: true,
  },
};

export default nextConfig;
