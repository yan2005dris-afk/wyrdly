import { useCallback, useState, type FC } from "react";
import { useParams, Link } from "react-router-dom";
import { ArrowLeft, Share2, Edit3, Loader2, AlertCircle } from "lucide-react";
import type { PostApiResponse } from "../types/feed";
import type { UpdateProfilePayload } from "../api/users";
import { useAuth } from "../features/auth";
import {
  ProfileHeaderCard,
  PostGridItem,
  EditProfileModal,
  useUserProfile,
  useUserPosts,
  useProfileUsers,
  type ProfileTabId,
} from "../features/profile";
import { useGraphSuggestions } from "../hooks/useGraphSuggestions";
import { GraphSuggestionsCard } from "../components/social";
import { UserListRow } from "../components/social/UserListRow";
import { Button } from "../components/ui/Button";

export const ProfilePage: FC = () => {
  const { username } = useParams<{ username: string }>();
  const { user: authUser } = useAuth();

  const profileUsername = username || authUser?.username;
  const { profile, isLoading, error, updateProfile, refetch } =
    useUserProfile(profileUsername);

  const [activeTab, setActiveTab] = useState<ProfileTabId>("posts");
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  const { posts, isLoading: postsLoading } = useUserPosts(profileUsername);
  const {
    users: followers,
    isLoading: followersLoading,
    refetch: refetchFollowers,
  } = useProfileUsers(profileUsername, "followers");
  const {
    users: following,
    isLoading: followingLoading,
    refetch: refetchFollowing,
  } = useProfileUsers(profileUsername, "following");
  const { suggestions: apiSuggestions } = useGraphSuggestions();

  const isCurrentUser = !!authUser && profile?.username === authUser.username;

  const handleAfterToggle = useCallback(() => {
    // Re-fetch the profile header (followers / following / postsCount)
    // and the two tab lists so a follow or unfollow in the Followers /
    // Following tab is reflected everywhere.
    refetch();
    refetchFollowers();
    refetchFollowing();
  }, [refetch, refetchFollowers, refetchFollowing]);

  const handleSaveProfile = async (payload: UpdateProfilePayload) => {
    setIsSaving(true);
    try {
      await updateProfile(payload);
      setIsEditOpen(false);
    } catch {
      // error is already surfaced via the hook
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div
        className="flex items-center justify-center py-20"
        data-testid="profile-loading"
      >
        <Loader2 className="w-8 h-8 text-indigo-500 animate-spin" />
        <span className="ml-3 text-sm text-slate-500">Loading profile…</span>
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div
        className="flex flex-col items-center justify-center py-20 gap-4"
        data-testid="profile-error"
      >
        <AlertCircle className="w-10 h-10 text-red-400" />
        <p className="text-sm text-slate-600">{error || "Profile not found"}</p>
        <Button variant="secondary" size="sm" onClick={refetch}>
          Try again
        </Button>
      </div>
    );
  }

  return (
    <>
      <div
        className="grid grid-cols-1 lg:grid-cols-12 gap-6 w-full"
        data-testid="profile-page"
      >
        {/* Central Profile Area (8 columns) */}
        <div className="lg:col-span-8 flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <Link
              to="/feed"
              className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-indigo-600 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Back to feed</span>
            </Link>

            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                leftIcon={<Share2 className="w-3.5 h-3.5" />}
              >
                Share
              </Button>
              {isCurrentUser && (
                <Button
                  variant="secondary"
                  size="sm"
                  leftIcon={<Edit3 className="w-3.5 h-3.5" />}
                  onClick={() => setIsEditOpen(true)}
                  data-testid="edit-profile-btn"
                >
                  Edit Profile
                </Button>
              )}
            </div>
          </div>

          <ProfileHeaderCard
            user={profile}
            coverUrl="https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1000&auto=format&fit=crop&q=80"
            isCurrentUser={isCurrentUser}
            isSubscribed={profile.isFollowing ?? false}
            activeTab={activeTab}
            onTabChange={setActiveTab}
            onSubscribeToggle={() => {
              /* delegated to a future follow-action button; the
                 ProfileHeaderCard receives isSubscribed for display only */
            }}
            onEditProfileClick={() => setIsEditOpen(true)}
          />

          {activeTab === "posts" && (
            <PostsTab posts={posts} isLoading={postsLoading} />
          )}

          {activeTab === "followers" && (
            <FollowersOrFollowingTab
              users={followers}
              isLoading={followersLoading}
              emptyMessage={`@${profile.username} has no followers yet`}
              testId="profile-followers-list"
              onAfterToggle={handleAfterToggle}
            />
          )}

          {activeTab === "following" && (
            <FollowersOrFollowingTab
              users={following}
              isLoading={followingLoading}
              emptyMessage={`@${profile.username} isn't following anyone yet`}
              testId="profile-following-list"
              onAfterToggle={handleAfterToggle}
            />
          )}

          {activeTab === "reactions" && (
            <div className="bg-white p-8 rounded-2xl border border-slate-200 text-center text-sm text-slate-500">
              Reactions tab coming soon.
            </div>
          )}
        </div>

        {/* Right Sidebar Area (4 columns) */}
        <aside className="lg:col-span-4 flex flex-col gap-4">
          <GraphSuggestionsCard
            suggestions={apiSuggestions}
            onAfterToggle={handleAfterToggle}
          />
        </aside>
      </div>

      {/* Edit Profile Modal */}
      {isEditOpen && (
        <EditProfileModal
          isOpen={isEditOpen}
          profile={profile}
          isSaving={isSaving}
          onSave={handleSaveProfile}
          onClose={() => setIsEditOpen(false)}
        />
      )}
    </>
  );
};

interface PostsTabProps {
  readonly posts: readonly PostApiResponse[];
  readonly isLoading: boolean;
}

const PostsTab: FC<PostsTabProps> = ({ posts, isLoading }) => {
  if (isLoading) {
    return (
      <div
        className="flex items-center justify-center py-12"
        data-testid="profile-posts-loading"
      >
        <Loader2 className="w-6 h-6 text-indigo-500 animate-spin" />
      </div>
    );
  }

  if (posts.length === 0) {
    return (
      <div
        className="bg-white p-8 rounded-2xl border border-slate-200 text-center text-sm text-slate-500"
        data-testid="profile-posts-empty"
      >
        No posts yet.
      </div>
    );
  }

  return (
    <div
      className="grid grid-cols-1 sm:grid-cols-3 gap-4"
      data-testid="profile-posts-grid"
    >
      {posts.map((post) => (
        <PostGridItem
          key={post.id}
          id={post.id}
          imageUrl={post.mediaUrl ?? ""}
          title={post.content.slice(0, 80)}
          likesCount={0}
          repliesCount={0}
        />
      ))}
    </div>
  );
};

interface FollowersOrFollowingTabProps {
  readonly users: readonly ProfileUserSummary[];
  readonly isLoading: boolean;
  readonly emptyMessage: string;
  readonly testId: string;
  readonly onAfterToggle?: (userId: string) => void;
}

const FollowersOrFollowingTab: FC<FollowersOrFollowingTabProps> = ({
  users,
  isLoading,
  emptyMessage,
  testId,
  onAfterToggle,
}) => {
  if (isLoading) {
    return (
      <div
        className="flex items-center justify-center py-12"
        data-testid={`${testId}-loading`}
      >
        <Loader2 className="w-6 h-6 text-indigo-500 animate-spin" />
      </div>
    );
  }

  if (users.length === 0) {
    return (
      <div
        className="bg-white p-8 rounded-2xl border border-slate-200 text-center text-sm text-slate-500"
        data-testid={`${testId}-empty`}
      >
        {emptyMessage}
      </div>
    );
  }

  return (
    <ul
      className="bg-white rounded-2xl border border-slate-200 divide-y divide-slate-100"
      data-testid={testId}
    >
      {users.map((user) => (
        <li key={user.id} data-testid={`profile-user-${user.id}`}>
          <UserListRow user={user} onAfterToggle={onAfterToggle} />
        </li>
      ))}
    </ul>
  );
};
