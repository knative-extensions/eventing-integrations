FROM registry.access.redhat.com/ubi9/nodejs-20 AS builder

# Set working directory
WORKDIR /app

# Copy package.json and package-lock.json before installing dependencies
# Ensure correct permissions by using --chown flag
COPY --chown=1001:0 package.json package-lock.json ./

# Install dependencies as non-root user
USER 1001
RUN npm install --frozen-lockfile

# Copy the rest of the application files
COPY --chown=1001:0 . .

# Use a minimal base image for runtime
FROM registry.access.redhat.com/ubi9/nodejs-20

# Set working directory
WORKDIR /app

# Copy built files and dependencies
COPY --from=builder --chown=1001:0 /app /app

# Switch to non-root user
USER 1001

ENV NODE_ENV=production

# Expose the application port
EXPOSE 8080

# Set the default command
CMD ["node", "jsonata.js"]
