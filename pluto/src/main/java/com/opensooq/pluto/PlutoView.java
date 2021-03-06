package com.opensooq.pluto;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PagerSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SnapHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import com.opensooq.pluto.base.PlutoAdapter;
import com.opensooq.pluto.listeners.OnSlideChangeListener;
import com.opensooq.pluto.listeners.SnapOnScrollListener;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Omar Altamimi on 28,April,2019
 */

public class PlutoView extends FrameLayout {
    private RecyclerView rvSlider;
    private OnSlideChangeListener mOnSlideChangeListener;
    private PlutoAdapter mAdapter;
    private long duration = 4000;
    private int currentPosition;
    private PlutoIndicator mIndicator;
    private boolean mIndicatorVisibility;
    private Timer mCycleTimer;
    private TimerTask mCycleTask;

    private Timer mResumingTimer;
    private boolean mCycling;
    private boolean mAutoRecover = true;
    private boolean mAutoCycle;
    private SnapHelper helper;
    private RecyclerView.OnScrollListener mOnScrollListener;
    private TimerTask mResumingTask;

    public PlutoView(@NonNull Context context) {
        this(context, null);
    }

    public PlutoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.PlutoViewStyle);

    }

    public PlutoView(@NonNull Context context,
                     @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(getContext(), R.layout.layout_view_slider, this);
        final TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.PlutoView,
                defStyleAttr, 0);

        rvSlider = findViewById(R.id.rvSlider);
        mAutoCycle = attributes.getBoolean(R.styleable.PlutoView_auto_cycle, true);
        mIndicatorVisibility = attributes.getBoolean(R.styleable.PlutoView_indicator_visibility,
                false);
        initScrollListener();

        rvSlider.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView,
                                                 @NonNull MotionEvent motionEvent) {
                int action = motionEvent.getAction();
                if (action == MotionEvent.ACTION_UP) {
                    recoverCycle();
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView recyclerView,
                                     @NonNull MotionEvent motionEvent) {

            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean b) {

            }
        });

        attributes.recycle();
        setIndicatorVisibility(mIndicatorVisibility);
        if (mAutoCycle) {
            startAutoCycle();
        }

    }

    private void initScrollListener() {
        mOnScrollListener = null;
        mOnScrollListener = new SnapOnScrollListener(helper, position -> {
            currentPosition = position;
            if (mOnSlideChangeListener != null) {
                mOnSlideChangeListener.onSlideChange(getAdapter(),
                        position % getAdapter().getRealCount());
            }
        });
    }

    public void create(PlutoAdapter adapter, long duration) {
        setAdapter(adapter);
        if (duration != -1)
            this.duration = duration;
        setIndicatorPosition(IndicatorPosition.CENTER_BOTTOM);
    }

    public void create(PlutoAdapter adapter) {
        create(adapter, -1);
    }

    /**
     * Set the visibility of the indicators.
     *
     * @param visibility
     */
    public void setIndicatorVisibility(boolean visibility) {
        if (mIndicator == null) {
            return;
        }

        mIndicator.setVisibility(visibility);
    }

    public boolean getIndicatorVisibility() {
        if (mIndicator != null) {
            return mIndicator.isVisible();
        }
        return false;

    }


    public enum IndicatorPosition {
        CENTER_BOTTOM("CENTER_BOTTOM", R.id.default_center_bottom_indicator),
        RIGHT_BOTTOM("RIGHT_BOTTOM", R.id.default_bottom_end_indicator),
        LEFT_BOTTOM("LEFT_BOTTOM", R.id.default_bottom_start_indicator),
        CENTER_TOP("CENTER_TOP", R.id.default_center_top_indicator),
        RIGHT_TOP("RIGHT_TOP", R.id.default_center_top_end_indicator),
        LEFT_TOP("LEFT_TOP", R.id.default_center_top_start_indicator);
        private final String name;
        private final int id;

        private IndicatorPosition(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public String toString() {
            return name;
        }

        public int getResourceId() {
            return id;
        }
    }

    public void setIndicatorPosition(IndicatorPosition presetIndicator) {
        PlutoIndicator PlutoIndicator = findViewById(presetIndicator.getResourceId());
        setCustomIndicator(PlutoIndicator);
    }

    public void setCustomIndicator(PlutoIndicator indicator) {
        if (mIndicator != null) {
            mIndicator.destroySelf();
        }
        mIndicator = indicator;
        mIndicator.setVisibility(mIndicatorVisibility);
        mIndicator.setRecyclerView(rvSlider, helper);
        mIndicator.redraw();
    }


    private void setAdapter(PlutoAdapter adapter) {
        if (adapter == null)
            throw new IllegalArgumentException("custom Adapter cannot be null");
        this.mAdapter = adapter;
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext()
                , LinearLayoutManager.HORIZONTAL, false);
        linearLayoutManager.setInitialPrefetchItemCount(4);
        rvSlider.setLayoutManager(linearLayoutManager);
        rvSlider.setAdapter(adapter);
        addScrollListener();
        setCurrentPosition(0, false);
    }


    private void addScrollListener() {
        helper = new PagerSnapHelper();
        rvSlider.setOnFlingListener(null);
        helper.attachToRecyclerView(rvSlider);
        if (mOnScrollListener != null) {
            rvSlider.removeOnScrollListener(mOnScrollListener);
        }
        initScrollListener();
        rvSlider.addOnScrollListener(mOnScrollListener);
    }

    private PlutoAdapter getAdapter() {
        return mAdapter;
    }

    public void setOnSlideChangeListener(OnSlideChangeListener onSlideChangeListener) {
        mOnSlideChangeListener = onSlideChangeListener;

    }


    public long getDuration() {
        return duration;
    }


    /**
     * get the current item position
     *
     * @return
     */
    public int getCurrentPosition() {
        if (getAdapter() == null)
            throw new IllegalStateException("You did not set a slider adapter");
        return currentPosition % getAdapter().getRealCount();
    }


    /**
     * remove  the slider at the position.
     */
    public void removeSliderAt(int position) {

        if (position >= getAdapter().getRealCount() || position <0) {
            throw new IndexOutOfBoundsException("trying to access position"+position+" where size"+getAdapter().getRealCount());
        }
        if (getAdapter() != null) {
            getAdapter().removeItemAt(position);
        }
    }

    /**
     * remove all the sliders.
     */
    public void removeAllSliders() {
        if (getAdapter() != null) {
            getAdapter().removeAllItems();
        }
    }

    /**
     * set current slider
     *
     * @param position
     */
    public void setCurrentPosition(int position, boolean smooth) {
        if (getAdapter() == null)
            onDestroy();
        if (position >= getAdapter().getRealCount() || position <0) {
            throw new IndexOutOfBoundsException("trying to access position"+position+" where size"+getAdapter().getRealCount());
        }
        currentPosition = getAdapter().getRealCount() * PlutoAdapter.MULTIPLY
                + position;
        if (smooth) {
            rvSlider.smoothScrollToPosition(currentPosition);
        } else {
            rvSlider.scrollToPosition(currentPosition);

        }

    }

    public void setCurrentPosition(int position) {
        setCurrentPosition(position, true);
    }

    /**
     * move to prev slide.
     */
    public void movePrevPosition(boolean smooth) {
        currentPosition--;

        if (getAdapter() == null)
            onDestroy();
        if (smooth) {
            rvSlider.smoothScrollToPosition(currentPosition);
        } else {
            rvSlider.scrollToPosition(currentPosition);
        }

    }

    public void movePrevPosition() {
        movePrevPosition(true);
    }

    /**
     * move to next slide.
     */
    public void moveNextPosition(boolean smooth) {
        currentPosition++;

        if (getAdapter() == null)
            onDestroy();
        if (smooth) {
            rvSlider.smoothScrollToPosition(currentPosition);
        } else {
            rvSlider.scrollToPosition(currentPosition);
        }
    }

    public void moveNextPosition() {
        moveNextPosition(true);
    }

    private Handler mh = new IncomingHandler(this);

    public void startAutoCycle() {
        startAutoCycle(duration, duration, mAutoRecover);
    }

    /**
     * start auto cycle.
     *
     * @param delay       delay time
     * @param duration    animation duration time.
     * @param autoRecover if recover after user touches the slider.
     */
    public void startAutoCycle(long delay, long duration, boolean autoRecover) {
        if (mCycleTimer != null) mCycleTimer.cancel();
        if (mCycleTask != null) mCycleTask.cancel();
        if (mResumingTask != null) mResumingTask.cancel();
        if (mResumingTimer != null) mResumingTimer.cancel();
        this.duration = duration;
        mCycleTimer = new Timer();
        mAutoRecover = autoRecover;
        mCycleTask = new TimerTask() {
            @Override
            public void run() {
                mh.sendEmptyMessage(0);
            }
        };
        mCycleTimer.schedule(mCycleTask, delay, duration);
        mCycling = true;
        mAutoCycle = true;
    }

    /**
     * runtime call
     * pause auto cycle.
     */
    private void pauseAutoCycle() {
        if (mCycleTask == null) {
            onDestroy();
            return;
        }
        if (mCycling) {
            mCycleTimer.cancel();
            mCycleTask.cancel();
            mCycling = false;
        } else {
            if (mResumingTimer != null && mResumingTask != null) {
                recoverCycle();
            }
        }
    }


    /**
     * when paused cycle, this method can wake it up.
     */
    private void recoverCycle() {
        if (!mAutoRecover || !mAutoCycle) {
            return;
        }

        if (!mCycling) {
            if (mResumingTask != null && mResumingTimer != null) {
                mResumingTimer.cancel();
                mResumingTask.cancel();
            }
            mResumingTimer = new Timer();
            mResumingTask = new TimerTask() {
                @Override
                public void run() {
                    startAutoCycle();
                }
            };
            mResumingTimer.schedule(mResumingTask, 6000);
        }
    }

    /**
     * set the duration between two slider changes. the duration value must bigger or equal to 500
     *
     * @param duration how long
     */
    public void setDuration(long duration) {
        if (duration >= 500) {
            this.duration = duration;
            if (mAutoCycle && mCycling) {
                startAutoCycle();
            }
        }
    }

    /**
     * stop the auto circle
     */
    public void stopAutoCycle() {
        if (mCycleTask != null) {
            mCycleTask.cancel();
        }
        if (mCycleTimer != null) {
            mCycleTimer.cancel();
        }
        if (mResumingTimer != null) {
            mResumingTimer.cancel();
        }
        if (mResumingTask != null) {
            mResumingTask.cancel();
        }
        mAutoCycle = false;
        mCycling = false;
        onDestroy();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            pauseAutoCycle();
        }
        return false;
    }

    public boolean isAutoCycling() {
        return mCycling;
    }

    @Override
    public void onDetachedFromWindow() {
        onDestroy();
        super.onDetachedFromWindow();
    }

    private void onDestroy() {
        if (mCycleTimer != null) mCycleTimer.cancel();
        if (mCycleTask != null) mCycleTask.cancel();
        if (mResumingTask != null) mResumingTask.cancel();
        if (mResumingTimer != null) mResumingTimer.cancel();
        if (mh != null) {
            mh.removeCallbacksAndMessages(null);
        }
        if (mIndicator != null) {
            mIndicator.destroySelf();
        }
        if (rvSlider != null && mOnScrollListener != null) {
            rvSlider.removeOnScrollListener(mOnScrollListener);
        }
        mOnScrollListener = null;
        mOnSlideChangeListener = null;
        mCycleTimer = null;
        mCycleTask = null;
        mResumingTask = null;
        mResumingTimer = null;
    }

    static class IncomingHandler extends Handler {
        private final WeakReference<PlutoView> mSliderWeakReference;

        IncomingHandler(PlutoView plutoView) {
            mSliderWeakReference = new WeakReference<>(plutoView);
        }

        @Override
        public void handleMessage(Message msg) {
            PlutoView plutoView = mSliderWeakReference.get();
            if (plutoView != null) {
                plutoView.moveNextPosition();
            }
        }
    }
}
