import http from 'node:http';
import { WebSocketServer, WebSocket } from 'ws';
import { SessionManager, DeviceSession } from './session.js';
import { ControlMessage, RegisterAckMessage, ErrorMessage } from './types.js';

export class RelayServer {
  private httpServer: http.Server;
  private wss: WebSocketServer;
  private sessionManager: SessionManager;
  private port: number;

  constructor(port = 8080, portRangeStart = 15000, portRangeEnd = 16000) {
    this.port = port;
    this.sessionManager = new SessionManager(portRangeStart, portRangeEnd);
    this.httpServer = http.createServer((req, res) => {
      if (req.url === '/health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(
          JSON.stringify({
            status: 'ok',
            activeSessions: this.sessionManager.getAllSessions().length,
          })
        );
        return;
      }
      res.writeHead(404);
      res.end();
    });

    this.wss = new WebSocketServer({ server: this.httpServer, path: '/ws/device' });
    this.setupWebSocket();
  }

  public getSessionManager(): SessionManager {
    return this.sessionManager;
  }

  public start(): Promise<void> {
    return new Promise((resolve) => {
      this.httpServer.listen(this.port, '0.0.0.0', () => {
        console.log(`[RelayServer] HTTP & WebSocket server listening on port ${this.port}`);
        resolve();
      });
    });
  }

  public stop(): Promise<void> {
    return new Promise((resolve) => {
      for (const session of this.sessionManager.getAllSessions()) {
        this.sessionManager.closeSession(session.deviceId);
      }
      this.wss.close(() => {
        this.httpServer.close(() => {
          resolve();
        });
      });
    });
  }

  private setupWebSocket() {
    this.wss.on('connection', (ws: WebSocket) => {
      let currentSession: DeviceSession | null = null;

      ws.on('message', async (raw) => {
        try {
          const msg: ControlMessage = JSON.parse(raw.toString());

          switch (msg.type) {
            case 'register': {
              const { deviceId, pin, requestedPort } = msg;
              if (!deviceId || !pin) {
                const errAck: RegisterAckMessage = {
                  type: 'register_ack',
                  status: 'error',
                  deviceId: deviceId || '',
                  message: 'Missing deviceId or pin',
                };
                ws.send(JSON.stringify(errAck));
                return;
              }

              try {
                currentSession = await this.sessionManager.createSession(
                  deviceId,
                  pin,
                  ws,
                  requestedPort
                );
                const okAck: RegisterAckMessage = {
                  type: 'register_ack',
                  status: 'ok',
                  deviceId,
                  assignedPort: currentSession.assignedPort,
                };
                ws.send(JSON.stringify(okAck));
                console.log(
                  `[RelayServer] Registered device ${deviceId} -> dynamic TCP port ${currentSession.assignedPort}`
                );
              } catch (err: any) {
                const errAck: RegisterAckMessage = {
                  type: 'register_ack',
                  status: 'error',
                  deviceId,
                  message: err.message || 'Failed to allocate dynamic port',
                };
                ws.send(JSON.stringify(errAck));
              }
              break;
            }

            case 'stream_data': {
              if (currentSession) {
                this.sessionManager.handleDeviceData(
                  currentSession,
                  msg.streamId,
                  msg.data
                );
              }
              break;
            }

            case 'stream_close': {
              if (currentSession) {
                this.sessionManager.closeStream(currentSession, msg.streamId);
              }
              break;
            }

            case 'ping': {
              if (currentSession) {
                currentSession.lastPingAt = Date.now();
              }
              ws.send(JSON.stringify({ type: 'pong', timestamp: Date.now() }));
              break;
            }

            default:
              console.warn('[RelayServer] Unknown message type:', (msg as any).type);
          }
        } catch (e: any) {
          console.error('[RelayServer] Error processing WS message:', e.message);
          const errMsg: ErrorMessage = {
            type: 'error',
            code: 'INVALID_JSON',
            message: 'Invalid JSON payload received',
          };
          ws.send(JSON.stringify(errMsg));
        }
      });

      ws.on('close', () => {
        if (currentSession) {
          console.log(
            `[RelayServer] Device ${currentSession.deviceId} disconnected. Cleaning up port ${currentSession.assignedPort}`
          );
          this.sessionManager.closeSession(currentSession.deviceId);
          currentSession = null;
        }
      });

      ws.on('error', (err) => {
        console.warn('[RelayServer] WebSocket connection error:', err.message);
      });
    });
  }
}

// Auto-start if run directly
if (process.argv[1] && process.argv[1].endsWith('server.js')) {
  const PORT = parseInt(process.env.PORT || '8080', 10);
  const START_PORT = parseInt(process.env.TCP_RANGE_START || '15000', 10);
  const END_PORT = parseInt(process.env.TCP_RANGE_END || '16000', 10);

  const server = new RelayServer(PORT, START_PORT, END_PORT);
  server.start();

  process.on('SIGINT', async () => {
    console.log('\n[RelayServer] Gracefully shutting down...');
    await server.stop();
    process.exit(0);
  });
}
