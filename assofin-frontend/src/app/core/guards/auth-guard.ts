import { CanMatchFn} from '@angular/router';

export const authGuard: CanMatchFn = (route, state) => {
  return true;
};
