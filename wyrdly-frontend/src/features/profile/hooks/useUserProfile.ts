import { useState, useEffect, useCallback } from "react";
import {
  usersApi,
  type UserProfileApiResponse,
  type UpdateProfilePayload,
} from "../../../api/users";
import type { UserProfileSummary } from "../../../types/domain";

interface UseUserProfileReturn {
  readonly profile: UserProfileSummary | null;
  readonly isLoading: boolean;
  readonly error: string | null;
  readonly updateProfile: (payload: UpdateProfilePayload) => Promise<void>;
  readonly refetch: () => void;
}

function mapApiResponseToSummary(
  data: UserProfileApiResponse,
): UserProfileSummary {
  const joinedDate = data.createdAt
    ? `Joined ${new Date(data.createdAt).toLocaleDateString("en-US", { month: "short", year: "numeric" })}`
    : undefined;

  return {
    id: data.id,
    username: data.username,
    fullName: data.fullName,
    avatarUrl: data.avatarUrl ?? undefined,
    bio: data.bio ?? undefined,
    isVerified: false,
    instanceUrl: "wyrdly.app",
    joinedDate,
    isFollowing: data.isFollowing,
    stats: {
      followersCount: data.followersCount,
      followingCount: data.followingCount,
      postsCount: data.postsCount,
    },
  };
}

export function useUserProfile(
  username: string | undefined,
): UseUserProfileReturn {
  const [profile, setProfile] = useState<UserProfileSummary | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(Boolean(username));
  const [error, setError] = useState<string | null>(null);

  const fetchProfile = useCallback(async () => {
    if (!username) {
      setProfile(null);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const data = await usersApi.getProfile(username);
      setProfile(mapApiResponseToSummary(data));
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to load profile";
      setError(message);
      setProfile(null);
    } finally {
      setIsLoading(false);
    }
  }, [username]);

  useEffect(() => {
    let isCancelled = false;

    if (!username) {
      return;
    }

    const load = async () => {
      try {
        const data = await usersApi.getProfile(username);
        if (!isCancelled) {
          setProfile(mapApiResponseToSummary(data));
          setError(null);
        }
      } catch (err) {
        if (!isCancelled) {
          const message =
            err instanceof Error ? err.message : "Failed to load profile";
          setError(message);
          setProfile(null);
        }
      } finally {
        if (!isCancelled) {
          setIsLoading(false);
        }
      }
    };

    void load();

    return () => {
      isCancelled = true;
    };
  }, [username]);

  const updateProfile = useCallback(async (payload: UpdateProfilePayload) => {
    setError(null);
    try {
      const data = await usersApi.updateProfile(payload);
      setProfile(mapApiResponseToSummary(data));
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to update profile";
      setError(message);
      throw err;
    }
  }, []);

  return { profile, isLoading, error, updateProfile, refetch: fetchProfile };
}
