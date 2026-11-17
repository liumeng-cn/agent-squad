<script setup lang="ts">
import { computed } from 'vue'
import type { Avatar, AvatarPalette } from '../model/user'
const props = withDefaults(defineProps<{ avatar?: Avatar; size?: number }>(), { size: 36 })
const palettes: Record<AvatarPalette, [string, string]> = {
  BLUE: ['#315efb', '#edf2ff'], TEAL: ['#18776d', '#e5f3f0'], VIOLET: ['#7250b2', '#f0eaf8'], AMBER: ['#996000', '#fff2d9'],
  ROSE: ['#a74468', '#f9e8ef'], CYAN: ['#216e93', '#e6f2f8'], GREEN: ['#42713b', '#ecf4e8'], INDIGO: ['#4b56a4', '#edeffa'],
}
const valid = computed(() => props.avatar?.style === 'GEOMETRIC_V1' && /^[01]{15}$/.test(props.avatar.pattern) && props.avatar.paletteKey in palettes)
const palette = computed(() => valid.value ? palettes[props.avatar!.paletteKey] : ['#78818d', '#edf0f4'])
const cells = computed(() => Array.from({ length: 25 }, (_, index) => ({ x: index % 5, y: Math.floor(index / 5) }))
  .filter(({ x, y }) => valid.value && props.avatar!.pattern[y * 3 + [0, 1, 2, 1, 0][x]!] === '1'))
</script>
<template>
  <svg class="user-avatar" :width="size" :height="size" viewBox="0 0 48 48" aria-hidden="true">
    <rect width="48" height="48" rx="12" :fill="palette[1]" />
    <rect v-for="cell in cells" :key="`${cell.x}-${cell.y}`" :x="8 + cell.x * 6.4" :y="8 + cell.y * 6.4" width="6.4" height="6.4" :fill="palette[0]" />
    <circle v-if="!valid" cx="24" cy="24" r="7" :fill="palette[0]" />
  </svg>
</template>
<style scoped>.user-avatar { display:block;flex-shrink:0 }</style>
