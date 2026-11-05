// @vitest-environment jsdom
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ModelSelector from '../ModelSelector.vue'
import type { ModelSelection } from '../../../../shared/api/modelsApi'
const { options } = vi.hoisted(() => ({ options: vi.fn() }))
vi.mock('../../../../shared/api/modelsApi', () => ({ modelOptions: options }))
beforeEach(() => options.mockResolvedValue([
  { modelId: 'default', displayName: 'Spark-X2.6', modelName: 'spark-x2.6', capabilities: ['REASONING'], reasoningModes: ['OFF', 'AUTO', 'ON'], defaultReasoningMode: 'AUTO' },
  { modelId: 'writer', displayName: '写作模型', modelName: 'writer', capabilities: ['TEXT_GENERATION'], reasoningModes: ['DEFAULT'], defaultReasoningMode: 'DEFAULT' },
]))
describe('即时模型选择', () => {
  function fixture() {
    return mount(defineComponent({ components: { ModelSelector }, setup() { return { choice: ref<ModelSelection | null>(null) } }, template: '<ModelSelector v-model="choice" />' }))
  }
  it('默认模型立即显示，滑动仅使用模型实际支持的模式', async () => {
    const wrapper = fixture()
    await flushPromises()
    expect(wrapper.find('button').text()).toContain('Spark-X2.6 · 自动')
    await wrapper.find('button').trigger('click')
    const slider = wrapper.find('input[type=range]')
    expect(slider.attributes('max')).toBe('2')
    await slider.setValue('2')
    expect(wrapper.find('button').text()).toContain('深入')
    expect(wrapper.findComponent(ModelSelector).emitted('update:modelValue')?.at(-1)).toEqual([{ modelId: 'default', reasoningMode: 'ON' }])
  })
  it('选择另一个模型立即生效，不支持推理模式的模型不显示滑块', async () => {
    const wrapper = fixture()
    await flushPromises()
    await wrapper.find('button').trigger('click')
    await wrapper.findAll('.model-choice-list button')[1]!.trigger('click')
    expect(wrapper.find('button').text()).toContain('写作模型')
    expect(wrapper.find('input[type=range]').exists()).toBe(false)
    expect(wrapper.findComponent(ModelSelector).emitted('update:modelValue')?.at(-1)).toEqual([{ modelId: 'writer', reasoningMode: 'DEFAULT' }])
  })
  it('无法读取目录时不伪造可用模型，并允许重新读取', async () => {
    options.mockRejectedValueOnce(new Error('offline'))
    const wrapper = fixture()
    await flushPromises()
    await wrapper.find('button').trigger('click')
    expect(wrapper.text()).toContain('暂时无法读取模型')
    expect(wrapper.findAll('.model-choice-list button')).toHaveLength(0)
    await wrapper.find('.text-button').trigger('click')
    await flushPromises()
    expect(wrapper.find('button').text()).toContain('Spark-X2.6')
  })
})
