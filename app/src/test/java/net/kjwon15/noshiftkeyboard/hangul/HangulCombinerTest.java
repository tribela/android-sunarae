// SPDX-License-Identifier: Apache-2.0
// 순아래 키보드 — HangulCombiner JUnit 테스트 (순수 JVM)

package net.kjwon15.noshiftkeyboard.hangul;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * 순아래(noshift) 조합 규칙 단위 테스트.
 *
 * <p>검증된 HeiliBoard 테스트(HangulNoshiftTest 27개 + HangulCombinerBaselineTest 14개)를
 * Java로 이식했다. 쌍자음 5종, 모음 연쇄, 받침 조합, jamo 단위 backspace, 표준 모드 회귀를
 * 커버한다.</p>
 */
public class HangulCombinerTest {

    private HangulCombiner combiner;

    @Before
    public void setUp() {
        combiner = new HangulCombiner(true);
    }

    private String sendKey(int codePoint) {
        combiner.process(codePoint);
        return combiner.combiningStateFeedback();
    }

    private String sendDelete() {
        combiner.processDelete();
        return combiner.combiningStateFeedback();
    }

    /** 표준 모드(noshift=false)에서 입력 순서별 피드백 목록 반환. */
    private List<String> standardCombinerFeedback(List<Integer> keys) {
        HangulCombiner standard = new HangulCombiner(false);
        List<String> result = new ArrayList<>();
        for (int cp : keys) {
            standard.process(cp);
            result.add(standard.combiningStateFeedback());
        }
        return result;
    }

    // ── 쌍자음 5종 (순아래 핵심) ──

    @Test
    public void ssangInitial_giyeok_doubleA() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("가", sendKey(0x314f));
        assertEquals("까", sendKey(0x314f));
    }

    @Test
    public void ssangInitial_tikeut_doubleEo() {
        assertEquals("ㄷ", sendKey(0x3137));
        assertEquals("더", sendKey(0x3153));
        assertEquals("떠", sendKey(0x3153));
    }

    @Test
    public void ssangInitial_bieup_doubleO() {
        assertEquals("ㅂ", sendKey(0x3142));
        assertEquals("보", sendKey(0x3157));
        assertEquals("뽀", sendKey(0x3157));
    }

    @Test
    public void ssangInitial_sios_doubleI() {
        assertEquals("ㅅ", sendKey(0x3145));
        assertEquals("시", sendKey(0x3163));
        assertEquals("씨", sendKey(0x3163));
    }

    @Test
    public void ssangInitial_cieuc_doubleU() {
        assertEquals("ㅈ", sendKey(0x3148));
        assertEquals("주", sendKey(0x315c));
        assertEquals("쭈", sendKey(0x315c));
    }

    // ── 쌍자음 불가 자음 ──

    @Test
    public void noSsangConsonant_doubleVowelCommits() {
        assertEquals("ㅁ", sendKey(0x3141));
        assertEquals("마", sendKey(0x314f));
        assertEquals("마ㅏ", sendKey(0x314f));
    }

    // ── 모음 연쇄 ──

    @Test
    public void vowelChain_o_a() {
        assertEquals("ㅗ", sendKey(0x3157));
        assertEquals("ㅘ", sendKey(0x314f));
    }

    @Test
    public void vowelChain_o_a_i() {
        assertEquals("ㅗ", sendKey(0x3157));
        assertEquals("ㅘ", sendKey(0x314f));
        assertEquals("ㅙ", sendKey(0x3163));
    }

    @Test
    public void vowelChain_u_eo() {
        assertEquals("ㅜ", sendKey(0x315c));
        assertEquals("ㅝ", sendKey(0x3153));
    }

    @Test
    public void vowelChain_u_eo_i() {
        assertEquals("ㅜ", sendKey(0x315c));
        assertEquals("ㅝ", sendKey(0x3153));
        assertEquals("ㅞ", sendKey(0x3163));
    }

    @Test
    public void vowelChain_ya_i() {
        assertEquals("ㅑ", sendKey(0x3151));
        assertEquals("ㅒ", sendKey(0x3163));
    }

    @Test
    public void vowelChain_yeo_i() {
        assertEquals("ㅕ", sendKey(0x3155));
        assertEquals("ㅖ", sendKey(0x3163));
    }

    @Test
    public void vowelChain_eu_i() {
        assertEquals("ㅡ", sendKey(0x3161));
        assertEquals("ㅢ", sendKey(0x3163));
    }

    @Test
    public void vowelChain_a_i_ae() {
        // wide 레이아웃에서 ㅐ 키가 제거됨 → ㅏ + ㅣ = ㅐ 조합
        assertEquals("ㅏ", sendKey(0x314f));
        assertEquals("ㅐ", sendKey(0x3163));
    }

    @Test
    public void vowelChain_eo_i_e() {
        // wide 레이아웃에서 ㅔ 키가 제거됨 → ㅓ + ㅣ = ㅔ 조합
        assertEquals("ㅓ", sendKey(0x3153));
        assertEquals("ㅔ", sendKey(0x3163));
    }

    // ── 받침 조합 ──

    @Test
    public void finalCombination_rieulGiyeok() {
        assertEquals("ㅂ", sendKey(0x3142));
        assertEquals("바", sendKey(0x314f));
        assertEquals("발", sendKey(0x3139));
        assertEquals("밝", sendKey(0x3131));
    }

    @Test
    public void finalCombination_ssangGiyeok() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("가", sendKey(0x314f));
        assertEquals("각", sendKey(0x3131));
        assertEquals("갂", sendKey(0x3131));
        HangulSyllable last = combiner.history().get(combiner.history().size() - 1);
        assertEquals(0x11a9, last.finalConsonant());
    }

    @Test
    public void finalCombination_ssangSios() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("가", sendKey(0x314f));
        assertEquals("갓", sendKey(0x3145));
        assertEquals("갔", sendKey(0x3145));
        HangulSyllable last = combiner.history().get(combiner.history().size() - 1);
        assertEquals(0x11bb, last.finalConsonant());
    }

    // ── jamo 단위 backspace ──

    @Test
    public void backspace_ssangUndo() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("가", sendKey(0x314f));
        assertEquals("까", sendKey(0x314f));
        assertEquals("가", sendDelete());
        assertEquals("ㄱ", sendDelete());
        assertEquals("", sendDelete());
    }

    @Test
    public void backspace_vowelRemoved() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("가", sendKey(0x314f));
        assertEquals("ㄱ", sendDelete());
    }

    @Test
    public void backspace_initialRemoved() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("", sendDelete());
    }

    @Test
    public void backspace_finalRemoved() {
        assertEquals("ㅂ", sendKey(0x3142));
        assertEquals("바", sendKey(0x314f));
        assertEquals("발", sendKey(0x3139));
        assertEquals("바", sendDelete());
    }

    @Test
    public void backspace_composedWord() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("가", sendKey(0x314f));
        assertEquals("까", sendKey(0x314f));
        assertEquals("까ㅏ", sendKey(0x314f));
        assertEquals("까", sendDelete());
        assertEquals("", sendDelete());
    }

    @Test
    public void backspace_ssangFinalUndo() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("가", sendKey(0x314f));
        assertEquals("까", sendKey(0x314f));
        assertEquals("깍", sendKey(0x3131));
        assertEquals("깎", sendKey(0x3131));
        assertEquals("깍", sendDelete());
        assertEquals("까", sendDelete());
        assertEquals("가", sendDelete());
    }

    @Test
    public void backspace_emptyState_noop() {
        assertEquals("", sendDelete());
    }

    @Test
    public void backspace_standardMode_unchanged() {
        HangulCombiner standard = new HangulCombiner(false);
        standard.process(0x3131);
        assertEquals("ㄱ", standard.combiningStateFeedback());
        standard.process(0x314f);
        assertEquals("가", standard.combiningStateFeedback());
        standard.process(0x314f);
        assertEquals("가ㅏ", standard.combiningStateFeedback());
        standard.processDelete();
        assertEquals("가", standard.combiningStateFeedback());
    }

    // ── 표준 모드 회귀 (noshift 규칙이 표준 동작을 침범하지 않음) ──

    @Test
    public void standardMode_isolated_doubleVowel() {
        assertEquals(java.util.Arrays.asList("ㄱ", "가", "가ㅏ"),
                standardCombinerFeedback(java.util.Arrays.asList(0x3131, 0x314f, 0x314f)));
    }

    @Test
    public void standardMode_isolated_wa_i() {
        assertEquals(java.util.Arrays.asList("ㅗ", "ㅘ", "ㅘㅣ"),
                standardCombinerFeedback(java.util.Arrays.asList(0x3157, 0x314f, 0x3163)));
    }

    @Test
    public void standardMode_isolated_ssangFinal() {
        assertEquals(java.util.Arrays.asList("ㄱ", "가", "각", "각ㄱ"),
                standardCombinerFeedback(java.util.Arrays.asList(0x3131, 0x314f, 0x3131, 0x3131)));
    }

    @Test
    public void standardMode_a_i_ae() {
        // ㅏ + ㅣ = ㅐ 조합은 표준/와이드 공통 허용
        assertEquals(java.util.Arrays.asList("ㅏ", "ㅐ"),
                standardCombinerFeedback(java.util.Arrays.asList(0x314f, 0x3163)));
    }

    @Test
    public void standardMode_eo_i_e() {
        // ㅓ + ㅣ = ㅔ 조합은 표준/와이드 공통 허용
        assertEquals(java.util.Arrays.asList("ㅓ", "ㅔ"),
                standardCombinerFeedback(java.util.Arrays.asList(0x3153, 0x3163)));
    }

    // ── 기본 조합 (표준 두벌식과 공통) ──

    @Test
    public void singleConsonant() {
        assertEquals("ㄱ", sendKey(0x3131));
    }

    @Test
    public void singleVowel() {
        assertEquals("ㅏ", sendKey(0x314f));
    }

    @Test
    public void basicCombination_giyeok_a() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("가", sendKey(0x314f));
    }

    @Test
    public void basicCombination_bieup_a_rieu_giyeok() {
        assertEquals("ㅂ", sendKey(0x3142));
        assertEquals("바", sendKey(0x314f));
        assertEquals("발", sendKey(0x3139));
        assertEquals("밝", sendKey(0x3131));
    }

    @Test
    public void basicCombination_hieuh_a_nieun() {
        assertEquals("ㅎ", sendKey(0x314e));
        assertEquals("하", sendKey(0x314f));
        assertEquals("한", sendKey(0x3134));
    }

    @Test
    public void vowelCombination_o_i() {
        assertEquals("ㅗ", sendKey(0x3157));
        assertEquals("ㅚ", sendKey(0x3163));
    }

    @Test
    public void vowelCombination_eu_i() {
        assertEquals("ㅡ", sendKey(0x3161));
        assertEquals("ㅢ", sendKey(0x3163));
    }

    @Test
    public void consonantSequence() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("ㄱㄴ", sendKey(0x3134));
    }

    @Test
    public void resetClearsState() {
        assertEquals("ㄱ", sendKey(0x3131));
        combiner.reset();
        assertEquals("", combiner.combiningStateFeedback());
    }

    // ── 받침 → 다음 모음 이동 ──

    @Test
    public void finalMovesToNextInitial() {
        assertEquals("ㅂ", sendKey(0x3142));
        assertEquals("바", sendKey(0x314f));
        assertEquals("발", sendKey(0x3139));
        assertEquals("바라", sendKey(0x314f));
    }

    @Test
    public void nonHangulCommitsComposingState() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("가", sendKey(0x314f));
        assertEquals("가 ", sendKey(' '));
    }

    @Test
    public void ssangFinalMovesToNextInitial() {
        assertEquals("ㄱ", sendKey(0x3131));
        assertEquals("가", sendKey(0x314f));
        assertEquals("각", sendKey(0x3131));
        assertEquals("갂", sendKey(0x3131));
        assertEquals("각가", sendKey(0x314f));
    }
}
