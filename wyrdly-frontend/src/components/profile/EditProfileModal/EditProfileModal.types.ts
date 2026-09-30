import type { UserProfileSummary } from "../../../types/domain";
import type { UpdateProfilePayload } from "../../../api/users";

export interface EditProfileModalProps {
  readonly isOpen: boolean;
  readonly profile: UserProfileSummary;
  readonly isSaving: boolean;
  readonly onSave: (payload: UpdateProfilePayload) => void;
  readonly onClose: () => void;
  readonly uploadAvatar?: (file: File) => Promise<string | null>;
}
