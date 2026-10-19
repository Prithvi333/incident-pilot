import { test, expect } from '@playwright/test';
import { uniqueUser, registerAndLogin } from './helpers.js';

test.describe('Knowledge Base / Document Upload', () => {
  let user;

  test.beforeEach(async ({ page }) => {
    user = uniqueUser();
    await registerAndLogin(page, user);
  });

  test('knowledge base page renders with upload area', async ({ page }) => {
    await page.goto('/documents');
    await expect(page.getByRole('heading', { name: 'Knowledge Base' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/drop a file/i)).toBeVisible();
    await expect(page.getByText(/\.txt and \.md/i)).toBeVisible();
  });

  test('knowledge base shows empty state when no documents', async ({ page }) => {
    await page.goto('/documents');
    await expect(page.getByText(/no documents yet/i)).toBeVisible({ timeout: 10000 });
  });

  test('upload area shows indexed documents section', async ({ page }) => {
    await page.goto('/documents');
    await expect(page.getByText('Indexed Documents')).toBeVisible({ timeout: 10000 });
  });
});
