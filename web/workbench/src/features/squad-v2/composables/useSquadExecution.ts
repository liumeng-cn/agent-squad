import { computed, reactive } from 'vue'
import type { SquadMsg } from '../../../shared/api/squadTypes'
import {
  applySquadEvent,
  createSquadExecutionState,
  getSquadAnswerText,
  getSquadTimeline,
  resetSquadExecution,
} from '../model/squadExecution'

export function useSquadExecution() {
  const state = reactive(createSquadExecutionState())
  const timeline = computed(() => getSquadTimeline(state))
  const agents = computed(() => [...state.agents.values()])
  const answerText = computed(() => getSquadAnswerText(state))
  const hasFinalAnswer = computed(() => state.finalAnswer !== null)

  function applyEvent(event: SquadMsg): boolean {
    return applySquadEvent(state, event)
  }

  function reset(): void {
    resetSquadExecution(state)
  }

  return {
    state,
    timeline,
    agents,
    answerText,
    hasFinalAnswer,
    applyEvent,
    reset,
  }
}
