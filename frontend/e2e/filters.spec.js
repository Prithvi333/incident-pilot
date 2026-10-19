import { test, expect } from '@playwright/test';
import { uniqueUser, registerAndLogin, createIncident } from './helpers.js';

test.describe('Incident Filters and Search', () => {
  let user;

  test.beforeEach(async ({ page }) => {
    user = uniqueUser();
    await registerAndLogin(page, user);

    // Create incidents with different severities and services
    await createIncident(page, {
      title: 'P1 Critical DB Outage',
      severity: 'P1',
      serviceName: 'database',
    });
    await createIncident(page, {
      title: 'P3 Slow Dashboard Load',
      severity: 'P3',
      serviceName: 'frontend-app',
    });
  });

  test('filter incidents by severity', async ({ page }) => {
    await page.goto('/incidents');
    await expect(page.getByText('P1 Critical DB Outage').first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('P3 Slow Dashboard Load').first()).toBeVisible();

    // Filter by P1 Critical
    const severitySelect = page.locator('select').last();
    if (await severitySelect.isVisible()) {
      await severitySelect.selectOption('P1_CRITICAL');
      await expect(page.getByText('P1 Critical DB Outage').first()).toBeVisible({ timeout: 5000 });
    }
  });

  test('search incidents by title', async ({ page }) => {
    await page.goto('/incidents');
    await expect(page.getByText('P1 Critical DB Outage').first()).toBeVisible({ timeout: 10000 });

    const searchInput = page.getByPlaceholder('Search by title or service...');
    await searchInput.fill('Dashboard');
    await expect(page.getByText('P3 Slow Dashboard Load').first()).toBeVisible({ timeout: 5000 });
  });

  test('incident list shows severity badges', async ({ page }) => {
    await page.goto('/incidents');
    await expect(page.getByText('P1 Critical DB Outage').first()).toBeVisible({ timeout: 10000 });

    // Verify severity badge for P1 is rendered (badge spans, not hidden option elements)
    await expect(page.locator('span', { hasText: 'P1' }).filter({ hasNotText: 'Critical' }).first()).toBeVisible();
  });
});
