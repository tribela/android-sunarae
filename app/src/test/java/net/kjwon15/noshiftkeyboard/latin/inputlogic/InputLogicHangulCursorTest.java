// SPDX-License-Identifier: Apache-2.0
// 순아래 키보드 — 한글 조합 중 커서 이동(손가락 탭) 후 입력 회귀 테스트

package net.kjwon15.noshiftkeyboard.latin.inputlogic;

import net.kjwon15.noshiftkeyboard.latin.LatinIME;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 수동 커서 이동 시 한글 조합 상태 동기화 검증.
 *
 * <p>스페이스바 스와이프 이동은 {@code onMoveCursorPointer}에서 조합을 먼저 커밋하지만,
 * 손가락으로 텍스트 필드를 직접 탭하면 {@link InputLogic#onUpdateSelection}만 호출된다.
 * 조합 상태를 커밋/리셋하지 않으면 이후 jamo 입력이 옛 조합에 덧붙여져서 커서 위치가 아닌
 * 문장 끝에 붙는 버그가 생긴다 (가나다라 + 중간 커서 + 하 → "가나하다라"가 아니라
 * "가나다라하").</p>
 */
public class InputLogicHangulCursorTest {

    /** {@code isKoreanLayout()}이 항상 true인 테스트 전용 LatinIME. */
    private static final class FakeKoreanLatinIME extends LatinIME {
        @Override
        public boolean isKoreanLayout() {
            return true;
        }
    }

    private InputLogic logic;

    @Before
    public void setUp() {
        logic = new InputLogic(new FakeKoreanLatinIME());
        logic.startInput();
        // 입력 시작 시 프레임워크가 보고하는 초기 커서 위치를 흉내 낸다. 이 호출이 있어야
        // RichInputConnection이 예상 커서 위치를 갖게 되고 이후 수동 이동을 감지할 수 있다.
        // (조합 중이 아니므로 composing span은 비활성(-1)이다.)
        logic.onUpdateSelection(0, 0, -1, -1);
    }

    /** compat jamo 코드포인트를 InputLogic을 통해 조합기에 전달한다. */
    private void send(int codePoint) {
        logic.sendKeyCodePoint(codePoint);
    }

    /** "가나다라"를 타이핑한 것과 동일한 조합 상태를 만든다. */
    private void typeGaNaDaRa() {
        send(0x3131); // ㄱ
        send(0x314F); // ㅏ
        send(0x3134); // ㄴ
        send(0x314F); // ㅏ
        send(0x3137); // ㄷ
        send(0x314F); // ㅏ
        send(0x3139); // ㄹ
        send(0x314F); // ㅏ
        assertEquals("가나다라", logic.hangulCombiningFeedback());
    }

    @Test
    public void manualCursorMoveCommitsCompositionAndResetsCombiner() {
        typeGaNaDaRa();

        // 사용자가 손으로 커서를 "가나" 뒤(위치 2)로 옮김. 대부분의 편집기는 커서를 옮기며
        // composing span을 해제(-1,-1)하므로, 이것이 권위 있는 "조합 포기" 신호가 된다.
        logic.onUpdateSelection(2, 2, -1, -1);

        // 조합이 확정되고 조합기가 리셋되어야 한다.
        assertEquals("", logic.hangulCombiningFeedback());

        // 이제 "하"를 치면 새 조합이 커서 위치에서 시작한다 → 편집기에 "가나하다라".
        send(0x314E); // ㅎ
        send(0x314F); // ㅏ
        assertEquals("하", logic.hangulCombiningFeedback());
    }

    @Test
    public void cursorMoveIntoComposingSpanMiddleStartsFreshAtNextKey() {
        typeGaNaDaRa();

        // 편집기가 span을 유지한 채 커서만 span 안(위치 2)으로 옮긴 드문 경우: 이 시점에선
        // 커밋하지 않는다 (span이 아직 활성이므로). 조합은 유지된다.
        logic.onUpdateSelection(2, 2, 0, 4);
        assertEquals("가나다라", logic.hangulCombiningFeedback());

        // 다음 jamo 입력 시점에 동기적으로 "커서가 옮겨졌음"을 감지해 조합을 커밋하므로,
        // "하"가 옛 조합 끝에 붙지 않고 커서 위치에서 새 조합을 시작한다 → "가나하다라".
        send(0x314E); // ㅎ
        send(0x314F); // ㅏ
        assertEquals("하", logic.hangulCombiningFeedback());
    }

    @Test
    public void imeDrivenSelectionUpdateKeepsComposition() {
        typeGaNaDaRa();

        // IME 자신이 setComposingText로 만든 선택 위치(4)와 활성 span이 프레임워크에서 그대로
        // 돌아온 경우: 수동 이동이 아니므로 조합을 유지해야 한다.
        logic.onUpdateSelection(4, 4, 0, 4);

        assertEquals("가나다라", logic.hangulCombiningFeedback());

        // 조합이 계속 이어져야 한다 (가나다라 + ㄱ → 가나다락, ㄱ은 라의 받침).
        send(0x3131); // ㄱ
        assertEquals("가나다락", logic.hangulCombiningFeedback());
    }

    @Test
    public void stalePreTypingCursorReportDoesNotSplitFirstSyllable() {
        // 첫 jamo 후 일부 편집기가 조합 전 커서(0)를 늦게 재전송한다. 이 stale 보고가
        // "커서 이동"으로 오인되면 첫 음절이 raw 자모로 분리된다 (ㄱㅏ 대신 ㄱ+ㅏ).
        send(0x3131); // ㄱ (조합 시작, 앵커 = 0)
        send(0x314F); // ㅏ

        // 조합 전 커서(0)가 그대로 조합 끝(1)으로 보고되는 게 아니라, 오래된 위치 0이 재전송.
        // span은 여전히 활성([0,1))이므로 onUpdateSelection에서 커밋되지 않아야 한다.
        logic.onUpdateSelection(0, 0, 0, 1);

        // stale 보고가 무시되어 "가" 조합이 유지되어야 한다 (첫 음절 분리 방지).
        assertEquals("가", logic.hangulCombiningFeedback());

        // 이후 jamo도 정상적으로 이어진다.
        send(0x3134); // ㄴ
        assertEquals("간", logic.hangulCombiningFeedback());
    }
}
