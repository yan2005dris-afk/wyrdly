import type { RelayRegion } from "./domain";

export type RelayHealthStatus = "EXCELLENT" | "DEGRADED" | "OFFLINE";

export interface RelayNode {
  readonly id: string;
  readonly name: string;
  readonly region: RelayRegion;
  readonly latencyMs: number;
  readonly isConnected: boolean;
  readonly activePeers: number;
}

export interface NetworkTopologySummary {
  readonly health: RelayHealthStatus;
  readonly connectedRelaysCount: number;
  readonly totalFederatedInstances: number;
  readonly avgLatencyMs: number;
  readonly relays: readonly RelayNode[];
}
