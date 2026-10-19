import { test, expect } from '@playwright/test';
import { uniqueUser, registerAndLogin, loginUser } from './helpers.js';

test.describe('Authentication', () => {
  test('register a new user and redirect to dashboard', async ({ page }) => {
    const user = uniqueUser();
    await registerAndLogin(page, user);

    await expect(page.getByRole('link', { name: 'Dashboard', exact: true })).toBeVisible();
    await expect(page.getByText(user.username).last()).toBeVisible();
  });

  test('login with registered user', async ({ page }) => {
    const user = uniqueUser();
    await registerAndLogin(page, user);

    // Logout — use first() since mobile + desktop sidebar both render the button
    await page.getByRole('button', { name: 'Sign out' }).last().click();
    await expect(page).toHaveURL('/login', { timeout: 5000 });

    // Login again
    await loginUser(page, user);
    await expect(page.getByRole('link', { name: 'Dashboard', exact: true })).toBeVisible();
  });

  test('login with wrong password shows error', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('Enter your username').fill('nonexistent');
    await page.getByPlaceholder('Enter your password').fill('wrongpass123');
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page.getByText(/failed|invalid|credentials/i)).toBeVisible({ timeout: 5000 });
  });

  test('register with duplicate username shows error', async ({ page }) => {
    const user = uniqueUser();
    await registerAndLogin(page, user);

    // Logout and try to register with same username
    await page.getByRole('button', { name: 'Sign out' }).last().click();
    await page.goto('/register');
    await page.getByPlaceholder('Choose a username').fill(user.username);
    await page.getByPlaceholder('At least 8 characters').fill('AnotherPass123!');
    await page.getByPlaceholder('you@company.com').fill('other@test.com');
    await page.getByRole('button', { name: 'Create Account' }).click();

    await expect(page.getByText(/already|taken|duplicate|exists/i)).toBeVisible({ timeout: 5000 });
  });

  test('unauthenticated user is redirected to login', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL('/login', { timeout: 5000 });
  });

  test('register page has link to login page', async ({ page }) => {
    await page.goto('/register');
    await expect(page.getByText('Sign in')).toBeVisible();
    await page.getByText('Sign in').click();
    await expect(page).toHaveURL('/login');
  });

  test('login page has link to register page', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByText('Create one')).toBeVisible();
    await page.getByText('Create one').click();
    await expect(page).toHaveURL('/register');
  });
});
