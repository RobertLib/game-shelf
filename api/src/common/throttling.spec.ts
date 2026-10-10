import { accountTracker } from './throttling.js';

describe('accountTracker', () => {
  it('keys sign-ins by the normalised e-mail', () => {
    expect(
      accountTracker({ body: { email: ' Rob@Example.COM ' }, ip: '1.2.3.4' }),
    ).toBe('account:rob@example.com');
  });

  it('ignores U+0000, which the pipes strip before the e-mail is checked', () => {
    expect(
      accountTracker({ body: { email: 'r\u0000ob@example.com\u0000' } }),
    ).toBe('account:rob@example.com');
  });

  it('falls back to the IP address without an e-mail', () => {
    expect(accountTracker({ body: {}, ip: '1.2.3.4' })).toBe('ip:1.2.3.4');
    expect(accountTracker({ body: { email: 42 }, ip: '1.2.3.4' })).toBe(
      'ip:1.2.3.4',
    );
  });
});
