// SPDX-License-Identifier: Apache-2.0
// 순아래 키보드 — 한글 조합 세션 (단일 소유자)

package net.kjwon15.noshiftkeyboard.hangul;

import net.kjwon15.noshiftkeyboard.latin.RichInputConnection;

/**
 * 단어 커밋의 단일 소유자. 조합기(HangulCombiner)와 앵커(조합 시작 커서)를 한 곳에서 관리하고
 * 커밋 결정도 한 곳에서만 한다. InputLogic/RichInputConnection에 흩어진 휴리스틱을 제거하기 위한
 * 재설계 포인트다.
 *
 * <p>커밋은 명시적 이벤트에서만 일어난다: span 해제, non-jamo, 세션 리셋. reported vs expected 같은
 * 비동기 추측은 세션 밖에서 하지 않는다.
 */
public final class HangulCompositionSession {
    private static final int NO_SELECTION = -1;

    private final HangulCombiner combiner = new HangulCombiner();

    private int anchor = NO_SELECTION;
    private boolean hasAnchor = false;

    public boolean isComposing(boolean isKoreanLayout) {
        return isKoreanLayout && combiner.combiningStateFeedback().length() > 0;
    }

    public String displayText() {
        return combiner.combiningStateFeedback();
    }

    public void reset() {
        combiner.reset();
        anchor = NO_SELECTION;
        hasAnchor = false;
    }

    public void startIfNeeded(int expectedSel, boolean hasPos) {
        if (combiner.combiningStateFeedback().length() == 0) {
            anchor = expectedSel;
            hasAnchor = hasPos;
        }
    }

    public String feed(int codePoint) {
        return combiner.process(codePoint);
    }

    public String processDelete() {
        return combiner.processDelete();
    }

    /**
     * 에디터가 조합을 버렸을 때(커서 이동으로 span 해제)만 커밋한다.
     * stale 보고로 인한 단어 중간 커밋을 막기 위해 span 신호만 신뢰한다.
     * @return 커밋했으면 true
     */
    public boolean handleUpdateSelection(int newSelStart, int newSelEnd,
            int spanStart, int spanEnd, RichInputConnection conn, boolean isKoreanLayout) {
        if (!isComposing(isKoreanLayout)) return false;
        if (spanStart == -1 && spanEnd == -1 && hasAnchor) {
            commit(conn);
            return true;
        }
        return false;
    }

    public boolean commitIfCursorMoved(int reportedStart, int reportedEnd,
            int expectedStart, int expectedEnd,
            boolean hasCursorPos, boolean hasReported,
            RichInputConnection conn, boolean isKoreanLayout) {
        if (!isComposing(isKoreanLayout) || !hasReported || !hasCursorPos) return false;
        if (reportedStart == expectedStart && reportedEnd == expectedEnd) return false;
        if (hasAnchor && reportedStart == anchor) return false;
        commit(conn);
        conn.updateSelection(reportedStart, reportedEnd);
        return true;
    }

    public void commit(RichInputConnection conn) {
        conn.finishComposingText();
        reset();
    }

    // 테스트/관찰용
    public HangulCombiner combiner() { return combiner; }
    public int anchorForTest() { return anchor; }
    public boolean hasAnchorForTest() { return hasAnchor; }
}
