import { test, expect } from '@playwright/test';
import { uniqueUser, registerAndLogin, createIncident } from './helpers.js';

test.describe('Incident Management', () => {
  let user;

  test.beforeEach(async ({ page }) => {
    user = uniqueUser();
    await registerAndLogin(page, user);
  });

  test('create a new incident and view it', async ({ page }) => {
    await createIncident(page, {
      title: 'Database connection timeout',
      description: 'Users reporting slow queries in production',
      severity: 'P1',
      serviceName: 'api-gateway',
      assignee: 'alice',
    });

    // Should redirect to incident detail
    await expect(page.getByText('Database connection timeout')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('api-gateway')).toBeVisible();
    await expect(page.getByText('alice')).toBeVisible();
    await expect(page.getByText('Triggered').first()).toBeVisible();
  });

  test('create incident form validates required fields', async ({ page }) => {
    await page.goto('/incidents/new');

    // Try to submit empty form — button should be disabled or form should prevent submission
    const submitButton = page.getByRole('button', { name: 'Create Incident' });

    // Fill only title (missing serviceName)
    await page.getByPlaceholder("Brief description of what's happening").fill('Test incident');
    // Don't fill serviceName
    await submitButton.click();

    // Should still be on the create page (validation prevents submission)
    await expect(page).toHaveURL('/incidents/new', { timeout: 3000 });
  });

  test('incident list page shows created incidents', async ({ page }) => {
    // Create an incident first
    await createIncident(page, {
      title: 'List test incident',
      serviceName: 'payment-service',
    });

    // Navigate to incident list
    await page.goto('/incidents');
    await expect(page.getByText('List test incident').first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('payment-service').first()).toBeVisible();
  });

  test('status transition from TRIGGERED to ACKNOWLEDGED', async ({ page }) => {
    await createIncident(page, {
      title: 'Transition test',
      serviceName: 'auth-service',
    });

    // Should see the transition button
    await expect(page.getByRole('button', { name: 'Acknowledge' })).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: 'Acknowledge' }).click();

    // Status should update
    await expect(page.getByText('Acknowledged').first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('button', { name: 'Start Investigation' })).toBeVisible();
  });

  test('full status lifecycle TRIGGERED → ACKNOWLEDGED → INVESTIGATING → RESOLVED', async ({ page }) => {
    await createIncident(page, {
      title: 'Full lifecycle test',
      serviceName: 'billing-service',
    });

    // TRIGGERED → ACKNOWLEDGED
    await page.getByRole('button', { name: 'Acknowledge' }).click();
    await expect(page.getByText('Acknowledged').first()).toBeVisible({ timeout: 10000 });

    // ACKNOWLEDGED → INVESTIGATING
    await page.getByRole('button', { name: 'Start Investigation' }).click();
    await expect(page.getByText('Investigating').first()).toBeVisible({ timeout: 10000 });

    // INVESTIGATING → RESOLVED
    await page.getByRole('button', { name: 'Resolve' }).click();
    await expect(page.getByText('Resolved').first()).toBeVisible({ timeout: 10000 });
  });

  test('add a timeline comment to an incident', async ({ page }) => {
    await createIncident(page, {
      title: 'Timeline test incident',
      serviceName: 'data-pipeline',
    });

    // Fill in the timeline event form
    await page.getByPlaceholder('Your name').fill('bob');
    await page.getByPlaceholder('What happened? What did you do?').fill('Investigating root cause');
    await page.getByRole('button', { name: 'Add' }).click();

    // Timeline entry should appear
    await expect(page.getByText('Investigating root cause')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('bob')).toBeVisible();
  });

  test('incident detail shows back to incidents link', async ({ page }) => {
    await createIncident(page, {
      title: 'Navigation test',
      serviceName: 'nav-service',
    });

    await expect(page.getByText('Back to incidents')).toBeVisible();
    await page.getByText('Back to incidents').click();
    await expect(page).toHaveURL('/incidents');
  });
});
