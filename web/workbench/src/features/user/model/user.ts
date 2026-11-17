export type AvatarPalette = 'BLUE' | 'TEAL' | 'VIOLET' | 'AMBER' | 'ROSE' | 'CYAN' | 'GREEN' | 'INDIGO'
export interface Avatar { style: 'GEOMETRIC_V1'; pattern: string; paletteKey: AvatarPalette }
export interface User {
  avatar?: Avatar
  userId: string
  userName: string
  phone: string
  displayName: string
  effectiveDisplayName: string
  status: 'ACTIVE' | 'DISABLED'
  version: number
}
