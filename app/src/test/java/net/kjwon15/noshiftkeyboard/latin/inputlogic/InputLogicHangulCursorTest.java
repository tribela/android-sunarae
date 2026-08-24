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
        logic.onUpdateSelection(0, 0);
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

        // 사용자가 손으로 커서를 "가나" 뒤(위치 2)로 옮김: IME가 예상한 위치(4)와 다르다.
        logic.onUpdateSelection(2, 2);

        // 조합이 확정되고 조합기가 리셋되어야 한다.
        assertEquals("", logic.hangulCombiningFeedback());

        // 이제 "하"를 치면 새 조합이 커서 위치에서 시작한다 → 편집기에 "가나하다라".
        send(0x314E); // ㅎ
        send(0x314F); // ㅏ
        assertEquals("하", logic.hangulCombiningFeedback());
    }

    @Test
    public void imeDrivenSelectionUpdateKeepsComposition() {
        typeGaNaDaRa();

        // IME 자신이 setComposingText로 만든 선택 위치(4)가 프레임워크에서 그대로 돌아온 경우:
        // 수동 이동이 아니므로 조합을 유지해야 한다.
        logic.onUpdateSelection(4, 4);

        assertEquals("가나다라", logic.hangulCombiningFeedback());

        // 조합이 계속 이어져야 한다 (가나다라 + ㄱ → 가나다락, ㄱ은 라의 받침).
        send(0x3131); // ㄱ
        assertEquals("가나다락", logic.hangulCombiningFeedback());
    }

    @Test
    public void cursorMoveToBeginningOfComposingWordStartsFresh() {
        typeGaNaDaRa();

        // 단어 맨 앞으로 커서 이동 — 가나다라 조합을 커밋하고 리셋해야 한다.
        // 이전 버그: anchor(0)로의 이동을 stale로 오인해 커밋하지 않고 다음 자모가 끝에 붙어
        // "가나다람"이 됐다. 기대: "ㅁ가나다라".
        logic.onUpdateSelection(0, 0);

        assertEquals("", logic.hangulCombiningFeedback());

        send(0x3141); // ㅁ
        assertEquals("ㅁ", logic.hangulCombiningFeedback());
    }

    @Test
    public void incompleteSingleJamoAnchorMoveWithSpanClearedCommits() {
        send(0x3131); // ㄱ (미완성, 길이1)

        // 에디터가 span을 해제하며 anchor로 보고한 진짜 탭 이동 — 커밋해야 한다.
        // stale 재보고(span 유지)와 구분된다.
        logic.onUpdateSelection(0, 0, -1, -1);

        assertEquals("", logic.hangulCombiningFeedback());

        send(0x3141); // ㅁ
        assertEquals("ㅁ", logic.hangulCombiningFeedback());
    }

    @Test
    public void incompleteMultiJamoMoveToBeginningStartsFresh() {
        // ㄱㄴㄷㄹ 미완성 조합 (각 자모가 받침으로 안 붙어 길이 4)
        send(0x3131); // ㄱ
        send(0x3134); // ㄴ
        send(0x3137); // ㄷ
        send(0x3139); // ㄹ
        assertEquals("ㄱㄴㄷㄹ", logic.hangulCombiningFeedback());

        // 맨 앞으로 이동 — 미완성이어도 커밋하고 다음 입력은 맨 앞에서 fresh.
        logic.onUpdateSelection(0, 0);

        assertEquals("", logic.hangulCombiningFeedback());

        send(0x3141); // ㅁ
        assertEquals("ㅁ", logic.hangulCombiningFeedback());
    }

    @Test
    public void deleteSwipeUndoingWholeCompositionResetsAnchor() {
        send(0x3131); // ㄱ 미완성 단일 자모
        assertEquals("ㄱ", logic.hangulCombiningFeedback());

        // 조합 전체 소거: 빈 span 정리 + anchor 리셋까지 이뤄져야 한다.
        logic.handleDeleteSwipe();
        assertEquals("", logic.hangulCombiningFeedback());

        // 소거 직후의 selection 보고는 커밋을 유발하지 않고, 다음 자모는 fresh 시작.
        logic.onUpdateSelection(3, 3);
        send(0x3141); // ㅁ
        assertEquals("ㅁ", logic.hangulCombiningFeedback());
    }

    @Test
    public void repeatedDeleteSwipeEmptiesMultiSyllableComposition() {
        typeGaNaDaRa(); // 가나다라

        // jamo 단위 역추적을 조합이 완전히 빌 때까지 반복한다.
        // (스냅샷 수는 음절 경계 커밋에 따라 달라지므로 고정 횟수가 아닌 조건 반복)
        for (int i = 0; i < 32 && !logic.hangulCombiningFeedback().isEmpty(); i++) {
            logic.handleDeleteSwipe();
        }
        assertEquals("", logic.hangulCombiningFeedback());

        send(0x314F); // ㅏ
        assertEquals("ㅏ", logic.hangulCombiningFeedback());
    }

    @Test
    public void restartInputClearsPendingCompositionState() {
        send(0x3131); // ㄱ 조합 중
        assertEquals("ㄱ", logic.hangulCombiningFeedback());

        // 입력 세션 재시작: 잔여 조합 상태와 anchor가 완전히 리셋되어야 한다.
        logic.startInput();
        assertEquals("", logic.hangulCombiningFeedback());

        logic.onUpdateSelection(1, 1);
        send(0x314F); // ㅏ
        assertEquals("ㅏ", logic.hangulCombiningFeedback());
    }
}
