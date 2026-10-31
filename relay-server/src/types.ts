export type MessageType =
  | 'register'
  | 'register_ack'
  | 'stream_open'
  | 'stream_data'
  | 'stream_close'
  | 'ping'
  | 'pong'
  | 'error';

export interface RegisterMessage {
  type: 'register';
  deviceId: string;
  pin: string;
  requestedPort?: number;
}

export interface RegisterAckMessage {
  type: 'register_ack';
  status: 'ok' | 'error';
  deviceId: string;
  assignedPort?: number;
  message?: string;
}

export interface StreamOpenMessage {
  type: 'stream_open';
  streamId: string;
}

export interface StreamDataMessage {
  type: 'stream_data';
  streamId: string;
  data: string; // Base64 encoded payload
}

export interface StreamCloseMessage {
  type: 'stream_close';
  streamId: string;
  reason?: string;
}

export interface PingMessage {
  type: 'ping';
  timestamp: number;
}

export interface PongMessage {
  type: 'pong';
  timestamp: number;
}

export interface ErrorMessage {
  type: 'error';
  code: string;
  message: string;
}

export type ControlMessage =
  | RegisterMessage
  | RegisterAckMessage
  | StreamOpenMessage
  | StreamDataMessage
  | StreamCloseMessage
  | PingMessage
  | PongMessage
  | ErrorMessage;
