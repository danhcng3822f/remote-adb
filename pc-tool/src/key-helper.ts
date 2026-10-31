import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

export function getAdbKeyPath(): string {
  const homeDir = os.homedir();
  const androidDir = path.join(homeDir, '.android');
  return path.join(androidDir, 'adbkey.pub');
}

export function readAdbPublicKey(customPath?: string): string | null {
  const targetPath = customPath || getAdbKeyPath();
  try {
    if (fs.existsSync(targetPath)) {
      return fs.readFileSync(targetPath, 'utf8').trim();
    }
  } catch (e) {
    console.warn(`[KeyHelper] Could not read adbkey at ${targetPath}:`, e);
  }
  return null;
}

export function buildConnectCommand(serverHost: string, port: number): string {
  return `adb connect ${serverHost}:${port}`;
}
