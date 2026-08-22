/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2025 Camille019
 * Copyright (C) 2023 Md. Rifat Hasan Jihan
 * Copyright (C) 2021 wittmane
 * Copyright (C) 2019 Emmanuel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kjwon15.noshiftkeyboard.latin.inputlogic;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import net.kjwon15.noshiftkeyboard.event.Event;
import net.kjwon15.noshiftkeyboard.event.InputTransaction;
import net.kjwon15.noshiftkeyboard.hangul.HangulCombiner;
import net.kjwon15.noshiftkeyboard.latin.LatinIME;
import net.kjwon15.noshiftkeyboard.latin.RichInputConnection;
import net.kjwon15.noshiftkeyboard.latin.common.Constants;
import net.kjwon15.noshiftkeyboard.latin.common.StringUtils;
import net.kjwon15.noshiftkeyboard.latin.settings.SettingsValues;
import net.kjwon15.noshiftkeyboard.latin.utils.InputTypeUtils;
import net.kjwon15.noshiftkeyboard.latin.utils.RecapitalizeStatus;
import net.kjwon15.noshiftkeyboard.latin.utils.SubtypeLocaleUtils;

/**
 * This class manages the input logic.
 */
public final class InputLogic {
    private static final String TAG = "InputLogic";
    // TODO : Remove this member when we can.
    final LatinIME mLatinIME;

    // This has package visibility so it can be accessed from InputLogicHandler.
    public final RichInputConnection mConnection;
    private final RecapitalizeStatus mRecapitalizeStatus = new RecapitalizeStatus();

    /**
     * Hangul jamo combiner for Korean layouts. Jamo code points are routed here instead of being
     * committed directly; {@link #mConnection#setComposingText} shows the composition in progress
     * and it is finalized with {@link #mConnection#finishComposingText()} when a non-jamo key is
     * pressed. Only active while the current subtype is a Korean layout.
     */
    private final HangulCombiner mHangulCombiner = new HangulCombiner();

    /**
     * Anchor (editor selection) where the current Hangul composition began. Used by
     * {@link #onUpdateSelection} to ignore a stale re-report of the pre-typing cursor at the
     * start of an input session: some apps re-send the initial cursor after the first keystroke,
     * and committing on that would prematurely finalize the first jamo and split the first
     * syllable into raw jamo ("ㄱㅏ" instead of "가"). {@code NO_SELECTION} while idle.
     */
    private static final int NO_SELECTION = -1;
    private int mCompositionStartSelection = NO_SELECTION;
    /**
     * Whether {@link #mCompositionStartSelection} holds a real editor position. The anchor is
     * only meaningful when the IME knows the cursor; {@code NO_SELECTION} (-1) is
     * indistinguishable from {@code RichInputConnection.INVALID_CURSOR_POSITION} (-1) when no
     * selection report has arrived yet, so the int alone cannot tell "unknown cursor" from
     * "known cursor at -1". Auto-commit on selection change is disabled while the anchor is
     * unknown so a stale/late initial selection report cannot split the first jamo.
     */
    private boolean mHasCompositionStartSelection = false;

    /**
     * Create a new instance of the input logic.
     * @param latinIME the instance of the parent LatinIME. We should remove this when we can.
     * dictionary.
     */
    public InputLogic(final LatinIME latinIME) {
        mLatinIME = latinIME;
        mConnection = new RichInputConnection(latinIME);
    }

    /**
     * Initializes the input logic for input in an editor.
     *
     * Call this when input starts or restarts in some editor (typically, in onStartInputView).
     */
    public void startInput() {
        mRecapitalizeStatus.disable(); // Do not perform recapitalize until the cursor is moved once
        mHangulCombiner.reset();
        mCompositionStartSelection = NO_SELECTION;
        mHasCompositionStartSelection = false;
        mConnection.clearComposingText();
    }

    public void clearCaches() {
        mConnection.clearCaches();
    }

    /**
     * Call this when the subtype changes.
     */
    public void onSubtypeChanged() {
        startInput();
    }

    /**
     * Whether a Hangul composition is currently in progress.
     *
     * <p>Queries the live subtype state instead of a cached flag: the flag was only refreshed in
     * {@link #startInput()}, which can be delayed during IME lifecycle races, leaving the first
     * jamo keypress to fall through to {@code commitText} and split the syllable into raw jamo.</p>
     */
    private boolean isComposing() {
        return mLatinIME.isKoreanLayout() && mHangulCombiner.combiningStateFeedback().length() > 0;
    }

    /**
     * Finalize the current Hangul composition to the editor and clear the combiner state.
     * Must only be called while {@link #isComposing()}.
     */
    private void commitComposingText() {
        mConnection.finishComposingText();
        mHangulCombiner.reset();
        mCompositionStartSelection = NO_SELECTION;
        mHasCompositionStartSelection = false;
    }

    /**
     * Finalize the current Hangul composition if one is in progress.
     * Used before cursor moves (space swipe) so the composing region is committed first.
     */
    public void commitComposingTextIfAny() {
        if (isComposing()) {
            commitComposingText();
        }
    }

    /**
     * Delete one unit as the delete swipe moves. While a Hangul composition is in
     * progress this undoes one jamo (same as backspace); otherwise it deletes the
     * selected text or the character before the cursor.
     */
    public void handleDeleteSwipe() {
        if (isComposing()) {
            final String after = mHangulCombiner.processDelete();
            if (after.length() == 0) {
                mConnection.setComposingText("", 1);
            } else {
                mConnection.setComposingText(after, 1);
            }
            return;
        }
        if (mConnection.hasSelection()) {
            mConnection.deleteSelectedText();
        } else {
            final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
            if (codePointBeforeCursor == Constants.NOT_A_CODE) {
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL);
            } else {
                final int numChars = Character.isSupplementaryCodePoint(codePointBeforeCursor) ? 2 : 1;
                mConnection.deleteTextBeforeCursor(numChars);
            }
        }
    }

    /**
     * React to a string input.
     *
     * This is triggered by keys that input many characters at once, like the ".com" key or
     * some additional keys for example.
     *
     * @param settingsValues the current values of the settings.
     * @param event the input event containing the data.
     * @return the complete transaction object
     */
    public InputTransaction onTextInput(final SettingsValues settingsValues, final Event event) {
        final String rawText = event.getTextToCommit().toString();
        final InputTransaction inputTransaction = new InputTransaction(settingsValues);
        final String text = performSpecificTldProcessingOnTextInput(rawText);
        mConnection.commitText(text, 1);
        // Space state must be updated before calling updateShiftState
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);
        return inputTransaction;
    }

    /**
     * Consider an update to the cursor position. Evaluate whether this update has happened as
     * part of normal typing or whether it was an explicit cursor move by the user. In any case,
     * do the necessary adjustments.
     *
     * <p>When the user moves the cursor by hand (tapping the text field, hardware arrow keys,
     * selection handles) the reported selection differs from the one the IME set itself, which
     * is exactly what {@link RichInputConnection} tracks the expected selection for. Any Hangul
     * composition in progress then refers to a stale position: typing a jamo afterwards would
     * append it to the old composition and {@code setComposingText} would replace the whole
     * composing region instead of inserting at the cursor (e.g. "가나다라" + cursor moved to the
     * middle + "하" would yield "가나다라하" instead of "가나하다라"). Finalize the composition
     * and reset the combiner so the next key starts a fresh composition at the new cursor.
     *
     * @param newSelStart new selection start
     * @param newSelEnd new selection end
     */
    private boolean isLastComposingSyllableComplete() {
        final String fb = mHangulCombiner.combiningStateFeedback();
        if (fb.isEmpty()) return false;
        final char last = fb.charAt(fb.length() - 1);
        return last >= 0xAC00 && last <= 0xD7A3;
    }

    // 테스트용 오버로드 — 실제 에디터는 span을 함께 주지만 단위테스트는 2인자로 호출한다.
    public void onUpdateSelection(final int newSelStart, final int newSelEnd) {
        if (isComposing() && mHasCompositionStartSelection) {
            onUpdateSelection(newSelStart, newSelEnd,
                    mCompositionStartSelection, mConnection.getExpectedSelectionStart());
        } else {
            onUpdateSelection(newSelStart, newSelEnd, -1, -1);
        }
    }

    public void onUpdateSelection(final int newSelStart, final int newSelEnd,
            final int spanStart, final int spanEnd) {
        final boolean isAnchorMove = mHasCompositionStartSelection
                && newSelStart == mCompositionStartSelection;
        final boolean isSpanCleared = spanStart == -1 && spanEnd == -1;
        // stale 재보고는 span이 유지된 채 anchor로 돌아오고, 진짜 탭 이동은 span이 해제된다.
        // 단일 미완성 자모(ㄱ)라도 span이 해제됐으면 진짜 이동으로 보고 커밋한다.
        final boolean shouldIgnoreAnchorMove = isAnchorMove
                && !isSpanCleared
                && !isLastComposingSyllableComplete()
                && mHangulCombiner.combiningStateFeedback().length() <= 1;
        final boolean isRealCommit;
        if (!mConnection.hasCursorPosition() || !isComposing() || !mHasCompositionStartSelection) {
            isRealCommit = false;
        } else if (newSelStart == mConnection.getExpectedSelectionStart()
                && newSelEnd == mConnection.getExpectedSelectionEnd()) {
            isRealCommit = false;
        } else if (shouldIgnoreAnchorMove) {
            isRealCommit = false;
        } else {
            isRealCommit = true;
        }
        if (isRealCommit) {
            Log.w(TAG, "onUpdateSelection(" + newSelStart + "," + newSelEnd
                    + ") committed composition: expected="
                    + mConnection.getExpectedSelectionStart() + ","
                    + mConnection.getExpectedSelectionEnd()
                    + " anchor=" + mCompositionStartSelection
                    + " hasAnchor=" + mHasCompositionStartSelection
                    + " composing=" + mHangulCombiner.combiningStateFeedback());
            commitComposingText();
        }
        mConnection.updateSelection(newSelStart, newSelEnd);
    }

    public void reloadTextCache() {
        mConnection.reloadTextCache();

        mRecapitalizeStatus.enable();
        mRecapitalizeStatus.stop();
    }

    /**
     * React to a code input. It may be a code point to insert, or a symbolic value that influences
     * the keyboard behavior.
     *
     * Typically, this is called whenever a key is pressed on the software keyboard. This is not
     * the entry point for gesture input; see the onBatchInput* family of functions for this.
     *
     * @param settingsValues the current settings values.
     * @param event the event to handle.
     * @return the complete transaction object
     */
    public InputTransaction onCodeInput(final SettingsValues settingsValues, final Event event) {
        final InputTransaction inputTransaction = new InputTransaction(settingsValues);

        Event currentEvent = event;
        while (null != currentEvent) {
            if (currentEvent.isConsumed()) {
                handleConsumedEvent(currentEvent);
            } else if (currentEvent.isFunctionalKeyEvent()) {
                handleFunctionalEvent(currentEvent, inputTransaction);
            } else {
                handleNonFunctionalEvent(currentEvent, inputTransaction);
            }
            currentEvent = currentEvent.mNextEvent;
        }
        return inputTransaction;
    }

    /**
     * Handle a consumed event.
     *
     * Consumed events represent events that have already been consumed, typically by the
     * combining chain.
     *
     * @param event The event to handle.
     */
    private void handleConsumedEvent(final Event event) {
        // A consumed event may have text to commit and an update to the composing state, so
        // we evaluate both. With some combiners, it's possible than an event contains both
        // and we enter both of the following if clauses.
        final CharSequence textToCommit = event.getTextToCommit();
        if (!TextUtils.isEmpty(textToCommit)) {
            mConnection.commitText(textToCommit, 1);
        }
    }

    /**
     * Handle a functional key event.
     *
     * A functional event is a special key, like delete, shift, emoji, or the settings key.
     * Non-special keys are those that generate a single code point.
     * This includes all letters, digits, punctuation, separators, emoji. It excludes keys that
     * manage keyboard-related stuff like shift, language switch, settings, layout switch, or
     * any key that results in multiple code points like the ".com" key.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleFunctionalEvent(final Event event, final InputTransaction inputTransaction) {
        switch (event.mKeyCode) {
            case Constants.CODE_DELETE:
                handleBackspaceEvent(event, inputTransaction);
                // Backspace is a functional key, but it affects the contents of the editor.
                break;
            case Constants.CODE_SHIFT:
                performRecapitalization();
                inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);
                break;
            case Constants.CODE_CAPSLOCK:
                // Note: Changing keyboard to shift lock state is handled in
                // {@link KeyboardSwitcher#onEvent(Event)}.
                break;
            case Constants.CODE_SYMBOL_SHIFT:
                // Note: Calling back to the keyboard on the symbol Shift key is handled in
                // {@link #onPressKey(int,int,boolean)} and {@link #onReleaseKey(int,boolean)}.
                break;
            case Constants.CODE_SWITCH_ALPHA_SYMBOL:
                // Note: Calling back to the keyboard on symbol key is handled in
                // {@link #onPressKey(int,int,boolean)} and {@link #onReleaseKey(int,boolean)}.
                break;
            case Constants.CODE_SETTINGS:
                if (isComposing()) {
                    commitComposingText();
                }
                onSettingsKeyPressed();
                break;
            case Constants.CODE_PASTE:
                if (isComposing()) {
                    commitComposingText();
                }
                mConnection.pasteClipboard();
                break;
            case Constants.CODE_ACTION_NEXT:
                if (isComposing()) {
                    commitComposingText();
                }
                performEditorAction(EditorInfo.IME_ACTION_NEXT);
                break;
            case Constants.CODE_ACTION_PREVIOUS:
                if (isComposing()) {
                    commitComposingText();
                }
                performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
                break;
            case Constants.CODE_LANGUAGE_SWITCH:
                if (isComposing()) {
                    // Finalize the composition so the composing text is committed before the
                    // subtype change (avoids the composing text being dropped on layout switch).
                    commitComposingText();
                }
                handleLanguageSwitchKey();
                break;
            case Constants.CODE_SHIFT_ENTER:
                if (isComposing()) {
                    commitComposingText();
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER, KeyEvent.META_SHIFT_ON);
                // Shift + Enter is not supported in all devices
                break;
            default:
                throw new RuntimeException("Unknown key code : " + event.mKeyCode);
        }
    }

    /**
     * Handle an event that is not a functional event.
     *
     * These events are generally events that cause input, but in some cases they may do other
     * things like trigger an editor action.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleNonFunctionalEvent(final Event event,
            final InputTransaction inputTransaction) {
        switch (event.mCodePoint) {
            case Constants.CODE_ENTER:
                if (isComposing()) {
                    commitComposingText();
                }
                final EditorInfo editorInfo = getCurrentInputEditorInfo();
                final int imeOptionsActionId =
                        InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo);
                if (InputTypeUtils.IME_ACTION_CUSTOM_LABEL == imeOptionsActionId) {
                    // Either we have an actionLabel and we should performEditorAction with
                    // actionId regardless of its value.
                    performEditorAction(editorInfo.actionId);
                } else if (EditorInfo.IME_ACTION_NONE != imeOptionsActionId) {
                    // We didn't have an actionLabel, but we had another action to execute.
                    // EditorInfo.IME_ACTION_NONE explicitly means no action. In contrast,
                    // EditorInfo.IME_ACTION_UNSPECIFIED is the default value for an action, so it
                    // means there should be an action and the app didn't bother to set a specific
                    // code for it - presumably it only handles one. It does not have to be treated
                    // in any specific way: anything that is not IME_ACTION_NONE should be sent to
                    // performEditorAction.
                    performEditorAction(imeOptionsActionId);
                } else {
                    // No action label, and the action from imeOptions is NONE: this is a regular
                    // enter key that should input a carriage return.
                    handleNonSpecialCharacterEvent(event, inputTransaction);
                }
                break;
            default:
                handleNonSpecialCharacterEvent(event, inputTransaction);
                break;
        }
    }

    /**
     * Handle inputting a code point to the editor.
     *
     * Non-special keys are those that generate a single code point.
     * This includes all letters, digits, punctuation, separators, emoji. It excludes keys that
     * manage keyboard-related stuff like shift, language switch, settings, layout switch, or
     * any key that results in multiple code points like the ".com" key.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleNonSpecialCharacterEvent(final Event event,
            final InputTransaction inputTransaction) {
        final int codePoint = event.mCodePoint;
        // Any non-jamo input finalizes a pending Hangul composition first (space, punctuation,
        // symbols, etc.). Jamo keys pass through to {@link #sendKeyCodePoint}.
        if (isComposing() && !HangulCombiner.isHangul(codePoint)) {
            commitComposingText();
        }
        if (inputTransaction.mSettingsValues.isWordSeparator(codePoint)
                || Character.getType(codePoint) == Character.OTHER_SYMBOL) {
            handleSeparatorEvent(event, inputTransaction);
        } else {
            handleNonSeparatorEvent(event);
        }
    }

    /**
     * Handle a non-separator.
     * @param event The event to handle.
     */
    private void handleNonSeparatorEvent(final Event event) {
        sendKeyCodePoint(event.mCodePoint);
    }

    /**
     * Handle input of a separator code point.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleSeparatorEvent(final Event event, final InputTransaction inputTransaction) {
        sendKeyCodePoint(event.mCodePoint);

        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);
    }

    /**
     * Handle a press on the backspace key.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleBackspaceEvent(final Event event, final InputTransaction inputTransaction) {
        // In many cases after backspace, we need to update the shift state. Normally we need
        // to do this right away to avoid the shift state being out of date in case the user types
        // backspace then some other character very fast. However, in the case of backspace key
        // repeat, this can lead to flashiness when the cursor flies over positions where the
        // shift state should be updated, so if this is a key repeat, we update after a small delay.
        // Then again, even in the case of a key repeat, if the cursor is at start of text, it
        // can't go any further back, so we can update right away even if it's a key repeat.
        final int shiftUpdateKind =
                event.isKeyRepeat() && mConnection.getExpectedSelectionStart() > 0
                ? InputTransaction.SHIFT_UPDATE_LATER : InputTransaction.SHIFT_UPDATE_NOW;
        inputTransaction.requireShiftUpdate(shiftUpdateKind);

        // While a Hangul composition is in progress, backspace undoes one jamo at a time
        // (까 -> 가 -> ㄱ -> nothing).
        if (isComposing()) {
            final String after = mHangulCombiner.processDelete();
            if (after.length() == 0) {
                // The whole composition was undone: clear the composing region entirely instead
                // of finishing it, which would wrongly commit the remaining composing text.
                mConnection.setComposingText("", 1);
            } else {
                mConnection.setComposingText(after, 1);
            }
            return;
        }

        if (mConnection.hasSelection()) {
            mConnection.deleteSelectedText();
        } else {
            final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
            if (codePointBeforeCursor == Constants.NOT_A_CODE) {
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL);
            } else {
                final int numChars = Character.isSupplementaryCodePoint(codePointBeforeCursor) ? 2 : 1;
                mConnection.deleteTextBeforeCursor(numChars);
            }
        }
    }

    /**
     * Handle a press on the language switch key (the "globe key")
     */
    private void handleLanguageSwitchKey() {
        mLatinIME.switchToNextSubtype();
    }

    /**
     * Performs a recapitalization event.
     */
    private void performRecapitalization() {
        if (!mConnection.hasSelection() || !mRecapitalizeStatus.mIsEnabled()) {
            return; // No selection or recapitalize is disabled for now
        }
        final int selectionStart = mConnection.getExpectedSelectionStart();
        final int selectionEnd = mConnection.getExpectedSelectionEnd();
        final int numCharsSelected = selectionEnd - selectionStart;
        if (numCharsSelected > Constants.MAX_CHARACTERS_FOR_RECAPITALIZATION) {
            // We bail out if we have too many characters for performance reasons. We don't want
            // to suck possibly multiple-megabyte data.
            return;
        }
        // If we have a recapitalize in progress, use it; otherwise, start a new one.
        if (!mRecapitalizeStatus.isStarted()
                || !mRecapitalizeStatus.isSetAt(selectionStart, selectionEnd)) {
            final CharSequence selectedText = mConnection.getSelectedText();
            if (TextUtils.isEmpty(selectedText)) return; // Race condition with the input connection
            mRecapitalizeStatus.start(selectionStart, selectionEnd, selectedText.toString(), mLatinIME.getCurrentLayoutLocale());
            // We trim leading and trailing whitespace.
            mRecapitalizeStatus.trim();
        }
        mConnection.beginBatchEdit();
        mConnection.setSelection(selectionStart, selectionStart);
        mRecapitalizeStatus.rotate();
        mConnection.replaceText(selectionStart, selectionEnd, mRecapitalizeStatus.getRecapitalizedString());
        mConnection.setSelection(mRecapitalizeStatus.getNewCursorStart(), mRecapitalizeStatus.getNewCursorEnd());
        mConnection.endBatchEdit();
    }

    /**
     * Gets the current auto-caps state, factoring in the space state.
     *
     * This method tries its best to do this in the most efficient possible manner. It avoids
     * getting text from the editor if possible at all.
     * This is called from the KeyboardSwitcher (through a trampoline in LatinIME) because it
     * needs to know auto caps state to display the right layout.
     *
     * @param settingsValues the relevant settings values
     * @param layoutSetName the name of the current keyboard layout set
     * @return a caps mode from TextUtils.CAP_MODE_* or Constants.TextUtils.CAP_MODE_OFF.
     */
    public int getCurrentAutoCapsState(final SettingsValues settingsValues,
                                       final String layoutSetName) {
        if (!settingsValues.mAutoCap || !layoutUsesAutoCaps(layoutSetName)) {
            return Constants.TextUtils.CAP_MODE_OFF;
        }

        final EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) return Constants.TextUtils.CAP_MODE_OFF;
        final int inputType = ei.inputType;
        // Warning: this depends on mSpaceState, which may not be the most current value. If
        // mSpaceState gets updated later, whoever called this may need to be told about it.
        return mConnection.getCursorCapsMode(inputType, settingsValues.mSpacingAndPunctuations);
    }

    private boolean layoutUsesAutoCaps(final String layoutSetName) {
        switch (layoutSetName) {
            case SubtypeLocaleUtils.LAYOUT_ARABIC:
            case SubtypeLocaleUtils.LAYOUT_BENGALI:
            case SubtypeLocaleUtils.LAYOUT_BENGALI_AKKHOR:
            case SubtypeLocaleUtils.LAYOUT_BENGALI_UNIJOY:
            case SubtypeLocaleUtils.LAYOUT_FARSI:
            case SubtypeLocaleUtils.LAYOUT_GEORGIAN:
            case SubtypeLocaleUtils.LAYOUT_HEBREW:
            case SubtypeLocaleUtils.LAYOUT_HINDI:
            case SubtypeLocaleUtils.LAYOUT_HINDI_COMPACT:
            case SubtypeLocaleUtils.LAYOUT_KANNADA:
            case SubtypeLocaleUtils.LAYOUT_KHMER:
            case SubtypeLocaleUtils.LAYOUT_LAO:
            case SubtypeLocaleUtils.LAYOUT_MALAYALAM:
            case SubtypeLocaleUtils.LAYOUT_MARATHI:
            case SubtypeLocaleUtils.LAYOUT_NEPALI_ROMANIZED:
            case SubtypeLocaleUtils.LAYOUT_NEPALI_TRADITIONAL:
            case SubtypeLocaleUtils.LAYOUT_NOSHIFT:
            case SubtypeLocaleUtils.LAYOUT_NOSHIFT_WIDE:
            case SubtypeLocaleUtils.LAYOUT_TAMIL:
            case SubtypeLocaleUtils.LAYOUT_TELUGU:
            case SubtypeLocaleUtils.LAYOUT_THAI:
            case SubtypeLocaleUtils.LAYOUT_URDU:
                return false;
            default:
                return true;
        }
    }

    public int getCurrentRecapitalizeState() {
        if (!mRecapitalizeStatus.isStarted()
                || !mRecapitalizeStatus.isSetAt(mConnection.getExpectedSelectionStart(),
                        mConnection.getExpectedSelectionEnd())) {
            // Not recapitalizing at the moment
            return RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        }
        return mRecapitalizeStatus.getCurrentMode();
    }

    /**
     * @return the editor info for the current editor
     */
    private EditorInfo getCurrentInputEditorInfo() {
        return mLatinIME.getCurrentInputEditorInfo();
    }

    /**
     * @param actionId the action to perform
     */
    private void performEditorAction(final int actionId) {
        mConnection.performEditorAction(actionId);
    }

    /**
     * Perform the processing specific to inputting TLDs.
     *
     * Some keys input a TLD (specifically, the ".com" key) and this warrants some specific
     * processing. First, if this is a TLD, we ignore PHANTOM spaces -- this is done by type
     * of character in onCodeInput, but since this gets inputted as a whole string we need to
     * do it here specifically. Then, if the last character before the cursor is a period, then
     * we cut the dot at the start of ".com". This is because humans tend to type "www.google."
     * and then press the ".com" key and instinctively don't expect to get "www.google..com".
     *
     * @param text the raw text supplied to onTextInput
     * @return the text to actually send to the editor
     */
    private String performSpecificTldProcessingOnTextInput(final String text) {
        if (text.length() <= 1 || text.charAt(0) != Constants.CODE_PERIOD
                || !Character.isLetter(text.charAt(1))) {
            // Not a tld: do nothing.
            return text;
        }
        final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
        // If no code point, #getCodePointBeforeCursor returns NOT_A_CODE_POINT.
        if (Constants.CODE_PERIOD == codePointBeforeCursor) {
            return text.substring(1);
        }
        return text;
    }

    /**
     * Handle a press on the settings key.
     */
    private void onSettingsKeyPressed() {
        mLatinIME.launchSettings();
    }

    /**
     * Sends a DOWN key event followed by an UP key event to the editor.
     *
     * If possible at all, avoid using this method. It causes all sorts of race conditions with
     * the text view because it goes through a different, asynchronous binder. Also, batch edits
     * are ignored for key events. Use the normal software input methods instead.
     *
     * @param keyCode the key code to send inside the key event.
     */
    public void sendDownUpKeyEvent(final int keyCode) {
        sendDownUpKeyEvent(keyCode, 0);
    }

    public void sendDownUpKeyEvent(final int keyCode, final int metaState) {
        final long eventTime = SystemClock.uptimeMillis();
        mConnection.sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        mConnection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    /**
     * Sends a code point to the editor, using the most appropriate method.
     *
     * Normally we send code points with commitText, but there are some cases (where backward
     * compatibility is a concern for example) where we want to use deprecated methods.
     *
     * @param codePoint the code point to send.
     */
    // TODO: replace these two parameters with an InputTransaction
    void sendKeyCodePoint(final int codePoint) {
        // In a Korean layout, jamo code points are fed to the Hangul combiner and displayed as
        // composing text instead of being committed directly. The live subtype is queried on
        // every keypress (never a cached flag) so the first key after an IME lifecycle race
        // still enters the combiner instead of being committed as a raw jamo.
        if (mLatinIME.isKoreanLayout() && HangulCombiner.isHangul(codePoint)) {
            if (mHangulCombiner.combiningStateFeedback().length() == 0) {
                // A fresh composition begins here: record the anchor (cursor position where this
                // syllable's typing started) so onUpdateSelection can tell a stale re-report of
                // the pre-typing cursor apart from a genuine user cursor move. The anchor is only
                // trusted when the cursor is actually known (hasCursorPosition()); otherwise the
                // late initial selection report must be allowed to establish it without
                // committing the first jamo.
                mCompositionStartSelection = mConnection.getExpectedSelectionStart();
                mHasCompositionStartSelection = mConnection.hasCursorPosition();
                Log.i(TAG, "sendKeyCodePoint start composition cp=" + Integer.toHexString(codePoint)
                        + " expected=" + mCompositionStartSelection
                        + " hasAnchor=" + mHasCompositionStartSelection);
            }
            mConnection.setComposingText(mHangulCombiner.process(codePoint), 1);
            return;
        }

        // Anything that is not a jamo finalizes a pending Hangul composition first.
        if (isComposing()) {
            commitComposingText();
        }

        // Digits are committed like every other code point. The legacy AOSP digit path
        // (sendDownUpKeyEvent with KEYCODE_0-9) is unreliable on modern editors (Compose,
        // WebView, custom InputConnections): key events travel through a separate, asynchronous
        // binder and can be dropped or reordered when typing several digits in a row from the
        // number pad. commitText() uses the same in-order pipeline as all other characters.
        mConnection.commitText(StringUtils.newSingleCodePointString(codePoint), 1);
    }

    /**
     * Test observation seam (JVM unit tests only): the current Hangul combiner state
     * (committed composing word + syllable being combined), without touching the editor.
     */
    String hangulCombiningFeedback() {
        return mHangulCombiner.combiningStateFeedback();
    }
}
