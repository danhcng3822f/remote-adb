import { describe, it, expect } from 'vitest';
import { buildConnectCommand, readAdbPublicKey } from '../src/key-helper.js';

describe('KeyHelper & Command Builder', () => {
  it('should build adb connect command correctly', () => {
    const cmd = buildConnectCommand('relay.mydomain.com', 15001);
    expect(cmd).toBe('adb connect relay.mydomain.com:15001');
  });

  it('should handle null/missing adbkey gracefully without throwing', () => {
    const nonExistentPath = '/path/to/non/existent/adbkey.pub';
    const result = readAdbPublicKey(nonExistentPath);
    expect(result).toBeNull();
  });
});
