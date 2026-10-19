import { expect } from '@playwright/test';

let userCounter = 0;

export function uniqueUser() {
  userCounter++;
  const ts = Date.now();
  return {
    username: `e2euser${ts}${userCounter}`,
    password: 'TestPass123!',
    email: `e2e${ts}${userCounter}@test.com`,
  };
}

export async function registerAndLogin(page, user) {
  await page.goto('/register');
  await page.getByPlaceholder('Choose a username').fill(user.username);
  await page.getByPlaceholder('At least 8 characters').fill(user.password);
  await page.getByPlaceholder('you@company.com').fill(user.email);
  await page.getByRole('button', { name: 'Create Account' }).click();
  await expect(page).toHaveURL('/', { timeout: 10000 });
}

export async function loginUser(page, user) {
  await page.goto('/login');
  await page.getByPlaceholder('Enter your username').fill(user.username);
  await page.getByPlaceholder('Enter your password').fill(user.password);
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page).toHaveURL('/', { timeout: 10000 });
}

export async function createIncident(page, { title, description, severity, serviceName, assignee }) {
  await page.goto('/incidents/new');
  await page.getByPlaceholder("Brief description of what's happening").fill(title);
  if (description) {
    await page.getByPlaceholder('Detailed information, error messages').fill(description);
  }
  if (severity) {
    const severityMap = {
      'P1': 'P1 — Critical',
      'P2': 'P2 — High',
      'P3': 'P3 — Medium',
      'P4': 'P4 — Low',
    };
    const label = severityMap[severity] || severity;
    await page.getByRole('button', { name: label }).click();
  }
  await page.getByPlaceholder('e.g. payment-service, api-gateway').fill(serviceName);
  if (assignee) {
    await page.getByPlaceholder("Who's handling this?").fill(assignee);
  }
  await page.getByRole('button', { name: 'Create Incident' }).click();
  await expect(page).toHaveURL(/\/incidents\/\d+/, { timeout: 10000 });
}
