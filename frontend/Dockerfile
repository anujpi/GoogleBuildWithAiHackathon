# Static React build served by nginx. The API URL is baked in at build time:
#   docker build --build-arg VITE_API_BASE_URL=https://<backend>/api -t agri-frontend frontend/
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_API_BASE_URL=http://localhost:8080/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.29-alpine
COPY --from=build /app/dist /usr/share/nginx/html
# SPA routing: unknown paths serve index.html. Cloud Run sets PORT (default 8080).
RUN printf 'server {\n  listen ${PORT};\n  root /usr/share/nginx/html;\n  location / { try_files $uri /index.html; }\n}\n' > /etc/nginx/templates/default.conf.template
ENV PORT=8080
EXPOSE 8080
