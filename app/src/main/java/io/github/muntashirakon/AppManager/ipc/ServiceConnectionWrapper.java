// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

class ServiceConnectionWrapper {
    public static final String TAG = ServiceConnectionWrapper.class.getSimpleName();

    @Nullable
    private IBinder mIBinder;
    @Nullable
    private CountDownLatch mServiceBoundWatcher;
    @Nullable
    private final Runnable mDeathCallback;

    private class ServiceConnectionImpl implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "service onServiceConnected: %s", name);
            mIBinder = service;
            try {
                service.linkToDeath(() -> {
                    if (mIBinder == service) {
                        mIBinder = null;
                        if (mDeathCallback != null) mDeathCallback.run();
                    }
                }, 0);
            } catch (RemoteException e) {
                mIBinder = null;
            }
            onResponseReceived();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "service onServiceDisconnected: %s", name);
            onBinderLost();
            onResponseReceived();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "service onBindingDied: %s", name);
            onBinderLost();
            onResponseReceived();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.d(TAG, "service onNullBinding: %s", name);
            onBinderLost();
            onResponseReceived();
        }

        private void onBinderLost() {
            if (mIBinder != null) {
                mIBinder = null;
                if (mDeathCallback != null) mDeathCallback.run();
            }
        }

        private void onResponseReceived() {
            if (mServiceBoundWatcher != null) {
                // Should never be null
                mServiceBoundWatcher.countDown();
            } else throw new RuntimeException("Service watcher should never be null!");
        }
    }

    @NonNull
    private final ComponentName mComponentName;
    @NonNull
    private final ServiceConnectionImpl mServiceConnection;

    public ServiceConnectionWrapper(@NonNull String pkgName, @NonNull String className) {
        this(new ComponentName(pkgName, className), null);
    }

    public ServiceConnectionWrapper(@NonNull String pkgName, @NonNull String className,
                                    @Nullable Runnable deathCallback) {
        this(new ComponentName(pkgName, className), deathCallback);
    }

    public ServiceConnectionWrapper(@NonNull ComponentName cn) {
        this(cn, null);
    }

    public ServiceConnectionWrapper(@NonNull ComponentName cn, @Nullable Runnable deathCallback) {
        mComponentName = cn;
        mDeathCallback = deathCallback;
        mServiceConnection = new ServiceConnectionImpl();
    }

    @NonNull
    public IBinder getService() throws RemoteException {
        if (!isBinderActive()) {
            throw new RemoteException("Binder not running.");
        }
        return Objects.requireNonNull(mIBinder);
    }

    @NonNull
    @NoOps(used = true)
    public IBinder bindService() throws RemoteException {
        synchronized (mServiceConnection) {
            if (!isBinderActive()) {
                startDaemon();
            }
            return getService();
        }
    }

    @MainThread
    public void unbindService() {
        synchronized (mServiceConnection) {
            RootService.unbind(mServiceConnection);
        }
    }

    @WorkerThread
    private void startDaemon() {
        synchronized (mServiceConnection) {
            if (isBinderActive()) {
                Log.d(TAG, "Binder is already active?");
                return;
            }
            mServiceBoundWatcher = new CountDownLatch(1);
            Log.d(TAG, "Launching service...");
            Intent intent = new Intent();
            intent.setComponent(mComponentName);
            ThreadUtils.postOnMainThread(() -> {
                if (mIBinder != null) {
                    RootService.stop(intent);
                }
                RootService.bind(intent, mServiceConnection);
            });
            // Wait for service to be bound
            try {
                mServiceBoundWatcher.await(45, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Service watcher interrupted.");
            }
        }
    }

    @WorkerThread
    public void stopDaemon() {
        Intent intent = new Intent();
        intent.setComponent(mComponentName);
        ThreadUtils.postOnMainThread(() -> RootService.stop(intent));
        mIBinder = null;
    }

    boolean isBinderActive() {
        return mIBinder != null && mIBinder.pingBinder();
    }
}
