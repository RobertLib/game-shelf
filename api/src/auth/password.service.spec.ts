import { PasswordService } from './password.service.js';

describe('PasswordService', () => {
  const passwords = new PasswordService();

  it('verifies the original password only', async () => {
    const hash = await passwords.hash('correct horse');
    expect(hash).toMatch(/^scrypt\$32768\$8\$3\$/);
    await expect(passwords.verify('correct horse', hash)).resolves.toBe(true);
    await expect(passwords.verify('wrong horse', hash)).resolves.toBe(false);
  });

  it('salts every hash', async () => {
    expect(await passwords.hash('same')).not.toBe(await passwords.hash('same'));
  });

  it('rejects malformed hashes', async () => {
    await expect(passwords.verify('x', 'bcrypt$whatever')).resolves.toBe(false);
  });
});
