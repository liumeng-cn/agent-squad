<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ChatMessageItem from './ChatMessageItem.vue'
import type { ChatMessage } from '../chatTypes'
const props = defineProps<{ messages: ChatMessage[] }>()
const viewport = ref<HTMLElement>()
const content = ref<HTMLElement>()
const following = ref(true)
const now = ref(Date.now())
let observer: ResizeObserver | undefined
let timer: ReturnType<typeof setInterval> | undefined
function scroll() {
  const el = viewport.value
  if (el) following.value = el.scrollHeight - el.scrollTop - el.clientHeight < 90
}
function toLatest() {
  following.value = true
  const el = viewport.value
  if (el) el.scrollTop = el.scrollHeight
}
function follow() {
  if (following.value) toLatest()
}
watch(() => props.messages.length, async () => {
  following.value = true
  await nextTick()
  follow()
})
onMounted(() => {
  timer = setInterval(() => { now.value = Date.now() }, 100)
  observer = new ResizeObserver(follow)
  if (content.value) observer.observe(content.value)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer); observer?.disconnect() })
</script>

<template>
  <div ref="viewport" class="viewport" @scroll="scroll">
    <div ref="content" class="content">
      <div v-if="!messages.length" class="welcome">
        <span class="eyebrow">S Q U A D</span>
        <h1>有什么可以帮你？</h1>
        <p>从一个问题开始，把事情一起做好。</p>
      </div>
      <ChatMessageItem v-for="message in messages" :key="message.id" :message="message" :now="now" />
    </div>
  </div>
  <button v-if="!following" class="latest" aria-label="回到最新消息" @click="toLatest">
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 5v14m-6-6 6 6 6-6" /></svg>
  </button>
</template>

<style scoped>
.viewport { flex: 1; min-height: 0; overflow-y: auto; overflow-anchor: none; }
.content { max-width: 860px; margin: 0 auto; padding: 52px 28px 10px; }
.welcome { padding: min(18vh, 180px) 0 40px; text-align: center; }
.eyebrow { font-size: 12px; color: #93998b; }
h1 { font-size: 30px; font-weight: 500; margin: 22px 0 12px; letter-spacing: -1px; }
p { color: #9a9e92; font-size: 14px; }
.latest { display: inline-flex; align-items: center; justify-content: center; padding: 0; color: #74796b; position: absolute; bottom: 164px; left: calc(50% - 18px); border: 1px solid #e4e6de; background: white; border-radius: 50%; width: 36px; height: 36px; box-shadow: 0 3px 12px #00000008; cursor: pointer; }
.latest svg { display: block; width: 18px; height: 18px; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
@media (max-width: 600px) { .content { padding: 28px 18px 10px; } h1 { font-size: 25px; } }
</style>
