package com.edisonwang.ps.lib;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.util.HashMap;

/**
 * @author edi
 */
class EventServiceConnection implements ServiceConnection {

    public static final String EXTRA_REQUEST_ID = "connection_request_id";
    public static final String EXTRA_REQUEST_TIME_MS = "connection_request_time";
    private final HashMap<String, Bundle> mPendingQueue = new HashMap<>();
    private final HashMap<String, Bundle> mRequestQueue = new HashMap<>();
    private final int[] mLock = {};
    private final Context mContext;
    private final EventServiceImpl.EventServiceResponseHandler mResponseHandler;
    private final int mPendingWarningThreshold;
    private Messenger mService;
    private Messenger mResponder;
    private boolean mPendingThresholdWarned;

    public EventServiceConnection(Context context,
                                  EventServiceImpl.EventServiceResponseHandler handler,
                                  PennStation.PennStationOptions options) {
        mContext = context;
        mResponseHandler = handler;
        mPendingWarningThreshold = options.pendingWarningThreshold;
    }

    public String queueAndExecute(Bundle bundle) {
        final String reqId = generateRequestId();
        bundle.putString(EXTRA_REQUEST_ID, reqId);
        bundle.putLong(EXTRA_REQUEST_TIME_MS, System.currentTimeMillis());
        synchronized (mLock) {
            Messenger service = mService;
            if (service != null) {
                mRequestQueue.put(reqId, bundle);
                final int size = mRequestQueue.size();
                if (mPendingWarningThreshold > 0 &&
                        size >= mPendingWarningThreshold) {
                    PennStation.getManager().getEventBus().getStickyEvent(QueuePressureStateChangedEvent.class);
                    PennStation.postLocalEvent(new QueuePressureStateChangedEvent(QueuePressureStateChangedEvent.STATE_ABOVE_THRESHOLD, size));
                    mPendingThresholdWarned = true;
                } else {
                    cancelWarningIfNeeded(size);
                }
                sendMessage(service, newPerformRequestMessage(bundle));
            } else {
                mPendingQueue.put(reqId, bundle);
            }
        }
        return reqId;
    }

    private void cancelWarningIfNeeded(int size) {
        if (mPendingThresholdWarned && size < mPendingWarningThreshold) {
            mPendingThresholdWarned = false;
            PennStation.postLocalEvent(new QueuePressureStateChangedEvent(QueuePressureStateChangedEvent.STATE_GOOD, size));
        }
    }

    public void cancelAllUnSubmitted() {
        synchronized (mLock) {
            mRequestQueue.clear();
            mPendingQueue.clear();
            cancelWarningIfNeeded(0);
        }
    }

    private Message newPerformRequestMessage(Bundle bundle) {
        final Message msg = Message.obtain();
        msg.what = EventServiceImpl.PERFORM_REQUEST;
        msg.setData(bundle);
        msg.replyTo = getServiceResponder();
        return msg;
    }

    private Message newCancelRequestMessage(String reqId) {
        final Message msg = Message.obtain();
        msg.what = EventServiceImpl.CANCEL_REQUEST;
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_REQUEST_ID, reqId);
        msg.setData(bundle);
        msg.replyTo = getServiceResponder();
        return msg;
    }

    private synchronized Messenger getServiceResponder() {
        if (mResponder == null) {
            mResponder = new Messenger(new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    Bundle bundle = null;
                    try {
                        Bundle b = msg.getData();
                        b.setClassLoader(mContext.getClassLoader());
                        bundle = b;
                    } catch (Throwable e) {
                        //Bundling error during IPC. Ignore.
                    }
                    mResponseHandler.handleServiceResponse(bundle);
                }
            });
        }
        return mResponder;
    }

    private void sendMessage(Messenger service, Message msg) {
        try {
            service.send(msg);
        } catch (Throwable e) {
            Log.e(EventServiceImpl.TAG, "Unable to send message to service.", e);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        synchronized (mLock) {
            Messenger service = new Messenger(binder);
            mService = service;
            mRequestQueue.putAll(mPendingQueue);
            for (Bundle action : mPendingQueue.values()) {
                sendMessage(service, newPerformRequestMessage(action));
            }
            mPendingQueue.clear();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        synchronized (mLock) {
            mService = null;
        }
    }

    public boolean isPending(String requestId) {
        synchronized (mLock) {
            return mRequestQueue.containsKey(requestId) || mPendingQueue.containsKey(requestId);
        }
    }

    public Bundle remove(String requestId) {
        synchronized (mLock) {
            Bundle action = mRequestQueue.get(requestId);
            mRequestQueue.remove(requestId);
            cancelWarningIfNeeded(mRequestQueue.size());
            return action;
        }
    }
    public void cancel(String requestId) {
        synchronized (mLock) {
            if (isPending(requestId)) {
                mRequestQueue.remove(requestId);
                mPendingQueue.remove(requestId);
            }
            Messenger service = mService;
            if (service != null) {
                sendMessage(service, newCancelRequestMessage(requestId));
            }
            cancelWarningIfNeeded(mRequestQueue.size());
        }
    }

    public String generateRequestId() {
        return Long.toHexString(Double.doubleToLongBits(Math.random())) + Long.toHexString(System.currentTimeMillis());
    }
}
