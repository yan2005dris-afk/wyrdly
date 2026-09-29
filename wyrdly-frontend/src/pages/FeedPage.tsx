import { useCallback, useState, type FC } from "react";
import type { CreatePostPayload, ReactionType } from "../types/feed";
import { mapPostApiResponseToPost } from "../types/feed";
import type { UserProfileSummary } from "../types/domain";
import { useAuth } from "../hooks/useAuth";
import { useGraphSuggestions } from "../hooks/useGraphSuggestions";
import { useFollow } from "../hooks/useFollow";
import { useCreatePost } from "../hooks/useCreatePost";
import { useFeed } from "../hooks/useFeed";
import { useMediaUpload } from "../hooks/useMediaUpload";
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

export const FeedPage: FC = () => {
  const { user } = useAuth();
  const [activeFilter, setActiveFilter] = useState<FeedFilter>("for_you");

  const { suggestions: apiSuggestions, refetch: refetchSuggestions } =
    useGraphSuggestions();
  const { follow, unfollow } = useFollow();
  const { posts, addPost, replacePost } = useFeed();
  const { createPost } = useCreatePost();
  const { upload: uploadMediaFile, isUploading: isUploadingMedia } =
    useMediaUpload();

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

  // Adapter: useMediaUpload.upload returns MediaUploadResponse | null;
  // CreatePostCard expects (file) => Promise<string | null>.
  const handleUploadMedia = useCallback(
    async (file: File): Promise<string | null> => {
      const response = await uploadMediaFile(file);
      return response?.fileUrl ?? null;
    },
    [uploadMediaFile],
  );

  const handlePublishPost = async (payload: CreatePostPayload) => {
    // Mirror CreatePostCard's guard: skip only when both text and media are empty.
    if (!payload.content.trim() && !payload.mediaUrl) return;
    const response = await createPost({
      content: payload.content,
      mediaUrl: payload.mediaUrl,
    });
    if (!response) return; // error surfaced via useCreatePost state
    const newPost = mapPostApiResponseToPost(response, payload.visibility);
    addPost(newPost);
  };

  // Optimistic reaction counter until HU09 wires the real API.
  const handleReaction = (postId: string, reaction: ReactionType) => {
    const target = posts.find((p) => p.id === postId);
    if (!target) return;
    const currentActive = target.userReaction === reaction;
    const diff = currentActive ? -1 : 1;
    replacePost({
      ...target,
      userReaction: currentActive ? undefined : reaction,
      reactions: {
        ...target.reactions,
        [reaction]: Math.max(0, target.reactions[reaction] + diff),
      },
    });
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
          uploadMedia={handleUploadMedia}
          isUploadingMedia={isUploadingMedia}
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
