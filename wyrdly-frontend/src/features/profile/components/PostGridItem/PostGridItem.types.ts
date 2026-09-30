export interface PostGridItemProps {
  readonly id: string;
  readonly imageUrl: string;
  readonly title: string;
  readonly likesCount: number;
  readonly repliesCount: number;
  readonly onClick?: (id: string) => void;
  readonly className?: string;
}
