// SPDX-License-Identifier: Apache-2.0
// 순아래 키보드 — 순아래 한글 조합기 (순수 Java, Android 의존성 없음)

package net.kjwon15.noshiftkeyboard.hangul;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 순아래(noshift) 한글 조합기. HeiliBoard 작업에서 검증된 Kotlin
 * {@code helium314.keyboard.event.HangulCombiner}를 Android 의존성 없이 Java로 이식했다.
 *
 * <p>조합 규칙 (noshift 모드):</p>
 * <ul>
 *   <li>쌍자음: 초성 + 중성 + 같은 중성 → 초성 쌍자음 (ㄱㅏㅏ→까, ㅅㅣㅣ→씨, …)</li>
 *   <li>모음 연쇄: 기존 두벌식(ㅗㅏ→ㅘ, ㅡㅣ→ㅢ, …) + 순아래 확장(ㅑㅣ→ㅒ, ㅘㅣ→ㅙ, …)</li>
 *   <li>받침 조합: ㄹㄱ→ㄺ, ㄱㄱ→ㄲ, ㅅㅅ→ㅆ 등</li>
 *   <li>jamo 단위 backspace: history의 마지막 스냅샷을 pop (까→가→ㄱ→빈 상태)</li>
 * </ul>
 *
 * <p>상태는 {@link #composingWord()} (커밋된 텍스트)와 {@link #history()} (조합 단계
 * 스냅샷 목록)로 구성된다. 조합 규칙은 모두 jamo 입력 단계마다 history에 스냅샷을
 * push하므로 backspace가 jamo 단위 역추적이 된다.</p>
 */
public final class HangulCombiner {

    private final boolean noshift;
    private final StringBuilder composingWord = new StringBuilder();
    private final List<HangulSyllable> history = new ArrayList<>();

    /** 초성 쌍자음 치환표: 초성 + 중성 + 같은 중성 → 쌍초성. */
    private static final Map<Integer, Integer> SSANG_INITIALS = mapOfInts(
            0x1100, 0x1101, // ㄱ → ㄲ
            0x1103, 0x1104, // ㄷ → ㄸ
            0x1107, 0x1108, // ㅂ → ㅃ
            0x1109, 0x110A, // ㅅ → ㅆ
            0x110C, 0x110D  // ㅈ → ㅉ
    );

    /** 두벌식 표준 조합표. (Kotlin COMBINATION_TABLE_DUBEOLSIK, 무수정) */
    private static final Map<Long, Integer> COMBINATION_TABLE_DUBEOLSIK = combinationMap(
            0x1161, 0x1175, 0x1162, // ㅏ + ㅣ = ㅐ
            0x1165, 0x1175, 0x1166, // ㅓ + ㅣ = ㅔ
            0x1169, 0x1161, 0x116A, // ㅗ + ㅏ = ㅘ
            0x1169, 0x1162, 0x116B, // ㅗ + ㅐ = ㅙ
            0x1169, 0x1175, 0x116C, // ㅗ + ㅣ = ㅚ
            0x116E, 0x1165, 0x116F, // ㅜ + ㅓ = ㅝ
            0x116E, 0x1166, 0x1170, // ㅜ + ㅔ = ㅞ
            0x116E, 0x1175, 0x1171, // ㅜ + ㅣ = ㅟ
            0x1173, 0x1175, 0x1174, // ㅡ + ㅣ = ㅢ

            0x11A8, 0x11BA, 0x11AA, // ㄱ + ㅅ = ㄳ
            0x11AB, 0x11BD, 0x11AC, // ㄴ + ㅈ = ㄵ
            0x11AB, 0x11C2, 0x11AD, // ㄴ + ㅎ = ㄶ
            0x11AF, 0x11A8, 0x11B0, // ㄹ + ㄱ = ㄺ
            0x11AF, 0x11B7, 0x11B1, // ㄹ + ㅁ = ㄻ
            0x11AF, 0x11B8, 0x11B2, // ㄹ + ㅂ = ㄼ
            0x11AF, 0x11BA, 0x11B3, // ㄹ + ㅅ = ㄽ
            0x11AF, 0x11C0, 0x11B4, // ㄹ + ㅌ = ㄾ
            0x11AF, 0x11C1, 0x11B5, // ㄹ + ㅍ = ㄿ
            0x11AF, 0x11C2, 0x11B6, // ㄹ + ㅎ = ㅀ
            0x11B8, 0x11BA, 0x11B9  // ㅂ + ㅅ = ㅄ
    );

    /** 순아래 전용 확장 조합표. 표준 조회 실패 후에만 사용. */
    private static final Map<Long, Integer> COMBINATION_TABLE_DUBEOLSIK_NOSHIFT = combinationMap(
            0x1163, 0x1175, 0x1164, // ㅑ + ㅣ = ㅒ
            0x1167, 0x1175, 0x1168, // ㅕ + ㅣ = ㅖ
            0x116A, 0x1175, 0x116B, // ㅘ + ㅣ = ㅙ
            0x116F, 0x1175, 0x1170, // ㅝ + ㅣ = ㅞ

            0x11A8, 0x11A8, 0x11A9, // ㄱ + ㄱ = ㄲ
            0x11BA, 0x11BA, 0x11BB  // ㅅ + ㅅ = ㅆ
    );

    /** 세벌식 표준 초성/중성/종성 직접 입력 조합표. (Kotlin COMBINATION_TABLE_SEBEOLSIK, 무수정) */
    private static final Map<Long, Integer> COMBINATION_TABLE_SEBEOLSIK = combinationMap(
            0x1100, 0x1100, 0x1101, // ㄱ + ㄱ = ㄲ
            0x1103, 0x1103, 0x1104, // ㄷ + ㄷ = ㄸ
            0x1107, 0x1107, 0x1108, // ㅂ + ㅂ = ㅃ
            0x1109, 0x1109, 0x110A, // ㅅ + ㅅ = ㅆ
            0x110C, 0x110C, 0x110D, // ㅈ + ㅈ = ㅉ

            0x1169, 0x1161, 0x116A, // ㅗ + ㅏ = ㅘ
            0x1169, 0x1162, 0x116B, // ㅗ + ㅐ = ㅙ
            0x1169, 0x1175, 0x116C, // ㅗ + ㅣ = ㅚ
            0x116E, 0x1165, 0x116F, // ㅜ + ㅓ = ㅝ
            0x116E, 0x1166, 0x1170, // ㅜ + ㅔ = ㅞ
            0x116E, 0x1175, 0x1171, // ㅜ + ㅣ = ㅟ
            0x1173, 0x1175, 0x1174, // ㅡ + ㅣ = ㅢ

            0x11A8, 0x11A8, 0x11A9, // ㄱ + ㄱ = ㄲ
            0x11A8, 0x11BA, 0x11AA, // ㄱ + ㅅ = ㄳ
            0x11AB, 0x11BD, 0x11AC, // ㄴ + ㅈ = ㄵ
            0x11AB, 0x11C2, 0x11AD, // ㄴ + ㅎ = ㄶ
            0x11AF, 0x11A8, 0x11B0, // ㄹ + ㄱ = ㄺ
            0x11AF, 0x11B7, 0x11B1, // ㄹ + ㅁ = ㄻ
            0x11AF, 0x11B8, 0x11B2, // ㄹ + ㅂ = ㄼ
            0x11AF, 0x11BA, 0x11B3, // ㄹ + ㅅ = ㄽ
            0x11AF, 0x11C0, 0x11B4, // ㄹ + ㅌ = ㄾ
            0x11AF, 0x11C1, 0x11B5, // ㄹ + ㅍ = ㄿ
            0x11AF, 0x11C2, 0x11B6, // ㄹ + ㅎ = ㅀ
            0x11B8, 0x11BA, 0x11B9, // ㅂ + ㅅ = ㅄ
            0x11BA, 0x11BA, 0x11BB  // ㅅ + ㅅ = ㅆ
    );

    /** 순아래(noshift) 조합기 생성. */
    public HangulCombiner() {
        this(true);
    }

    /**
     * @param noshift true면 순아래 규칙(쌍자음/모음연쇄 확장) 적용, false면 표준 두벌식만.
     */
    public HangulCombiner(boolean noshift) {
        this.noshift = noshift;
    }

    /** 현재 조합 중인 음절. 없으면 null. */
    public HangulSyllable currentSyllable() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    /** 조합 상태 피드백: 커밋된 텍스트 + 조합 중 음절. */
    public String combiningStateFeedback() {
        HangulSyllable syllable = currentSyllable();
        return composingWord.toString() + (syllable != null ? syllable.string() : "");
    }

    /** 커밋된 텍스트(조합이 확정된 부분). */
    public String composingWord() {
        return composingWord.toString();
    }

    /** 조합 단계 스냅샷 목록 (테스트/검사용). */
    public List<HangulSyllable> history() {
        return history;
    }

    /** 상태 초기화. */
    public void reset() {
        composingWord.setLength(0);
        history.clear();
    }

    /**
     * 한 문자(codePoint) 입력을 처리하고 조합 상태 피드백을 반환한다.
     * 한글 자모는 조합 규칙을 적용하고, 한글이 아닌 문자는 현재 조합을 커밋한 뒤
     * 그 문자를 커밋 텍스트로 추가한다.
     */
    public String process(int codePoint) {
        if (HangulJamo.typeOf(codePoint) == HangulJamo.Type.NON_HANGUL) {
            HangulSyllable current = currentSyllable();
            if (current != null) {
                composingWord.append(current.string());
            }
            composingWord.appendCodePoint(codePoint);
            history.clear();
            return combiningStateFeedback();
        }
        HangulSyllable current = currentSyllable();
        if (current == null) {
            current = new HangulSyllable();
        }
        switch (HangulJamo.typeOf(codePoint)) {
            case CONSONANT:
                processConsonant(codePoint, current);
                break;
            case VOWEL:
                processVowel(codePoint, current);
                break;
            case INITIAL:
                processInitial(codePoint, current);
                break;
            case MEDIAL:
                processMedial(codePoint, current);
                break;
            case FINAL:
                processFinal(codePoint, current);
                break;
            default:
                break;
        }
        return combiningStateFeedback();
    }

    /**
     * jamo 단위 backspace: history의 마지막 스냅샷을 pop한다 (까→가→ㄱ→빈 상태).
     * history가 비어 있으면 커밋된 텍스트의 마지막 문자를 지운다.
     */
    public String processDelete() {
        if (!history.isEmpty()) {
            history.remove(history.size() - 1);
        } else if (composingWord.length() > 0) {
            composingWord.deleteCharAt(composingWord.length() - 1);
        }
        return combiningStateFeedback();
    }

    /** codePoint가 한글 자모(조합 대상)인지 여부. */
    public static boolean isHangul(int codePoint) {
        return HangulJamo.isHangul(codePoint);
    }

    private void processConsonant(int codePoint, HangulSyllable current) {
        int initial = HangulJamo.compatConsonantToInitial(codePoint);
        int finalJamo = HangulJamo.compatConsonantToFinal(codePoint);
        if (current.initial() != HangulSyllable.NONE && current.medial() != HangulSyllable.NONE) {
            if (current.finalConsonant() == HangulSyllable.NONE) {
                int combination = get(COMBINATION_TABLE_DUBEOLSIK,
                        current.initial(), initial != HangulSyllable.NONE ? initial : -1);
                if (combination != HangulSyllable.NONE) {
                    history.add(current.withInitial(combination));
                } else if (finalJamo != HangulSyllable.NONE) {
                    history.add(current.withFinal(finalJamo));
                } else {
                    commit(current);
                    history.add(new HangulSyllable(initial, HangulSyllable.NONE, HangulSyllable.NONE));
                }
            } else {
                int pairSecond = finalJamo != HangulSyllable.NONE ? finalJamo : -1;
                int combination = dubeolsikCombination(current.finalConsonant(), pairSecond);
                if (combination != HangulSyllable.NONE) {
                    history.add(current.withFinal(combination, current.finalConsonant(), pairSecond));
                } else {
                    commit(current);
                    history.add(new HangulSyllable(initial, HangulSyllable.NONE, HangulSyllable.NONE));
                }
            }
        } else {
            commit(current);
            history.add(new HangulSyllable(initial, HangulSyllable.NONE, HangulSyllable.NONE));
        }
    }

    private void processVowel(int codePoint, HangulSyllable current) {
        int medial = HangulJamo.compatVowelToMedial(codePoint);
        if (current.finalConsonant() == HangulSyllable.NONE) {
            if (noshift && current.medial() != HangulSyllable.NONE
                    && current.initial() != HangulSyllable.NONE
                    && medial != HangulSyllable.NONE && current.medial() == medial
                    && SSANG_INITIALS.containsKey(current.initial())) {
                // 쌍자음 규칙: 초성+중성+같은 중성 → 초성 쌍자음 치환
                history.add(current.withInitial(SSANG_INITIALS.get(current.initial())));
            } else if (current.medial() != HangulSyllable.NONE) {
                int combination = dubeolsikCombination(current.medial(),
                        medial != HangulSyllable.NONE ? medial : -1);
                if (combination != HangulSyllable.NONE) {
                    history.add(current.withMedial(combination));
                } else {
                    commit(current);
                    history.add(new HangulSyllable(HangulSyllable.NONE, medial, HangulSyllable.NONE));
                }
            } else {
                history.add(current.withMedial(medial));
            }
        } else if (current.finalPairFirst() != HangulSyllable.NONE) {
            // 조합형 종성(ㄲ 등) 분리: 첫 자모는 종성 유지, 둘째 자모는 새 음절 초성으로
            history.remove(history.size() - 1);
            history.add(current.withFinal(current.finalPairFirst()));
            HangulSyllable syllable = currentSyllable();
            composingWord.append(syllable != null ? syllable.string() : "");
            history.clear();
            int initial = HangulJamo.compatConsonantToInitial(
                    HangulJamo.finalToCompatConsonant(current.finalPairSecond()));
            HangulSyllable newSyllable = new HangulSyllable(initial, HangulSyllable.NONE, HangulSyllable.NONE);
            history.add(newSyllable);
            history.add(newSyllable.withMedial(medial));
        } else {
            // 단일 종성 → 새 음절의 초성으로 이동 (발ㅏ → 바 + 라)
            history.remove(history.size() - 1);
            HangulSyllable syllable = currentSyllable();
            composingWord.append(syllable != null ? syllable.string() : "");
            history.clear();
            int initial = HangulJamo.compatConsonantToInitial(
                    HangulJamo.finalToCompatConsonant(current.finalConsonant()));
            HangulSyllable newSyllable = new HangulSyllable(initial, HangulSyllable.NONE, HangulSyllable.NONE);
            history.add(newSyllable);
            history.add(newSyllable.withMedial(medial));
        }
    }

    private void processInitial(int codePoint, HangulSyllable current) {
        if (current.initial() != HangulSyllable.NONE) {
            int combination = get(COMBINATION_TABLE_SEBEOLSIK, current.initial(), codePoint);
            if (combination != HangulSyllable.NONE
                    && current.medial() == HangulSyllable.NONE
                    && current.finalConsonant() == HangulSyllable.NONE) {
                history.add(current.withInitial(combination));
            } else {
                commit(current);
                history.add(new HangulSyllable(codePoint, HangulSyllable.NONE, HangulSyllable.NONE));
            }
        } else {
            history.add(current.withInitial(codePoint));
        }
    }

    private void processMedial(int codePoint, HangulSyllable current) {
        if (current.medial() != HangulSyllable.NONE) {
            int combination = get(COMBINATION_TABLE_SEBEOLSIK, current.medial(), codePoint);
            if (combination != HangulSyllable.NONE) {
                history.add(current.withMedial(combination));
            } else {
                commit(current);
                history.add(new HangulSyllable(HangulSyllable.NONE, codePoint, HangulSyllable.NONE));
            }
        } else {
            history.add(current.withMedial(codePoint));
        }
    }

    private void processFinal(int codePoint, HangulSyllable current) {
        if (current.finalConsonant() != HangulSyllable.NONE) {
            int combination = get(COMBINATION_TABLE_SEBEOLSIK, current.finalConsonant(), codePoint);
            if (combination != HangulSyllable.NONE) {
                history.add(current.withFinal(combination));
            } else {
                commit(current);
                history.add(new HangulSyllable(HangulSyllable.NONE, HangulSyllable.NONE, codePoint));
            }
        } else {
            history.add(current.withFinal(codePoint));
        }
    }

    /** 표준 두벌식 우선 → noshift 확장 순으로 조회. */
    private int dubeolsikCombination(int first, int second) {
        int result = get(COMBINATION_TABLE_DUBEOLSIK, first, second);
        if (result != HangulSyllable.NONE) {
            return result;
        }
        return noshift ? get(COMBINATION_TABLE_DUBEOLSIK_NOSHIFT, first, second) : HangulSyllable.NONE;
    }

    /** 현재 음절을 커밋 텍스트로 확정하고 history를 비운다. */
    private void commit(HangulSyllable current) {
        composingWord.append(current.string());
        history.clear();
    }

    private static int get(Map<Long, Integer> table, int first, int second) {
        Integer result = table.get(key(first, second));
        return result != null ? result : HangulSyllable.NONE;
    }

    private static long key(int first, int second) {
        return ((long) first << 16) | (second & 0xFFFFL);
    }

    private static Map<Integer, Integer> mapOfInts(int... kvs) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put(kvs[i], kvs[i + 1]);
        }
        return map;
    }

    private static Map<Long, Integer> combinationMap(int... triples) {
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < triples.length; i += 3) {
            map.put(key(triples[i], triples[i + 1]), triples[i + 2]);
        }
        return map;
    }
}
