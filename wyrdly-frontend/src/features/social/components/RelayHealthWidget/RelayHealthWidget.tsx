import type { FC } from "react";
import { Activity } from "lucide-react";
import type { RelayHealthWidgetProps } from "./RelayHealthWidget.types";
import styles from "./RelayHealthWidget.module.css";

const SEGMENT_CLASSES: Record<"excellent" | "good" | "fair" | "idle", string> =
  {
    excellent: styles.segmentExcellent,
    good: styles.segmentGood,
    fair: styles.segmentFair,
    idle: styles.segmentIdle,
  };

export const RelayHealthWidget: FC<RelayHealthWidgetProps> = ({
  health = "EXCELLENT",
  federatedInstancesCount = 14,
  regionsSummary = "EU & APAC",
  latencySegments = [
    "excellent",
    "excellent",
    "excellent",
    "excellent",
    "idle",
  ],
  className = "",
}) => {
  const healthLabel = health.charAt(0) + health.slice(1).toLowerCase();

  return (
    <div
      className={`${styles.card} ${className}`}
      data-testid="relay-health-widget"
    >
      <div className={styles.header}>
        <span>Relay health: {healthLabel}</span>
        <Activity
          className={styles.pulseIcon}
          data-testid="relay-health-icon"
        />
      </div>

      <p className={styles.description}>
        You are federating with {federatedInstancesCount} instances across{" "}
        {regionsSummary}.
      </p>

      <div className={styles.segmentsRow} data-testid="relay-latency-segments">
        {latencySegments.map((segment, idx) => (
          <div
            key={idx}
            className={`${styles.segment} ${SEGMENT_CLASSES[segment]}`}
            data-testid={`latency-segment-${idx}`}
          />
        ))}
      </div>
    </div>
  );
};
