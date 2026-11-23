FROM nginxinc/nginx-unprivileged:1.28-alpine
ENV PORT=8080 \
    GATEWAY_URL=http://api-gateway:8080 \
    NGINX_ENVSUBST_FILTER="^(PORT|GATEWAY_URL)$"
COPY frontend/default.conf.template /etc/nginx/templates/default.conf.template
COPY frontend/public/ /usr/share/nginx/html/
EXPOSE 8080
