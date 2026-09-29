import { useState, type FC } from "react";
import type { Post, CreatePostPayload, ReactionType } from "../types/feed";
import type { UserProfileSummary } from "../types/domain";
import { useAuth } from "../hooks/useAuth";
import { useGraphSuggestions } from "../hooks/useGraphSuggestions";
import { useFollow } from "../hooks/useFollow";
import {
  CreatePostCard,
  PostCard,
  GraphSuggestionsCard,
  RelayHealthWidget,
} from "../components/social";
import { Tabs, type TabItem } from "../components/ui/Tabs";

type FeedFilter = "for_you" | "latest" | "relays";

const FEED_FILTER_TABS: readonly TabItem<FeedFilter>[] = [
  { id: "for_you", label: "For you (Graph Feed)" },
  { id: "latest", label: "Latest" },
  { id: "relays", label: "Relays near you" },
];

const INITIAL_POSTS: readonly Post[] = [
  {
    id: "post-1",
    author: {
      id: "user-jonas",
      username: "jonas",
      fullName: "Jonas Weber",
      avatarUrl:
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&auto=format&fit=crop&q=80",
      isVerified: true,
      instanceUrl: "mastodon.social",
      stats: { followersCount: 4200, followingCount: 650, postsCount: 180 },
    },
    content:
      "Just shipped our relay cluster to 99.99% uptime. Decentralized social finally feels instant — no single point of failure. Full write-up + benchmarks inside.",
    createdAt: "12m",
    attachments: [
      {
        id: "att-1",
        url: "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=800&auto=format&fit=crop&q=80",
        storageProvider: "RUSTFS_S3",
        mimeType: "image/jpeg",
        altText: "Server cluster",
      },
    ],
    reactions: { LIKE: 1200, LOVE: 80, CELEBRATE: 40, RETWEET: 342 },
    commentsCount: 89,
    visibility: "PUBLIC",
  },
  {
    id: "post-2",
    author: {
      id: "user-alice",
      username: "alice",
      fullName: "Alice Chen",
      avatarUrl:
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&auto=format&fit=crop&q=80",
      isVerified: true,
      instanceUrl: "wyrdly.app",
      stats: { followersCount: 120, followingCount: 80, postsCount: 45 },
    },
    content:
      "Decentralized identity via W3C DID specification: here is how we handle mutual verifiability without centralized registry bottlenecks.",
    createdAt: "45m",
    attachments: [],
    reactions: { LIKE: 84, LOVE: 12, CELEBRATE: 8, RETWEET: 19 },
    commentsCount: 14,
    visibility: "FEDERATED",
  },
];

export const FeedPage: FC = () => {
  const { user } = useAuth();
  const [activeFilter, setActiveFilter] = useState<FeedFilter>("for_you");
  const [posts, setPosts] = useState<readonly Post[]>(INITIAL_POSTS);

  const { suggestions: apiSuggestions, refetch: refetchSuggestions } =
    useGraphSuggestions();
  const { follow, unfollow } = useFollow();

  // Optimistic follow state: when the user clicks Follow, mark the user as
  // followed immediately so the button flips to "Following" without waiting
  // for the backend refetch. Rolled back on API error.
  const [locallyFollowed, setLocallyFollowed] = useState<ReadonlySet<string>>(
    () => new Set<string>(),
  );

  const suggestions = apiSuggestions.map((s) => ({
    id: s.id,
    username: s.username,
    fullName: s.fullName,
    avatarUrl: s.avatarUrl ?? undefined,
    mutualConnectionSnippet: s.mutualConnectionSnippet,
    isFollowing: s.isFollowing || locallyFollowed.has(s.id),
  }));

  const currentUserSummary: UserProfileSummary = {
    id: user?.id || "usr-current",
    username: user?.username || "maya",
    fullName: user?.fullName || "Maya Krishnan",
    avatarUrl:
      user?.avatarUrl ||
      "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80",
    bio: user?.bio || "Building federated mesh networks",
    instanceUrl: "wyrdly.app",
    isVerified: true,
    stats: {
      postsCount: 12,
      followersCount: 148,
      followingCount: 92,
    },
  };

  const handlePublishPost = (payload: CreatePostPayload) => {
    const newPost: Post = {
      id: `post-${Date.now()}`,
      author: currentUserSummary,
      content: payload.content,
      createdAt: "Just now",
      attachments: (payload.attachments || []).map((f: File, i: number) => ({
        id: `att-${Date.now()}-${i}`,
        url: URL.createObjectURL(f),
        storageProvider: "RUSTFS_S3",
        mimeType: f.type,
      })),
      reactions: { LIKE: 0, LOVE: 0, CELEBRATE: 0, RETWEET: 0 },
      commentsCount: 0,
      visibility: payload.visibility,
    };

    setPosts((prev) => [newPost, ...prev]);
  };

  const handleReaction = (postId: string, reaction: ReactionType) => {
    setPosts((prev) =>
      prev.map((p) => {
        if (p.id !== postId) return p;
        const currentActive = p.userReaction === reaction;
        const diff = currentActive ? -1 : 1;
        return {
          ...p,
          userReaction: currentActive ? undefined : reaction,
          reactions: {
            ...p.reactions,
            [reaction]: Math.max(0, p.reactions[reaction] + diff),
          },
        };
      }),
    );
  };

  const handleFollowToggle = async (userId: string) => {
    const suggestion = apiSuggestions.find((s) => s.id === userId);
    if (!suggestion) return;

    const wasFollowing = suggestion.isFollowing || locallyFollowed.has(userId);
    // Optimistic: flip the local flag immediately for instant feedback.
    setLocallyFollowed((prev) => {
      const next = new Set(prev);
      if (wasFollowing) {
        next.delete(userId);
      } else {
        next.add(userId);
      }
      return next;
    });

    try {
      if (wasFollowing) {
        await unfollow(userId);
      } else {
        await follow(userId);
      }
      // Backend will exclude newly-followed users via WHERE NOT in the next
      // refetch; re-sync to drop them from the list.
      refetchSuggestions();
    } catch (err) {
      // Rollback the optimistic flag on failure.
      setLocallyFollowed((prev) => {
        const next = new Set(prev);
        if (wasFollowing) {
          next.add(userId);
        } else {
          next.delete(userId);
        }
        return next;
      });
      console.error("Follow toggle failed", err);
    }
  };

  return (
    <div
      className="grid grid-cols-1 lg:grid-cols-12 gap-6 w-full"
      data-testid="feed-page"
    >
      {/* Central Timeline (8 columns) */}
      <div className="lg:col-span-8 flex flex-col gap-4">
        <CreatePostCard
          currentUser={currentUserSummary}
          onPublish={handlePublishPost}
        />

        <div className="py-2">
          <Tabs<FeedFilter>
            items={FEED_FILTER_TABS}
            activeTab={activeFilter}
            onChange={setActiveFilter}
            variant="underline"
          />
        </div>

        <div className="flex flex-col gap-4">
          {posts.map((post) => (
            <PostCard key={post.id} post={post} onReaction={handleReaction} />
          ))}
        </div>
      </div>

      {/* Right Social Context Column (4 columns) */}
      <aside className="lg:col-span-4 flex flex-col gap-4">
        <GraphSuggestionsCard
          suggestions={suggestions}
          onFollowToggle={handleFollowToggle}
        />
        <RelayHealthWidget />
      </aside>
    </div>
  );
};
