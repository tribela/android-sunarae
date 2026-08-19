// SPDX-License-Identifier: Apache-2.0
// 순아래 키보드 — 첫 글자 자모 분리 회귀 테스트 (mIsKoreanLayout stale-cache 레이스)

package net.kjwon15.noshiftkeyboard.latin.inputlogic;

import net.kjwon15.noshiftkeyboard.latin.LatinIME;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 첫 글자 자모 분리(첫 jamo가 setComposingText로 조합되지 않고 commitText로
 * 개별 커밋되어 "ㄱㅏ"처럼 분리 입력되는 현상) 회귀 테스트.
 *
 * <p>근본 원인: InputLogic의 {@code mIsKoreanLayout} 캐시는 {@code startInput()}에서만
 * 갱신되는데, onStartInputView의 {@code startInput()}이 지연/누락될 수 있는 레이스
 * 윈도우(orientation change, IME 재표시, 하드웨어 키보드 서프레스 등)가 존재한다.
 * 캐시가 아직 false인 상태에서 첫 jamo 키가 도착하면 {@code sendKeyCodePoint}가
 * 한글 조합기를 건너뛰고 jamo를 raw 커밋한다. 이후 subtype 변경 이벤트로 캐시가
 * true가 되면 두 번째 자모부터는 조합되므로 "첫 글자만 분리"되는 증상이 된다.</p>
 *
 * <p>수정 방향: 캐시 대신 라이브 {@link LatinIME#isKoreanLayout()}을 매번 조회해서
 * stale-cache 클래스 자체를 제거한다.</p>
 */
public class InputLogicFirstJamoCompositionTest {

    /** {@code isKoreanLayout()}이 항상 true인 테스트 전용 LatinIME. */
    private static final class FakeKoreanLatinIME extends LatinIME {
        @Override
        public boolean isKoreanLayout() {
            return true;
        }
    }

    /**
     * {@code startInput()}을 호출하지 않아 {@code mIsKoreanLayout} 캐시가 초기값
     * false인 상태를 시뮬레이션한다. 이 레이스 상태에서도 첫 자모부터 조합기에
     * 들어가 "가"로 조합되어야 한다.
     */
    @Test
    public void firstJamoComposesEvenWhenStartInputWasSkipped() {
        InputLogic logic = new InputLogic(new FakeKoreanLatinIME());

        logic.sendKeyCodePoint(0x3131); // ㄱ
        logic.sendKeyCodePoint(0x314F); // ㅏ

        assertEquals("가", logic.hangulCombiningFeedback());
    }

    /** startInput()이 정상 호출된 경우에도 첫 글자 조합이 동작하는지 보장. */
    @Test
    public void firstJamoComposesAfterNormalStartInput() {
        InputLogic logic = new InputLogic(new FakeKoreanLatinIME());
        logic.startInput();

        logic.sendKeyCodePoint(0x3131); // ㄱ
        logic.sendKeyCodePoint(0x314F); // ㅏ

        assertEquals("가", logic.hangulCombiningFeedback());
    }

    /**
     * 첫 jamo 입력 시점에 커서 위치를 아직 모른 채 anchor가 -1로 기록된 상태에서,
     * 호스트 앱이 (1) 조합 스팬 반영 커서와 (2) 원래(초기) 커서를 연달아 보고하면,
     * 두 번째 보고가 anchor(-1)와 달라 pre-fix 코드는 이를 사용자 커서 이동으로 오인해
     * 첫 조합을 조기 커밋하고 자모를 분리한다. 첫 보고가 expected를 확립한 뒤 두 번째
     * 보고가 commit 조건(hasCursorPosition && expected와 상이 && composing && anchor와
     * 상이)을 모두 충족하기 때문에, 보고가 하나뿐이면 재현되지 않는다.
     */
    @Test
    public void staleInitialSelectionReportDoesNotSplitFirstJamo() {
        InputLogic logic = new InputLogic(new FakeKoreanLatinIME());
        logic.startInput();

        // 커서를 모른 채 첫 jamo 입력 → anchor가 -1로 기록됨.
        logic.sendKeyCodePoint(0x3131); // ㄱ

        // (1) 호스트가 조합 스팬을 반영한 실제 커서 (1,1) 보고 → expected 확립.
        logic.onUpdateSelection(1, 1);

        // (2) 호스트가 원래(초기) 커서 (0,0)을 stale 재보고 → pre-fix는 이를
        // 사용자 커서 이동으로 오인해 첫 조합을 커밋한다.
        logic.onUpdateSelection(0, 0);

        // 두 번째 jamo가 첫 조합을 이어 "가"로 완성되어야 한다.
        logic.sendKeyCodePoint(0x314F); // ㅏ
        assertEquals("가", logic.hangulCombiningFeedback());
    }

    /**
     * 이전 입력 세션에서 남은 composing text가 {@code startInput()}으로 새 세션이
     * 시작된 뒤에도 살아남으면, 새 세션 첫 jamo의 {@code setComposingText} 산술을
     * 오염시켜 expected가 음수로 꼬이고, 이어지는 조합 스팬 반영 커서
     * {@code onUpdateSelection(1,1)}이 이를 사용자 커서 이동으로 오인해 첫 조합을
     * 조기 커밋하고 자모를 분리한다. {@code startInput()}은 combiner뿐 아니라
     * 연결의 composing text도 반드시 비워야 한다.
     *
     * <p>오염된 expected가 {@code INVALID_CURSOR_POSITION(-1)}과 같으면 오히려
     * 커밋이 억제되므로, 재현하려면 stale composition이 3자 이상(여기선 ㄱㄴㄷㄹ 4자,
     * 0-4+1=-3)이어야 한다.</p>
     */
    @Test
    public void staleComposingTextAcrossInputRestartDoesNotSplitFirstJamo() {
        InputLogic logic = new InputLogic(new FakeKoreanLatinIME());
        logic.startInput();

        // 이전 세션에서 "ㄱㄴㄷㄹ"(4자) 조합을 남긴다.
        logic.sendKeyCodePoint(0x3131); // ㄱ
        logic.sendKeyCodePoint(0x3134); // ㄴ
        logic.sendKeyCodePoint(0x3137); // ㄷ
        logic.sendKeyCodePoint(0x3139); // ㄹ

        // 새 입력 세션 시작: combiner가 리셋되고 composing text도 비워져야 한다.
        logic.startInput();

        // reloadTextCache가 새 필드의 initialSelStart(0)로 expected를 리셋하는 동작 재현.
        logic.mConnection.updateSelection(0, 0);

        // 새 세션 첫 jamo 입력.
        logic.sendKeyCodePoint(0x3141); // ㅁ

        // 조합 스팬 반영 커서 (1,1) 보고가 사용자 이동으로 오인되지 않아야 한다.
        logic.onUpdateSelection(1, 1);

        // 두 번째 jamo가 첫 조합을 이어 "마"로 완성되어야 한다.
        logic.sendKeyCodePoint(0x314F); // ㅏ
        assertEquals("마", logic.hangulCombiningFeedback());
    }
}