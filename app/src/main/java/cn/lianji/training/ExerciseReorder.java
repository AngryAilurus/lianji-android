package cn.lianji.training;

import android.content.ClipData;
import android.graphics.drawable.ColorDrawable;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;

final class ExerciseReorder {
    interface OnMove { void move(int from, int to); }

    private static final int MOVE_UP = R.id.move_exercise_up;
    private static final int MOVE_DOWN = R.id.move_exercise_down;

    private final MainActivity activity;
    private final ScrollView scroll;
    private final OnMove onMove;
    private final List<Row> rows = new ArrayList<>();
    private final ColorDrawable marker = new ColorDrawable(MainActivity.GREEN);
    private final int[] scrollLocation = new int[2];
    private final int[] rowLocation = new int[2];
    private final Runnable autoScroll = this::scrollAtEdge;
    private Row source;
    private Row target;
    private float pointerY;
    private boolean dropped;

    ExerciseReorder(MainActivity activity, OnMove onMove) {
        this.activity = activity;
        scroll = activity.scroll;
        this.onMove = onMove;
        scroll.setOnDragListener((view, event) -> onDrag(event));
    }

    void addHandle(LinearLayout header, View card, int index, String name) {
        Row row = new Row(card, index);
        rows.add(row);
        ImageView handle = new ImageView(activity);
        handle.setImageDrawable(activity.icon(R.drawable.ic_drag, MainActivity.GREEN));
        handle.setScaleType(ImageView.ScaleType.CENTER);
        handle.setContentDescription("调整 " + name + " 的顺序");
        handle.setTooltipText("拖动调整顺序");
        handle.setFocusable(true);
        header.addView(handle, 0, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));

        int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downY;

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = event.getY();
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (source == null && Math.abs(event.getY() - downY) > slop) {
                            source = row;
                            if (view.startDragAndDrop(ClipData.newPlainText("", ""),
                                    new View.DragShadowBuilder(header), row, 0)) {
                                card.setAlpha(.45f);
                            } else {
                                source = null;
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        handle.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                int position = rows.indexOf(row);
                if (position > 0) info.addAction(new AccessibilityNodeInfo.AccessibilityAction(MOVE_UP, "上移"));
                if (position < rows.size() - 1) {
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(MOVE_DOWN, "下移"));
                }
            }

            @Override public boolean performAccessibilityAction(View host, int action, android.os.Bundle args) {
                if (action != MOVE_UP && action != MOVE_DOWN) return super.performAccessibilityAction(host, action, args);
                int next = rows.indexOf(row) + (action == MOVE_UP ? -1 : 1);
                if (next < 0 || next >= rows.size()) return false;
                onMove.move(row.index, rows.get(next).index);
                return true;
            }
        });
    }

    private boolean onDrag(DragEvent event) {
        if (source == null || event.getLocalState() != source) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                pointerY = event.getY();
                updateTarget();
                scroll.removeCallbacks(autoScroll);
                if (edgeStep() != 0) scroll.postOnAnimation(autoScroll);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                scroll.removeCallbacks(autoScroll);
                mark(null);
                return true;
            case DragEvent.ACTION_DROP:
                pointerY = event.getY();
                updateTarget();
                dropped = target != null;
                return dropped;
            case DragEvent.ACTION_DRAG_ENDED:
                Row from = source;
                Row to = dropped && event.getResult() ? target : null;
                scroll.removeCallbacks(autoScroll);
                mark(null);
                source = null;
                dropped = false;
                from.view.setAlpha(1);
                if (to != null) scroll.post(() -> {
                    if (scroll.isAttachedToWindow()) onMove.move(from.index, to.index);
                });
                return true;
            default:
                return true;
        }
    }

    private void updateTarget() {
        scroll.getLocationOnScreen(scrollLocation);
        float screenY = scrollLocation[1] + pointerY;
        if (screenY < top(rows.get(0)) || screenY > bottom(rows.get(rows.size() - 1))) {
            mark(null);
            return;
        }
        int slot = 0;
        for (Row row : rows) {
            if (screenY < top(row) + row.view.getHeight() / 2f) break;
            slot++;
        }
        int sourcePosition = rows.indexOf(source);
        Row next = rows.get(slot > sourcePosition ? slot - 1 : slot);
        mark(next == source ? null : next);
    }

    private int top(Row row) {
        row.view.getLocationOnScreen(rowLocation);
        return rowLocation[1];
    }

    private int bottom(Row row) { return top(row) + row.view.getHeight(); }

    private void mark(Row row) {
        if (row == target) return;
        if (target != null) target.view.getOverlay().remove(marker);
        target = row;
        if (row == null) return;
        int thickness = activity.dp(3);
        int y = row.index > source.index ? row.view.getHeight() - thickness : 0;
        int inset = activity.dp(10);
        marker.setBounds(inset, y, row.view.getWidth() - inset, y + thickness);
        row.view.getOverlay().add(marker);
    }

    private int edgeStep() {
        int edge = activity.dp(56);
        if (pointerY < edge) return -activity.dp(9);
        if (pointerY > scroll.getHeight() - edge) return activity.dp(9);
        return 0;
    }

    private void scrollAtEdge() {
        int step = edgeStep();
        if (step == 0 || !scroll.isAttachedToWindow() || !scroll.canScrollVertically(step)) return;
        scroll.scrollBy(0, step);
        updateTarget();
        scroll.postOnAnimation(autoScroll);
    }

    private static final class Row {
        final View view;
        final int index;

        Row(View view, int index) { this.view = view; this.index = index; }
    }
}
