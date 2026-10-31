import net from 'node:net';
import { WebSocket } from 'ws';
import { ControlMessage, StreamDataMessage } from './types.js';

/** Pause the TCP source once the WebSocket has this many bytes queued. */
const WS_HIGH_WATER_MARK = 4 * 1024 * 1024;
/** Resume the TCP source once the queue drains below this. */
const WS_LOW_WATER_MARK = 1 * 1024 * 1024;
/** How often to re-check the WebSocket queue while paused. */
const DRAIN_POLL_INTERVAL_MS = 20;

export interface DeviceSession {
  deviceId: string;
  pin: string;
  ws: WebSocket;
  assignedPort: number;
  tcpServer: net.Server;
  activeSockets: Map<string, net.Socket>;
  createdAt: number;
  lastPingAt: number;
}

export class SessionManager {
  private sessions = new Map<string, DeviceSession>();
  private portToDeviceId = new Map<number, string>();
  private portRangeStart: number;
  private portRangeEnd: number;

  constructor(portRangeStart = 15000, portRangeEnd = 16000) {
    this.portRangeStart = portRangeStart;
    this.portRangeEnd = portRangeEnd;
  }

  public getSession(deviceId: string): DeviceSession | undefined {
    return this.sessions.get(deviceId);
  }

  public getSessionByPort(port: number): DeviceSession | undefined {
    const deviceId = this.portToDeviceId.get(port);
    return deviceId ? this.sessions.get(deviceId) : undefined;
  }

  public getAllSessions(): DeviceSession[] {
    return Array.from(this.sessions.values());
  }

  public async createSession(
    deviceId: string,
    pin: string,
    ws: WebSocket,
    requestedPort?: number
  ): Promise<DeviceSession> {
    // If an existing session exists for this device, remove it first
    if (this.sessions.has(deviceId)) {
      this.closeSession(deviceId);
    }

    const assignedPort = await this.allocatePort(requestedPort);
    const activeSockets = new Map<string, net.Socket>();

    const tcpServer = net.createServer((socket) => {
      const streamId = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
      activeSockets.set(streamId, socket);
      socket.setNoDelay(true);

      // Notify device about new incoming stream
      this.sendMessage(ws, {
        type: 'stream_open',
        streamId,
      });

      socket.on('data', (chunk) => {
        if (ws.readyState !== WebSocket.OPEN) return;

        const msg: StreamDataMessage = {
          type: 'stream_data',
          streamId,
          data: chunk.toString('base64'),
        };
        this.sendMessage(ws, msg);

        // Backpressure: large transfers (adb push/install) can outrun the
        // WebSocket. Without this the relay buffers the whole payload in RAM
        // and the transfer stalls or dies. Pause the TCP source until the
        // socket drains below the low-water mark.
        if (socket.readable && !socket.isPaused() && ws.bufferedAmount > WS_HIGH_WATER_MARK) {
          socket.pause();
          const resume = () => {
            if (socket.destroyed) return;
            if (ws.readyState !== WebSocket.OPEN) {
              socket.destroy();
              return;
            }
            if (ws.bufferedAmount <= WS_LOW_WATER_MARK) {
              socket.resume();
            } else {
              setTimeout(resume, DRAIN_POLL_INTERVAL_MS);
            }
          };
          setTimeout(resume, DRAIN_POLL_INTERVAL_MS);
        }
      });

      socket.on('close', () => {
        activeSockets.delete(streamId);
        if (ws.readyState === WebSocket.OPEN) {
          this.sendMessage(ws, {
            type: 'stream_close',
            streamId,
            reason: 'tcp_closed_by_client',
          });
        }
      });

      socket.on('error', (err) => {
        console.warn(`[TCP] Socket error on stream ${streamId}:`, err.message);
        socket.destroy();
      });
    });

    await new Promise<void>((resolve, reject) => {
      tcpServer.listen(assignedPort, '0.0.0.0', () => {
        resolve();
      });
      tcpServer.on('error', (err) => {
        reject(err);
      });
    });

    const session: DeviceSession = {
      deviceId,
      pin,
      ws,
      assignedPort,
      tcpServer,
      activeSockets,
      createdAt: Date.now(),
      lastPingAt: Date.now(),
    };

    this.sessions.set(deviceId, session);
    this.portToDeviceId.set(assignedPort, deviceId);

    return session;
  }

  public handleDeviceData(session: DeviceSession, streamId: string, base64Data: string) {
    const socket = session.activeSockets.get(streamId);
    if (socket && !socket.destroyed) {
      const buffer = Buffer.from(base64Data, 'base64');
      socket.write(buffer);
    }
  }

  /** Aggregate bytes still queued on any client TCP socket for this session. */
  public getPendingTcpBytes(session: DeviceSession): number {
    let total = 0;
    for (const socket of session.activeSockets.values()) {
      total += socket.writableLength;
    }
    return total;
  }

  public closeStream(session: DeviceSession, streamId: string) {
    const socket = session.activeSockets.get(streamId);
    if (socket) {
      socket.end();
      session.activeSockets.delete(streamId);
    }
  }

  public closeSession(deviceId: string) {
    const session = this.sessions.get(deviceId);
    if (!session) return;

    // Close all client TCP sockets
    for (const socket of session.activeSockets.values()) {
      socket.destroy();
    }
    session.activeSockets.clear();

    // Close the dynamic TCP server
    session.tcpServer.close();

    // Release port
    this.portToDeviceId.delete(session.assignedPort);
    this.sessions.delete(deviceId);
  }

  private sendMessage(ws: WebSocket, msg: ControlMessage) {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(msg));
    }
  }

  private async allocatePort(requestedPort?: number): Promise<number> {
    if (
      requestedPort &&
      requestedPort >= this.portRangeStart &&
      requestedPort <= this.portRangeEnd &&
      !this.portToDeviceId.has(requestedPort)
    ) {
      const isFree = await this.checkPortAvailable(requestedPort);
      if (isFree) return requestedPort;
    }

    for (let port = this.portRangeStart; port <= this.portRangeEnd; port++) {
      if (!this.portToDeviceId.has(port)) {
        const isFree = await this.checkPortAvailable(port);
        if (isFree) return port;
      }
    }

    throw new Error('No available port in the allocated range');
  }

  private checkPortAvailable(port: number): Promise<boolean> {
    return new Promise((resolve) => {
      const tester = net.createServer();
      tester.once('error', () => {
        resolve(false);
      });
      tester.once('listening', () => {
        tester.close(() => resolve(true));
      });
      tester.listen(port, '0.0.0.0');
    });
  }
}
