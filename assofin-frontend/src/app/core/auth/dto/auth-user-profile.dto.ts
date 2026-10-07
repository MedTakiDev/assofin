export type AppPermission =
// User Management Feature Clearance
  | 'user:read'   // 👈 Evaluated by the Route Guard
  | 'user:create' // 👈 Evaluated by the Component Layer
  | 'user:update' // 👈 Evaluated by the Component Layer
  | 'user:delete' // 👈 Evaluated by the Component Layer

  // Invoice Management Feature Clearance
  | 'invoice:read'
  | 'invoice:create'
  | 'invoice:approve';


export interface AuthUserProfileDto {
  id: string;
  email: string;
  firstname: string;
  lastname: string;
  role: {
    name: string;
    permissions: AppPermission[]; // 👈 Array of granular permissions granted to this user
  };          // e.g., "Finance Manager"
}


//todo refactor the code above to meet the best practices permissions / roles / userProfile
