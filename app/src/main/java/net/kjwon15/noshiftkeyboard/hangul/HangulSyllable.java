// SPDX-License-Identifier: Apache-2.0
// 순아래 키보드 — 조합 중 음절 상태 (순수 Java, Android 의존성 없음)

package net.kjwon15.noshiftkeyboard.hangul;

/**
 * 조합 중인 한 음절 상태. 초성/중성/종성을 표준 자모 codePoint(int)로 보관한다.
 *
 * <p>Kotlin {@code HangulCombiner.HangulSyllable} (data class)을 이식한 불변 클래스.
 * 자모가 없으면 해당 필드는 0이다. 조합형 종성(예: ㄲ = ㄱ+ㄱ)은 {@code finalPairFirst}/
 * {@code finalPairSecond}로 분해 가능한 자모쌍을 함께 보관한다 — 나중에 모음이 입력되면
 * 둘째 자모를 새 음절의 초성으로 옮기기 위함이다.</p>
 *
 * <pre>
 *   combined()   = 0xAC00 + (초성-0x1100)*21*28 + (중성-0x1161)*28 + (종성-0x11A7)
 *   combinable() = 현대 한글 규칙 충족 (초성/중성 존재, 종성은 현대 또는 없음)
 *   string()     = 조합 가능하면 완성형 음절, 아니면 호환 자모 나열
 * </pre>
 */
public final class HangulSyllable {

    public static final int NONE = 0;

    private final int initial;
    private final int medial;
    private final int finalConsonant;
    private final int finalPairFirst;
    private final int finalPairSecond;

    public HangulSyllable() {
        this(NONE, NONE, NONE, NONE, NONE);
    }

    public HangulSyllable(int initial, int medial, int finalConsonant) {
        this(initial, medial, finalConsonant, NONE, NONE);
    }

    public HangulSyllable(int initial, int medial, int finalConsonant,
                          int finalPairFirst, int finalPairSecond) {
        this.initial = initial;
        this.medial = medial;
        this.finalConsonant = finalConsonant;
        this.finalPairFirst = finalPairFirst;
        this.finalPairSecond = finalPairSecond;
    }

    public int initial() {
        return initial;
    }

    public int medial() {
        return medial;
    }

    public int finalConsonant() {
        return finalConsonant;
    }

    public int finalPairFirst() {
        return finalPairFirst;
    }

    public int finalPairSecond() {
        return finalPairSecond;
    }

    /** 현대 한글 규칙: 초성/중성 존재, 종성은 현대 또는 없음. */
    public boolean combinable() {
        return modernInitial(initial)
                && modernMedial(medial)
                && (finalConsonant == NONE || modernFinal(finalConsonant));
    }

    /** 완성형 음절 문자. (초성/중성 없으면 0으로 계산하되, combinable()이 아니면 호출자 책임) */
    public String combined() {
        int i = initial != NONE ? initial - 0x1100 : 0;
        int m = medial != NONE ? medial - 0x1161 : 0;
        int f = finalConsonant != NONE ? finalConsonant - 0x11A7 : 0;
        return Character.toString((char) (0xAC00 + i * 21 * 28 + m * 28 + f));
    }

    /** 표준 자모 나열 문자열 (예: 0x1100+0x1161 → "가"). */
    public String uncombined() {
        StringBuilder sb = new StringBuilder(3);
        if (initial != NONE) {
            sb.appendCodePoint(initial);
        }
        if (medial != NONE) {
            sb.appendCodePoint(medial);
        }
        if (finalConsonant != NONE) {
            sb.appendCodePoint(finalConsonant);
        }
        return sb.toString();
    }

    /** 호환 자모 나열 문자열 (예: 초성 ㄱ+중성 ㅏ → "ㄱㅏ"). */
    public String uncombinedCompat() {
        StringBuilder sb = new StringBuilder(3);
        if (initial != NONE) {
            sb.appendCodePoint(HangulJamo.initialToCompatConsonant(initial));
        }
        if (medial != NONE) {
            sb.appendCodePoint(HangulJamo.medialToCompatVowel(medial));
        }
        if (finalConsonant != NONE) {
            sb.appendCodePoint(HangulJamo.finalToCompatConsonant(finalConsonant));
        }
        return sb.toString();
    }

    /** 조합 가능하면 완성형 음절, 아니면 호환 자모 나열. */
    public String string() {
        return combinable() ? combined() : uncombinedCompat();
    }

    public HangulSyllable withInitial(int newInitial) {
        return new HangulSyllable(newInitial, medial, finalConsonant, finalPairFirst, finalPairSecond);
    }

    public HangulSyllable withMedial(int newMedial) {
        return new HangulSyllable(initial, newMedial, finalConsonant, finalPairFirst, finalPairSecond);
    }

    /** 단일 종성 설정 (조합형 종성 정보는 해제). */
    public HangulSyllable withFinal(int newFinal) {
        return new HangulSyllable(initial, medial, newFinal, NONE, NONE);
    }

    /** 조합형 종성 설정 (분해 자모쌍 보관). */
    public HangulSyllable withFinal(int newFinal, int pairFirst, int pairSecond) {
        return new HangulSyllable(initial, medial, newFinal, pairFirst, pairSecond);
    }

    private static boolean modernInitial(int cp) {
        return cp >= 0x1100 && cp <= 0x1112;
    }

    private static boolean modernMedial(int cp) {
        return cp >= 0x1161 && cp <= 0x1175;
    }

    private static boolean modernFinal(int cp) {
        return cp >= 0x11A8 && cp <= 0x11C2;
    }
}
