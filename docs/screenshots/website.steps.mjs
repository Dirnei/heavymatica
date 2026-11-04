// The player website's screenshots. The script signs the browser in as the documentation player first; each step
// brings the page into shape, then the script saves website-<name>.png.
export const WEBSITE_STEPS = [
  {
    name: 'library',
    run: async (page, { cloud }) => {
      await page.goto(`${cloud}/library/`);
      await page.waitForLoadState('networkidle');
    },
  },
  {
    name: 'details',
    run: async (page) => {
      await page.getByText('Forest cottage').first().click();
      await page.waitForLoadState('networkidle');
    },
    wait: 2500,
  },
  {
    name: 'upload',
    run: async (page, { cloud }) => {
      await page.goto(`${cloud}/upload/`);
      await page.waitForLoadState('networkidle');
    },
  },
];
