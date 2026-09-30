import { useState, type FC } from "react";
import { useParams, Link } from "react-router-dom";
import {
  ArrowLeft,
  Share2,
  Edit3,
  Loader2,
  AlertCircle,
  Users,
} from "lucide-react";
import type { PostApiResponse } from "../types/feed";
import type { UpdateProfilePayload } from "../api/users";
import type { ProfileTabId } from "../components/profile/ProfileHeaderCard";
import type { ProfileUserSummary } from "../types/suggestions";
import { useAuth } from "../hooks/useAuth";
import { useUserProfile } from "../hooks/useUserProfile";
import { useUserPosts } from "../hooks/useUserPosts";
import { useProfileUsers } from "../hooks/useProfileUsers";
import { useGraphSuggestions } from "../hooks/useGraphSuggestions";
import { useFollow } from "../hooks/useFollow";
import { GraphSuggestionsCard } from "../components/social";
import {
  ProfileHeaderCard,
  PostGridItem,
  EditProfileModal,
} from "../components/profile";
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
  const { users: followers, isLoading: followersLoading } = useProfileUsers(
    profileUsername,
    "followers",
  );
  const { users: following, isLoading: followingLoading } = useProfileUsers(
    profileUsername,
    "following",
  );
  const { suggestions: apiSuggestions } = useGraphSuggestions();
  const { follow, unfollow } = useFollow();

  const isCurrentUser = !!authUser && profile?.username === authUser.username;

  const handleFollowToggle = async (userId: string) => {
    const suggestion = apiSuggestions.find((s) => s.id === userId);
    if (!suggestion) return;
    try {
      if (suggestion.isFollowing) {
        await unfollow(userId);
      } else {
        await follow(userId);
      }
    } catch (err) {
      console.error("Follow toggle failed", err);
    }
  };

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

  const headerSuggestions = apiSuggestions.map((s) => ({
    id: s.id,
    username: s.username,
    fullName: s.fullName,
    avatarUrl: s.avatarUrl ?? undefined,
    mutualConnectionSnippet: s.mutualConnectionSnippet,
    isFollowing: s.isFollowing,
  }));

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
            />
          )}

          {activeTab === "following" && (
            <FollowersOrFollowingTab
              users={following}
              isLoading={followingLoading}
              emptyMessage={`@${profile.username} isn't following anyone yet`}
              testId="profile-following-list"
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
            suggestions={headerSuggestions}
            onFollowToggle={handleFollowToggle}
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
}

const FollowersOrFollowingTab: FC<FollowersOrFollowingTabProps> = ({
  users,
  isLoading,
  emptyMessage,
  testId,
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
        <li
          key={user.id}
          className="flex items-center justify-between p-4"
          data-testid={`profile-user-${user.id}`}
        >
          <Link
            to={`/profile/${user.username}`}
            className="flex items-center gap-3 flex-1 min-w-0"
          >
            <div className="w-10 h-10 rounded-full bg-slate-200 overflow-hidden flex-shrink-0">
              {user.avatarUrl ? (
                <img
                  src={user.avatarUrl}
                  alt={user.fullName}
                  className="w-full h-full object-cover"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-slate-500">
                  <Users className="w-5 h-5" />
                </div>
              )}
            </div>
            <div className="min-w-0">
              <div className="font-medium text-sm text-slate-900 truncate">
                {user.fullName}
              </div>
              <div className="text-xs text-slate-500 truncate">
                @{user.username}
              </div>
            </div>
          </Link>
          <span
            className={`text-xs px-2 py-1 rounded-full ${
              user.isFollowing
                ? "bg-emerald-50 text-emerald-700"
                : "bg-slate-50 text-slate-500"
            }`}
            data-testid={`profile-user-${user.id}-following`}
          >
            {user.isFollowing ? "Following" : "Not following"}
          </span>
        </li>
      ))}
    </ul>
  );
};
