FROM registry.access.redhat.com/ubi9/nodejs-20 AS builder

# Set working directory
WORKDIR /app

# Switch to non-root user
USER 1001

# Install pnpm globally
RUN npm install -g pnpm

# Copy package.json and pnpm-lock.yaml before installing dependencies
COPY package.json pnpm-lock.yaml ./

# Install dependencies using pnpm
RUN pnpm install --frozen-lockfile

# Copy the rest of the application files
COPY . .

# Build the application (if necessary)
RUN pnpm build || echo "No build step found, skipping"

# Use a minimal base image for runtime
FROM registry.access.redhat.com/ubi9/nodejs-20

ENV NODE_ENV=production

# Set working directory
WORKDIR /app

# Switch to non-root user
USER 1001

# Copy built files and dependencies
COPY --from=builder /app /app

# Expose the application port
EXPOSE 8080

# Set the default command
CMD ["node", "jsonata.js"]
