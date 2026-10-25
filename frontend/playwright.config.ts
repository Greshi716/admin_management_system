import { defineConfig, devices } from '@playwright/test';

const isWindows = process.platform === 'win32';
const backendPort = 8080;
const uiPort = 4200;

/**
 * Starts the backend with the `test` profile (which wipes and rebuilds `admin_management_test` on startup, see
 * TestDatabaseReset) and the Angular dev server, which proxies /api to the backend.
 *
 * Needs the local PostgreSQL from `docker compose -f deploy/docker-compose.yml up -d` with the
 * `admin_management_test` database, and JAVA_HOME pointing at a Java 21 JDK.
 */
export default defineConfig({
  testDir: './e2e',
  // The first run after a cold start waits for the dev server to compile the lazy-loaded screens.
  expect: { timeout: 15_000 },
  // One database, one admin: specs share state, so they run one at a time.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${uiPort}`,
    trace: 'retain-on-failure',
    locale: 'en-IN',
    timezoneId: 'Asia/Kolkata',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `${isWindows ? '.\\mvnw.cmd' : './mvnw'} spring-boot:run -Dspring-boot.run.profiles=test`,
      cwd: '../management',
      // Any answer counts as up; before sign-in exists the API answers 401, which Playwright accepts.
      url: `http://localhost:${backendPort}/api/auth/me`,
      // Never reuse a running backend: it may be the dev server on the real database, and the test run
      // must start from an empty test database.
      reuseExistingServer: false,
      timeout: 240_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      command: 'npm start',
      url: `http://localhost:${uiPort}`,
      reuseExistingServer: !process.env['CI'],
      timeout: 180_000,
    },
  ],
});
