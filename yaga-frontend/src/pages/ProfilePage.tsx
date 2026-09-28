import { useState, type FC } from "react";
import { useParams, Link } from "react-router-dom";
import { ArrowLeft, Share2, Edit3, Loader2, AlertCircle } from "lucide-react";
import type { GraphSuggestionUser } from "../types/domain";
import type { UpdateProfilePayload } from "../api/users";
import type { ProfileTabId } from "../components/profile/ProfileHeaderCard";
import { useAuth } from "../hooks/useAuth";
import { useUserProfile } from "../hooks/useUserProfile";
import { GraphSuggestionsCard } from "../components/social";
import {
  ProfileHeaderCard,
  PostGridItem,
  EditProfileModal,
} from "../components/profile";
import { Button } from "../components/ui/Button";

interface PostGalleryItem {
  readonly id: string;
  readonly imageUrl: string;
  readonly title: string;
  readonly likesCount: number;
  readonly repliesCount: number;
}

const GALLERY_POSTS: readonly PostGalleryItem[] = [
  {
    id: "gal-1",
    imageUrl:
      "https://images.unsplash.com/photo-1519501025264-65ba15a82390?w=400&auto=format&fit=crop&q=80",
    title: "Night deploys hit different...",
    likesCount: 412,
    repliesCount: 38,
  },
  {
    id: "gal-2",
    imageUrl:
      "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=400&auto=format&fit=crop&q=80",
    title: "Relay v2 benchmarks live",
    likesCount: 1100,
    repliesCount: 204,
  },
  {
    id: "gal-3",
    imageUrl:
      "https://images.unsplash.com/photo-1448375240586-882707db888b?w=400&auto=format&fit=crop&q=80",
    title: "Offline weekend in forest",
    likesCount: 689,
    repliesCount: 51,
  },
];

const SUGGESTIONS: readonly GraphSuggestionUser[] = [
  {
    id: "user-alice",
    username: "alice",
    fullName: "Alice Chen",
    avatarUrl:
      "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&auto=format&fit=crop&q=80",
    mutualConnectionSnippet: "Mutual followers: 4",
    isFollowing: false,
  },
];

export const ProfilePage: FC = () => {
  const { username } = useParams<{ username: string }>();
  const { user: authUser } = useAuth();

  const profileUsername = username || authUser?.username;
  const { profile, isLoading, error, updateProfile, refetch } =
    useUserProfile(profileUsername);

  const [activeTab, setActiveTab] = useState<ProfileTabId>("posts");
  const [isSubscribed, setIsSubscribed] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [suggestions, setSuggestions] =
    useState<readonly GraphSuggestionUser[]>(SUGGESTIONS);

  const isCurrentUser =
    !!authUser && profile?.username === authUser.username;

  const handleFollowToggle = (userId: string) => {
    setSuggestions((prev) =>
      prev.map((s) =>
        s.id === userId ? { ...s, isFollowing: !s.isFollowing } : s,
      ),
    );
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
        <p className="text-sm text-slate-600">
          {error || "Profile not found"}
        </p>
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
            activeTab={activeTab}
            onTabChange={setActiveTab}
            isSubscribed={isSubscribed}
            onSubscribeToggle={() => setIsSubscribed(!isSubscribed)}
            onEditProfileClick={() => setIsEditOpen(true)}
          />

          {activeTab === "posts" && (
            <div
              className="grid grid-cols-1 sm:grid-cols-3 gap-4"
              data-testid="profile-posts-grid"
            >
              {GALLERY_POSTS.map((item) => (
                <PostGridItem
                  key={item.id}
                  id={item.id}
                  imageUrl={item.imageUrl}
                  title={item.title}
                  likesCount={item.likesCount}
                  repliesCount={item.repliesCount}
                />
              ))}
            </div>
          )}

          {activeTab !== "posts" && (
            <div className="bg-white p-8 rounded-2xl border border-slate-200 text-center text-sm text-slate-500">
              Showing {activeTab} for @{profile.username}
            </div>
          )}
        </div>

        {/* Right Sidebar Area (4 columns) */}
        <aside className="lg:col-span-4 flex flex-col gap-4">
          <GraphSuggestionsCard
            suggestions={suggestions}
            onFollowToggle={handleFollowToggle}
          />
        </aside>
      </div>

      {/* Edit Profile Modal */}
      <EditProfileModal
        isOpen={isEditOpen}
        profile={profile}
        isSaving={isSaving}
        onSave={handleSaveProfile}
        onClose={() => setIsEditOpen(false)}
      />
    </>
  );
};
