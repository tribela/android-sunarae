/*
 * Copyright (C) 2026 Raimondas Rimkus
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

package net.kjwon15.noshiftkeyboard.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Pattern;

import net.kjwon15.noshiftkeyboard.R;
import net.kjwon15.noshiftkeyboard.compat.PreferenceManagerCompat;
import net.kjwon15.noshiftkeyboard.latin.utils.ResourceUtils;

public final class EmojiPaletteView extends View {
    private static final String TAG = EmojiPaletteView.class.getSimpleName();

    private static final int COLUMNS = 8;
    private static final int MAX_RECENTS = 40;
    private static final String PREF_EMOJI_RECENTS = "pref_emoji_recents";
    private static final String RECENTS_SEPARATOR = "|";

    private static final String[] CATEGORY_ICONS = {
        "\uD83D\uDD58",
        "\uD83D\uDE00",
        "\uD83E\uDDD1",
        "\uD83D\uDC3B",
        "\uD83C\uDF54",
        "\uD83D\uDE97",
        "\u26BD",
        "\uD83D\uDCA1",
        "\u2764\uFE0F",
        "\uD83C\uDFC1",
    };

    public interface Listener {
        void onEmojiSelected(String emoji);
        void onBackspace();
        void onEnter();
        void onClose();
    }

    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBackgroundPaint = new Paint();
    private final Paint mHighlightPaint = new Paint();
    private final Scroller mScroller;
    private final int mTouchSlop;
    private final float mTabHeight;
    private final float mActionHeight;

    private final ArrayList<String> mRecents = new ArrayList<>();

    private String[][] mEmojiByCategory;
    private Listener mListener;

    private int mPaletteHeight;
    private int mCategoryIndex;
    private int mScrollY;
    private int mMaxScrollY;
    private int mActivePointerId = MotionEvent.INVALID_POINTER_ID;
    private float mDownX;
    private float mDownY;
    private float mLastY;
    private boolean mIsScrolling;
    private VelocityTracker mVelocityTracker;

    public EmojiPaletteView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final Resources res = context.getResources();
        final float density = res.getDisplayMetrics().density;
        mTabHeight = 44.0f * density;
        mActionHeight = 52.0f * density;
        mBackgroundPaint.setColor(
                themeColor(context, R.attr.keyNormalBackgroundColor, 0xFF202124));
        mHighlightPaint.setColor(
                themeColor(context, R.attr.keyPressedBackgroundColor, 0x33FFFFFF));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTypeface(Typeface.DEFAULT);
        final TypedArray keyStyle = context.obtainStyledAttributes(null,
                R.styleable.Keyboard_Key, R.attr.keyboardViewStyle, R.style.KeyboardView);
        try {
            mTextPaint.setColor(keyStyle.getColor(
                    R.styleable.Keyboard_Key_keyTextColor, Color.WHITE));
        } finally {
            keyStyle.recycle();
        }
        mScroller = new Scroller(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        loadRecents();
    }

    private static int themeColor(final Context context, final int attr, final int fallback) {
        final TypedArray a = context.obtainStyledAttributes(new int[] {attr});
        try {
            return a.getColor(0, fallback);
        } finally {
            a.recycle();
        }
    }

    public void setListener(final Listener listener) {
        mListener = listener;
    }

    public void setPaletteHeight(final int height) {
        if (height > 0 && height != mPaletteHeight) {
            mPaletteHeight = height;
            requestLayout();
            invalidate();
        }
    }

    public void resetToRecents() {
        mCategoryIndex = 0;
        mScrollY = 0;
        invalidate();
    }

    private void loadRecents() {
        mRecents.clear();
        final SharedPreferences prefs = PreferenceManagerCompat.getDeviceSharedPreferences(getContext());
        final String raw = prefs.getString(PREF_EMOJI_RECENTS, "");
        if (raw.isEmpty()) {
            return;
        }
        for (final String emoji : raw.split(Pattern.quote(RECENTS_SEPARATOR))) {
            if (!emoji.isEmpty()) {
                mRecents.add(emoji);
            }
        }
    }

    private void addRecent(final String emoji) {
        mRecents.remove(emoji);
        mRecents.add(0, emoji);
        while (mRecents.size() > MAX_RECENTS) {
            mRecents.remove(mRecents.size() - 1);
        }
        final StringBuilder raw = new StringBuilder();
        for (final String item : mRecents) {
            if (raw.length() > 0) {
                raw.append(RECENTS_SEPARATOR);
            }
            raw.append(item);
        }
        PreferenceManagerCompat.getDeviceSharedPreferences(getContext()).edit()
                .putString(PREF_EMOJI_RECENTS, raw.toString()).apply();
    }

    private void ensureEmojiLoaded() {
        if (mEmojiByCategory != null) {
            return;
        }
        final ArrayList<String[]> categories = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getResources().openRawResource(R.raw.emoji_categories), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final int separator = line.indexOf('|');
                if (separator > 0) {
                    categories.add(line.substring(separator + 1).split(" "));
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to load emoji data", e);
        }
        mEmojiByCategory = categories.toArray(new String[0][]);
    }

    private int getCategoryCount() {
        ensureEmojiLoaded();
        return 1 + mEmojiByCategory.length;
    }

    private String[] getEmojiList() {
        ensureEmojiLoaded();
        if (mCategoryIndex == 0) {
            return mRecents.toArray(new String[0]);
        }
        return mEmojiByCategory[mCategoryIndex - 1];
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = mPaletteHeight > 0
                ? mPaletteHeight : ResourceUtils.getDefaultKeyboardHeight(getResources());
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        final int width = getWidth();
        final int contentTop = getPaddingTop();
        final int contentBottom = getHeight() - getPaddingBottom();
        final int gridTop = (int) (contentTop + mTabHeight);
        final int gridBottom = (int) (contentBottom - mActionHeight);
        final int gridHeight = Math.max(1, gridBottom - gridTop);
        final int cell = Math.max(1, width / COLUMNS);
        final String[] emoji = getEmojiList();
        final int rows = (emoji.length + COLUMNS - 1) / COLUMNS;
        mMaxScrollY = Math.max(0, rows * cell - gridHeight);
        mScrollY = Math.max(0, Math.min(mScrollY, mMaxScrollY));

        canvas.drawRect(0, 0, width, getHeight(), mBackgroundPaint);

        final int categoryCount = getCategoryCount();
        final float tabWidth = width / (float) categoryCount;
        mTextPaint.setTextSize(mTabHeight * 0.46f);
        for (int i = 0; i < categoryCount; i++) {
            if (i == mCategoryIndex) {
                canvas.drawRect(i * tabWidth, contentTop, (i + 1) * tabWidth,
                        contentTop + mTabHeight, mHighlightPaint);
            }
            final String icon = i < CATEGORY_ICONS.length ? CATEGORY_ICONS[i] : "";
            drawCenteredText(canvas, icon, i * tabWidth + tabWidth / 2.0f,
                    contentTop + mTabHeight / 2.0f);
        }

        canvas.save();
        canvas.clipRect(0, gridTop, width, gridBottom);
        mTextPaint.setTextSize(cell * 0.60f);
        final int firstRow = mScrollY / cell;
        final int lastRow = Math.min(rows - 1, (mScrollY + gridHeight) / cell);
        for (int row = firstRow; row <= lastRow; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                final int index = row * COLUMNS + column;
                if (index >= emoji.length) {
                    break;
                }
                final float centerX = column * cell + cell / 2.0f;
                final float centerY = gridTop + row * cell - mScrollY + cell / 2.0f;
                drawCenteredText(canvas, emoji[index], centerX, centerY);
            }
        }
        canvas.restore();

        final float actionTop = contentBottom - mActionHeight;
        final float actionWidth = width / 3.0f;
        final String[] actions = {"ABC", "\u232B", "\u21B5"};
        for (int i = 0; i < actions.length; i++) {
            mTextPaint.setTextSize(mActionHeight * (i == 0 ? 0.32f : 0.42f));
            drawCenteredText(canvas, actions[i], actionWidth * i + actionWidth / 2.0f,
                    actionTop + mActionHeight / 2.0f);
        }
    }

    private void drawCenteredText(final Canvas canvas, final String text,
            final float centerX, final float centerY) {
        if (text.isEmpty()) {
            return;
        }
        final Paint.FontMetrics metrics = mTextPaint.getFontMetrics();
        final float baseline = centerY - (metrics.ascent + metrics.descent) / 2.0f;
        canvas.drawText(text, centerX, baseline, mTextPaint);
    }

    private void selectCategory(final int index) {
        final int count = getCategoryCount();
        if (index < 0 || index >= count || index == mCategoryIndex) {
            return;
        }
        mCategoryIndex = index;
        mScrollY = 0;
        invalidate();
    }

    private void scrollByPixels(final int delta) {
        final int next = Math.max(0, Math.min(mScrollY + delta, mMaxScrollY));
        if (next != mScrollY) {
            mScrollY = next;
            invalidate();
        }
    }

    private void handleTap(final float x, final float y) {
        final int contentTop = getPaddingTop();
        final int contentBottom = getHeight() - getPaddingBottom();
        if (y < contentTop + mTabHeight) {
            final float tabWidth = getWidth() / (float) getCategoryCount();
            selectCategory((int) (x / tabWidth));
            return;
        }
        if (y >= contentBottom - mActionHeight) {
            final int action = (int) (x / (getWidth() / 3.0f));
            if (mListener == null) {
                return;
            }
            if (action == 0) {
                mListener.onClose();
            } else if (action == 1) {
                mListener.onBackspace();
            } else {
                mListener.onEnter();
            }
            return;
        }
        final int cell = Math.max(1, getWidth() / COLUMNS);
        final int row = (int) Math.floor((y - contentTop - mTabHeight + mScrollY) / (float) cell);
        final int column = (int) (x / cell);
        if (column < 0 || column >= COLUMNS || row < 0) {
            return;
        }
        final String[] emoji = getEmojiList();
        final int index = row * COLUMNS + column;
        if (index < 0 || index >= emoji.length) {
            return;
        }
        addRecent(emoji[index]);
        if (mListener != null) {
            mListener.onEmojiSelected(emoji[index]);
        }
        invalidate();
    }

    private void flingIfNeeded() {
        if (mVelocityTracker == null) {
            return;
        }
        mVelocityTracker.computeCurrentVelocity(1000);
        final int velocityY = (int) mVelocityTracker.getYVelocity();
        if (Math.abs(velocityY) > mTouchSlop * 10) {
            mScroller.fling(0, mScrollY, 0, -velocityY, 0, 0, 0, mMaxScrollY);
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            mScroller.forceFinished(true);
            mDownX = event.getX();
            mDownY = event.getY();
            mLastY = mDownY;
            mIsScrolling = false;
            mActivePointerId = event.getPointerId(0);
            return true;
        case MotionEvent.ACTION_MOVE: {
            final int index = event.findPointerIndex(mActivePointerId);
            if (index < 0) {
                break;
            }
            final float y = event.getY(index);
            if (!mIsScrolling && Math.abs(y - mDownY) > mTouchSlop) {
                mIsScrolling = true;
            }
            if (mIsScrolling) {
                scrollByPixels((int) (mLastY - y));
                mLastY = y;
            }
            break;
        }
        case MotionEvent.ACTION_UP: {
            final int index = event.findPointerIndex(mActivePointerId);
            if (mIsScrolling) {
                flingIfNeeded();
            } else if (index >= 0) {
                handleTap(event.getX(index), event.getY(index));
            }
            mActivePointerId = MotionEvent.INVALID_POINTER_ID;
            mVelocityTracker.recycle();
            mVelocityTracker = null;
            break;
        }
        case MotionEvent.ACTION_CANCEL:
            mActivePointerId = MotionEvent.INVALID_POINTER_ID;
            if (mVelocityTracker != null) {
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }
            break;
        default:
            break;
        }
        return true;
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mScrollY = Math.max(0, Math.min(mScroller.getCurrY(), mMaxScrollY));
            postInvalidateOnAnimation();
        }
    }
}
