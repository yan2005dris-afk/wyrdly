import type { FC } from "react";
import { Share2, Server, Zap, ShieldCheck, Lock } from "lucide-react";
import type { AuthGraphHeroProps } from "./AuthGraphHero.types";
import styles from "./AuthGraphHero.module.css";

export const AuthGraphHero: FC<AuthGraphHeroProps> = ({ className = "" }) => {
  return (
    <div
      className={`${styles.container} ${className}`}
      data-testid="auth-graph-hero"
    >
      {/* Brand Header */}
      <div className={styles.brandHeader}>
        <div className={styles.brandIcon}>
          <Share2 className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className={styles.brandName}>Wyrdly</h1>
          <p className={styles.brandSubtext}>Where connections weave the future</p>
        </div>
      </div>

      {/* Social Graph SVG Visualization */}
      <div className={styles.graphWrapper}>
        <svg
          className={styles.svgGraph}
          viewBox="0 0 500 200"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          data-testid="graph-svg"
        >
          {/* Edges (:SIGUE / Connect) */}
          <line
            x1="80"
            y1="100"
            x2="160"
            y2="40"
            stroke="#818cf8"
            strokeWidth="2"
            strokeDasharray="4 4"
          />
          <line
            x1="80"
            y1="100"
            x2="160"
            y2="160"
            stroke="#818cf8"
            strokeWidth="2"
          />
          <line
            x1="160"
            y1="40"
            x2="280"
            y2="90"
            stroke="#a5b4fc"
            strokeWidth="2.5"
          />
          <line
            x1="160"
            y1="160"
            x2="280"
            y2="90"
            stroke="#818cf8"
            strokeWidth="2"
          />
          <line
            x1="280"
            y1="90"
            x2="380"
            y2="30"
            stroke="#c7d2fe"
            strokeWidth="2"
            strokeDasharray="4 4"
          />
          <line
            x1="280"
            y1="90"
            x2="390"
            y2="150"
            stroke="#818cf8"
            strokeWidth="2"
          />

          {/* Nodes (:Usuario) */}
          <g transform="translate(80, 100)">
            <circle r="18" fill="#4f46e5" />
            <circle r="8" fill="#ffffff" />
          </g>
          <g transform="translate(160, 40)">
            <circle r="14" fill="#6366f1" opacity="0.9" />
          </g>
          <g transform="translate(160, 160)">
            <circle r="14" fill="#6366f1" opacity="0.9" />
          </g>
          <g transform="translate(280, 90)">
            <circle r="24" fill="#4338ca" />
            <circle r="12" fill="#ffffff" />
            <circle r="6" fill="#4f46e5" />
          </g>
          <g transform="translate(380, 30)">
            <circle r="12" fill="#818cf8" />
          </g>
          <g transform="translate(390, 150)">
            <circle r="15" fill="#ffffff" />
          </g>
        </svg>
      </div>

      {/* Copy and Feature Pills */}
      <div className={styles.heroContent}>
        <h2 className={styles.heroHeadline}>
          Your social graph,
          <br />
          <span className={styles.gradientText}>owned by no one.</span>
        </h2>
        <p className={styles.heroDescription}>
          Federated relays, portable identity, and real-time presence — a
          distributed network powered by Neo4j, RustFS, and reactive WebSockets
          that stays up even when individual nodes don't.
        </p>

        <div className={styles.featureCardsGrid}>
          <div className={styles.glassCard}>
            <Server className={styles.glassIcon} />
            <h4 className={styles.glassTitle}>Federated Relays</h4>
            <p className={styles.glassSubtitle}>Multi-region, zero SPOF</p>
          </div>
          <div className={styles.glassCard}>
            <Zap className={styles.glassIcon} />
            <h4 className={styles.glassTitle}>Real-time Sync</h4>
            <p className={styles.glassSubtitle}>WebSocket &lt; 50ms</p>
          </div>
          <div className={styles.glassCard}>
            <ShieldCheck className={styles.glassIcon} />
            <h4 className={styles.glassTitle}>You Own Data</h4>
            <p className={styles.glassSubtitle}>S3 / RustFS Blobs</p>
          </div>
        </div>
      </div>

      {/* Footer Specs */}
      <div className={styles.footerBadges}>
        <span>
          <Lock className="w-3.5 h-3.5 inline mr-1 text-indigo-400" />
          End-to-end encrypted DMs
        </span>
        <span>•</span>
        <span>Neo4j Cypher Graph Engine</span>
        <span>•</span>
        <span>Web Push VAPID</span>
      </div>
    </div>
  );
};
