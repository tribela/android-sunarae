// SPDX-License-Identifier: Apache-2.0
// 순아래 키보드 — 한글 자모 분류/변환 유틸리티 (순수 Java, Android 의존성 없음)

package net.kjwon15.noshiftkeyboard.hangul;

/**
 * 한글 자모 분류와 호환 자모 ↔ 표준 자모 변환을 담당하는 순수 Java 유틸리티.
 *
 * <p>HeiliBoard 작업에서 검증된 Kotlin {@code HangulCombiner.HangulJamo} (sealed class)의
 * 자모 분류/변환 로직을 Android 의존성 없이 이식했다. 모든 자모는 Unicode codePoint(int)로
 * 표현하며, 변환표는 Kotlin의 {@code COMPAT_CONSONANTS}/{@code COMPAT_VOWELS}/
 * {@code CONVERT_INITIALS}/{@code CONVERT_MEDIALS}/{@code CONVERT_FINALS} 문자열을
 * int 배열로 옮긴 것이다.</p>
 *
 * <pre>
 *   초성(표준)  0x1100-0x1112  (ᄀ..ᅒ)
 *   중성(표준)  0x1161-0x1175  (ᅡ..ᅵ)
 *   종성(표준)  0x11A8-0x11C2  (ᆨ..ᇂ)
 *   자음(호환)  0x3131-0x314E  (ㄱ..ㅎ)
 *   모음(호환)  0x314F-0x3163  (ㅏ..ㅣ)
 * </pre>
 */
public final class HangulJamo {
    private HangulJamo() {
    }

    /** 자모 종류. */
    public enum Type {
        /** 표준 초성 (0x1100-0x115F). */
        INITIAL,
        /** 표준 중성 (0x1160-0x11A7). */
        MEDIAL,
        /** 표준 종성 (0x11A8-0x11FF). */
        FINAL,
        /** 호환 자음 (0x3131-0x314E). */
        CONSONANT,
        /** 호환 모음 (0x314F-0x3163). */
        VOWEL,
        /** 한글이 아닌 문자. */
        NON_HANGUL
    }

    /** 호환 자음 배열. index = ordinal (0x3131-0x314E 순서). */
    public static final int[] COMPAT_CONSONANTS = {
            0x3131, 0x3132, 0x3133, 0x3134, 0x3135, 0x3136, 0x3137, 0x3138, 0x3139, 0x313A,
            0x313B, 0x313C, 0x313D, 0x313E, 0x313F, 0x3140, 0x3141, 0x3142, 0x3143, 0x3144,
            0x3145, 0x3146, 0x3147, 0x3148, 0x3149, 0x314A, 0x314B, 0x314C, 0x314D, 0x314E
    };

    /** 호환 모음 배열. index = ordinal (0x314F-0x3163 순서). */
    public static final int[] COMPAT_VOWELS = {
            0x314F, 0x3150, 0x3151, 0x3152, 0x3153, 0x3154, 0x3155, 0x3156, 0x3157, 0x3158,
            0x3159, 0x315A, 0x315B, 0x315C, 0x315D, 0x315E, 0x315F, 0x3160, 0x3161, 0x3162,
            0x3163
    };

    /** 호환 자음 index → 표준 초성. 없으면 0. */
    public static final int[] CONVERT_INITIALS = {
            0x1100, 0x1101, 0x0000, 0x1102, 0x0000, 0x0000, 0x1103, 0x1104, 0x1105, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x1106, 0x1107, 0x1108, 0x0000,
            0x1109, 0x110A, 0x110B, 0x110C, 0x110D, 0x110E, 0x110F, 0x1110, 0x1111, 0x1112
    };

    /** 호환 모음 index → 표준 중성. */
    public static final int[] CONVERT_MEDIALS = {
            0x1161, 0x1162, 0x1163, 0x1164, 0x1165, 0x1166, 0x1167, 0x1168, 0x1169, 0x116A,
            0x116B, 0x116C, 0x116D, 0x116E, 0x116F, 0x1170, 0x1171, 0x1172, 0x1173, 0x1174,
            0x1175
    };

    /** 호환 자음 index → 표준 종성. 없으면 0. */
    public static final int[] CONVERT_FINALS = {
            0x11A8, 0x11A9, 0x11AA, 0x11AB, 0x11AC, 0x11AD, 0x11AE, 0x0000, 0x11AF, 0x11B0,
            0x11B1, 0x11B2, 0x11B3, 0x11B4, 0x11B5, 0x11B6, 0x11B7, 0x11B8, 0x0000, 0x11B9,
            0x11BA, 0x11BB, 0x11BC, 0x11BD, 0x0000, 0x11BE, 0x11BF, 0x11C0, 0x11C1, 0x11C2
    };

    /**
     * codePoint의 자모 종류를 반환한다.
     * (Kotlin {@code HangulJamo.of}와 동일 범위)
     */
    public static Type typeOf(int codePoint) {
        if (codePoint >= 0x3131 && codePoint <= 0x314E) {
            return Type.CONSONANT;
        }
        if (codePoint >= 0x314F && codePoint <= 0x3163) {
            return Type.VOWEL;
        }
        if (codePoint >= 0x1100 && codePoint <= 0x115F) {
            return Type.INITIAL;
        }
        if (codePoint >= 0x1160 && codePoint <= 0x11A7) {
            return Type.MEDIAL;
        }
        if (codePoint >= 0x11A8 && codePoint <= 0x11FF) {
            return Type.FINAL;
        }
        return Type.NON_HANGUL;
    }

    /** 한글 자모(codePoint)인지 여부. */
    public static boolean isHangul(int codePoint) {
        return typeOf(codePoint) != Type.NON_HANGUL;
    }

    /** 호환 자음 → 표준 초성. 변환 불가면 0. */
    public static int compatConsonantToInitial(int codePoint) {
        int idx = indexOf(COMPAT_CONSONANTS, codePoint);
        return idx < 0 ? 0 : CONVERT_INITIALS[idx];
    }

    /** 호환 자음 → 표준 종성. 변환 불가면 0. */
    public static int compatConsonantToFinal(int codePoint) {
        int idx = indexOf(COMPAT_CONSONANTS, codePoint);
        return idx < 0 ? 0 : CONVERT_FINALS[idx];
    }

    /** 호환 모음 → 표준 중성. 변환 불가면 0. */
    public static int compatVowelToMedial(int codePoint) {
        int idx = indexOf(COMPAT_VOWELS, codePoint);
        return idx < 0 ? 0 : CONVERT_MEDIALS[idx];
    }

    /** 표준 초성 → 호환 자음. 변환 불가면 0. */
    public static int initialToCompatConsonant(int codePoint) {
        int idx = indexOf(CONVERT_INITIALS, codePoint);
        return idx < 0 ? 0 : COMPAT_CONSONANTS[idx];
    }

    /** 표준 중성 → 호환 모음. 변환 불가면 0. */
    public static int medialToCompatVowel(int codePoint) {
        int idx = indexOf(CONVERT_MEDIALS, codePoint);
        return idx < 0 ? 0 : COMPAT_VOWELS[idx];
    }

    /** 표준 종성 → 호환 자음. 변환 불가면 0. */
    public static int finalToCompatConsonant(int codePoint) {
        int idx = indexOf(CONVERT_FINALS, codePoint);
        return idx < 0 ? 0 : COMPAT_CONSONANTS[idx];
    }

    /** codePoint를 문자열로 (BMP 포함 전 영역 지원). */
    public static String toString(int codePoint) {
        return Character.toString(codePoint);
    }

    private static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }
}
