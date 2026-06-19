#!/usr/bin/env bash
set -euo pipefail

ROOT="${ROOT:-/home/justant/Data/Again-Spring}"
WORKERS="${WORKERS:-8}"
DRAFTS="${DRAFTS:-4}"

declare -A H2H_CONTEXTS=(
  [THEQOO]=20
  [CLIEN]=12
  [NATEPAN]=20
)

run_net() {
  bash "${ROOT}/.result/ai-user/scripts/run_python_in_dev_network.sh" "$@"
}

echo "[phase1] read-only live dev probe"
cd "${ROOT}"
python3 .result/ai-user/scripts/probe_dev_ai_user_stack.py

echo
echo "[phase1] strict runtime probe"
for community in THEQOO CLIEN NATEPAN; do
  echo "  - ${community}"
  run_net \
    .result/ai-user/scripts/probe_runtime_pipeline.py \
    --community "${community}" \
    --strict-runtime
done

echo
echo "[phase1] runtime h2h survey generation"
for community in THEQOO CLIEN NATEPAN; do
  echo "  - ${community} (${H2H_CONTEXTS[$community]} contexts)"
  run_net \
    .result/ai-user/scripts/build_h2h_survey.py \
    --community "${community}" \
    --generator runtime \
    --strict-runtime \
    --n-contexts "${H2H_CONTEXTS[$community]}" \
    --drafts "${DRAFTS}" \
    --workers "${WORKERS}"
done

echo
echo "[phase1] runtime A-B (공식 cond4 측정 — strict-runtime Claude)"
for community in THEQOO CLIEN NATEPAN; do
  sf=$(echo "${community}" | tr '[:upper:]' '[:lower:]')
  n_ctx=20
  [ "${community}" = "CLIEN" ] && n_ctx=12
  echo "  - ${community} (${n_ctx} contexts)"
  run_net \
    .result/ai-user/scripts/run_ab_test.py \
    --community "${community}" \
    --generator runtime \
    --strict-runtime \
    --n-contexts "${n_ctx}" \
    --drafts "${DRAFTS}" \
    --workers "${WORKERS}" \
    --source-filter "${sf}"
done

cat <<'EOF'

[phase1] next manual checkpoints
1. Answer:
   - .result/ai-user/blind/r13-h2h-theqoo-survey.md
   - .result/ai-user/blind/r13-h2h-clien-survey.md
   - .result/ai-user/blind/r13-h2h-natepan-survey.md
   - .result/ai-user/blind/r14-cond5-natepan-survey.md
2. Import survey answers:
   python3 .result/ai-user/scripts/import_survey_answers.py \
     --survey <survey.md> \
     --answers <answers-template.json> \
     --respondent owner
3. Summarize:
   python3 .result/ai-user/scripts/summarize_h2h_results.py --answers <h2h-answers.json>
   python3 .result/ai-user/scripts/summarize_cond5_results.py --answers .result/ai-user/blind/r14-cond5-natepan-answers-template.json

EOF
