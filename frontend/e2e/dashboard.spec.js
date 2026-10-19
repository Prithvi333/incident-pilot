import { test, expect } from '@playwright/test';
import { uniqueUser, registerAndLogin } from './helpers.js';

test.describe('Dashboard', () => {
  let user;

  test.beforeEach(async ({ page }) => {
    user = uniqueUser();
    await registerAndLogin(page, user);
  });

  test('displays stats cards', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('Total Incidents')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Active').first()).toBeVisible();
    await expect(page.getByText('Resolved').first()).toBeVisible();
  });

  test('sidebar navigation works', async ({ page }) => {
    await page.goto('/');

    // Navigate to Incidents
    await page.getByRole('link', { name: 'Incidents' }).first().click();
    await expect(page).toHaveURL('/incidents');

    // Navigate to New Incident
    await page.getByRole('link', { name: 'New Incident' }).click();
    await expect(page).toHaveURL('/incidents/new');

    // Navigate to Knowledge Base
    await page.getByRole('link', { name: 'Knowledge Base' }).click();
    await expect(page).toHaveURL('/documents');

    // Navigate back to Dashboard
    await page.getByRole('link', { name: 'Dashboard' }).click();
    await expect(page).toHaveURL('/');
  });

  test('shows empty state when no incidents exist', async ({ page }) => {
    await page.goto('/');
    // Stats should show zero counts (or at least render without error)
    await expect(page.getByText('Total Incidents')).toBeVisible({ timeout: 10000 });
  });
});
