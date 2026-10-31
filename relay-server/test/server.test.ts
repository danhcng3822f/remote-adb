import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { RelayServer } from '../src/server.js';
import { WebSocket } from 'ws';
import net from 'node:net';

describe('RelayServer', () => {
  let server: RelayServer;
  const SERVER_PORT = 9090;
  const TCP_START = 17000;
  const TCP_END = 17010;

  beforeAll(async () => {
    server = new RelayServer(SERVER_PORT, TCP_START, TCP_END);
    await server.start();
  });

  afterAll(async () => {
    await server.stop();
  });

  it('should register device and assign dynamic TCP port', async () => {
    const ws = new WebSocket(`ws://127.0.0.1:${SERVER_PORT}/ws/device`);

    await new Promise<void>((resolve, reject) => {
      ws.on('open', () => {
        ws.send(
          JSON.stringify({
            type: 'register',
            deviceId: 'test-device-01',
            pin: '123456',
          })
        );
      });

      ws.on('message', (raw) => {
        const msg = JSON.parse(raw.toString());
        expect(msg.type).toBe('register_ack');
        expect(msg.status).toBe('ok');
        expect(msg.deviceId).toBe('test-device-01');
        expect(msg.assignedPort).toBeGreaterThanOrEqual(TCP_START);
        expect(msg.assignedPort).toBeLessThanOrEqual(TCP_END);
        ws.close();
        resolve();
      });

      ws.on('error', reject);
    });
  });

  it('should forward data between TCP client and device WebSocket stream', async () => {
    const ws = new WebSocket(`ws://127.0.0.1:${SERVER_PORT}/ws/device`);
    let assignedPort: number;

    await new Promise<void>((resolve) => {
      ws.on('open', () => {
        ws.send(
          JSON.stringify({
            type: 'register',
            deviceId: 'test-device-02',
            pin: '654321',
          })
        );
      });

      ws.on('message', (raw) => {
        const msg = JSON.parse(raw.toString());
        if (msg.type === 'register_ack') {
          assignedPort = msg.assignedPort;
          resolve();
        }
      });
    });

    let currentStreamId = '';

    // Set up message handler for streams
    const streamOpenPromise = new Promise<void>((resolve) => {
      ws.on('message', (raw) => {
        const msg = JSON.parse(raw.toString());
        if (msg.type === 'stream_open') {
          currentStreamId = msg.streamId;
          resolve();
        }
      });
    });

    // Connect a fake TCP client (like adb client)
    const client = net.createConnection({ port: assignedPort!, host: '127.0.0.1' });

    await streamOpenPromise;
    expect(currentStreamId).toBeDefined();

    // Test TCP client -> Device WS forwarding
    const receivedFromTcpPromise = new Promise<string>((resolve) => {
      ws.on('message', (raw) => {
        const msg = JSON.parse(raw.toString());
        if (msg.type === 'stream_data') {
          resolve(Buffer.from(msg.data, 'base64').toString('utf8'));
        }
      });
    });

    client.write('HELLO_ADB');
    const receivedText = await receivedFromTcpPromise;
    expect(receivedText).toBe('HELLO_ADB');

    // Test Device WS -> TCP client response forwarding
    const receivedFromWsPromise = new Promise<string>((resolve) => {
      client.on('data', (chunk) => {
        resolve(chunk.toString('utf8'));
      });
    });

    ws.send(
      JSON.stringify({
        type: 'stream_data',
        streamId: currentStreamId,
        data: Buffer.from('OKAY_ADB_RESPONSE').toString('base64'),
      })
    );

    const clientReceived = await receivedFromWsPromise;
    expect(clientReceived).toBe('OKAY_ADB_RESPONSE');

    client.destroy();
    ws.close();
  });

  it('should preserve integrity and ordering of a large transfer (adb push simulation)', async () => {
    const ws = new WebSocket(`ws://127.0.0.1:${SERVER_PORT}/ws/device`);
    let assignedPort: number;

    // Register
    await new Promise<void>((resolve) => {
      ws.on('open', () => {
        ws.send(
          JSON.stringify({ type: 'register', deviceId: 'test-device-03', pin: '111111' })
        );
      });
      ws.on('message', (raw) => {
        const msg = JSON.parse(raw.toString());
        if (msg.type === 'register_ack') {
          assignedPort = msg.assignedPort;
          resolve();
        }
      });
    });

    // Wait for stream_open
    let streamId = '';
    const received: Buffer[] = [];
    const client = net.createConnection({ port: assignedPort!, host: '127.0.0.1' });
    client.on('data', (chunk) => received.push(chunk));
    await new Promise<void>((resolve) => {
      const onMsg = (raw: any) => {
        const msg = JSON.parse(raw.toString());
        if (msg.type === 'stream_open') {
          streamId = msg.streamId;
          ws.off('message', onMsg);
          resolve();
        }
      };
      ws.on('message', onMsg);
    });

    // Push a 1 MiB transfer in 64 KiB chunks.
    const totalBytes = 1024 * 1024;
    const chunkSize = 64 * 1024;
    let seed = 12345;
    const nextByte = () => {
      seed = (seed * 1103515245 + 12345) & 0x7fffffff;
      return seed & 0xff;
    };

    const sendPromises: Promise<void>[] = [];
    for (let offset = 0; offset < totalBytes; offset += chunkSize) {
      const size = Math.min(chunkSize, totalBytes - offset);
      const buf = Buffer.alloc(size);
      for (let i = 0; i < size; i++) buf[i] = nextByte();
      sendPromises.push(
        new Promise<void>((resolve) =>
          ws.send(
            JSON.stringify({
              type: 'stream_data',
              streamId,
              data: buf.toString('base64'),
            }),
            () => resolve()
          )
        )
      );
    }
    await Promise.all(sendPromises);

    // Wait until the TCP client has received exactly totalBytes, or give up.
    const deadline = Date.now() + 8000;
    while (received.reduce((n, b) => n + b.length, 0) < totalBytes && Date.now() < deadline) {
      await new Promise((r) => setTimeout(r, 25));
    }

    client.end();
    await new Promise<void>((resolve) => client.on('end', () => resolve()));

    const assembled = Buffer.concat(received);
    expect(assembled.length).toBe(totalBytes);

    // Verify integrity matches the sequence we generated.
    seed = 12345;
    for (let i = 0; i < totalBytes; i++) {
      const expected = nextByte();
      if (assembled[i] !== expected) {
        throw new Error(`Integrity mismatch at byte ${i}: got ${assembled[i]}, expected ${expected}`);
      }
    }

    ws.close();
  }, 15000);
});
