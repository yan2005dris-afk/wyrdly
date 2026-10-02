import { useCallback, useState, type FC } from "react";
import type { CreatePostPayload, ReactionType } from "../types/feed";
import { mapPostApiResponseToPost } from "../types/feed";
import type { UserProfileSummary } from "../types/domain";
import { useAuth } from "../features/auth";
import {
  CreatePostCard,
  PostCard,
  PostCardSkeleton,
  GraphSuggestionsCard,
  RelayHealthWidget,
  useGraphSuggestions,
  useCreatePost,
  useFeed,
} from "../features/social";
import { useMediaUpload } from "../hooks/useMediaUpload";
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

  const {
    suggestions: apiSuggestions,
    isLoading: isSuggestionsLoading,
    refetch: refetchSuggestions,
  } = useGraphSuggestions();
  const { posts, isLoading: isFeedLoading, addPost, replacePost } = useFeed();
  const { createPost } = useCreatePost();
  const { upload: uploadMediaFile, isUploading: isUploadingMedia } =
    useMediaUpload();

  const suggestions = apiSuggestions;

  const currentUserSummary: UserProfileSummary = {
    id: user?.id || "",
    username: user?.username || "",
    fullName: user?.fullName || "User",
    avatarUrl: user?.avatarUrl || undefined,
    bio: user?.bio || "",
    instanceUrl: "wyrdly.social",
    isVerified: false,
    stats: {
      postsCount: 0,
      followersCount: 0,
      followingCount: 0,
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

  // Follow/unfollow is now owned by the UserListRow component used by
  // GraphSuggestionsCard. After a successful toggle we refetch the
  // suggestion list so the next call reflects the new state.

  const handleAfterToggle = useCallback(() => {
    refetchSuggestions();
  }, [refetchSuggestions]);

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
          {isFeedLoading ? (
            <div
              data-testid="feed-posts-loading"
              className="flex flex-col gap-4"
            >
              {Array.from({ length: 3 }).map((_, i) => (
                <PostCardSkeleton key={i} />
              ))}
            </div>
          ) : posts.length === 0 ? (
            <div
              className="bg-white p-8 rounded-2xl border border-slate-200 text-center text-sm text-slate-500"
              data-testid="feed-posts-empty"
            >
              No posts found. Start by following people or publishing a thought!
            </div>
          ) : (
            posts.map((post) => (
              <PostCard key={post.id} post={post} onReaction={handleReaction} />
            ))
          )}
        </div>
      </div>

      {/* Right Social Context Column (4 columns) */}
      <aside className="lg:col-span-4 flex flex-col gap-4">
        <GraphSuggestionsCard
          suggestions={suggestions}
          isLoading={isSuggestionsLoading}
          onAfterToggle={handleAfterToggle}
        />
        <RelayHealthWidget />
      </aside>
    </div>
  );
};
