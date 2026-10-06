import { UserProfileDto } from './user-profile.dto';

export interface AuthResponseDto {
  token: string;
  user: UserProfileDto | null;
  message: string;
}
