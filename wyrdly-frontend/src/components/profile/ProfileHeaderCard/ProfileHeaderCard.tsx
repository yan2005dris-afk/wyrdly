import type { FC } from "react";
import {
  MapPin,
  Link as LinkIcon,
  Calendar,
  Server,
  Cloud,
  CheckCircle,
  MessageSquare,
  Bell,
  Edit3,
} from "lucide-react";
import type {
  ProfileHeaderCardProps,
  ProfileTabId,
} from "./ProfileHeaderCard.types";
import type { TabItem } from "../../ui/Tabs";
import { Avatar } from "../../ui/Avatar";
import { Tabs } from "../../ui/Tabs";
import { Button } from "../../ui/Button";
import styles from "./ProfileHeaderCard.module.css";

const formatCount = (count: number): string => {
  if (count >= 1_000_000)
    return (count / 1_000_000).toFixed(1).replace(/\.0$/, "") + "M";
  if (count >= 1_000)
    return (count / 1_000).toFixed(1).replace(/\.0$/, "") + "k";
  return count.toString();
};

const PROFILE_TABS: readonly TabItem<ProfileTabId>[] = [
  { id: "posts", label: "Posts" },
  { id: "followers", label: "Followers" },
  { id: "following", label: "Following" },
  { id: "reactions", label: "Reactions" },
];

export const ProfileHeaderCard: FC<ProfileHeaderCardProps> = ({
  user,
  coverUrl,
  isCurrentUser = false,
  isSubscribed = false,
  activeTab,
  onTabChange,
  onMessageClick,
  onSubscribeToggle,
  onEditProfileClick,
  className = "",
}) => {
  return (
    <div
      className={`${styles.card} ${className}`}
      data-testid="profile-header-card"
    >
      {/* Cover Image (RustFS) */}
      <div className={styles.coverContainer}>
        {coverUrl && (
          <img
            src={coverUrl}
            alt="Profile cover"
            className={styles.coverImage}
            loading="lazy"
          />
        )}
        <span className={styles.rustfsBadge} data-testid="cover-rustfs-badge">
          <Cloud className="w-3 h-3 text-indigo-400" />
          <span>Cover stored on RustFS</span>
        </span>
      </div>

      <div className={styles.body}>
        {/* Avatar overlay & action buttons */}
        <div className={styles.avatarAndActions}>
          <Avatar
            src={user.avatarUrl}
            alt={user.fullName}
            size="xl"
            shape="rounded"
            showRing={true}
            ringColor="white"
          />

          <div className={styles.actionsGroup}>
            {isCurrentUser ? (
              <Button
                variant="primary"
                size="sm"
                leftIcon={<Edit3 className="w-3.5 h-3.5" />}
                onClick={onEditProfileClick}
                data-testid="edit-profile-btn"
              >
                Edit Profile
              </Button>
            ) : (
              <>
                <Button
                  variant="secondary"
                  size="sm"
                  leftIcon={<MessageSquare className="w-3.5 h-3.5" />}
                  onClick={onMessageClick}
                  data-testid="profile-message-btn"
                >
                  Message
                </Button>
                <Button
                  variant={isSubscribed ? "secondary" : "primary"}
                  size="sm"
                  leftIcon={<Bell className="w-3.5 h-3.5" />}
                  onClick={onSubscribeToggle}
                  data-testid="profile-subscribe-btn"
                >
                  {isSubscribed ? "Subscribed" : "Subscribe"}
                </Button>
              </>
            )}
          </div>
        </div>

        {/* Name and Handle */}
        <div className={styles.nameRow}>
          <h2 className={styles.fullName}>{user.fullName}</h2>
          {user.isVerified && (
            <span
              className={styles.verifiedIcon}
              data-testid="profile-verified-badge"
            >
              <CheckCircle className="w-4 h-4 fill-indigo-600 text-white" />
            </span>
          )}
        </div>
        <p className={styles.handleText}>
          @{user.username} • {user.instanceUrl}
        </p>

        {/* Bio */}
        {user.bio && <p className={styles.bio}>{user.bio}</p>}

        {/* Metadata Badges */}
        <div className={styles.metadataGrid}>
          {user.location && (
            <span className={styles.metadataItem}>
              <MapPin className="w-3.5 h-3.5 text-slate-400" />
              <span>{user.location}</span>
            </span>
          )}
          {user.website && (
            <span className={styles.metadataItem}>
              <LinkIcon className="w-3.5 h-3.5 text-slate-400" />
              <a
                href={`https://${user.website}`}
                target="_blank"
                rel="noreferrer"
                className="hover:underline"
              >
                {user.website}
              </a>
            </span>
          )}
          {user.joinedDate && (
            <span className={styles.metadataItem}>
              <Calendar className="w-3.5 h-3.5 text-slate-400" />
              <span>Joined {user.joinedDate}</span>
            </span>
          )}
          <span className={styles.metadataItem}>
            <Server className="w-3.5 h-3.5 text-slate-400" />
            <span>Home relay: {user.homeRelay || "ap-south-1"}</span>
          </span>
        </div>

        {/* Social Graph Counts */}
        <div className={styles.countsRow}>
          <div className={styles.countItem}>
            <strong>{formatCount(user.stats.followingCount)}</strong>{" "}
            <span>Following</span>
          </div>
          <div className={styles.countItem}>
            <strong>{formatCount(user.stats.followersCount)}</strong>{" "}
            <span>Followers</span>
          </div>
          <div className={styles.countItem}>
            <strong>{formatCount(user.stats.postsCount)}</strong>{" "}
            <span>Posts</span>
          </div>
        </div>
      </div>

      {/* Profile Section Tabs */}
      <div className={styles.tabsContainer}>
        <Tabs<ProfileTabId>
          items={PROFILE_TABS}
          activeTab={activeTab}
          onChange={onTabChange}
          variant="underline"
        />
      </div>
    </div>
  );
};
