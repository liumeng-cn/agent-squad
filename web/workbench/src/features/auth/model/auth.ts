import type { User } from '../../user/model/user'

export interface Login {
  user: User
  csrfToken: string
  expiresAt: string
}
