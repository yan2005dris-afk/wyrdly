import type { RelayHealthStatus } from "../../../types/relay";

export interface RelayHealthWidgetProps {
  readonly health?: RelayHealthStatus;
  readonly federatedInstancesCount?: number;
  readonly regionsSummary?: string;
  readonly latencySegments?: readonly (
    "excellent" | "good" | "fair" | "idle"
  )[];
  readonly className?: string;
}
