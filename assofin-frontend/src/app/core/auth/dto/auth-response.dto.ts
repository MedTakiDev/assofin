import {AuthUserProfileDto} from './auth-user-profile.dto';

export interface AuthResponseDto {
  token: string;
  user: AuthUserProfileDto | null;
  message: string;
}
