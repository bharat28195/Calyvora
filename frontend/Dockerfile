# Frontend image — Next.js 14 standalone. Build context is the `frontend/` directory.
#
# NOTE: the /api/* -> backend proxy (next.config.mjs rewrites) is baked at BUILD time, so
# BACKEND_ORIGIN must be passed as a build arg. NEXT_PUBLIC_API_MODE is also compiled in.

# ---- build stage ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG NEXT_PUBLIC_API_MODE=live
ARG BACKEND_ORIGIN=http://backend:8080
ENV NEXT_PUBLIC_API_MODE=$NEXT_PUBLIC_API_MODE
ENV BACKEND_ORIGIN=$BACKEND_ORIGIN
RUN npm run build

# ---- run stage ----
FROM node:20-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
# BACKEND_ORIGIN also at runtime, harmless, keeps parity with the build value.
ARG BACKEND_ORIGIN=http://backend:8080
ENV BACKEND_ORIGIN=$BACKEND_ORIGIN
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
CMD ["node", "server.js"]
