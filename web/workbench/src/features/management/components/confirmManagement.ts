import { ElMessageBox } from 'element-plus'

/** 确认框选项：标题、确认文字和危险操作提示。 */
export interface ConfirmOptions {
  title?: string
  confirmText?: string
  danger?: boolean
}

/** 所有管理确认操作共用 Element Plus；取消、Esc 和关闭均不执行操作。 */
export async function confirmManagement(
  message: string,
  options: ConfirmOptions = {},
): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, options.title ?? '操作确认', {
      confirmButtonText: options.confirmText ?? '确定',
      cancelButtonText: '取消',
      type: options.danger ? 'warning' : 'info',
      confirmButtonClass: options.danger ? 'el-button--danger' : '',
      closeOnClickModal: false,
    })
    return true
  } catch {
    return false
  }
}
