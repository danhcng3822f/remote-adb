#!/usr/bin/env node
import { Command } from 'commander';
import qrcode from 'qrcode-terminal';
import { readAdbPublicKey, buildConnectCommand } from './key-helper.js';
import { execSync } from 'node:child_process';

const program = new Command();

program
  .name('adb-remote')
  .description('CLI tool for connecting to Remote ADB devices over Internet Relay')
  .version('0.1.0');

program
  .command('show-key')
  .description('Display your PC public key (adbkey.pub) and show QR code to scan on phone')
  .option('-p, --path <path>', 'Custom path to adbkey.pub')
  .action((options) => {
    const key = readAdbPublicKey(options.path);
    if (!key) {
      console.error('❌ Could not find adbkey.pub. Please ensure adb has been run at least once on this PC.');
      process.exit(1);
    }

    console.log('\n🔑 Your PC ADB Public Key:');
    console.log('----------------------------------------------------');
    console.log(key);
    console.log('----------------------------------------------------\n');
    console.log('📱 Scan this QR code in Remote ADB Android App to trust this PC automatically:');
    qrcode.generate(key, { small: true });
  });

program
  .command('connect')
  .description('Connect to a remote Android device via Relay Server')
  .requiredOption('-s, --server <host>', 'Relay Server Host (e.g., relay.example.com or 123.45.67.89)')
  .requiredOption('-p, --port <number>', 'Assigned Device Port on Relay Server', (val) => parseInt(val, 10))
  .option('--scrcpy', 'Automatically launch scrcpy after connecting')
  .action((options) => {
    const cmd = buildConnectCommand(options.server, options.port);
    console.log(`🔌 Executing: ${cmd}...`);

    try {
      const output = execSync(cmd, { encoding: 'utf8' });
      console.log(output);

      if (output.includes('connected to') || output.includes('already connected to')) {
        console.log('✅ Connected successfully to remote device!');
        if (options.scrcpy) {
          console.log('🚀 Launching scrcpy...');
          execSync(`scrcpy -s ${options.server}:${options.port}`, { stdio: 'inherit' });
        }
      }
    } catch (err: any) {
      console.error('❌ ADB Connect failed:', err.message);
      process.exit(1);
    }
  });

program.parse(process.argv);
