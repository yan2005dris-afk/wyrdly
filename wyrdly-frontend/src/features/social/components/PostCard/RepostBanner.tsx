import type { FC } from "react";
import { Link } from "react-router-dom";
import { Repeat } from "lucide-react";
import type { RepostContext } from "../../../../types/feed";
import styles from "./PostCard.module.css";

export interface RepostBannerProps {
  readonly context: RepostContext;
}

/**
 * Discreet header shown above a post that appears in a timeline because
 * someone shared it (HU #150). The post's own header keeps showing the
 * original author; this banner only names who reposted it.
 */
export const RepostBanner: FC<RepostBannerProps> = ({ context }) => {
  return (
    <p className={styles.repostBanner} data-testid="repost-banner">
      <Repeat className="w-3 h-3" aria-hidden="true" />
      <Link
        to={`/profile/${context.reposterUsername}`}
        className={styles.repostBannerLink}
        data-testid="repost-banner-link"
      >
        {context.reposterName}
      </Link>
      <span>reposted</span>
      <time dateTime={context.repostedAt} className="sr-only">
        {context.repostedAt}
      </time>
    </p>
  );
};
