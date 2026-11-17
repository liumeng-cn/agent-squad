import { useAuthStore } from '../../auth/stores/auth'
import { updateUserApi } from '../api/userApi'

/** 资料编辑属于用户模块，成功后刷新当前登录主体的展示资料。 */
export function useUserProfile() {
  const auth = useAuthStore()
  async function update(displayName: string) {
    const user = auth.user
    if (!user) throw new Error('请先登录')
    const updated = await updateUserApi(displayName, user.version)
    if (auth.user?.userId === user.userId) auth.user = updated
  }
  return { update }
}
