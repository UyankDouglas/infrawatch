FROM node:22-alpine
WORKDIR /app
COPY server.js ./
ENV NODE_ENV=production
USER node
EXPOSE 9100
CMD ["node", "server.js"]
